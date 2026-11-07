# 200 MP gerçek zoom tanılaması — v1.6 TEST

## Sorun
XCamera + CameraAI v1.5, 200 MP (mode 175) için [1x,3x,4.3x] düğmelerini **yalnızca arayüze** yazıyor. Xiaomi 18 Pro Kamera portu bu modda gerçek zoom veya lens değişimini uygulamıyor olabilir. Kullanıcının cihaz testinde üçü de aynı kadrajı gösteriyor. Bu, **v1.5'te doğrulanmış bir kusurdur**.

## Gözlemlenen kod yolları
- `com.android.camera.features.mode.capture.CaptureModule.isZoomEnabled()`: gerçek zoom izni. Bu metot 200 MP'de `false` dönerse arayüz düğmeleri etkin görünse bile zoom engellenebilir.
- `com.xiaomi.camera.features.zoom.ui.view.toggle.ZoomRatioToggleView.setZoomRatio(float)`: görünümün gördüğü zoom oranı.
- `ZoomRatioToggleView.setZoomArray(float[])`: görünümün gördüğü oran listesi.
- `Camera2Module.updateSATZooming(int)`: uyumlu modlarda SAT zoom yenileme yolu.
- Kamera 6.8 DEX içinde ayrıca `isDeviceCaptureSuppressed: 200M + noOpticalZoom` koşulu bulunuyor. Bu kısıtın kullanıcının tıklamalarında uygulanıp uygulanmadığı **henüz bilinmiyor**.

## v1.6 davranışı
- `debug.cameraai.zoomprofile=2` veya boş: 1x / 3x / 4.3x düğmeleri.
- `debug.cameraai.realzoom=0` veya boş: yalnızca tanılama; mevcut zoom yetkisi değiştirilmez.
- `debug.cameraai.realzoom=1`: **deneysel**; yalnızca `CaptureModule.isZoomEnabled()` 200 MP modunda `false` dönerse `true` olarak geçilir. Sensör, çözünürlük, HAL veya zoom oranlarını zorla değiştirmez. Gerçek zoom sağlayacağı garanti değil.

## Deneme (PowerShell)
```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell setprop debug.cameraai.realzoom 1
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```
Kamerada 200 MP'ye geçip 1x, 3x, 4.3x düğmelerine sırasıyla basın. Ardından:
```powershell
& $adb logcat -d -v time |
    Select-String -Pattern "CameraAIMerged|XCamera|UltraPixel|noOpticalZoom|updateSATZooming" |
    Select-Object -Last 100
```

## Geri dönüş
```powershell
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell am force-stop com.android.camera
```

## Doğrulama sınırları
Cihaz testi yapılmadığı için bu, **nihai düzeltme değil**; kaynak koddan hazırlanmış tanılama ve kontrollü denemedir. Gerçek 200 MP sensörü fiziksel olarak 4.3x periskop lenstir. 1x/3x düğmelerinin farklı optik lenslere geçmesi, her ikisinin de gerçek sensör kaynaklı 200 MP fotoğraf üreteceği anlamına gelmez. İlerideki düzeltme bu donanım gerçeğine göre yapılmalı.
