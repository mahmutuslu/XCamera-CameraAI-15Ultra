# Xiaomi 15 Ultra (xuanyuan) — gerçek HAL kamera/Quadra CFA haritası

**Kanıt:** Kullanıcının gönderdiği 8 Kasım 2026 tarihli, 56.156 satırlık `adb shell dumpsys media.camera -v 2` dökümü (`Kamera_HAL_Bilgileri.txt`). Bu dosya kişisel/cihaz tanılama bilgisi içerebileceğinden depoya yüklenmedi; yalnızca aşağıdaki sınırlı teknik bulgular eklendi.

## Kesinleşen kamera eşleşmesi

`Camera HAL device vendor_qti/6`, gerçek `android.request.availableCapabilities` içinde `LOGICAL_MULTI_CAMERA` sunuyor. `android.logicalMultiCamera.physicalIds = [3 2 4 5]`.

Fiziksel lensler, HAL `android.lens.info.availableFocalLengths` ve `android.sensor.info.physicalSize` ile eşleştirildi:

| HAL Camera ID | Sensör türü | Focal length | Sensör fiziksel genişliği | Not |
| --- | --- | ---: | ---: | --- |
| 2 | 1x ana kamera | 8.72 mm | 13.1072 mm | Native 50MP QCFA |
| 3 | 0.6x ultra geniş | 2.13 mm | 5.24288 mm | QCFA |
| 4 | 3x telefoto | 11.50 mm | 5.7344 mm | Native 50MP QCFA |
| 5 | 4.3x periskop | 25.10 mm | 9.1392 mm | Native 200MP QCFA |
| 6 | Mantıksal çoklu arka kamera | 8.72 mm | 13.1072 mm | Fiziksel ID'ler: 3,2,4,5 |
| 0,7 | Ek mantıksal kamera kimlikleri | 8.72 mm | 13.1072 mm | Aynı fiziksel ID'ler |
| 8,9 | `SYSTEM_CAMERA` özel cihazları | — | — | Fotoğraf modunda normal lens gibi kullanmayın |

Focal/sensör genişliği oranı 4/2 için ≈ 3x, 5/2 için ≈ 4.1x kadraj farkına denk geliyor. 4.3x UI değeri üretici kalibrasyonu olabilir.

## Yüksek çözünürlük altyapısı **HAL düzeyinde var**

QCFA vendor metadata'sı `org.codeaurora.qcamera3.quadra_cfa` altında:

| HAL fiziksel kamera | `is_qcfa_sensor` | `qcfa_dimension` | `availableStreamConfigurations` çıktısı |
| --- | --- | --- | --- |
| 2 — ana | 1 | `[8192 6144]` | `[33 8192 6144 0]` ve format 34,35 |
| 4 — 3x | 1 | `[8192 6144]` | `[33 8192 6144 0]` ve format 34,35 |
| 5 — 4.3x | 1 | `[16320 12288]` | `[33 16320 12288 0]` ve format 34,35 |

- Android `ImageFormat.JPEG` = 256; Camera2 format 33 burada camera metadata'da **BLOB** (JPEG/HEIC taşıyabilir) ve 34 PRIV, 35 YUV_420_888.
- `0` kamera metadata akış dizilerinde `OUTPUT` yönünü ifade eder; gerçek Camera2 oturum uyumluluğu, format/stream kombinasyonu ve vendor remosaic ayarlarıyla birlikte test edilmelidir.
- `com.xiaomi.qcfa.customSizes` ID 5 için `[8160 6144 16320 12288]` bildiriyor; periskopta hem yaklaşık 50MP hem 200MP QCFA yolu var.
- Standart `android.sensor.info.pixelArraySize` fiziksel ID 2/4/5 için **binlenmiş** ≈12MP aktif alan döndürebilir. Bu, vendor QCFA'nın 50MP/200MP yetenekleri olmadığı anlamına gelmez.

Kanıtın HAL dökümündeki kaynak satırları:
- Kamera 2: 13609 (bölüm), 13738 (focal), 14469 (size), 15743–15747 (QCFA).
- Kamera 4: 25725 (bölüm), 25840 (focal), 26536 (size), 27789–27793 (QCFA).
- Kamera 5: 31731 (bölüm), 31856 (focal), 32539 (size), 33803–33807 (QCFA), 34706 (`customSizes`).
- Mantıksal 6: 37642 (bölüm), 42240 (`physicalIds`).

## Önceki testlerle bağlantı

- Port Xiaomi Kamera 6.8, normal Fotoğraf modunda `mode 163` için açık kamera `ID 6` üzerinde doğru zoom aralığını ve değişen HAL crop/zoom oranlarını kullanıyor. HAL bu ID'nin gerçekten fiziksel 2,3,4,5 kameralarından oluşan mantıksal kamera olduğunu doğruluyor. Hangi fiziksel sensörün belli zoom'da etkinleştiğini bu log tek başına göstermez; `android.logicalMultiCamera.activePhysicalId` CaptureResult gerekir.
- Portun 200MP `PixelModule mode 175` kamerası her zoom değerinde fiziksel **ID 5** açıyor; `B2.c.c(0,175,flag)` farklı 1x/3x/4.3x değerlerinde 5 dönüyor; `i9.g.nh` HAL oranını hep `1.1219512` yapıyor.
- Donanım engeli yok: 2 ve 4 için HAL QCFA 8192×6144, 5 için 16320×12288 destek bilgisini sunuyor. **Ancak aynı PixelModule mode175 oturumunda 2/4'e 200MP stream göndermek destekli değil**. 50MP sensörde 200MP stream'le doğrudan kameraId 5→2/4 zorlamak güvenli değil.

## Sonraki mühendislik işi

1. Port 6.8 `PixelModule` içindeki 200MP `mCameraDevice`, QCFA remosaic, output surface ve stream size seçimini bul.
2. Mode175 ekranında 1x/3x seçimi için fiziksel 2/4'ün `8192×6144` QCFA output + yeni session ile açılmasını sağlayabilecek kamera-native *50MP destekli yüksek çözünürlük yolu* tespit et. Mode167 PRO değildir ve bu amaçla kullanılmamalıdır.
3. 4.3x seçimi için fiziksel 5 + `16320×12288` yolunu koru. İlk açılış 1x için UI tercihi ve session setup birlikte çözülmeli.
4. Hedef test: zoom değişince gerçek `activePhysicalId` ya da açık fiziksel ID, stream boyutu ve fotoğrafın EXIF çıktısı doğrulanacak; AI/XCamera kancaları korunacak.

**Durum:** Kamera/hardware kimlikleri ve yüksek çözünürlük desteği kanıtlandı. Port uygulamasında hibrit lens geçişi halen gerçekleştirilmedi; bu rapor çözülmüş fonksiyon iddiası değildir.
