# CameraAI + XCamera — v2.11 Türkçe son paket (8 Kasım 2026)

## Bileşenler ve kurulum

**Önerilen mevcut kurulum:** Çalışan `CameraAI + XCamera v2.10` modülünü **kaldırmayın**. Yanına `CameraAI_Turkce_v2.11_Ek_Modul.apk` APK'sını yükleyin. LSPosed modern API 102'de yeni modülü etkinleştirip kapsam olarak yalnızca `com.android.camera` seçin; Kamera uygulamasını kapatıp açın. Gerekirse telefonu yeniden başlatın.

**Önemli:** Yayınlanan v2.11 Türkçe APK, **ayrı bir modüldür**. Mevcut v2.10 modülünün **yerine geçen** tek parça APK değildir. Bu sayede 200 MP akışı, zoom gizleme ve deklanşör çevresindeki basit dönen halka + `200MP görüntü işleniyor…` görünümü v2.10 kodundan bağımsız kalır. Ek bir PowerShell/ADB `setprop` komutu gerekmez.

**Kapsam:** 18 Pro kamera portunun mevcut Türkçe kaynaklarından yararlanma; AI Capture Assist / AI çekim asistanı, poz önerileri, ağ ve sunucu hata mesajları, menüler, belge/kimlik fotoğrafı, efekt ve filtre metinlerindeki belirli İngilizce veya Çince etiketlere çeviri sağlama. Kullanıcının cihazında **tüm** metinlerin çevrildiği henüz onaylanmadı. Dinamik sunucu metinleri ve farklı cihaz APK'sındaki yeni yazılar çevrilmeden kalabilir.

**Güvenlik ve geri alma:** Sistemdeki Kamera APK'sını kaldırmayın. Sorun olursa yalnızca Türkçe ek modülünü LSPosed'den devre dışı bırakın; v2.10'a müdahale etmeyin. Kamera/HAL, EXIF, görüntü işleme ve kamera sensör ayarlarını değiştirmek için tasarlanmamıştır.

## Doğrulama

- Modern API 102 Java kodunun GitHub Actions ile derlenmesi ve APK ZIP/CRC denetimi.
- Android APK Signature Scheme v2 imzası ve LSPosed giriş noktası denetimi.
- Çalışan v2.10'un önceki cihaz testi **ayrıdır**; v2.11 Türkçe arayüzün gerçek cihaz doğrulaması henüz yapılmadı.
- CI her paketi yeni, geçici bir sertifikayla imzalar. Bu nedenle daha önce başka anahtarla imzalanmış Türkçe APK'nın **üzerine yüklenemeyebilir**; bu eklentiyi kaldırıp yeni sürümü kurmak gerekebilir. Esas v2.10 modülünü kaldırmayın.

## Kaynak kodu ve yeniden derleme

- Çeviri ve API 102 hook kaynakları: `app/src/main/java/dev/cameraai/override/CameraTurkishHook.java`
- Mevcut CameraAI/Pixel175 kaynakları: `ModernCameraAIHook.java`, `Pixel175Tracer.java`, `ProcessingHint.java`
- Ayrı modül paketleme kaynağı: `tools/build_translation_apk.py`
- GitHub Actions: `.github/workflows/build.yml` ve `.github/workflows/publish-v211.yml`

```bash
gradle --no-daemon :app:assembleDebug
python -m pip install -r requirements.txt
python tools/build_translation_apk.py --bridge app/build/outputs/apk/debug/app-debug.apk --output dist/CameraAI_Turkce_v2.11_Ek_Modul.apk
```

Sisteme Android Studio kurmak gerekmez; GitHub Actions derleme gerçekleştirir. Oluşan özel imza anahtarı repoya konmaz. v2.10 ana APK'sı ayrı bir ürünün (XCamera 1.2) derlenmiş bileşenini içerir. Bu depodaki `app/` kaynakları CameraAI köprüsü ve Türkçe hook içindir; **XCamera'nın üçüncü taraf kaynak kodları ve Xiaomi Kamera APK'sı mevcut değildir**. Kaynakların erişilebilir olması üçüncü taraf kodlar için yeniden dağıtım izni vermez.

## Paket bütünlüğü

Release altında `CameraAI_Turkce_v2.11_Ek_Modul.apk` ile `SHA256SUMS.txt` yer alır. SHA256 değeri, indirilen APK'ya karşı doğrulanabilir. GitHub otomatik kaynak kod ZIP/TAR arşivlerini aynı etiket/commit için sunar.
