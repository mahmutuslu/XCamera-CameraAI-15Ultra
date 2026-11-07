# CameraAI Model Override v0.4.0 — Nihai Sürüm

**Xiaomi 15 Ultra (xuanyuan) + HyperOS 4 üzerinde Xiaomi 18 Pro Camera 6.8.002090.2 AI Capture Assist uyumluluk düzeltmesi.**

### Düzeltmeler
- `phone_model` AI JSON değeri `madrid` olarak gönderilir.
- Yalnızca AI sahne JSON'unda `motion=0` kullanılır; cihazın hareket sensörü değiştirilmez.
- AI bulut sorgulaması ve genel işlem zaman aşımları 15 saniyeye uzatılır.

### Test durumu
7 Kasım 2026 tarihli cihaz testlerinde AI sonuçları `status=completed, errCode=0` olarak döndü. Araç ve cansız nesne sahnelerinde öneriler alındı; kullanıcı ayrıca insan yüzü ve çiçekte AI önerilerinin geldiğini bildirdi. Başka cihazlar veya kamera sürümleri için garanti verilmez.

### Kurulum
1. Aşağıdaki **CameraAI-ModelOverride-v0.4.0.apk** dosyasını Android'e kurun.
2. LSPosed'da modülü etkinleştirin; kapsamı **com.android.camera** seçin.
3. Telefonu yeniden başlatın ve AI Capture Assist'i açın.

Bu, KernelSU ZIP paketi değil **LSPosed APK modülüdür**. Daha eski, farklı imzalı APK ile güncelleme hatası alırsanız önce eski CameraAI modülünü kaldırıp yenisini kurun; Kamera uygulamasını kaldırmayın.

**APK GitHub Actions tarafından debug anahtarıyla imzalanmıştır.** Xiaomi tarafından yayımlanmış veya onaylanmış resmi yazılım değildir.

Kaynak kod ve ayrıntılı notlar: [CameraAI-ModelOverride/README.md](https://github.com/mahmutuslu/fcmfix-hyperos/tree/cameraai-madrid-build/CameraAI-ModelOverride).
