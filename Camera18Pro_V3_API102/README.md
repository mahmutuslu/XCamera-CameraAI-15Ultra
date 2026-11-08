# Camera18Pro v3 Alpha 3 — Modern Xposed API 102

Xiaomi 15 Ultra (**xuanyuan**, HyperOS 4) + Xiaomi 18 Pro kamera `6.8.002090.2` için **salt-okunur Pixel175 tanı modülü**.

**Önemli:** Bu modül CameraAIMerged + XCamera v2.3'ün yerini ALMAZ. Kamera değerleri, 200MP akışı, yakınlaştırma oranı, işleniyor ekranı veya JPEG verisi değiştirilmez.

## Alpha 3 — hata izolasyonu ve doğru eşleştirme

- 8 Kasım 2026 **14:04:52** başarısız JPEG: `SuperNightSE=false` ve `miHalBufferReceiveError`; bu nedenle gece işleme bayrağını zorlamıyoruz.
- 8 Kasım 2026 **17:28** Alpha 3 testi: dört tam 16320×12288 JPEG, ilk üçünün `isNightCapture=true`, dördüncünün `false` olduğu görüldü.
- Gerçek gözlem: `onPictureTakenFinished`, bazı normal çekimlerde `onCaptureStart` çağrısından **önce** geliyor.
- Alpha 3 kancası yalnız bekleyen, son 1,8 saniyede başlamış, daha önce ön aşaması bitmemiş bir çekime `SHOT2SHOT_FINISH` bağlar. Diğerlerini `UNMATCHED_PRESTART_OR_LATE` olarak gösterir.
- MiVi sonucu fotoğraf dosyasındaki saniye çözünürlüklü zaman ile başlama zamanını **yaklaşık** ilişkilendirir (`FILENAME_TIME_APPROX`). Eşleşemezse `FIFO_HEURISTIC`; hiçbir bekleyen çekim yoksa `NO_PENDING` yazar.
- Yanlış ilişkilendirmeyi azaltır, fakat kesin donanım frame-ID eşleştirmesi değildir.
- Bu sürüm HAL hatasını onarmaz, kamera uygulamasını veya çekim ayarlarını değiştirmez.

## Alpha 1 → Alpha 3 değişiklikleri

- Her `PixelModule.onCaptureStart` çağrısına tekil bir `id` atar.
- `SHOT2SHOT_FINISH` ilk çekim aşamasını (tam JPEG kaydı **değil**) kaydeder.
- `ImageReceiverExecutor$2.onSuccess` gerçek MiVi yanıtındaki **resim dosyası adı**, kare ve toplam süreyi kaydeder.
- Başarı/hata yanıtlarını bekleyen çekimlerle **FIFO tahmini** olarak eşleştirir. Eşleştirme kesin değildir; çoklu ve sırası değişmiş işler yanlış eşleşebilir.
- `onError`, `onHalFailed` ve `AidlBGServiceClient$1.onCaptureFailed` kancalarıyla hata yolunu kaydeder.
- `onEarlyImageAvailable` / `onFinalImageAvailable` dinlemesi korunur. Alpha 1'in beş başarılı çekiminde bu olaylar tetiklenmedi.
- `onHalFailed` tek başına fotoğrafın başarısız olduğunu **kanıtlamaz**. JPEG sonucu orijinal kamera logunda `CAPTURE-END` ve `16320x12288` ile doğrulanmalıdır.

## API ve güvenlik

- `minApiVersion=102`, `targetApiVersion=102`
- `XposedModule`, `hook().intercept()`, `META-INF/xposed/java_init.list`
- LSPosed kapsamı: **yalnızca `com.android.camera`**
- Kurulum veya kullanım için Android Studio gerekli değil.
- **Eski v2.3 modülü etkin kalsın.**
- Alpha 1 GitHub Actions tarafından test amaçlı debug anahtarıyla imzalandı. Alpha 3 farklı bir debug anahtarıyla imzalanmış olabilir. Android "uygulama yüklenmedi" derse **yalnızca `dev.camera18pro.v3` Alpha 1 uygulamasını kaldırıp** Alpha 3'ü kur. v2.3 modülünü veya Kamera uygulamasını kaldırma.

## Masaüstüne test logu kaydetme (Windows PowerShell)

```powershell
$adb = 'C:\platform-tools\adb.exe'
& $adb logcat -c
# Telefonda: Xiaomi 18 Pro kamera > 200 MP > 4,3x > birkaç çekim, kaydı bitir.
$dosya = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Camera18Pro_V3_Alpha3_Pixel175.txt'
& $adb logcat -d -v threadtime -b main -b system -b crash |
  Out-File -FilePath $dosya -Encoding utf8
Get-Item $dosya | Select-Object FullName,Length
```

Gönderilecek dosyada `CAMERA18PRO_V3`, `onReprocessSuccess`, `[CAPTURE-END]`, `miHalBufferReceiveError` ve `smallPicture=true` beraber incelenir.

## Durum

**Tanı sürümü.** Hatanın kök nedeni henüz kesinleşmedi ve PixelModule orijinal `İşleniyor` arayüzü taşınmadı. API 102 kurulumu ve derlemesi doğrulanmalı; çalışması cihaz testine tabidir.
