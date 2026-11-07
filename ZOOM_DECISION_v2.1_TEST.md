# v2.1 — 200 MP başlangıç 4.3x ve fiziksel lens seçimi: gerçek kaynak tanısı

**Durum: test sürümü, gerçek zoom çözümü değil.** Xiaomi 15 Ultra, port Kamera 6.8.002090.2, PixelModule mode 175, modern LSPosed API 102.

## Neyi kesin biliyoruz?

- 1x/3x/4.3x düğmeleri çalışıyor ve ZoomManager seçimi alıyor.
- Fiziksel kamera seçimi `B2.c.c(I,I,Z)I` (port 6.8) mode 175 için bütün zoom değerlerinde **cameraId 5** döndürüyor.
- PixelModule kamera değiştirme izni zaten `true`.
- `i9.g.nh(float)`, üç zoom değerini de donanımda `1.1219512` oranına dönüştürüyor.
- 200 MP başlangıçta 4.3x seçiyor; bunu değiştirmenin etkin ve güvenli yolu henüz doğrulanmadı.

## v2.1 yeni metotlar (DEX doğrulandı)

- `com.android.camera.data.data.i.n(int,int): float`: `mode=175` için özel dal mevcut. **İlk argüman=175** olduğunda verilen lens parametresini ve gerçek dönüş değerini kaydet.
- `com.android.camera.data.data.i.O(int): float`: fiziksel seçici `B2.c.c` 175 dalında bu zoom/lens yapılandırma değerini okuyor. **Argüman=175** için sonucu kaydet.
- `i9.g.B7(): void`: ZoomManager'in başlangıç uygulama yolunda `l` zoom alanını önce/sonra kaydet.

Bu kancalar **yalnızca okur**, özgün hesaplamayı değiştirmez. Önceki v2.0 `B2.c.c` seçici logları korunur. v1.8'de yanlışlıkla kullanılan `167` Pro moduna geçilmez.

## Tek cihaz testi

v2.1 birleşik LSPosed APK'yı v2.0 üzerine aynı imzayla kur:

```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.hybridroute 0
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell setprop debug.cameraai.pixelzoom 0
& $adb shell setprop debug.cameraai.pixelrouteprobe 1
& $adb shell setprop debug.cameraai.zoomdecisionprobe 1
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```

Kamerayı aç, 200 MP modunu seç ve sırayla `4.3x → 1x → 3x → 4.3x` tuşlarına bas. Ardından:

```powershell
$dosya = "$env:USERPROFILE\Desktop\Pixel_200MP_21.txt"
& $adb logcat -d -v time |
  Select-String -Pattern "PIXEL175 zoom preference|PIXEL175 camera selector i.O|PIXEL175 ZoomManager.B7|PIXEL175 physical selector|PIXEL175 requested physical-lens zoom|nativeCameraId|actualCameraId|zoom decision traces unavailable|AndroidRuntime" |
  Out-File $dosya -Encoding UTF8 -Width 3000
Write-Host "Kaydedildi: $dosya"
```

Paylaşılacak dosya: `Pixel_200MP_21.txt`. `i.n` ve `i.O` kayıtları çağrılmazsa bu da ilgili yolun cihazda tetiklenmediğini gösterir; **başka bir başlangıç dalı** aranmalıdır. İki dönüş değerini görmeden default 1x yaması yapılmaz.

## Geri dönüş

```powershell
& $adb shell setprop debug.cameraai.zoomdecisionprobe 0
& $adb shell setprop debug.cameraai.pixelrouteprobe 0
& $adb shell am force-stop com.android.camera
```

**Çözüm için kabul kriteri**: 1x/3x kamera ID'leri ve gerçek 50 MP çıktı akışları birlikte doğru çalışacak, 4.3x gerçek 200 MP 12288×16320 çıktı korunacak, mümkünse başlangıç 1x olacak. Testten önce çalışan diye yayımlama.
