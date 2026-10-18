package com.blablagoz.hk9analyzer;

import android.Manifest;
import android.app.*;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity implements BleClient.Listener {
    private static final int BG=Color.rgb(14,18,20), CARD=Color.rgb(25,31,33), LINE=Color.rgb(43,52,54);
    private static final int WHITE=Color.rgb(241,247,244), MUTED=Color.rgb(151,168,164), MINT=Color.rgb(179,240,205);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final PacketDecoder decoder=new PacketDecoder();
    private final List<Entry> frames=new ArrayList<>(), raw=new ArrayList<>(), imported=new ArrayList<>();
    private final LinkedHashMap<String,Found> found=new LinkedHashMap<>();
    private final Deque<String> events=new ArrayDeque<>();
    private BleClient ble;
    private SharedPreferences prefs;
    private LinearLayout root,content,devices,packetList;
    private TextView packetCountView,lastPacketView,batteryView,liveCountView;
    private int tab=0,totalFrames=0,totalRaw=0;
    private boolean importing,useImported,scanAfterPermission,reconnectAfterPermission,refreshScheduled;
    private long lastPacket=0;
    private String importedName="",exportText="";
    private static final int PERMISSION=101,IMPORT=102,EXPORT=103;
    private final Runnable refresh=() -> { refreshScheduled=false; updateMetrics(); if(tab==2) drawPackets(); };

    private static final class Entry {
        final String time,kind; final byte[] bytes;
        Entry(String t,String k,byte[] b) { time=t;kind=k;bytes=b.clone(); }
        JSONObject json() throws JSONException { return new JSONObject().put("time",time).put("kind",kind).put("hex",PacketDecoder.hex(bytes)); }
    }
    private static final class Found {
        BluetoothDevice device; String name; int rssi;
        Found(BluetoothDevice d,String n,int r) { device=d;name=n;rssi=r; }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(0);
        prefs=getSharedPreferences("companion",MODE_PRIVATE);ble=new BleClient(this,this);
        tab=state==null?Math.max(0,Math.min(3,getIntent().getIntExtra("screen",0))):state.getInt("screen",0);
        root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,i) -> { v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i; });
        setContentView(root);render();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putInt("screen",tab);super.onSaveInstanceState(out); }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v; }
    private TextView text(String s,int size,int color) {
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;
    }
    private TextView strong(String s,int size,int color) { TextView v=text(s,size,color);v.setTypeface(Typeface.create("sans-serif-medium",0));return v; }
    private void gap(LinearLayout v,int n) { v.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n))); }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    private LinearLayout card() { LinearLayout v=column();v.setPadding(dp(20),dp(20),dp(20),dp(20));v.setBackground(shape(CARD,24));return v; }
    private void addCard(LinearLayout v) { content.addView(v,new LinearLayout.LayoutParams(-1,-2));gap(content,14); }
    private TextView action(String label,boolean primary,Runnable task) {
        TextView v=strong(label,15,primary?BG:WHITE);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(52));v.setPadding(dp(14),dp(12),dp(14),dp(12));
        v.setBackground(shape(primary?MINT:LINE,16));v.setOnClickListener(w -> task.run());v.setContentDescription(label);v.setFocusable(true);return v;
    }
    private void render() {
        if(root==null||isFinishing()||isDestroyed()) return;
        root.removeAllViews();devices=null;packetList=null;packetCountView=null;lastPacketView=null;batteryView=null;liveCountView=null;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=column();content.setPadding(dp(22),dp(22),dp(22),dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0) home();else if(tab==1) watch();else if(tab==2) records();else settings();bottomNav();updateMetrics();
    }
    private void header(String eyebrow,String title,String subtitle) {
        TextView over=strong(eyebrow,11,MINT);over.setLetterSpacing(.16f);content.addView(over);gap(content,14);
        content.addView(strong(title,34,WHITE));gap(content,9);content.addView(text(subtitle,14,MUTED));gap(content,25);
    }
    private void home() {
        header("HK9  /  COMPANION","Her şey,\nbir bakışta.",new SimpleDateFormat("d MMMM EEEE",Locale.forLanguageTag("tr-TR")).format(new Date()));
        LinearLayout hero=card();GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(34,55,46),CARD});gradient.setCornerRadius(dp(26));hero.setBackground(gradient);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout info=column();
        info.addView(strong("SAATİM",11,MINT));gap(info,10);info.addView(strong(ble.connected?ble.name:prefs.getString("name","HK9 Pro+"),24,WHITE));gap(info,10);
        info.addView(text(ble.connected?"●  "+(ble.subscribed?"Canlı bağlantı":"Bağlı"):"○  Bağlantı bekleniyor",12,ble.connected?MINT:MUTED));
        gap(info,18);info.addView(text("Birlikte, daha iyi\nbir saat deneyimi.",13,MUTED));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(new WatchArt(),new LinearLayout.LayoutParams(dp(110),dp(175)));hero.addView(row);gap(hero,18);
        hero.addView(action(ble.connected?"Saatimi görüntüle  →":"Saatimi bağla  →",true,() -> selectTab(1)));addCard(hero);
        LinearLayout metrics=new LinearLayout(this);LinearLayout power=metric("PİL","—","Standart pil verisi");batteryView=(TextView)power.getChildAt(2);
        LinearLayout count=metric("VERİ AKIŞI",String.valueOf(totalFrames),"Alınan paket");liveCountView=(TextView)count.getChildAt(2);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.setMargins(0,0,dp(7),0);LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);right.setMargins(dp(7),0,0,0);
        metrics.addView(power,left);metrics.addView(count,right);content.addView(metrics);gap(content,26);
        content.addView(strong("Sana ait bir merkez",20,WHITE));gap(content,15);LinearLayout activity=card();
        item(activity,"activity","Hareket","Adım verisi henüz doğrulanmadı",null);divider(activity);item(activity,"heart","Sağlık","Ölçümler hazır olduğunda burada",null);addCard(activity);
        content.addView(action("Veri merkezini keşfet  →",false,() -> selectTab(2)));
    }
    private LinearLayout metric(String label,String value,String sub) {
        LinearLayout v=card();v.setPadding(dp(17),dp(18),dp(17),dp(18));v.addView(strong(label,10,MUTED));gap(v,13);v.addView(strong(value,32,WHITE));gap(v,8);v.addView(text(sub,11,MUTED));return v;
    }
    private void watch() {
        header("BAĞLANTI","Saatim",ble.connected?"Saatinle aynı ritimde.":"Saatini bul. Bağlan. Keşfet.");
        LinearLayout box=card(),artRow=new LinearLayout(this);artRow.setGravity(Gravity.CENTER);artRow.addView(new WatchArt(),new LinearLayout.LayoutParams(dp(122),dp(170)));box.addView(artRow);gap(box,10);
        TextView model=strong(ble.connected||ble.connecting?ble.name:prefs.getString("name","HK9 Pro+"),25,WHITE);model.setGravity(Gravity.CENTER);box.addView(model);gap(box,10);
        TextView status=text(ble.state,13,ble.connected?MINT:MUTED);status.setGravity(Gravity.CENTER);box.addView(status);gap(box,20);
        if(ble.connected||ble.connecting) box.addView(action("Bağlantıyı kes",false,ble::disconnect));
        else {
            box.addView(action(ble.scanning?"Taramayı durdur":"Yakındaki cihazları bul",true,() -> { if(ble.scanning) ble.stopScan();else beginScan(); }));
            if(!prefs.getString("address","").isEmpty()) { gap(box,10);box.addView(action("Son saatime yeniden bağlan",false,this::reconnect)); }
        }
        addCard(box);
        if(ble.connected) {
            LinearLayout details=card();item(details,"link","Veri kanalı",ble.subscribed?"Açık · saatten gelen veriler dinleniyor":"Bu cihazda canlı kanal açılamadı",null);divider(details);
            item(details,"device","Cihaz bilgileri",ble.services+" Bluetooth servisi keşfedildi",this::showDetails);addCard(details);
            content.addView(action("Canlı kayıtlara git  →",true,() -> { useImported=false;selectTab(2); }));
        }
        if(ble.scanning||!found.isEmpty()) { gap(content,8);content.addView(strong(ble.scanning?"Yakındaki cihazlar":"Bulunan cihazlar",19,WHITE));gap(content,12);devices=column();content.addView(devices);drawDevices(); }
        gap(content,18);content.addView(text("Saat görünmüyorsa yaklaştır. Bağlantı kurulamıyorsa nRF Connect veya Wearfit Pro bağlantısını kapatıp tekrar dene.",12,MUTED));
        if(!ble.available()) { gap(content,12);content.addView(text("Bu telefonda Bluetooth adaptörü bulunamadı.",13,MINT)); }
    }
    private void records() {
        header("VERİ MERKEZİ","Saatinin\nsöyledikleri.","Canlı akışı izle veya bir nRF Connect kaydı aç.");
        LinearLayout switches=new LinearLayout(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(0,0,dp(8),0);
        switches.addView(action("Canlı oturum",!useImported,() -> { useImported=false;render(); }),p);
        switches.addView(action("İçe aktarılan",useImported,() -> { useImported=true;render(); }),new LinearLayout.LayoutParams(0,-2,1));content.addView(switches);gap(content,16);
        LinearLayout summary=card();summary.addView(strong(useImported?"DOSYADAN OKUNAN":"BU OTURUMDA ALINAN",11,MUTED));gap(summary,10);
        packetCountView=strong("0",44,MINT);summary.addView(packetCountView);gap(summary,8);lastPacketView=text("",12,MUTED);summary.addView(lastPacketView);addCard(summary);
        LinearLayout tools=new LinearLayout(this);tools.addView(action(importing?"Dosya okunuyor…":"Kayıt aç",false,this::importLog),p);
        tools.addView(action("JSON kaydet",false,this::export),new LinearLayout.LayoutParams(0,-2,1));content.addView(tools);gap(content,24);
        LinearLayout label=new LinearLayout(this);label.addView(strong("Son paketler",20,WHITE),new LinearLayout.LayoutParams(0,-2,1));
        TextView clear=text("Temizle",13,MINT);clear.setPadding(dp(12),dp(10),dp(4),dp(10));clear.setOnClickListener(v -> confirmClear());label.addView(clear);content.addView(label);gap(content,10);
        packetList=column();content.addView(packetList);drawPackets();
    }
    private void settings() {
        header("SANA GÖRE","Ayarlar","Az kalabalık. Daha fazla kontrol.");
        LinearLayout privacy=card();item(privacy,"shield","Verilerin telefonda","Hesap ve internet bağlantısı gerektirmez.",null);addCard(privacy);
        LinearLayout options=card();item(options,"file","Oturumu dışa aktar","Kayıtlarını JSON dosyasına kaydet",this::export);divider(options);
        item(options,"file","nRF Connect kaydı aç","Daha önce aldığın .txt kayıtlarını incele",this::importLog);divider(options);
        item(options,"device","Kayıtlı saati unut","Telefondaki cihaz tercihini temizle",() -> new AlertDialog.Builder(this).setTitle("Kayıtlı saati unut?")
            .setMessage("Telefondaki son cihaz tercihi kaldırılacak.").setPositiveButton("Unut",(d,w) -> { ble.disconnect();prefs.edit().clear().apply();render(); }).setNegativeButton("Vazgeç",null).show());addCard(options);
        LinearLayout about=card();about.addView(strong("HK9 Companion",21,WHITE));gap(about,8);about.addView(text("Sürüm 0.2.0 · Android",13,MUTED));gap(about,18);
        about.addView(text("Saatinle daha sade bir bağlantı. Canlı veri dinleme, cihaz bilgileri ve yerel kayıt araçları.",14,MUTED));gap(about,15);
        about.addView(text("Sağlık ve adım alanları henüz çözümlenmedi. Firmware değiştirme ve uygulama kaldırma bu sürümde yok.",12,MUTED));addCard(about);
        content.addView(text("Bağlantı uygulama açıkken kullanılır. Oturum kayıtları bellekte tutulur; kalıcı saklamak için JSON kaydet. Dışa aktarılan kayıtlar cihaz adresi ve ham veri içerebilir.",12,MUTED));
    }
    private void item(LinearLayout parent,String icon,String title,String detail,Runnable task) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(8));row.addView(new Icon(icon,MINT),new LinearLayout.LayoutParams(dp(28),dp(32)));
        LinearLayout labels=column();labels.setPadding(dp(14),0,dp(6),0);labels.addView(strong(title,15,WHITE));gap(labels,6);labels.addView(text(detail,12,MUTED));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(task!=null) { row.addView(text("›",24,MUTED));row.setOnClickListener(v -> task.run());row.setFocusable(true);row.setContentDescription(title); }parent.addView(row);
    }
    private void divider(LinearLayout v) { gap(v,9);View line=new View(this);line.setBackgroundColor(LINE);v.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));gap(v,9); }
    private void bottomNav() {
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(8),dp(10),dp(8),dp(10));nav.setBackgroundColor(BG);
        String[] labels={"Özet","Saatim","Kayıtlar","Ayarlar"},icons={"home","device","activity","settings"};
        for(int i=0;i<4;i++) {
            final int index=i;LinearLayout entry=column();entry.setGravity(Gravity.CENTER);entry.setPadding(0,dp(7),0,dp(7));if(i==tab) entry.setBackground(shape(Color.rgb(29,44,37),18));
            entry.addView(new Icon(icons[i],i==tab?MINT:MUTED),new LinearLayout.LayoutParams(dp(22),dp(22)));gap(entry,6);
            TextView label=text(labels[i],11,i==tab?MINT:MUTED);label.setGravity(Gravity.CENTER);entry.addView(label);
            entry.setSelected(i==tab);entry.setContentDescription(labels[i]);entry.setFocusable(true);entry.setOnClickListener(v -> selectTab(index));nav.addView(entry,new LinearLayout.LayoutParams(0,-2,1));
        }root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
    }
    private void selectTab(int index) { tab=index;render(); }
    private void updateMetrics() {
        if(batteryView!=null) batteryView.setText(ble.connected&&ble.battery>=0?ble.battery+"%":"—");if(liveCountView!=null) liveCountView.setText(String.valueOf(totalFrames));
        if(packetCountView!=null) packetCountView.setText(String.valueOf(useImported?imported.size():totalFrames));
        if(lastPacketView!=null) lastPacketView.setText(useImported?(imported.isEmpty()?"Dosya açarak kayıtlarını incele.":importedName+" · çevrimdışı kayıt")
            :(lastPacket==0?"Saatten gelen ilk paket bekleniyor.":totalRaw+" bildirim · son veri "+time(lastPacket)));
    }
    private void drawPackets() {
        if(packetList==null) return;packetList.removeAllViews();List<Entry> list=useImported?imported:frames;
        if(list.isEmpty()) {
            LinearLayout empty=card();empty.addView(new Icon("activity",MINT),new LinearLayout.LayoutParams(dp(32),dp(32)));gap(empty,16);
            empty.addView(strong(useImported?"Kayıtlarını buraya getir":"İlk sinyali bekliyoruz",19,WHITE));gap(empty,10);
            empty.addView(text(useImported?"nRF Connect'ten dışa aktardığın metin dosyasını aç. Paketler burada listelenecek.":"Saatine bağlandığında gelen paketleri burada göreceksin. Bazı veriler yalnızca saatte bir işlem yaptığında gönderilir.",13,MUTED));packetList.addView(empty);return;
        }
        for(int i=list.size()-1;i>=Math.max(0,list.size()-20);i--) {
            Entry e=list.get(i);LinearLayout box=card();box.setPadding(dp(16),dp(16),dp(16),dp(16));LinearLayout top=new LinearLayout(this);
            top.addView(strong(String.format(Locale.ROOT,"0x%02X",PacketDecoder.command(e.bytes)),14,MINT),new LinearLayout.LayoutParams(0,-2,1));top.addView(text(e.time,11,MUTED));box.addView(top);gap(box,10);
            box.addView(strong(PacketDecoder.label(e.bytes),14,WHITE));gap(box,7);box.addView(text(e.bytes.length+" bayt · ham veri",11,MUTED));gap(box,10);
            TextView hex=text(PacketDecoder.hex(e.bytes),11,MUTED);hex.setTypeface(Typeface.MONOSPACE);hex.setMaxLines(2);box.addView(hex);
            box.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Paket ayrıntısı").setMessage(e.time+"\n"+PacketDecoder.label(e.bytes)+"\n\n"+PacketDecoder.hex(e.bytes)
                +"\n\nAlan adları protokol araştırması için aday yorumlardır; doğrulanmış ölçüm değildir.").setPositiveButton("Tamam",null).show());packetList.addView(box);gap(packetList,10);
        }
        if(list.size()>20) packetList.addView(text("Son 20 paket gösteriliyor. JSON dosyası bellekte tutulan tüm kayıtları içerir (en fazla 500).",12,MUTED));
    }
    private void drawDevices() {
        if(devices==null) return;devices.removeAllViews();List<Found> list=new ArrayList<>(found.values());
        list.sort(Comparator.comparingInt((Found f) -> f.name.toLowerCase(Locale.ROOT).contains("hk9")?0:1).thenComparing(f -> f.name));
        if(list.isEmpty()) devices.addView(text("Aranıyor… Saatini telefonuna yakın tut.",13,MUTED));
        for(Found f:list) {
            LinearLayout box=card();box.setPadding(dp(16),dp(12),dp(16),dp(12));item(box,"device",f.name,f.device.getAddress()+" · "+f.rssi+" dBm",() -> { resetSession();ble.connect(f.device,f.name); });devices.addView(box);gap(devices,8);
        }
    }
    private void resetSession() { frames.clear();raw.clear();totalFrames=0;totalRaw=0;lastPacket=0;decoder.reset(); }
    private void beginScan() {
        if(!ble.available()) { message("Bu telefonda Bluetooth bulunamadı.");return; }if(!ensurePermission(false)) return;
        if(!ble.enabled()) { message("Telefonun Bluetooth'unu açıp tekrar dene.");startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));return; }found.clear();ble.scan();
    }
    private boolean ensurePermission(boolean reconnect) {
        if(ble.permitted()) return true;scanAfterPermission=!reconnect;reconnectAfterPermission=reconnect;
        requestPermissions(Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION},PERMISSION);return false;
    }
    private void reconnect() { if(!ensurePermission(true)) return;resetSession();ble.reconnect(prefs.getString("address",""),prefs.getString("name","HK9 Pro+")); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);if(request!=PERMISSION) return;
        boolean scan=scanAfterPermission,reconnect=reconnectAfterPermission;scanAfterPermission=false;reconnectAfterPermission=false;
        if(ble.permitted()) { if(reconnect) reconnect();else if(scan) beginScan(); }else message("Yakındaki cihazlar izni gerekli. Telefon ayarlarından izin verebilirsin.");
    }
    @Override public void changed() { if(ble.connected&&!ble.address.isEmpty()) prefs.edit().putString("name",ble.name).putString("address",ble.address).apply();render(); }
    @Override public void device(BluetoothDevice d,String name,int rssi) {
        Found existing=found.get(d.getAddress());if(existing==null) { if(found.size()>=100) return;found.put(d.getAddress(),new Found(d,name,rssi));drawDevices(); }else existing.rssi=rssi;
    }
    @Override public void event(String s) { events.add(time(System.currentTimeMillis())+" "+s);while(events.size()>100) events.removeFirst(); }
    @Override public void data(byte[] bytes) {
        lastPacket=System.currentTimeMillis();totalRaw++;append(raw,new Entry(time(lastPacket),"notification",bytes));
        for(byte[] frame:decoder.feed(bytes)) { totalFrames++;append(frames,new Entry(time(lastPacket),"frame",frame)); }
        if(!refreshScheduled) { refreshScheduled=true;main.postDelayed(refresh,400); }
    }
    private void append(List<Entry> list,Entry e) { list.add(e);if(list.size()>500) list.remove(0); }
    private String time(long n) { return new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date(n)); }
    private void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private void showDetails() {
        String s="Model: "+(ble.model.isEmpty()?"Bildirilmedi":ble.model)+"\nÜretici: "+(ble.manufacturer.isEmpty()?"Bildirilmedi":ble.manufacturer)
            +"\nFirmware: "+(ble.firmware.isEmpty()?"Standart serviste bildirilmedi":ble.firmware)+"\n\n"+String.join("\n",ble.serviceDetails);
        TextView v=text(s,12,WHITE);v.setTextIsSelectable(true);v.setPadding(dp(18),dp(12),dp(18),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(v);
        new AlertDialog.Builder(this).setTitle("Cihaz bilgileri").setView(scroll).setPositiveButton("Tamam",null).show();
    }
    private void confirmClear() {
        new AlertDialog.Builder(this).setTitle("Bu kayıtlar temizlensin mi?").setMessage("Dosyaya kaydetmediğin oturum kayıtları kaldırılacak.")
            .setPositiveButton("Temizle",(d,w) -> { if(useImported) imported.clear();else resetSession();updateMetrics();drawPackets(); }).setNegativeButton("Vazgeç",null).show();
    }
    private void importLog() {
        if(importing) return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/*").addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i,IMPORT); }catch(ActivityNotFoundException e) { message("Dosya seçici bulunamadı."); }
    }
    private void export() {
        if((useImported?imported:raw).isEmpty()) { message("Önce saatine bağlan veya bir kayıt dosyası aç.");return; }
        try {
            JSONObject json=new JSONObject().put("app","HK9 Companion 0.2.0").put("exported_at",new Date().toString()).put("source",useImported?"imported_nrf_log":"live_ble_session")
                .put("interpretations_verified",false).put("retained_limit",500);
            if(!useImported) json.put("device",new JSONObject().put("name",ble.name).put("address",ble.address)).put("total_frames",totalFrames).put("total_notifications",totalRaw);
            JSONArray parsed=new JSONArray(),notifications=new JSONArray();for(Entry e:(useImported?imported:frames)) parsed.put(e.json());if(!useImported) for(Entry e:raw) notifications.put(e.json());
            json.put("frames",parsed).put("notifications",notifications).put("events",useImported?new JSONArray():new JSONArray(events));exportText=json.toString(2);
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,"hk9-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json"),EXPORT);
        }catch(JSONException|ActivityNotFoundException e) { message("Dışa aktarma başlatılamadı."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);if(result!=RESULT_OK||intent==null||intent.getData()==null) return;Uri uri=intent.getData();
        if(request==EXPORT) {
            try(OutputStream out=getContentResolver().openOutputStream(uri)) { if(out==null) throw new IOException("Dosya açılamadı");out.write(exportText.getBytes(StandardCharsets.UTF_8));message("Oturum dosyası kaydedildi."); }
            catch(IOException|SecurityException e) { message("Dosya kaydedilemedi."); }exportText="";
        }
        if(request==IMPORT) {
            importing=true;render();new Thread(() -> {
                try(InputStream stream=getContentResolver().openInputStream(uri)) {
                    if(stream==null) throw new IOException("Dosya açılamadı");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                    while((n=stream.read(buffer))!=-1) { if(out.size()+n>2*1024*1024) throw new IOException("Dosya 2 MB'dan büyük");out.write(buffer,0,n); }
                    List<Entry> entries=new ArrayList<>();PacketDecoder parser=new PacketDecoder();
                    Pattern pattern=Pattern.compile("(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})[^\\r\\n]*Notification received[^\\r\\n]*value: \\(0x\\) ([0-9A-Fa-f]+(?:-[0-9A-Fa-f]+)+)");
                    Matcher matcher=pattern.matcher(out.toString("UTF-8"));int count=0;
                    while(matcher.find()) for(byte[] frame:parser.feed(PacketDecoder.fromHex(matcher.group(2)))) { count++;if(entries.size()<500) entries.add(new Entry(matcher.group(1),"imported_frame",frame)); }
                    final int total=count;main.post(() -> { if(isDestroyed()) return;imported.clear();imported.addAll(entries);importedName="nRF Connect · "+total+" paket";importing=false;useImported=true;tab=2;render();
                        message(total==0?"Bu dosyada tanınan AB/EA bildirim paketi bulunamadı.":total+" paket okundu"+(total>500?"; ilk 500 gösteriliyor.":".")); });
                }catch(IOException|SecurityException|IllegalArgumentException e) { main.post(() -> { if(isDestroyed()) return;importing=false;render();message("Kayıt açılamadı: "+e.getMessage()); }); }
            },"hk9-import").start();
        }
    }
    @Override protected void onStop() { ble.stopScan();super.onStop(); }
    @Override protected void onDestroy() { ble.destroy();main.removeCallbacksAndMessages(null);super.onDestroy(); }

    private class Icon extends View {
        private final String kind;private final Paint p=new Paint(3);private final int color;
        Icon(String k,int c) { super(MainActivity.this);kind=k;color=c;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);c.save();c.scale(getWidth()/24f,getHeight()/24f);p.setColor(color);p.setStrokeWidth(1.6f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            if(kind.equals("device")) { c.drawRoundRect(6,5,18,19,4,4,p);c.drawLine(9,1,15,1,p);c.drawLine(9,23,15,23,p); }
            else if(kind.equals("home")) { Path path=new Path();path.moveTo(3,10);path.lineTo(12,3);path.lineTo(21,10);path.moveTo(5,9);path.lineTo(5,21);path.lineTo(19,21);path.lineTo(19,9);path.moveTo(10,21);path.lineTo(10,14);path.lineTo(14,14);path.lineTo(14,21);c.drawPath(path,p); }
            else if(kind.equals("activity")) { Path path=new Path();path.moveTo(2,13);path.lineTo(7,13);path.lineTo(10,5);path.lineTo(14,20);path.lineTo(17,11);path.lineTo(22,11);c.drawPath(path,p); }
            else if(kind.equals("heart")) { Path path=new Path();path.moveTo(12,21);path.cubicTo(-7,9,7,-2,12,7);path.cubicTo(17,-2,31,9,12,21);c.drawPath(path,p); }
            else if(kind.equals("shield")) { Path path=new Path();path.moveTo(12,2);path.lineTo(21,6);path.lineTo(20,15);path.quadTo(16,21,12,23);path.quadTo(8,21,4,15);path.lineTo(3,6);path.close();c.drawPath(path,p);c.drawLine(8,12,11,15,p);c.drawLine(11,15,17,9,p); }
            else if(kind.equals("file")) { c.drawRoundRect(5,2,19,22,2,2,p);c.drawLine(9,9,15,9,p);c.drawLine(9,13,15,13,p);c.drawLine(9,17,13,17,p); }
            else if(kind.equals("link")) { c.drawRoundRect(2,8,14,16,4,4,p);c.drawRoundRect(10,8,22,16,4,4,p); }
            else { c.drawCircle(12,12,8,p);c.drawCircle(12,12,3,p);for(int i=0;i<8;i++) { double a=i*Math.PI/4;c.drawLine(12+(float)Math.cos(a)*9,12+(float)Math.sin(a)*9,12+(float)Math.cos(a)*11,12+(float)Math.sin(a)*11,p); } }c.restore();
        }
    }
    private class WatchArt extends View {
        private final Paint p=new Paint(3);
        WatchArt() { super(MainActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        private void fill(Canvas c,int color,float x,float y,float r,float b,float radius) { p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(x,y,r,b,radius,radius,p); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);c.save();c.scale(getWidth()/120f,getHeight()/180f);fill(c,Color.rgb(46,61,53),34,0,86,180,18);p.setColor(Color.rgb(67,82,72));p.setStrokeWidth(2);
            for(int i=8;i<180;i+=8) c.drawLine(38,i,82,i,p);
            fill(c,Color.rgb(121,134,128),10,37,110,143,26);fill(c,Color.rgb(212,220,213),12,39,108,141,24);fill(c,Color.rgb(24,30,27),16,43,104,137,21);fill(c,Color.rgb(112,125,117),108,62,115,80,3);
            p.setColor(MINT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);c.drawArc(29,58,92,121,-90,260,false,p);p.setColor(Color.rgb(82,142,116));p.setStrokeWidth(5);c.drawArc(39,68,82,111,-90,210,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(WHITE);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(17);p.setTextAlign(Paint.Align.CENTER);c.drawText("HK9",60,94,p);p.setColor(MUTED);p.setTextSize(6);c.drawText("COMPANION",60,105,p);c.restore();
        }
    }
}
