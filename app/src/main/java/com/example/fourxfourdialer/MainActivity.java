package com.example.fourxfourdialer;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.media.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
 private static final UUID HFP_AG=UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");
 private static final UUID HSP_AG=UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");
 private AudioManager audio; private AudioRecord recorder; private BluetoothAdapter adapter; private BluetoothSocket socket; private OutputStream output;
 private volatile boolean connected=false; private volatile boolean stop=false;
 private BluetoothDevice selected; private TextView log; private EditText number; private Spinner devices;
 private final ArrayList<BluetoothDevice> paired=new ArrayList<>();
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final Object connectionLock=new Object();
 private final BroadcastReceiver sdpReceiver=new BroadcastReceiver(){
  @Override public void onReceive(Context context,Intent intent){
   if(!BluetoothDevice.ACTION_UUID.equals(intent.getAction()))return;
   BluetoothDevice device;
   if(Build.VERSION.SDK_INT>=33)device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice.class);
   else device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
   if(device==null||selected==null||!device.getAddress().equals(selected.getAddress()))return;
   android.os.Parcelable[] values=intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
   note("SDP: התקבלה תשובה"+(values==null?" (ללא רשימה)":"; שירותים: "+values.length));
   if(values!=null)for(android.os.Parcelable p:values)note("UUID: "+p.toString());
   note("הערה: שירותים שלא פורסמו ב-SDP לא יופיעו ברשימה");
  }
 };
 private void note(String s){ui.post(()->{if(log!=null){log.append(s+"\n");}});}
 private void add(LinearLayout r,String label,Runnable action){Button b=new Button(this);b.setText(label);r.addView(b);b.setOnClickListener(v->action.run());}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(20,16,20,16);
  TextView title=new TextView(this);title.setText("K-Dial | אבחון Bluetooth 3");title.setTextSize(22);root.addView(title);
  devices=new Spinner(this);root.addView(devices);
  add(root,"רענון מכשירים",this::refresh);
  add(root,"בדיקת שירותי Bluetooth (SDP)",this::discover);
  add(root,"אבחון מלא: מערכת, פרופילים ושמע",this::diagnose);
  add(root,"בדיקת נתיבי שמע",this::audioRoutes);
  add(root,"בדיקת מיקרופון (3 שניות)",this::micTest);
  add(root,"השמעת צליל בדיקה",this::toneTest);
  add(root,"ניסיון SCO שמע Bluetooth (10 שניות)",this::scoTest);
  add(root,"בדיקת פקודות AT ללא חיוג",this::atTest);
  add(root,"בדיקת HFP מאובטח",()->connect(HFP_AG,false));
  add(root,"בדיקת HFP לא מאובטח",()->connect(HFP_AG,true));
  add(root,"בדיקת Headset מאובטח",()->connect(HSP_AG,false));
  number=new EditText(this);number.setHint("מספר לחיוג (רק אחרי חיבור)");number.setInputType(InputType.TYPE_CLASS_PHONE);root.addView(number);
  add(root,"חייג",()->{String n=number.getText().toString().trim();if(!n.matches("[0-9+*#]{2,25}")){note("מספר לא תקין");return;}send("ATD"+n+";");});
  add(root,"נתק שיחה",()->send("AT+CHUP"));add(root,"סגור חיבור",this::disconnect);
  add(root,"העתק יומן",()->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("K-Dial diagnostic",log.getText()));Toast.makeText(this,"היומן הועתק",Toast.LENGTH_SHORT).show();});
  TextView warning=new TextView(this);warning.setText("גרסת אבחון בלבד. אין מימוש שמע בטאבלט. אין הבטחה ש-HFP זמין לאפליקציה רגילה.");root.addView(warning);
  ScrollView scroll=new ScrollView(this);log=new TextView(this);log.setTextIsSelectable(true);scroll.addView(log);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  audio=(AudioManager)getSystemService(AUDIO_SERVICE); BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
  IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_UUID);
  if(Build.VERSION.SDK_INT>=33)registerReceiver(sdpReceiver,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(sdpReceiver,filter);
  if(Build.VERSION.SDK_INT>=31&&!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.RECORD_AUDIO},10);else refresh();
 }
 private void diagnose(){
  note("--- מערכת ---");note("יצרן: "+Build.MANUFACTURER+" דגם: "+Build.MODEL+" SDK: "+Build.VERSION.SDK_INT);
  note("Bluetooth: "+(adapter==null?"לא קיים":adapter.isEnabled()?"פעיל":"כבוי"));
  note("הרשאת Bluetooth: "+permitted()+"; הרשאת מיקרופון: "+micPermitted());
  BluetoothDevice d=choice();if(d!=null){try{note("יעד: "+d.getName()+" "+d.getAddress()+" bond="+d.getBondState()+" type="+d.getType());android.os.ParcelUuid[] u=d.getUuids();if(u!=null)for(android.os.ParcelUuid x:u)note("שירות שמור: "+x);else note("אין UUID שמורים");}catch(Exception e){note("יעד: "+e);}}
  if(adapter!=null&&permitted())for(int profile:new int[]{BluetoothProfile.HEADSET,BluetoothProfile.A2DP,BluetoothProfile.HEALTH,BluetoothProfile.GATT}){
   try{boolean requested=adapter.getProfileProxy(this,new BluetoothProfile.ServiceListener(){
    public void onServiceConnected(int p,BluetoothProfile proxy){try{note("פרופיל "+p+" מחובר למכשירים: "+proxy.getConnectedDevices().size());for(BluetoothDevice bd:proxy.getConnectedDevices())note("פרופיל "+p+": "+bd.getName()+" "+bd.getAddress());}catch(Exception e){note("פרופיל "+p+" שגיאה: "+e);}finally{adapter.closeProfileProxy(p,proxy);}}
    public void onServiceDisconnected(int p){note("פרופיל "+p+" נותק");}},profile);note("בקשת פרופיל "+profile+": "+requested);}catch(Exception e){note("פרופיל "+profile+": "+e);}}
  audioRoutes();discover();
 }
 private boolean micPermitted(){return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}
 private void audioRoutes(){if(audio==null){note("אין AudioManager");return;}note("--- שמע ---");
  note("מצב שמע: "+audio.getMode()+"; מוסיקה פעילה: "+audio.isMusicActive()+"; SCO פעיל: "+audio.isBluetoothScoOn());
  if(Build.VERSION.SDK_INT>=23)for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_ALL))note("נתיב: "+d.getProductName()+" type="+d.getType()+" source="+d.isSource()+" sink="+d.isSink());
  if(Build.VERSION.SDK_INT>=31){AudioDeviceInfo dev=audio.getCommunicationDevice();note("נתיב תקשורת: "+(dev==null?"אין":dev.getProductName()+" type="+dev.getType()));}
 }
 private void micTest(){if(!micPermitted()){note("נדרשת הרשאת מיקרופון; בקש בהגדרות האפליקציה");return;}
  new Thread(()->{int rate=16000;int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0){note("מיקרופון: גודל מאגר לא תקין "+min);return;}AudioRecord r=null;
   try{r=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,2048));recorder=r;if(r.getState()!=AudioRecord.STATE_INITIALIZED){note("המיקרופון לא אותחל");return;}r.startRecording();short[] b=new short[1024];long sum=0,count=0;long end=System.currentTimeMillis()+3000;while(System.currentTimeMillis()<end){int n=r.read(b,0,b.length);if(n>0){for(int i=0;i<n;i++){sum+=(long)b[i]*b[i];}count+=n;}else{note("שגיאת קריאה מהמיקרופון: "+n);break;}}note("מיקרופון VOICE_COMMUNICATION: RMS="+(count==0?"אין דגימות":String.format(java.util.Locale.US,"%.1f",Math.sqrt((double)sum/count)))+"; דגימות="+count);}
   catch(Exception e){note("מיקרופון: "+e);}finally{if(r!=null){try{r.stop();}catch(Exception ignored){}r.release();}recorder=null;}
  }).start();}
 private void toneTest(){new Thread(()->{ToneGenerator t=null;try{t=new ToneGenerator(AudioManager.STREAM_MUSIC,65);t.startTone(ToneGenerator.TONE_PROP_BEEP,700);Thread.sleep(850);note("צליל נשלח; בדוק מאיזה רמקול נשמע");}catch(Exception e){note("צליל: "+e);}finally{if(t!=null)t.release();}}).start();}
 private void scoTest(){if(audio==null)return;note("ניסיון SCO; אין הבטחה שהמערכת תאפשר חיבור לטלפון");new Thread(()->{try{if(Build.VERSION.SDK_INT>=31){AudioDeviceInfo target=null;for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices())if(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO){target=d;break;}if(target==null){note("אין התקן Bluetooth SCO זמין לניתוב תקשורת");return;}boolean ok=audio.setCommunicationDevice(target);note("setCommunicationDevice SCO: "+ok);Thread.sleep(10000);audio.clearCommunicationDevice();}
  else{audio.startBluetoothSco();Thread.sleep(1500);note("SCO לאחר בקשה: "+audio.isBluetoothScoOn());Thread.sleep(8500);audio.stopBluetoothSco();}note("בדיקת SCO הסתיימה");}catch(Exception e){note("SCO: "+e);}}).start();}
 private void atTest(){new Thread(()->{for(String c:new String[]{"AT","AT+BRSF?","AT+CIND?","AT+CLCC","AT+CSQ"}){try{sendInternal(c);Thread.sleep(350);}catch(Exception e){note("AT: "+e.getMessage());break;}}}).start();}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] grants){super.onRequestPermissionsResult(req,permissions,grants);if(req==10){if(permitted())refresh();else note("נדרשת הרשאת מכשירים בקרבת מקום");if(!micPermitted())note("הרשאת מיקרופון לא ניתנה");}}
 private void refresh(){if(adapter==null){note("אין מתאם Bluetooth");return;}if(!permitted()){note("אין הרשאת Bluetooth");return;}paired.clear();ArrayList<String> names=new ArrayList<>();try{for(BluetoothDevice d:adapter.getBondedDevices()){paired.add(d);names.add(d.getName()+" ("+d.getAddress()+")");}}catch(SecurityException e){note(e.toString());}devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));note("מכשירים מצומדים: "+paired.size());}
 private BluetoothDevice choice(){if(!permitted()){note("אין הרשאה");return null;}int i=devices.getSelectedItemPosition();if(i<0||i>=paired.size()){note("בחר מכשיר מצומד");return null;}return paired.get(i);}
 private void discover(){BluetoothDevice d=choice();if(d==null)return;selected=d;try{android.os.ParcelUuid[] cached=d.getUuids();note("UUID שמורים במטמון: "+(cached==null?"אין":cached.length));if(cached!=null)for(android.os.ParcelUuid u:cached)note("מטמון: "+u);boolean started=d.fetchUuidsWithSdp();note("בקשת SDP נשלחה: "+started+"; המתן לתוצאה");}catch(Exception e){note("SDP נכשל: "+e.getMessage());}}
 private void connect(UUID uuid,boolean insecure){BluetoothDevice d=choice();if(d==null)return;disconnect();stop=false;note("מנסה "+uuid+(insecure?" ללא הצפנת RFCOMM":" עם RFCOMM מאובטח"));new Thread(()->{
  BluetoothSocket s=null;
  try{s=insecure?d.createInsecureRfcommSocketToServiceRecord(uuid):d.createRfcommSocketToServiceRecord(uuid);
   synchronized(connectionLock){socket=s;}
   s.connect();if(stop){s.close();return;}
   synchronized(connectionLock){output=s.getOutputStream();connected=true;}
   note("RFCOMM מחובר. מנסה AT handshake");
   InputStream input=s.getInputStream();Thread reader=new Thread(()->{try{byte[] buffer=new byte[1024];int count;while(!stop&&(count=input.read(buffer))!=-1)note("תגובה: "+new String(buffer,0,count,StandardCharsets.US_ASCII).replace("\r"," ").trim());}catch(IOException e){if(!stop)note("קריאה הסתיימה: "+e.getMessage());}});reader.start();
   sendInternal("AT+BRSF=0");Thread.sleep(500);sendInternal("AT+CIND=?");Thread.sleep(500);sendInternal("AT+CIND?");Thread.sleep(500);sendInternal("AT+CMER=3,0,0,1");
  }catch(Exception e){if(!stop)note("ניסיון חיבור נכשל: "+e.getClass().getSimpleName()+": "+e.getMessage());}
  finally{if(s!=null&&!connected){try{s.close();}catch(Exception ignored){}}}
 }).start();}
 private void sendInternal(String command)throws IOException{synchronized(connectionLock){if(!connected||output==null)throw new IOException("אין חיבור פעיל");output.write((command+"\r").getBytes(StandardCharsets.US_ASCII));output.flush();}note("נשלח: "+command);}
 private void send(String command){new Thread(()->{try{sendInternal(command);}catch(Exception e){note("שליחה נכשלה: "+e.getMessage());}}).start();}
 private void disconnect(){stop=true;connected=false;synchronized(connectionLock){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;output=null;}note("החיבור נסגר");}
 @Override protected void onDestroy(){disconnect();unregisterReceiver(sdpReceiver);super.onDestroy();}
}
