# Xiaomi 15 Ultra — 200 MP Üç Zoom Testi

1. Önceki çalışan XCamera + CameraAI modüllerinin APK'larını saklayın.
2. LSPosed'da tek birleşik modülü etkinleştirin. Kapsam `com.android.camera` olmalıdır.
3. `adb shell getprop debug.cameraai.zoomprofile` komutunda boş çıktı varsayılan profil 2 anlamındadır.
4. Kamerayı açın, 200 MP modundaki zoom düğmeleri **1x / 3x / 4.3x** olmalıdır.
5. Modun ilk açılışında **1x** seçili olup olmadığını kontrol edin (ayrı başlangıç zoom kancası yok).
6. İyi ışıkta sabit bir hedefin 1x, 3x ve 4.3x fotoğraflarını çekip Galeri/Ayrıntılar ekranındaki çözünürlükleri karşılaştırın.
7. AI Capture Assist sahne önerilerini ve yanıtlarını kontrol edin.
8. Loglar: `adb logcat -d -v time | findstr /I "CameraAIMerged XCamera"`.

## Geri dönüş
```powershell
& "C:\platform-tools\adb.exe" shell setprop debug.cameraai.zoomprofile 0
& "C:\platform-tools\adb.exe" shell am force-stop com.android.camera
```
Bu yalnızca zoom listesini XCamera'nın önceki değerlerine döndürür. Kamera açılmazsa birleşik modülü devre dışı bırakıp önceki çalışan APK'yı geri yükleyin.
