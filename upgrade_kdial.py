from pathlib import Path
p=Path('app/src/main/java/com/example/fourxfourdialer/MainActivity.java')
s=p.read_text(encoding='utf-8')
assert 'private static final UUID HFP=' in s and 'private void connect(int requestId)' in s
s=s.replace(' private static final UUID HFP=UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");',' private static final UUID HFP=UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb");\n private static final UUID HSP=UUID.fromString("00001112-0000-1000-8000-00805f9b34fb");\n private final StringBuilder diagnostics=new StringBuilder();\n private String connectionMethod="";\n private int connectionGeneration=0;')
s=s.replace(' private void log(String s){android.util.Log.i("KDial",s);ui.post(()->{if(status!=null)status.setText(s);});}', ''' private void log(String s){android.util.Log.i("KDial",s);synchronized(diagnostics){diagnostics.append(new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date())).append("  ").append(s).append("\\n");if(diagnostics.length()>30000)diagnostics.delete(0,diagnostics.length()-25000);}ui.post(()->{if(status!=null)status.setText(s);});}
 private void showDiagnostics(){String body; synchronized(diagnostics){body=diagnostics.toString();}final String text=body;new AlertDialog.Builder(this).setTitle("K-Dial | יומן חיבור Bluetooth").setMessage(text.isEmpty()?"אין עדיין אירועים":text).setPositiveButton("סגור",null).setNeutralButton("העתק יומן",(d,w)->{android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("K-Dial log",text));Toast.makeText(this,"היומן הועתק",Toast.LENGTH_SHORT).show();}).show();}''')
s=s.replace('  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);','''  root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  Button diagnosticsButton=new Button(this);diagnosticsButton.setText("יומן חיבור Bluetooth | הצג הצלחות ושגיאות");diagnosticsButton.setAllCaps(false);diagnosticsButton.setOnClickListener(v->showDiagnostics());root.addView(diagnosticsButton,new LinearLayout.LayoutParams(-1,-2));setContentView(root);''')
a=s.index(' private void connect(int requestId){')
b=s.index(' private void read(BluetoothSocket s){',a)
s=s[:a]+''' private void connect(int requestId){
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
''' +s[b:]
s=s.replace('finally{if(!stopping)disconnect();}}', 'finally{if(!stopping&&socket==s)disconnect();}}')
s=s.replace('private void disconnect(){stopping=true;connected=false;busy=false;', 'private void disconnect(){stopping=true;connectionGeneration++;connected=false;busy=false;connectionMethod="";')
s=s.replace('connected?"HFP RFCOMM מחובר":"בחר טלפון ולחץ התחבר"','connected?"Bluetooth RFCOMM מחובר: "+connectionMethod:"בחר טלפון ולחץ התחבר"')
p.write_text(s,encoding='utf-8')
print('Updated',p,'bytes',len(s))
