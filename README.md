# HK9 Companion

HK9 Pro+ için sade, koyu temalı Android eşlikçi uygulaması. Android 8.0+.

## 0.2.0

- Özet, Saatim, Kayıtlar ve Ayarlar ekranları; mint vurgular, yerel vektör çizimler, büyük dokunma alanları.
- BLE tarama, cihaz seçimi ve son saate yeniden bağlantı.
- Nordic UART TX kanalının gerçek CCCD aboneliği ile canlı veri dinleme.
- Parçalı ve birleştirilmiş AB/EA paketlerini ayırma; son 500 paket ve ham bildirimi bellekte tutma.
- Standart Bluetooth servisleri varsa pil, üretici, model ve firmware bilgilerini okuma.
- nRF Connect metin kayıtlarını içe aktarma (2 MB'a kadar), canlı ve içe aktarılmış kayıtları ayrı gösterme.
- JSON dışa aktarma; hesap, internet izni veya sunucuya otomatik gönderim yok.

## Kullanım

Saatim → Yakındaki cihazları bul → saatini seç. Yakındaki cihazlar iznine izin ver. Bağlantı kurulamazsa nRF Connect veya Wearfit Pro'nun saat bağlantısını kapatıp yeniden dene. Android 8–11'de konum servislerinin açık olması gerekebilir.

Kayıtlar bölümünde canlı paketler görünür. Kayıt aç ile nRF Connect'ten dışa aktarılmış .txt dosyası seçilebilir. JSON kaydet, seçili kaynağın verisini Android dosya seçicisiyle kaydeder. Uygulama kapanırsa oturum belleği temizlenir; saklamak istediğin kaydı önce dosyaya kaydet.

## Sınırlar

HK9'un üreticiye özgü pil, adım, sağlık ve sürüm alanları henüz doğrulanmış değildir. Paket etiketlerindeki aday yorumlar kesin ölçüm değildir. Ana ekrandaki pil yalnızca standart Battery Level karakteristiğinden okunursa gösterilir. Sağlık ve hareket kartları gerçek veri çözülene kadar boş durum gösterir. Firmware yazma, saat içinden uygulama silme/yükleme, AI hizmeti ve arka planda kalıcı bağlantı bu sürümde uygulanmadı.

Fiziksel HK9 Pro+ ve telefon üzerinde test gereklidir. JSON dosyaları cihaz adresini ve ham veri içerebilir. Kullanıcının gerçek Bluetooth kayıtları kaynak depoya eklenmez.

## Derleme ve kontroller

JDK 17, Android SDK 35 ve Gradle wrapper:

```sh
bash ./gradlew assembleDebug
```

GitHub Actions APK üretir. Elle Run workflow başlatıldığında dört ekran Android 35 emülatöründe açılır, çökme kontrolü yapılır ve ekran görüntüleri artifact olarak kaydedilir. Paket ayrıştırıcı testleri parçalanma, birden fazla paket, bozuk uzunluk, sıfırlama ve hex dönüşümünü denetler.

APK: app/build/outputs/apk/debug/app-debug.apk. APK debug imzalıdır; eski APK'nın debug sertifikası farklıysa eski uygulamayı kaldırmak gerekebilir.

Önceki sohbetin Kotlin kaynakları HK9Analyzer-original-source.zip içinde değiştirilmeden korunmuştur. Depo kökündeki Java projesinden ayrı bir sürümdür.
