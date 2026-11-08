# CameraAI + XCamera — v2.12 Tek Parça Final (9 Kasım 2026)

**Bu, önceki ayrı Türkçe v2.11 eklentisinin yerini alan TEK APK sürümüdür.** Kullanıcının istediği gibi kamera kancaları, 200 MP UI ve Türkçeleştirme tek bir LSPosed modern API 102 APK'sında bulunur. Başka modül yüklemeye gerek yoktur.

## İçerik

- Orijinal, değiştirilmemiş XCamera 1.2 `classes.dex` kancaları.
- CameraAI API 102 `ModernCameraAIHook`: Xiaomi 18 Pro Kamera `6.8.002090.2` portu için AI uyumluluk işlemleri.
- `Pixel175Tracer` + `ProcessingHint`: 200 MP (175) çekiminde **“200MP görüntü işleniyor…”** metni ve dönen deklanşör halkası. Eski mozaik/bulanıklık animasyonu kaldırılmıştır.
- `CameraTurkishHook`: AI Capture / AI çekim asistanı, kamera ayarları, poz önerileri, efekt/filtreler, belge çekimi, ağ/sunucu hata mesajları için Türkçe UI. Mevcut `values-tr` kaynaklarından yararlanır.
- Modül otomatik çalışır. Kullanıcının PowerShell'den `setprop` girmesi gerekmez.
- LSPosed kapsamı **yalnızca `com.android.camera`**.

## Kurulum (Xiaomi 15 Ultra / xuanyuan / HyperOS 4)

1. Mevcut v2.10 XCamera + CameraAI ve ayrı v2.11 Türkçe eklentisi varsa **yalnızca bu LSPosed modüllerini** devre dışı bırakıp kaldırın. v2.12 farklı imzalıdır; APK üstüne kurulum başarısız olabilir. Gerekli modül ayarlarının yedeğini alın.
2. **Sistem Xiaomi Kamera uygulamasını ve kamera verilerini kaldırmayın.**
3. Bu sürümün **tek dosyası** `CameraAI_XCamera_v2.12_Tek_Parca_Final.apk` APK'sını kurun.
4. LSPosed'de yeni modülü etkinleştirip kapsamı `com.android.camera` olarak işaretleyin.
5. Kamerayı yeniden başlatın; gerekirse telefonu yeniden başlatın. 200 MP çekimi ve AI Capture menüsü metinlerini test edin.

**Geri dönüş:** Yeni modül nedeniyle kamera açılmıyorsa LSPosed'de v2.12'yi kapatın; gerekirse kaldırıp daha önce cihazda çalışan v2.10 APK'sını tekrar kurun.

## Doğrulama ve sınırlamalar

- GitHub Actions ile API 102 Java derleme.
- Çıktıda dört giriş noktası ve tek `com.android.camera` kapsamı denetlenir.
- Orijinal XCamera `classes.dex` SHA256 doğrulaması, DEX/ZIP CRC, `resources.arsc` hizalaması ve APK Signature Scheme v2 imza doğrulaması yapılır.
- **Gerçek Xiaomi 15 Ultra cihazında v2.12 birleşik sürüm henüz test edilmedi.** “Final” burada tek dosyalı paketleme hedefinin tamamlanmasıdır; tüm AI sahne özelliklerinin çevrildiği veya her 200 MP çekiminin başarılı olduğu iddia edilmez.
- Türkçeleştirme bazı üçüncü taraf dinamik metinleri kapsamayabilir. Kişiye/sunucuya özel yanıtları değiştirmez; sadece uygulama arayüzünün belirli metinlerini çevirir.

## Kaynak kodu

GitHub deposundaki `app/src/main/java/dev/cameraai/override/` klasöründe `ModernCameraAIHook.java`, `Pixel175Tracer.java`, `ProcessingHint.java` ve `CameraTurkishHook.java` bulunur. Otomatik birleşik paketleyici `tools/build_final_v212.py`, v2 imzalayıcı `tools/merge_and_sign.py` ve yayımlama iş akışı `.github/workflows/publish-final-v212.yml` içerir.

**Kaynak açıklığı:** CameraAI/Türkçe kısmının kaynakları mevcuttur; orijinal XCamera 1.2 ve Xiaomi Kamera uygulamasının üçüncü taraf kaynak kodları bu depoda yoktur. Mevcut projede önceden onaylanmış XCamera ikili dosyasından üretim yapılır. Bu durum “XCamera'nın tüm kaynak kodları açık” anlamına gelmez.

## Otomatik derleme (Android Studio gerektirmez)

```bash
gradle --no-daemon :app:assembleDebug
python -m pip install -r requirements.txt
python tools/build_final_v212.py \
  --base XCamera_CameraAI_v2.3_QCFA_TEST.apk \
  --bridge app/build/outputs/apk/debug/app-debug.apk \
  --output dist/CameraAI_XCamera_v2.12_Tek_Parca_Final.apk
(cd dist && sha256sum -c SHA256SUMS.txt)
```

İndirilebilir Release APK'sı otomatik oluşturulup imzalanır. Yayınlanan imzanın SHA-256 dosyası aynı sürümde sunulur. CI'da özel imza anahtarı depo kaynaklarına eklenmez; bu nedenle bağımsız yeniden derleme yapıldığında imza farklı olabilir. GitHub'daki mevcut aynı sürüm APK'sını yeniden imzalamadan koruyun.

**İlgili önceki sürümler:** v2.10 çalışan tek modül tabanıdır; v2.11 Türkçe ayrı eklentisi artık kullanılmamalıdır. v2.12 ikisinin işlevlerini tek kurulum paketine toplar.
