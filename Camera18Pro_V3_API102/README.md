# Camera18Pro v3 alpha 1 (Modern Xposed API 102)

Xiaomi 15 Ultra (xuanyuan, HyperOS 4) üzerinde Xiaomi 18 Pro Kamera 6.8.002090.2 için bağımsız, salt-okunur Pixel175 / 200 MP tanı modülü.

**Bu APK mevcut CameraAIMerged + XCamera v2.3 birleşik modülünün yerine geçmez.** Onunla birlikte etkinleştirilmek için ayrı bir Android paketidir: dev.camera18pro.v3.

- Modern libxposed API **102**, XposedModule, hook().intercept().
- Kapsam: yalnızca com.android.camera.
- 200 MP olayları: onCaptureStart, onEarlyImageAvailable, onFinalImageAvailable, onPictureTakenFinished.
- MiVi sonuçları: onSuccess, onError, onHalFailed.
- Kamera kimliğine, QCFA akışına, zoom profiline veya çıkış görüntüsüne müdahale **yok**.
- DEX/kamera sürüm farkları yüzünden bazı hook'lar yüklenmeyebilir; logdaki HOOK ... installed=0 kontrol edilmeli.

## Kurulum

GitHub Actions > Camera18Pro v3 - Xposed API 102 Build > başarılı çalıştırma > Artifacts altından Camera18Pro-v3-alpha1-API102-debug ZIP'ini indir. ZIP içindeki app-debug.apk dosyasını Android'e kur; LSPosed içinde etkinleştir ve kapsam olarak yalnız com.android.camera seç. Eski v2.3 etkin kalmalı. Kamera uygulamasını yeniden başlat.

PowerShell:
```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb logcat -c
& $adb logcat -v threadtime -b main -b system -b crash > "$env:USERPROFILE\Downloads\Camera18Pro_V3_API102_Test.txt"
```
Fotoğraf çek, PowerShell'de Ctrl+C ile durdur. CAMERA18PRO_V3, onReprocessSuccess, CAPTURE-END, onCaptureFailed ve miHalBufferReceiveError karşılaştırılır.

**Durum:** Alpha 1 tanı projesi; tam port uyumluluk modülü ve 200 MP hatası için nihai çözüm değildir. Bu dal ana sürümü değiştirmez.
