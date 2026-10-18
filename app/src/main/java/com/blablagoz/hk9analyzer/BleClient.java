package com.blablagoz.hk9analyzer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

@SuppressLint("MissingPermission")
public final class BleClient {
    public interface Listener {
        void changed();
        void device(BluetoothDevice device, String name, int rssi);
        void data(byte[] value);
        void event(String message);
    }
    private final Activity activity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private final Deque<BluetoothGattCharacteristic> reads = new ArrayDeque<>();
    private final UUID uart = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final UUID tx = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private final UUID cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private BluetoothGattCharacteristic pendingRead;
    public String state = "Bağlı değil", name = "HK9 Pro+", address = "", firmware = "", manufacturer = "", model = "";
    public boolean scanning, connected, subscribed, connecting;
    public int battery = -1, rssi = 0, services = 0;
    public final List<String> serviceDetails = new ArrayList<>();
    private final Runnable scanEnd = this::stopScan;
    private final Runnable operationTimeout = () -> fail("Saat yanıt vermedi. Yeniden bağlanabilirsin.");

    public BleClient(Activity activity, Listener listener) {
        this.activity = activity; this.listener = listener;
        BluetoothManager manager = activity.getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
    }
    public boolean available() { return adapter != null; }
    public boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31) return activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            && activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        return activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    public boolean enabled() { return adapter != null && permitted() && adapter.isEnabled(); }
    private void changed(String value) { state = value; listener.changed(); }
    public void scan() {
        if (!permitted() || adapter == null) { changed("Bluetooth izinleri gerekli."); return; }
        if (!adapter.isEnabled()) { changed("Bluetooth'u açıp tekrar dene."); return; }
        stopScan();
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { changed("Bluetooth tarayıcı kullanılamıyor."); return; }
            scanning = true;
            scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            changed("Yakındaki saatler aranıyor…");
            main.postDelayed(scanEnd, 15000);
        } catch (SecurityException | IllegalStateException e) { stopScan(); changed("Tarama başlatılamadı."); }
    }
    public void stopScan() {
        main.removeCallbacks(scanEnd);
        if (!scanning) return;
        scanning = false;
        try { if (scanner != null) scanner.stopScan(scanCallback); } catch (SecurityException | IllegalStateException ignored) { }
        changed(connected ? (subscribed ? "Canlı bağlantı" : "Bağlı") : "Tarama tamamlandı");
    }
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            main.post(() -> {
                if (!scanning || !permitted()) return;
                try {
                    BluetoothDevice device = result.getDevice();
                    String label = result.getScanRecord() == null ? null : result.getScanRecord().getDeviceName();
                    if (label == null) label = device.getName();
                    listener.device(device, label == null ? "İsimsiz cihaz" : label, result.getRssi());
                } catch (SecurityException ignored) { }
            });
        }
        @Override public void onScanFailed(int code) { main.post(() -> { stopScan(); changed("Tarama hatası (" + code + ")"); }); }
    };
    public void connect(BluetoothDevice device, String label) {
        if (!permitted()) return;
        stopScan(); close(false);
        name = label; address = device.getAddress(); firmware = ""; manufacturer = ""; model = "";
        battery = -1; rssi = 0; services = 0; serviceDetails.clear();
        connecting = true; changed("Saatine bağlanılıyor…");
        listener.event("CONNECT " + name);
        try {
            gatt = device.connectGatt(activity, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) { fail("Bağlantı başlatılamadı."); return; }
            main.postDelayed(operationTimeout, 20000);
        } catch (SecurityException | IllegalArgumentException e) { fail("Bağlantı izni alınamadı."); }
    }
    public void reconnect(String savedAddress, String savedName) {
        if (adapter == null || !permitted()) return;
        try { connect(adapter.getRemoteDevice(savedAddress), savedName); }
        catch (IllegalArgumentException e) { changed("Kayıtlı cihaz adresi geçersiz."); }
    }
    public void disconnect() { close(true); }
    private void fail(String message) { close(false); changed(message); listener.event(message); }
    private void close(boolean notify) {
        main.removeCallbacks(operationTimeout); reads.clear(); pendingRead = null;
        BluetoothGatt old = gatt; gatt = null;
        connected = false; connecting = false; subscribed = false;
        if (old != null) {
            try { old.disconnect(); old.close(); } catch (SecurityException ignored) { }
        }
        if (notify) { changed("Bağlantı kapatıldı"); listener.event("DISCONNECT"); }
    }
    private void nextRead() {
        main.removeCallbacks(operationTimeout);
        pendingRead = null;
        if (gatt == null) return;
        while (!reads.isEmpty()) {
            BluetoothGattCharacteristic c = reads.removeFirst();
            try {
                pendingRead = c;
                if (gatt.readCharacteristic(c)) { main.postDelayed(operationTimeout, 12000); return; }
            } catch (SecurityException e) { fail("Bluetooth izni kaldırılmış."); return; }
            pendingRead = null;
        }
        changed(subscribed ? "Canlı bağlantı" : "Bağlı · canlı veri kanalı yok");
    }
    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int code, int newState) {
            main.post(() -> {
                if (g != gatt) return;
                if (code != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    fail("Bağlantı kesildi (" + code + "). Gerekirse Wearfit Pro bağlantısını kapat."); return;
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connecting = false; connected = true; changed("Saat bulundu · hazırlanıyor…");
                    try { if (!g.discoverServices()) fail("Servisler keşfedilemedi."); }
                    catch (SecurityException e) { fail("Bluetooth izni kaldırılmış."); }
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int code) {
            main.post(() -> {
                if (g != gatt) return;
                main.removeCallbacks(operationTimeout);
                if (code != BluetoothGatt.GATT_SUCCESS) { fail("Servis keşfi başarısız: " + code); return; }
                services = g.getServices().size(); serviceDetails.clear();
                for (BluetoothGattService s : g.getServices()) {
                    serviceDetails.add("SERVİS  " + s.getUuid());
                    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        serviceDetails.add("  " + c.getUuid() + "  [" + c.getProperties() + "]");
                        String id = c.getUuid().toString().substring(0,8);
                        if ((id.equals("00002a19") || id.equals("00002a26") || id.equals("00002a29") || id.equals("00002a24"))
                            && (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) reads.addLast(c);
                    }
                }
                BluetoothGattService service = g.getService(uart);
                BluetoothGattCharacteristic channel = service == null ? null : service.getCharacteristic(tx);
                BluetoothGattDescriptor descriptor = channel == null ? null : channel.getDescriptor(cccd);
                try {
                    if (descriptor != null && (channel.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                        && g.setCharacteristicNotification(channel, true)) {
                        boolean started;
                        if (Build.VERSION.SDK_INT >= 33) started = g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS;
                        else { descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); started = g.writeDescriptor(descriptor); }
                        if (started) { changed("Canlı veri kanalı açılıyor…"); main.postDelayed(operationTimeout, 12000); return; }
                    }
                    listener.event("UART bildirim kanalı açılamadı; standart bilgiler okunuyor.");
                    nextRead();
                } catch (SecurityException e) { fail("Bildirim izni alınamadı."); }
            });
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int code) {
            main.post(() -> {
                if (g != gatt) return;
                subscribed = code == BluetoothGatt.GATT_SUCCESS;
                listener.event(subscribed ? "Saatin canlı veri kanalı açıldı." : "Bildirim aboneliği başarısız: " + code);
                nextRead();
            });
        }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) { notifyValue(g,c,value); }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT < 33) notifyValue(g,c,c.getValue());
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int code) { readValue(g,c,value,code); }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int code) {
            if (Build.VERSION.SDK_INT < 33) readValue(g,c,c.getValue(),code);
        }
    };
    private void notifyValue(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
        if (!c.getUuid().equals(tx)) return;
        byte[] snapshot = value == null ? new byte[0] : value.clone();
        main.post(() -> { if (g == gatt) listener.data(snapshot); });
    }
    private void readValue(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int code) {
        byte[] snapshot = value == null ? new byte[0] : value.clone();
        main.post(() -> {
            if (g != gatt || c != pendingRead) return;
            if (code == BluetoothGatt.GATT_SUCCESS) {
                String id = c.getUuid().toString().substring(0,8);
                String text = new String(snapshot, java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", "").trim();
                if (id.equals("00002a19") && snapshot.length > 0 && (snapshot[0]&255) <= 100) battery = snapshot[0]&255;
                if (id.equals("00002a26")) firmware = text;
                if (id.equals("00002a29")) manufacturer = text;
                if (id.equals("00002a24")) model = text;
            }
            nextRead();
        });
    }
    public void destroy() { stopScan(); close(false); main.removeCallbacksAndMessages(null); }
}
