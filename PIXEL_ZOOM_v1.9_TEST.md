# v1.9 — 200 MP PixelModule gerçek zoom izni testi

## v1.8 hatasının düzeltilmesi

Xiaomi 18 Pro Kamera `6.8.002090.2` DEX incelemesine göre:

- `ProModuleEntry.getModuleId() = 167` (**Pro modu**). Önceki `175→167` deneyindeki “50 MP modu” varsayımı yanlıştı.
- `CaptureModuleEntry.getModuleId() = 163` (**Fotoğraf**).
- `PixelModuleEntry.getModuleId() = 175` (**Yüksek çözünürlük / 200 MP**).

v1.8'in `StartControl.create(175)` çağrısını `167`ye çeviren hibrit kancası **tamamen kaldırıldı**. `debug.cameraai.hybridroute` v1.9'da kullanılmaz.

## Yeni gerçek 200 MP kancaları

Sadece `com.android.camera.features.mode.pixel.PixelModule` örneğinde:
- `PixelModule.isZoomEnabled()`: `false` ise deneysel olarak `true` döndürebilir;
- `Camera2Module.isCameraSwitchingDuringZoomingAllowed()`: yalnızca PixelModule için `false` ise deneysel olarak `true` döndürebilir.

`debug.cameraai.pixelzoom=1` ile etkinleşir. **Varsayılan kapalı.** Kameranın başlatma modu 175 olarak kalır. 200 MP yakalama akışı, HAL, sensör kimliği, kırpma oranları veya fotoğraf boyutu doğrudan değiştirilmez.

Ek bir `debug.cameraai.pixelrouteprobe=1` seçeneği fiziksel kamera seçici `B2.c.c` için yalnızca alternatif Fotoğraf-modu kamera kimliğini tanılar. Bu **ek tanıyı şimdilik KAPALI** tutun. Yeni algoritmaya dair cihaz testi yok.

## Cihazda deneme — Windows PowerShell

Önce eski çalışan modül APK'sını ve fotoğraflarınızı yedekleyin. Yalnızca birleşik v1.9 APK'sını kurun; LSPosed kapsamı `com.android.camera`.

```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.hybridroute 0
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell setprop debug.cameraai.pixelrouteprobe 0
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell setprop debug.cameraai.pixelzoom 1
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```

Kamera > 200 MP > sırasıyla 4.3x, 1x, 3x, 4.3x düğmelerini deneyin. Gerçek kadraj ve çekimlerin ayrıntıları kontrol edilsin.

```powershell
& $adb logcat -d -v time |
  Select-String -Pattern "PIXEL175|PixelModule|mPendingOpenModule|actualCameraId|applyZoomRatio-R|switchCameraLens|AndroidRuntime|CameraAIMerged" |
  Select-Object -Last 120
```

Günlükte `PIXEL175 actual isZoomEnabled=...` ve `PIXEL175 camera lens switching allowed=...` kaydı görülürse hedef kancalar çağrılıyor demektir. Kayıt bulunmaması, davranışın bambaşka metot tarafından yönetildiğini gösterebilir.

## Geri dönüş

```powershell
& $adb shell setprop debug.cameraai.pixelzoom 0
& $adb shell am force-stop com.android.camera
```

Kamera açılmaz veya çökme görülürse deney kapatılır; gerekirse önceki çalışan v1.7 APK'sına dönüş yapılır. **Sistem Kamera APK'sını kaldırmayın.**

## Fiziksel sınır ve açık noktalar

- 200 MP gerçek sensör 4.3x periskoptadır; 1x ana/3x telefoto sensörler 50 MP'dir.
- Yukarıdaki izin kancaları lensin **gerçekten değişmesini garanti etmez**. 1x/3x tam sensör çözünürlüğü, lens geçişi ve 1x başlangıç henüz sağlanmadı.
- İleride mode175 içindeki fiziksel lens kimliği ve çıktı stream yapılandırmalarının birlikte koordine edilmesi gerekebilir.
- Bu nedenle v1.9 bir **cihaz doğrulaması bekleyen TEST**, nihai çözüm değildir.
