# XCamera + CameraAI — Xiaomi 15 Ultra

Xiaomi 15 Ultra (`xuanyuan`, HyperOS 4) için **bağımsız, deneysel LSPosed modül projesi**. FCMFix ile ilişkili değildir.

## Amaç
- Xiaomi 18 Pro Kamera `6.8.002090.2` (`com.android.camera`) ile XCamera 1.2 uyumluluk kancalarını korumak.
- CameraAI: `phone_model=madrid`, AI sahne `motion=0` ve ilgili AI zaman aşımını 15 saniyeye yükseltmek.
- 200 MP (mod kimliği `175`) için **1x / 3x / 4.3x** zoom düğmelerini göstermek.
- **Hedef:** 1x başlangıç; **cihaz testiyle doğrulanan mevcut davranış:** 200 MP moduna girişte uygulama 4.3x seçiyor. V1.7 başlangıç değerini zorla değiştirmez.
- LSPosed kapsamı: yalnızca `com.android.camera`.

## Doğrulama durumu
- XCamera 1.2: `29/29 15 Ultra values set` log ile doğrulandı.
- Birleşik API 102 modülündeki CameraAI kancalarının kurulumu log ile doğrulandı.
- Önceki **tek düğmeli** [4.3] deneyinde 12.288 × 16.320 (200,54 MP) fotoğraf doğrulandı.
- **Üç düğmeli [1, 3, 4.3] profil cihazda test edildi ve gerçek zoom'da BAŞARISIZ.** Düğmeler algılanıp oranlar 1/3/4.3'e değişse de kameraya her seferinde 1.121951 zoom oranı gönderiliyor; gerçek kamera kimliği 5 olarak kalıyor. 200 MP modu açıldığında uygulama 4.3x ile başlıyor.
- **v1.7**, zoom dönüştürücü `i9.g.nh(float)` yöntemini salt-okunur izler; gerçek zoom düzeltmesi değil, teşhis sürümüdür.
- **v1.8 hibrit lens yönlendirmesi — CİHAZ TESTİNDE BAŞARISIZ.** `175→167` önerisi hem geçişi tamamlayamadı hem de **167'nin Pro modu** olduğu sonradan doğrulandı. v1.9'da bu yanlış deney kaldırıldı. [Hata raporu](HYBRID_LENS_v1.8_TEST.md).
- **v1.9 PixelModule lens izni testi — CİHAZ TESTİNDE BAŞARISIZ.** `isCameraSwitchingDuringZoomingAllowed()` zaten `true`; müdahale sensör değiştirmiyor. `cameraId=5` ve fiziksel zoom `1.1219512` kalıyor. `debug.cameraai.pixelzoom=0` önerilir. [Tanı ve geri dönüş](PIXEL_ZOOM_v1.9_TEST.md).

- **v2.0 fiziksel seçici incelemesi — OKUMA AMAÇLI TEST.** Stok 6.7 ve port 6.8'in `PixelModule` kamera seçim koşulları karşılaştırıldı. `B2.c.c` ve rol/yetenek bayrakları tek orijinal çağrıda kaydediliyor; lens seçimi değiştirilmez. [Kod incelemesi ve test](PIXEL_SELECTOR_v2.0_KARAR_DALI.md).

- **v2.1 zoom karar mekanizması — SALT OKUNUR TEST.** Kamera 6.8 DEX'inde doğrulanan `com.android.camera.data.data.i.n(int,int)`, `i.O(int)` ve `i9.g.B7()` giriş/çıkışlarını kayıt altına alır; kamera ID'si/zoom hesabı/çıktı değiştirilmez. [Test planı](ZOOM_DECISION_v2.1_TEST.md).

## HAL gerçek kamera haritası (8 Kasım 2026)

Cihazın `dumpsys media.camera -v 2` verilerinden, `cameraId 6`nın `physicalIds=[3,2,4,5]` mantıksal çoklu kamerası olduğu doğrulandı. Gerçek lensler ve Qualcomm Quadra CFA (`qcfa_dimension`) boyutları:

| Fiziksel kamera | Lens | HAL QCFA boyutu |
|---|---|---|
| `2` | 1x ana | `8192×6144` (50MP) |
| `3` | 0.6x ultra geniş | `8192×6144` (50MP) |
| `4` | 3x telefoto | `8192×6144` (50MP) |
| `5` | 4.3x periskop | `16320×12288` (200MP) |

Bu **donanım destek kanıtıdır**, PixelModule 175 içinde otomatik hibrit lens geçişi yaptığı anlamına gelmez. 4.3x çalışan 200MP yolu korunurken 1x/3x için 50MP stream ve yeniden yapılandırılmış kamera oturumu gerekir. [HAL kanıt raporu](HAL_XUANYUAN_KAMERA_HARITASI_2026-11-08.md).

## v2.2 QCFA akış tanısı (8 Kasım 2026)

Kamera 6.8'in gerçek `Camera2Module.updatePictureAndPreviewSize()`, `updateConfigQcfa()` ve `m6.o.f(...):Size` metotlarına salt-okunur kancalar eklendi. `debug.cameraai.streamprobe=1` ile `PixelModule` mod 175 için kamera/çözünürlük/QCFA akış kararları loglanır. V2.2 normalde bu kancayı kapalı tutar. [v2.2 test planı](QCFA_STREAM_v2.2_TEST.md).

**Sürüm henüz gerçek 1x/3x lens değiştirme düzeltmesi değildir.** 4.3x 200MP fotoğraf yoluna dokunulmamıştır. 1x/3x için HAL'deki 50MP boyutlar ve uygun CaptureSession birlikte seçilmeden fiziksel ID zorlaması yapılmayacak.

## Kaynak kod ve üçüncü taraf dosyalar
`app/` bizim geliştirdiğimiz modern LSPosed API 102 köprüsüdür. XCamera 1.2 APK'sı ve Xiaomi Kamera APK'sı depoda **yer almaz**. `tools/merge_and_sign.py`, kullanıcı tarafından sağlanan XCamera 1.2 APK'sına kendi derlediğimiz köprünün DEX dosyasını ekler. XCamera'nın `classes.dex` kodunu değiştirmez.

## Derleme
GitHub Actions → **Build CameraAI LSPosed Bridge** çalıştırın; derleme sonrası artifact olarak `app-debug.apk` alın. Bu APK **tek başına kurulacak birleşik modül değildir**.

Lokal derleme:

```bash
gradle :app:assembleDebug
python -m pip install -r requirements.txt
python tools/merge_and_sign.py --xcamera XCamera-1.2.apk --bridge app/build/outputs/apk/debug/app-debug.apk --output XCamera-CameraAI-v2.2-TEST.apk --version-name 2.2
```

Birleştirici ilk çalışmada `keys/` altında **yerel özel imza anahtarı** üretir. Güncellemelerde aynı anahtarı koruyun; depoya veya başkasına göndermeyin. Farklı imzalı XCamera'nın üzerine APK yüklenemeyeceğinden önceki modül/kurulum yedeğini alın. **Sistem Kamera uygulamasını kaldırmayın**.

## 200 MP zoom profilleri
`debug.cameraai.zoomprofile` sistem özelliği, Kamera işlemi açılırken okunur:

| Profil | 200 MP seçenekleri | Durum |
|---|---|---|
| `0` | XCamera orijinal: 0.6x / 1x / 3x / 4.3x | Geri dönüş |
| `1` | 4.3x | Daha önce doğrulanmış tek düğmeli test |
| `2` | 1x / 3x / 4.3x | **Varsayılan üç düğmeli TEST** |

Örnek geri dönüş:
```powershell
& "C:\platform-tools\adb.exe" shell setprop debug.cameraai.zoomprofile 0
& "C:\platform-tools\adb.exe" shell am force-stop com.android.camera
```

Önceki `debug.cameraai.zoomfix` ayarı bu sürümde kullanılmaz.

## Sınırlar
[Xiaomi'nin resmî teknik özelliklerine](https://www.mi.com/tr/product/xiaomi-15-ultra/specs/) göre **200 MP modu periskop kameraya özgüdür**; 1x ana kamera ve 3x telefoto 50 MP sensörler kullanır. 200 MP sensör 15 Ultra'da yaklaşık 4.3x doğal optik görüş açısına sahiptir. 1x veya 3x'in görünmesi, o kadrajda sensörden **gerçek 200 MP ayrıntı** alınacağı anlamına gelmez. Bu kod Kamera HAL'ini ya da fiziksel sensörü değiştirmez. Cihazda test etmeden nihai sürüm olarak yayımlamayın.

Ayrıntılı test akışı: [TEST_PLANI.md](TEST_PLANI.md).
