package com.blablagoz.hk9analyzer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Button> rows = new LinkedHashMap<>();
    private final StringBuilder journal = new StringBuilder();
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt connection;
    private BluetoothGattCharacteristic reading;
    private boolean scanning;
    private TextView status, logView;
    private LinearLayout deviceList, serviceList;
    private Button scanButton;
    private final Runnable scanTimeout = this::stopScan;
    private final Runnable readTimeout = () -> {
        if (reading != null) {
            log("Okuma zaman aşımı; bağlantı kapatılıyor.");
            disconnect();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        adapter = manager == null ? null : manager.getAdapter();
        LinearLayout root = column();
        root.setPadding(24, 24, 24, 24);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(24 + insets.getSystemWindowInsetLeft(),
                24 + insets.getSystemWindowInsetTop(), 24 + insets.getSystemWindowInsetRight(),
                24 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = text("HK9 Analyzer", 28);
        root.addView(title);
        root.addView(text("BLE tarama · GATT inceleme · Ham veri", 14));
        status = text("Hazır", 16);
        root.addView(status);
        LinearLayout controls = new LinearLayout(this);
        scanButton = button("Tara", this::startScan);
        controls.addView(scanButton, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(button("Bağlantıyı kes", this::disconnect), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls);
        root.addView(button("Günlüğü paylaş", this::shareLog));
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column();
        body.addView(text("Cihazlar", 20));
        deviceList = column();
        body.addView(deviceList);
        body.addView(text("Servisler ve karakteristikler", 20));
        serviceList = column();
        body.addView(serviceList);
        body.addView(text("Oturum günlüğü", 20));
        logView = text("Henüz kayıt yok.", 12);
        logView.setTextIsSelectable(true);
        body.addView(logView);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        if (adapter == null) status.setText("Bu cihazda Bluetooth bulunamadı.");
        log("HK9 Analyzer 0.1.0 — standart BLE inceleme oturumu");
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, 8, 0, 8);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private boolean permitted() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startScan() {
        if (scanning) { stopScan(); return; }
        if (adapter == null) { log("Bluetooth adaptörü yok."); return; }
        if (!permitted()) {
            String[] permissions = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(permissions, 1);
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                status.setText("Bluetooth açıldıktan sonra tekrar Tara'ya dokunun.");
                return;
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { log("BLE tarayıcı kullanılamıyor."); return; }
            rows.clear();
            deviceList.removeAllViews();
            scanning = true;
            scanner.startScan(null, new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback);
            scanButton.setText("Durdur");
            status.setText("Taranıyor (15 saniye)…");
            log("BLE taraması başladı.");
            handler.postDelayed(scanTimeout, 15000);
        } catch (SecurityException | IllegalStateException e) {
            stopScan();
            log("Tarama başlatılamadı: " + e.getMessage());
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1 && permitted()) startScan();
        else log("Tarama için Bluetooth/konum izni gerekli. Ayarlar'dan izin verebilirsiniz.");
    }

    private void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if (scanning && scanner != null) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException | IllegalStateException ignored) { }
        }
        if (scanning) {
            scanning = false;
            scanButton.setText("Tara");
            status.setText(rows.size() + " cihaz bulundu.");
            log("Tarama durdu: " + rows.size() + " cihaz.");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            handler.post(() -> {
                if (!scanning) return;
                BluetoothDevice device = result.getDevice();
                String address = device.getAddress();
                ScanRecord record = result.getScanRecord();
                String name = record == null ? null : record.getDeviceName();
                String label = (name == null ? "İsimsiz cihaz" : name) + "\n" + address
                    + " · " + result.getRssi() + " dBm";
                Button row = rows.get(address);
                if (row == null) {
                    row = button(label, () -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Cihaza bağlan")
                        .setMessage(address + "\nWearfit Pro bağlantısı ikinci bağlantıyı engelleyebilir.")
                        .setPositiveButton("Bağlan", (dialog, which) -> connect(device))
                        .setNegativeButton("İptal", null).show());
                    rows.put(address, row);
                    deviceList.addView(row);
                    log("ADV " + label.replace('\n', ' ') + " HEX="
                        + hex(record == null ? new byte[0] : record.getBytes()));
                } else row.setText(label);
            });
        }
        @Override public void onScanFailed(int error) {
            handler.post(() -> { stopScan(); log("Tarama hatası: " + error); });
        }
    };

    private void connect(BluetoothDevice device) {
        stopScan();
        disconnect();
        serviceList.removeAllViews();
        try {
            status.setText("Bağlanıyor: " + device.getAddress());
            log("CONNECT " + device.getAddress());
            connection = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) { log("Bağlantı izni yok: " + e.getMessage()); }
    }

    private void disconnect() {
        handler.removeCallbacks(readTimeout);
        reading = null;
        BluetoothGatt old = connection;
        connection = null;
        if (old != null) {
            try { old.disconnect(); } catch (SecurityException ignored) { }
            old.close();
            status.setText("Bağlantı kapatıldı.");
            log("DISCONNECT");
        }
        if (serviceList != null) serviceList.removeAllViews();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt gatt, int code, int state) {
            handler.post(() -> {
                if (gatt != connection) return;
                log("GATT status=" + code + " state=" + state);
                if (code == BluetoothGatt.GATT_SUCCESS && state == BluetoothProfile.STATE_CONNECTED) {
                    status.setText("Bağlandı; servisler aranıyor…");
                    try {
                        if (!gatt.discoverServices()) { log("Servis keşfi başlatılamadı."); disconnect(); }
                    } catch (SecurityException e) { log("Bluetooth izni kaldırılmış."); disconnect(); }
                } else if (code != BluetoothGatt.GATT_SUCCESS || state == BluetoothProfile.STATE_DISCONNECTED) {
                    disconnect();
                    status.setText("Bağlantı kapandı. GATT durum kodu: " + code);
                }
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt gatt, int code) {
            handler.post(() -> {
                if (gatt != connection) return;
                if (code != BluetoothGatt.GATT_SUCCESS) { log("Servis keşif hatası: " + code); return; }
                serviceList.removeAllViews();
                status.setText("Bağlı · " + gatt.getServices().size() + " servis");
                for (BluetoothGattService service : gatt.getServices()) {
                    serviceList.addView(text("Servis " + service.getUuid(), 15));
                    log("SERVICE " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        String label = characteristic.getUuid() + "\n" + properties(characteristic.getProperties());
                        Button row = button(label, () -> read(characteristic));
                        row.setEnabled((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
                        serviceList.addView(row);
                        log("CHAR " + label.replace('\n', ' '));
                    }
                }
            });
        }
        @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, int code) {
            if (Build.VERSION.SDK_INT < 33) received(gatt, c, c.getValue(), code);
        }
        @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic c, byte[] value, int code) {
            received(gatt, c, value, code);
        }
    };

    private void read(BluetoothGattCharacteristic characteristic) {
        if (connection == null) { log("Önce cihaza bağlanın."); return; }
        if (reading != null) { log("Önceki okuma bekleniyor…"); return; }
        try {
            reading = characteristic;
            if (!connection.readCharacteristic(characteristic)) {
                reading = null;
                log("Okuma başlatılamadı.");
            } else handler.postDelayed(readTimeout, 10000);
        } catch (SecurityException e) { reading = null; log("Okuma izni yok."); }
    }

    private void received(BluetoothGatt gatt, BluetoothGattCharacteristic c, byte[] value, int code) {
        byte[] snapshot = value == null ? new byte[0] : value.clone();
        handler.post(() -> {
            if (gatt != connection) return;
            reading = null;
            handler.removeCallbacks(readTimeout);
            String message = "READ " + c.getUuid() + " status=" + code + "\nHEX " + hex(snapshot)
                + "\nUTF-8 " + new String(snapshot, StandardCharsets.UTF_8);
            log(message);
            if (!isFinishing()) new AlertDialog.Builder(this).setTitle("Karakteristik verisi")
                .setMessage(message).setPositiveButton("Tamam", null).show();
        });
    }

    private String properties(int flags) {
        StringBuilder value = new StringBuilder();
        if ((flags & 2) != 0) value.append("READ ");
        if ((flags & 8) != 0) value.append("WRITE ");
        if ((flags & 4) != 0) value.append("WRITE_NO_RESPONSE ");
        if ((flags & 16) != 0) value.append("NOTIFY ");
        if ((flags & 32) != 0) value.append("INDICATE ");
        return value.length() == 0 ? "properties=" + flags : value.toString();
    }

    private String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02X ", b & 255));
        return value.toString().trim();
    }

    private void log(String value) {
        journal.append(new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date()))
            .append(' ').append(value).append('\n');
        if (journal.length() > 50000) journal.delete(0, journal.length() - 40000);
        if (logView != null) logView.setText(journal.toString());
    }

    private void shareLog() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "HK9 Analyzer oturum günlüğü");
        share.putExtra(Intent.EXTRA_TEXT, journal.toString());
        startActivity(Intent.createChooser(share, "Günlüğü paylaş"));
    }

    @Override protected void onStop() { stopScan(); super.onStop(); }
    @Override protected void onDestroy() { stopScan(); disconnect(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
