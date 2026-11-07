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

## Xiaomi 15 Ultra cihaz testi — 7 Kasım 2026, saat 22:08

Gerçek cihaz logları v1.6'da şunları doğruladı:
- `mode175 presets=[1.0, 3.0, 4.3]` gerçekten uygulanıyor.
- `debug.cameraai.realzoom=1` ile `CaptureModule.isZoomEnabled` hook'u başarıyla kuruluyor.
- Ancak `isZoomEnabled original=...` veya `zoom toggle setZoomRatio=...` kayıtları **hiç oluşmadı**.
- Üç basışta da `actualCameraId: 5` olarak kalıyor; aynı mod `175` tekrar açılıyor.
- Bu nedenle gerçek zoom düzeltmesi **doğrulanmadı**; v1.6'nın çözüm olduğunu söylemeyin.

### 6.8.002090.2 DEX incelemesi ile güncel ayrım

- Gerçek kullanıcı tıklama işleyicisi `com.android.camera.ui.zoom.ZoomRatioToggleView.onClick(android.view.View)`, **classes3.dex** içinde bulunuyor; `clickChildAtIndex:` ve `onClick: optical zooming` günlük dizgilerini içeriyor.
- v1.6'nın izlediği `com.xiaomi.camera.features.zoom.ui.view.toggle.ZoomRatioToggleView.setZoomRatio`, **classes8.dex** içinde farklı bir sınıf; bunu dinlemek gerçek düğme tıklamasını yakalamak için yeterli değil.
- **Bir sonraki adım:** mevcut APK'nın logcat'inde `ZoomRatioToggleView|clickChildAtIndex|zoomRatio|ZoomManager` satırlarını inceleyin ve gerçek tıklama yönlendirmesini belirleyin. Etkisiz `realzoom=1` deneyini `0` yapın.

Donanım sınırı: Xiaomi'nin resmi teknik özelliklerine göre 200 MP mod, **100 mm (4.3x) periskop** sensör içindir; 1x ana ve 3x telefoto kameralar **50 MP** sensör kullanır. 1x/3x gerçek kadrajları sağlamak fiziksel lens değiştirmeyi gerektirir; bu iki kademenin tümüyle gerçek 200 MP sensör çıktısı sağlayacağı varsayılmamalıdır.
