package com.example.fourxfourdialer;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
 private static final UUID HFP=UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");
 private static final UUID HSP=UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");
 private final StringBuilder diagnostics=new StringBuilder();
 private String connectionMethod="";
 private int connectionGeneration=0;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private BluetoothAdapter adapter; private BluetoothSocket socket; private OutputStream output;
 private volatile boolean connected=false,stopping=false,busy=false;
 private final Object lock=new Object();
 private WebView web; private Spinner devices; private TextView status; private final ArrayList<BluetoothDevice> paired=new ArrayList<>();
 private ValueCallback<Uri[]> uploadCallback;
 private volatile boolean audioTesting=false;
 private AudioManager audio;
 private void log(String s){android.util.Log.i("KDial",s);synchronized(diagnostics){diagnostics.append(new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date())).append("  ").append(s).append("\n");if(diagnostics.length()>30000)diagnostics.delete(0,diagnostics.length()-25000);}ui.post(()->{if(status!=null)status.setText(s);});}
 private void showDiagnostics(){String body; synchronized(diagnostics){body=diagnostics.toString();}final String text=body;new AlertDialog.Builder(this).setTitle("K-Dial | יומן חיבור Bluetooth").setMessage(text.isEmpty()?"אין עדיין אירועים":text).setPositiveButton("סגור",null).setNeutralButton("העתק יומן",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("K-Dial log",text));Toast.makeText(this,"היומן הועתק",Toast.LENGTH_SHORT).show();}).show();}
 private void js(String code){ui.post(()->{if(web!=null)web.evaluateJavascript(code,null);});}
 private void reply(int id,boolean ok,String message){if(id>0)js("window.kdialReply("+id+","+ok+","+org.json.JSONObject.quote(message)+")");}
 private void report(){js("window.kdialStatus("+connected+","+busy+","+org.json.JSONObject.quote(connected?"Bluetooth RFCOMM מחובר: "+connectionMethod:"בחר טלפון ולחץ התחבר")+")");}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  audio=(AudioManager)getSystemService(AUDIO_SERVICE);
  BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  devices=new Spinner(this); // selection is managed inside the HTML settings screen
  status=new TextView(this);status.setText("ממתין לחיבור Bluetooth");
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(true);
  web.addJavascriptInterface(new Bridge(),"KDialAndroid");
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"file".equalsIgnoreCase(request.getUrl().getScheme());}});
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){if(uploadCallback!=null)uploadCallback.onReceiveValue(null);uploadCallback=cb;try{startActivityForResult(params.createIntent(),1001);return true;}catch(Exception e){uploadCallback=null;return false;}}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  Button audioButton=new Button(this);audioButton.setText("בדיקת כל אפשרויות השמע | SCO, ניתוב ומיקרופון");audioButton.setAllCaps(false);audioButton.setOnClickListener(v->startAudioTests());root.addView(audioButton,new LinearLayout.LayoutParams(-1,-2));
  Button advancedButton=new Button(this);advancedButton.setText("אבחון Bluetooth מתקדם | HFP Client ופרופילים");advancedButton.setAllCaps(false);advancedButton.setOnClickListener(v->runAdvancedBluetoothDiagnostics());root.addView(advancedButton,new LinearLayout.LayoutParams(-1,-2));
  Button diagnosticsButton=new Button(this);diagnosticsButton.setText("יומן חיבור Bluetooth | הצג הצלחות ושגיאות");diagnosticsButton.setAllCaps(false);diagnosticsButton.setOnClickListener(v->showDiagnostics());root.addView(diagnosticsButton,new LinearLayout.LayoutParams(-1,-2));setContentView(root);
  web.loadUrl("file:///android_asset/index.html");
  if(Build.VERSION.SDK_INT>=31&&!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},10);else refresh();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1001&&uploadCallback!=null){uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));uploadCallback=null;}}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==10&&permitted())refresh();}
 private void refresh(){if(adapter==null||!permitted()){log("Bluetooth אינו זמין או חסרה הרשאה");return;}paired.clear();ArrayList<String> names=new ArrayList<>();try{for(BluetoothDevice d:adapter.getBondedDevices()){paired.add(d);names.add(d.getName()+" ("+d.getAddress()+")");}}catch(Exception e){log("שגיאת מכשירים: "+e.getMessage());}devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));for(int i=0;i<paired.size();i++)if("4X4".equalsIgnoreCase(paired.get(i).getName())){devices.setSelection(i);break;}log("נמצאו "+paired.size()+" מכשירים מצומדים");publishDevices();}
 private void publishDevices(){org.json.JSONArray a=new org.json.JSONArray();for(int i=0;i<paired.size();i++){try{org.json.JSONObject d=new org.json.JSONObject();d.put("index",i);d.put("name",paired.get(i).getName());d.put("address",paired.get(i).getAddress());a.put(d);}catch(Exception ignored){}}js("window.kdialDevices("+a.toString()+")");}
 private void connectSelected(int id,int index){if(!permitted()){reply(id,false,"נדרשת הרשאת Bluetooth");return;}if(index<0||index>=paired.size()){reply(id,false,"בחר מכשיר מהרשימה");return;}devices.setSelection(index);connect(id);}
 private void connect(int requestId){
  if(!permitted()){reply(requestId,false,"נדרשת הרשאת Bluetooth");return;}
  int i=devices.getSelectedItemPosition();if(i<0||i>=paired.size()){reply(requestId,false,"בחר מכשיר מצומד");return;}
  BluetoothDevice target=paired.get(i);
  disconnect();stopping=false;final int generation=++connectionGeneration;
  log("מתחיל חיבור ל-"+target.getName()+" ("+target.getAddress()+")");
  new Thread(()->{
   String[] labels={"HFP מאובטח","HFP לא מאובטח","HSP מאובטח","HSP לא מאובטח"};
   UUID[] uuids={HFP,HFP,HSP,HSP};boolean[] insecure={false,true,false,true};
   StringBuilder failures=new StringBuilder();
   try{if(adapter!=null)adapter.cancelDiscovery();}catch(Exception e){log("ביטול סריקה: "+e.getMessage());}
   for(int attempt=0;attempt<labels.length;attempt++){
    if(stopping||generation!=connectionGeneration){log("החיבור בוטל");return;}
    BluetoothSocket candidate=null;
    String method=labels[attempt];long started=System.currentTimeMillis();
    log("ניסיון "+(attempt+1)+"/4: "+method);
    try{
     candidate=insecure[attempt]?target.createInsecureRfcommSocketToServiceRecord(uuids[attempt]):target.createRfcommSocketToServiceRecord(uuids[attempt]);
     candidate.connect();
     if(stopping||generation!=connectionGeneration){candidate.close();return;}
     synchronized(lock){socket=candidate;output=candidate.getOutputStream();connected=true;connectionMethod=method;}
     log("הצלחה: "+method+" ("+(System.currentTimeMillis()-started)+" ms)");
     report();reply(requestId,true,"מחובר באמצעות "+method);
     final BluetoothSocket active=candidate;new Thread(()->read(active)).start();
     try{
      sendRaw("AT+BRSF=0");Thread.sleep(300);
      sendRaw("AT+CIND=?");Thread.sleep(300);
      sendRaw("AT+CIND?");Thread.sleep(300);
      sendRaw("AT+CMER=3,0,0,1");
      log("נשלחו פקודות אתחול AT; בדוק ביומן את תשובות הטלפון");
     }catch(Exception e){log("שגיאת אתחול AT אחרי חיבור: "+e.getClass().getSimpleName()+": "+e.getMessage());}
     return;
    }catch(Exception e){
     String why=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
     log("נכשל: "+method+" לאחר "+(System.currentTimeMillis()-started)+" ms | "+why);
     failures.append(method).append(": ").append(why).append("; ");
     try{if(candidate!=null)candidate.close();}catch(Exception ignored){}
    }
   }
   if(stopping||generation!=connectionGeneration)return;
   log("כל ארבע שיטות החיבור נכשלו: "+failures);
   report();reply(requestId,false,"כל שיטות החיבור נכשלו. פתח יומן חיבור Bluetooth לפרטים.");
  },"KDial-Connect").start();
 }
 private void read(BluetoothSocket s){try{InputStream in=s.getInputStream();byte[] buf=new byte[2048];int n;StringBuilder sb=new StringBuilder();while(!stopping&&(n=in.read(buf))!=-1){sb.append(new String(buf,0,n,StandardCharsets.US_ASCII));int idx;while((idx=sb.indexOf("\n"))>=0){String line=sb.substring(0,idx).trim();sb.delete(0,idx+1);if(!line.isEmpty()){log("HFP: "+line);if(line.startsWith("+CIEV:")){/* indicators are device-specific */}if(line.equals("NO CARRIER")){busy=false;report();}}}}}catch(Exception e){if(!stopping)log("חיבור נותק: "+e.getMessage());}finally{if(!stopping&&socket==s)disconnect();}}
 private String audioDeviceName(AudioDeviceInfo d){return "type="+d.getType()+" name="+d.getProductName()+" input="+d.isSource()+" output="+d.isSink();}
 private void audioSnapshot(String label){
  log("שמע "+label+": mode="+audio.getMode()+" scoOn="+audio.isBluetoothScoOn()+" speaker="+audio.isSpeakerphoneOn()+" offCallSco="+audio.isBluetoothScoAvailableOffCall());
  if(Build.VERSION.SDK_INT>=31){
   AudioDeviceInfo current=audio.getCommunicationDevice();log("התקן תקשורת נוכחי: "+(current==null?"אין":audioDeviceName(current)));
   for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices())log("התקן תקשורת זמין: "+audioDeviceName(d));
  }
  for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_ALL))log("התקן שמע: "+audioDeviceName(d));
 }
 private void startAudioTests(){
  if(audioTesting){log("בדיקת שמע כבר פועלת");showDiagnostics();return;}
  if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   log("נדרשת הרשאת מיקרופון. אשר ואז לחץ שוב על בדיקת השמע");requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},12);return;
  }
  if(!connected)log("אזהרה: אין חיבור HFP. בדיקת הניתוב תתבצע אך לא תוכל לאמת שמע שיחה");
  audioTesting=true;
  new Thread(()->{
   int oldMode=audio.getMode();boolean oldSpeaker=audio.isSpeakerphoneOn();boolean oldSco=audio.isBluetoothScoOn();
   AudioDeviceInfo oldComm=Build.VERSION.SDK_INT>=31?audio.getCommunicationDevice():null;
   try{
    log("========== KDial-Audio: בדיקה משולבת ==========");
    log("הבדיקה בודקת זמינות ניתוב ולא מוכיחה ששמע השיחה הגיע לטאבלט");
    audioSnapshot("מצב התחלתי");
    audio.setMode(AudioManager.MODE_IN_COMMUNICATION);log("מצב MODE_IN_COMMUNICATION הוגדר");
    audioSnapshot("תקשורת");
    if(Build.VERSION.SDK_INT>=31){
     for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices()){
      int t=d.getType();
      if(t==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||t==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER||t==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE||t==AudioDeviceInfo.TYPE_WIRED_HEADSET||t==AudioDeviceInfo.TYPE_USB_HEADSET){
       try{boolean ok=audio.setCommunicationDevice(d);log("setCommunicationDevice "+audioDeviceName(d)+" => "+ok);Thread.sleep(600);audioSnapshot("אחרי בחירת התקן");}catch(Exception e){log("כשל ניתוב התקן: "+e);}
      }
     }
     audio.clearCommunicationDevice();
    }
    audio.setSpeakerphoneOn(false);log("רמקול כבוי; ניסיון startBluetoothSco");
    try{audio.startBluetoothSco();log("startBluetoothSco נקרא");}catch(Exception e){log("startBluetoothSco נכשל: "+e);}
    for(int i=0;i<5;i++){Thread.sleep(1000);log("SCO שנייה "+(i+1)+": "+audio.isBluetoothScoOn());}
    try{audio.setBluetoothScoOn(true);log("setBluetoothScoOn(true) נקרא");}catch(Exception e){log("setBluetoothScoOn נכשל: "+e);}
    audioSnapshot("אחרי SCO");
    audio.setSpeakerphoneOn(true);log("רמקול מופעל לבדיקה");audioSnapshot("רמקול");
    int min=AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    if(min>0){
     AudioRecord rec=null;
     try{rec=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,2048));
      if(rec.getState()==AudioRecord.STATE_INITIALIZED){rec.startRecording();short[] samples=new short[1024];long total=0;int count=0;for(int i=0;i<8;i++){int n=rec.read(samples,0,samples.length);if(n>0){for(int j=0;j<n;j++)total+=Math.abs((int)samples[j]);count+=n;}}log("בדיקת מיקרופון מקומי: "+(count>0?"נקלטו "+count+" דגימות, עוצמה ממוצעת="+(total/count):"לא התקבלו דגימות"));rec.stop();}else log("מיקרופון: AudioRecord לא אותחל");
     }catch(Exception e){log("שגיאת מיקרופון: "+e);}finally{if(rec!=null)rec.release();}
    }else log("מיקרופון: גודל buffer לא תקין: "+min);
    log("========== בדיקת שמע הסתיימה ==========");
   }catch(Exception e){log("שגיאת בדיקת שמע: "+e);}finally{
    try{audio.stopBluetoothSco();audio.setBluetoothScoOn(oldSco);audio.setSpeakerphoneOn(oldSpeaker);if(Build.VERSION.SDK_INT>=31){audio.clearCommunicationDevice();if(oldComm!=null)audio.setCommunicationDevice(oldComm);}audio.setMode(oldMode);log("הגדרות השמע המקוריות שוחזרו");}catch(Exception e){log("שגיאת שחזור שמע: "+e);}
    audioTesting=false;ui.post(()->showDiagnostics());
   }
  },"KDial-Audio-Tests").start();
 }
 private void runAdvancedBluetoothDiagnostics(){
  log("========== אבחון Bluetooth מתקדם ==========");
  log("מכשיר="+Build.MANUFACTURER+" "+Build.MODEL+" Android="+Build.VERSION.RELEASE+" API="+Build.VERSION.SDK_INT);
  log("הרשאת BLUETOOTH_CONNECT="+(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED));
  log("הרשאת BLUETOOTH_SCAN="+(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED));
  log("הרשאת RECORD_AUDIO="+(Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED));
  if(adapter==null){log("אין מתאם Bluetooth");showDiagnostics();return;}
  log("Bluetooth enabled="+adapter.isEnabled()+" RFCOMM connected="+connected+" method="+connectionMethod);
  log("בדיקת HFP Client: פרופיל 16 הוא מזהה Android פנימי; קבלת Proxy אינה הוכחה לניתוב שמע");
  try{
   boolean requested=adapter.getProfileProxy(this,new BluetoothProfile.ServiceListener(){
    @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
     log("HFP Client callback CONNECTED profile="+profile+" class="+proxy.getClass().getName());
     try{log("HFP Client connected devices="+proxy.getConnectedDevices().size());for(BluetoothDevice d:proxy.getConnectedDevices())log("HFP Client device="+d.getName()+" "+d.getAddress());}catch(Exception e){log("HFP Client device read error="+e);}
     try{adapter.closeProfileProxy(profile,proxy);}catch(Exception e){log("HFP Client close error="+e);}
    }
    @Override public void onServiceDisconnected(int profile){log("HFP Client callback DISCONNECTED profile="+profile);}
   },16);
   log("HFP Client getProfileProxy(16) request="+requested+" (callback may arrive later)");
  }catch(Throwable e){log("HFP Client access failed="+e.getClass().getSimpleName()+": "+e.getMessage());}
  try{
   for(BluetoothDevice d:adapter.getBondedDevices()){
    if(!"4X4".equalsIgnoreCase(d.getName())&&!"4C:11:54:B2:9E:38".equalsIgnoreCase(d.getAddress()))continue;
    log("Target="+d.getName()+" bond="+d.getBondState()+" type="+d.getType());
    android.os.ParcelUuid[] uuids=d.getUuids();
    if(uuids==null)log("Target UUIDs: null (not necessarily unsupported)");
    else for(android.os.ParcelUuid u:uuids)log("Target UUID="+u.toString());
   }
  }catch(Throwable e){log("Target UUID read failed="+e);}
  try{audioSnapshot("אבחון מתקדם (ללא שינוי ניתוב)");}catch(Throwable e){log("Audio snapshot failed="+e);}
  try{
   PackageManager pm=getPackageManager();
   log("Feature bluetooth="+pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));
   log("Feature bluetooth_le="+pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));
  }catch(Throwable e){log("Feature check failed="+e);}
  log("========== אבחון ראשוני הסתיים; המתן 3 שניות לתשובת HFP Client ==========");
  ui.postDelayed(()->showDiagnostics(),3000);
 }
 private void sendRaw(String cmd)throws IOException{synchronized(lock){if(!connected||output==null)throw new IOException("אין חיבור HFP");output.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));output.flush();}log("AT: "+cmd.replaceAll("ATD.*;","ATD[מספר מוסתר];"));}
 private void command(int id,String cmd,String phone){if("status".equals(cmd)){report();publishDevices();return;}if("refresh".equals(cmd)){ui.post(()->{refresh();reply(id,true,"רשימת מכשירים עודכנה");});return;}if("connectSelected".equals(cmd)){try{int selectedIndex=Integer.parseInt(phone);ui.post(()->connectSelected(id,selectedIndex));}catch(Exception e){reply(id,false,"בחירה לא תקינה");}return;}if("connect".equals(cmd)){connect(id);return;}if("disconnect".equals(cmd)){disconnect();reply(id,true,"נותק");return;}if("dial".equals(cmd)){if(phone==null||!phone.matches("\\+?[0-9]{3,20}")){reply(id,false,"מספר לא תקין");return;}new Thread(()->{try{sendRaw("ATD"+phone+";");busy=true;report();reply(id,true,"פקודת חיוג נשלחה");}catch(Exception e){reply(id,false,e.getMessage());}}).start();return;}if("hangup".equals(cmd)){new Thread(()->{try{sendRaw("AT+CHUP");busy=false;report();reply(id,true,"פקודת ניתוק נשלחה");}catch(Exception e){reply(id,false,e.getMessage());}}).start();}}
 private void disconnect(){stopping=true;connectionGeneration++;connected=false;busy=false;connectionMethod="";synchronized(lock){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;output=null;}report();}
 public class Bridge{@JavascriptInterface public void command(int id,String action,String value){MainActivity.this.command(id,action,value);}
  @JavascriptInterface public void exportCsv(String csv){try{String name="KDial_contacts_"+System.currentTimeMillis()+".csv";if(Build.VERSION.SDK_INT>=29){android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,name);v.put(android.provider.MediaStore.Downloads.MIME_TYPE,"text/csv");v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,"Download/");Uri uri=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("Cannot create download");try(OutputStream o=getContentResolver().openOutputStream(uri)){o.write(csv.getBytes(StandardCharsets.UTF_8));}}else{File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);try(FileOutputStream o=new FileOutputStream(new File(dir,name))){o.write(csv.getBytes(StandardCharsets.UTF_8));}}log("CSV נשמר: "+name);}catch(Exception e){log("שגיאת ייצוא CSV: "+e.getMessage());}}
 }
 @Override protected void onDestroy(){disconnect();if(web!=null){web.removeJavascriptInterface("KDialAndroid");web.destroy();}super.onDestroy();}
}
