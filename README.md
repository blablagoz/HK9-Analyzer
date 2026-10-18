# HK9 Analyzer

HK9 Pro ve diğer BLE cihazlarını incelemek için Android uygulaması. Android 8.0 ve üzeri gerektirir.

## Özellikler

- 15 saniyelik BLE taraması; cihaz adı, adresi, RSSI ve ham reklam verisi.
- Seçilen cihaza bağlantı, GATT servisleri ve karakteristik UUID/özellikleri.
- READ özelliği olan karakteristikleri tek tek okuma; hex ve UTF-8 görünümü.
- Zaman damgalı oturum günlüğünü Android paylaş menüsüyle dışa aktarma.
- Bluetooth ve çalışma zamanı izinleri için kontroller.

## Kullanım

Bluetooth'u açın, izinleri verin ve **Tara** düğmesine dokunun. Android 8–11'de konum servislerinin açık olması gerekebilir. Listeden saatinizi seçin; bağlantı sonrası karakteristiklere dokunarak okuyun. Saat Wearfit Pro ile bağlıyken ikinci bağlantıyı reddedebilir; gerekirse Wearfit Pro bağlantısını geçici olarak kapatın.

Bu ilk sürüm standart BLE keşif ve okuma araçları sunar. Wearfit Pro'nun özel komutları, sağlık verisi çözümleme, bildirim aboneliği ve firmware işlemleri henüz uygulanmadı. Gerçek HK9 Pro üzerinde donanım testi gereklidir. Günlükler cihaz adresleri ve ham veriler içerebilir; paylaşmadan önce inceleyin. Uygulama günlükleri sunucuya göndermez.

Önceki sohbetin proje arşivine erişilemediğinden bu kaynaklar yeni oluşturulmuştur.

## APK

GitHub **Actions → Build Android APK → Run workflow** yoluyla derleyin. Başarılı çalışmanın **Artifacts** bölümünden `HK9-Analyzer-debug-apk` dosyasını indirin. Debug APK test amaçlı imzalanır.

Yerel derleme için JDK 17 ve Android SDK 35 ile:

```sh
bash ./gradlew assembleDebug
```

Windows: `gradlew.bat assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
