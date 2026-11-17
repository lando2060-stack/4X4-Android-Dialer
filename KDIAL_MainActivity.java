package com.example.fourxfourdialer;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
 private static final UUID HFP=UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");
 private final Handler ui=new Handler(Looper.getMainLooper());
 private BluetoothAdapter adapter; private BluetoothSocket socket; private OutputStream output;
 private volatile boolean connected=false,stopping=false,busy=false;
 private final Object lock=new Object();
 private WebView web; private Spinner devices; private TextView status; private final ArrayList<BluetoothDevice> paired=new ArrayList<>();
 private ValueCallback<Uri[]> uploadCallback;
 private void log(String s){android.util.Log.i("KDial",s);ui.post(()->{if(status!=null)status.setText(s);});}
 private void js(String code){ui.post(()->{if(web!=null)web.evaluateJavascript(code,null);});}
 private void reply(int id,boolean ok,String message){if(id>0)js("window.kdialReply("+id+","+ok+","+org.json.JSONObject.quote(message)+")");}
 private void report(){js("window.kdialStatus("+connected+","+busy+","+org.json.JSONObject.quote(connected?"HFP RFCOMM מחובר":"בחר טלפון ולחץ התחבר")+")");}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
  devices=new Spinner(this); // selection is managed inside the HTML settings screen
  status=new TextView(this);status.setText("ממתין לחיבור Bluetooth");
  web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(true);
  web.addJavascriptInterface(new Bridge(),"KDialAndroid");
  web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"file".equalsIgnoreCase(request.getUrl().getScheme());}});
  web.setWebChromeClient(new WebChromeClient(){@Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams params){if(uploadCallback!=null)uploadCallback.onReceiveValue(null);uploadCallback=cb;try{startActivityForResult(params.createIntent(),1001);return true;}catch(Exception e){uploadCallback=null;return false;}}});
  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  web.loadUrl("file:///android_asset/index.html");
  if(Build.VERSION.SDK_INT>=31&&!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},10);else refresh();
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1001&&uploadCallback!=null){uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));uploadCallback=null;}}
 private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){super.onRequestPermissionsResult(req,p,g);if(req==10&&permitted())refresh();}
 private void refresh(){if(adapter==null||!permitted()){log("Bluetooth אינו זמין או חסרה הרשאה");return;}paired.clear();ArrayList<String> names=new ArrayList<>();try{for(BluetoothDevice d:adapter.getBondedDevices()){paired.add(d);names.add(d.getName()+" ("+d.getAddress()+")");}}catch(Exception e){log("שגיאת מכשירים: "+e.getMessage());}devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));for(int i=0;i<paired.size();i++)if("4X4".equalsIgnoreCase(paired.get(i).getName())){devices.setSelection(i);break;}log("נמצאו "+paired.size()+" מכשירים מצומדים");publishDevices();}
 private void publishDevices(){org.json.JSONArray a=new org.json.JSONArray();for(int i=0;i<paired.size();i++){try{org.json.JSONObject d=new org.json.JSONObject();d.put("index",i);d.put("name",paired.get(i).getName());d.put("address",paired.get(i).getAddress());a.put(d);}catch(Exception ignored){}}js("window.kdialDevices("+a.toString()+")");}
 private void connectSelected(int id,int index){if(!permitted()){reply(id,false,"נדרשת הרשאת Bluetooth");return;}if(index<0||index>=paired.size()){reply(id,false,"בחר מכשיר מהרשימה");return;}devices.setSelection(index);connect(id);}
 private void connect(int requestId){if(!permitted()){reply(requestId,false,"נדרשת הרשאת Bluetooth");return;}int i=devices.getSelectedItemPosition();if(i<0||i>=paired.size()){reply(requestId,false,"בחר מכשיר מצומד");return;}BluetoothDevice target=paired.get(i);disconnect();stopping=false;log("מתחבר ל-"+target.getName());new Thread(()->{BluetoothSocket s=null;try{if(adapter!=null)adapter.cancelDiscovery();s=target.createRfcommSocketToServiceRecord(HFP);s.connect();if(stopping){s.close();return;}synchronized(lock){socket=s;output=s.getOutputStream();connected=true;}log("Bluetooth HFP מחובר");report();reply(requestId,true,"מחובר");final BluetoothSocket active=s;new Thread(()->read(active)).start();sendRaw("AT+BRSF=0");Thread.sleep(250);sendRaw("AT+CIND=?");Thread.sleep(250);sendRaw("AT+CIND?");Thread.sleep(250);sendRaw("AT+CMER=3,0,0,1");}catch(Exception e){log("חיבור נכשל: "+e.getMessage());reply(requestId,false,"חיבור נכשל: "+e.getMessage());if(s!=null&&!connected)try{s.close();}catch(Exception ignored){}report();}}).start();}
 private void read(BluetoothSocket s){try{InputStream in=s.getInputStream();byte[] buf=new byte[2048];int n;StringBuilder sb=new StringBuilder();while(!stopping&&(n=in.read(buf))!=-1){sb.append(new String(buf,0,n,StandardCharsets.US_ASCII));int idx;while((idx=sb.indexOf("\n"))>=0){String line=sb.substring(0,idx).trim();sb.delete(0,idx+1);if(!line.isEmpty()){log("HFP: "+line);if(line.startsWith("+CIEV:")){/* indicators are device-specific */}if(line.equals("NO CARRIER")){busy=false;report();}}}}}catch(Exception e){if(!stopping)log("חיבור נותק: "+e.getMessage());}finally{if(!stopping)disconnect();}}
 private void sendRaw(String cmd)throws IOException{synchronized(lock){if(!connected||output==null)throw new IOException("אין חיבור HFP");output.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));output.flush();}log("AT: "+cmd.replaceAll("ATD.*;","ATD[מספר מוסתר];"));}
 private void command(int id,String cmd,String phone){if("status".equals(cmd)){report();publishDevices();return;}if("refresh".equals(cmd)){ui.post(()->{refresh();reply(id,true,"רשימת מכשירים עודכנה");});return;}if("connectSelected".equals(cmd)){try{int selectedIndex=Integer.parseInt(phone);ui.post(()->connectSelected(id,selectedIndex));}catch(Exception e){reply(id,false,"בחירה לא תקינה");}return;}if("connect".equals(cmd)){connect(id);return;}if("disconnect".equals(cmd)){disconnect();reply(id,true,"נותק");return;}if("dial".equals(cmd)){if(phone==null||!phone.matches("\\+?[0-9]{3,20}")){reply(id,false,"מספר לא תקין");return;}new Thread(()->{try{sendRaw("ATD"+phone+";");busy=true;report();reply(id,true,"פקודת חיוג נשלחה");}catch(Exception e){reply(id,false,e.getMessage());}}).start();return;}if("hangup".equals(cmd)){new Thread(()->{try{sendRaw("AT+CHUP");busy=false;report();reply(id,true,"פקודת ניתוק נשלחה");}catch(Exception e){reply(id,false,e.getMessage());}}).start();}}
 private void disconnect(){stopping=true;connected=false;busy=false;synchronized(lock){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;output=null;}report();}
 public class Bridge{@JavascriptInterface public void command(int id,String action,String value){MainActivity.this.command(id,action,value);}
  @JavascriptInterface public void exportCsv(String csv){try{String name="KDial_contacts_"+System.currentTimeMillis()+".csv";if(Build.VERSION.SDK_INT>=29){android.content.ContentValues v=new android.content.ContentValues();v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,name);v.put(android.provider.MediaStore.Downloads.MIME_TYPE,"text/csv");v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,"Download/");Uri uri=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("Cannot create download");try(OutputStream o=getContentResolver().openOutputStream(uri)){o.write(csv.getBytes(StandardCharsets.UTF_8));}}else{File dir=getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);try(FileOutputStream o=new FileOutputStream(new File(dir,name))){o.write(csv.getBytes(StandardCharsets.UTF_8));}}log("CSV נשמר: "+name);}catch(Exception e){log("שגיאת ייצוא CSV: "+e.getMessage());}}
 }
 @Override protected void onDestroy(){disconnect();if(web!=null){web.removeJavascriptInterface("KDialAndroid");web.destroy();}super.onDestroy();}
}
