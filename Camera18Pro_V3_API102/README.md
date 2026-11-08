# Camera18Pro v3 Alpha 4 — API 102 / Deneysel İşleniyor arayüzü

**Deneysel dal:** `camera18pro-v3-alpha4-processing-ui`

Bu sürüm, kullanıcının cihazından alınan **orijinal Xiaomi 15 Ultra Kamera 6.7.000180.0** DEX kodundaki `PixelModule.onEarlyImageAvailable`, `onFinalImageAvailable` ve `isBlockSnap` metotlarının **Xiaomi 18 Pro 6.8.002090.2** ile karşılaştırılmasına dayanıyor.

## Kodda doğrulanan gerçek farklar

- Orijinal `onEarlyImageAvailable`: 180 DEX birimi, `stopFaceDetection`, `pausePreview`; port: 34 birim, aynı çağrılar yok.
- Orijinal `onFinalImageAvailable`: 81 birim, `Handler.obtainMessage` / `Message.sendToTarget`; port: 24 birim, bu UI mesaj yolu yok.
- Her iki APK'da `isBlockSnap` içinde `ImagePoolAdapter.getAllAcquiredImageCount()` ve `Camera2Module.getReprocessDataSize()` çağrıları var. Portta tampon koruması tamamen kayıp değil.
- `restoreUiState()` her iki sürümde `resumePreview()` çağırıyor.

Orijinal `pausePreview` metodunu portta körlemesine çalıştırmak kamera akışını bozabilir; bu test sürümü onu **çağırmaz**.

## Alpha 4'te yeni özellik

Sadece `debug.cameraai.processingtip=1` olduğunda:

- `PixelModule.onCaptureStart` ile **“200 MP işleniyor…”** etiketi açılır.
- `ImageReceiverExecutor$2.onSuccess` veya `onError` gelince, bekleyen başka çekim yoksa etiket kapanır.
- `com.android.camera.Camera.onWindowFocusChanged` üzerinden Activity ekranına dokunma engellemeyen küçük bir yazı eklenir.

**Varsayılan KAPALI.** Yalnızca kamera uygulaması kullanıcı arayüzünü görüntüler. Kamera ID, QCFA, remosaic, SuperNightSE, RAW-AISP, ses, deklanşör, JPEG veya önizleme kontrolü değiştirilmez.

Bu, OEM'in tam orijinal **İşleniyor** arayüzünün kopyası değildir; **MiVi işlemi tamamlanma süresini gösteren geri alınabilir deneysel gösterge**dir. `MIVI_SUCCESS`, diske yazılan `CAPTURE-END` ile aynı an değildir.

## Deneysel kullanım (telefon testi yapılmadı)

1. **CameraAIMerged + XCamera v2.3'ü kaldırma.** Kamera APK'sını değiştirme.
2. Alpha 3 sürümüne dönme olanağını koru. Alpha 4 farklı imzayla derlenebileceği için yalnız `dev.camera18pro.v3` uygulaması değiştirilebilir.
3. İlk test varsayılan **kapalı** olsun: `adb shell getprop debug.cameraai.processingtip`.
4. Etiketi etkinleştirmek için Windows PowerShell: `& 'C:\platform-tools\adb.exe' shell su -c 'setprop debug.cameraai.processingtip 1'`; ardından `am force-stop com.android.camera` ve yeniden aç.
5. Kapatmak için aynı komutla `0` yap ve kamerayı yeniden başlat.
6. Log konumu her zaman Masaüstü: `Join-Path ([Environment]::GetFolderPath('Desktop')) 'Camera18Pro_Alpha4_UI.txt'`.

**Güvenlik:** Modül yalnızca `com.android.camera` için, modern **libxposed API 102** ile çalışır. Android Studio gerekmez. Kök HAL/JPEG hatası henüz çözülmüş değildir; bu bir UI izolasyon deneyidir.

**Referans:** Samsung ISOCELL HP9 ve Xiaomi 15 Ultra resmi kamera bilgileri. Pixel binning, remosaic ve AISP yapısı kapalı HAL/sensör işlem hattının bir bölümü olabilir; bu algoritmaların APK'da tam Java kaynak kodu bulunduğu varsayılmıyor.


## 8 Kasım 2026 — Alpha 4 cihaz testi

Kullanıcının `Camera18Pro_Alpha4_Isleniyor_Test.txt` logunda, `debug.cameraai.processingtip=1` etkin ve LSPosed UI hook kurulmuşken **“200 MP işleniyor…” göstergesinin göründüğü** kullanıcı tarafından doğrulandı.

| Fotoğraf | CAPTURE_START | SHOT2SHOT_FINISH | MIVI_SUCCESS | CAPTURE-END | Veri uzunluğu |
| --- | --- | --- | --- | --- | --- |
| `IMG_20261108_182430.jpg` | 18:24:30.434 (id 1) | +326 ms | +1882 ms | +1934 ms | 45.653.482 bayt |
| `IMG_20261108_182439.jpg` | 18:24:39.451 (id 2) | +316 ms | +1782 ms | +1832 ms | 46.318.057 bayt |

İkisi de `moduleIndex=175`, `isCaiOn=false`, `16320x12288` çıktı; `onReprocessSuccess` ve `[CAPTURE-END]` var. `miHalBufferReceiveError`, `MIVI_ERROR`, `onCaptureFailed`, `smallPicture=true` veya `UI_TIMEOUT_20S` görülmedi. `isNightCapture=false`, `isSuperNightSE=false`.

**Önemli ayrım:** `MIVI_SUCCESS` ile JPEG'in `CAPTURE-END` olayı arasında yaklaşık 50 ms var; gösterge şu anda **MiVi tamamlandığında** kapatılıyor, diske kaydın bitmesini beklemiyor. Kamera ekranında gerçek gizlenme olayına ait `UI_HIDE` kaydı henüz eklenmediği için kaybolduğu yalnız bu logdan kanıtlanamaz. Bu sürüm, donanım/RAW işleme algoritmalarını değiştirmiyor ve önceki aralıklı HAL hatasının kalıcı çözümü değil.

Öncelikli sonraki güvenli geliştirme: UI göster/gizle olaylarını kendi etiketleriyle kaydetmek; JPEG diske yazımını bloke etmeden ve `pausePreview()`'u zorlamadan, `CAPTURE-END` eşdeğerinin stabil Java geri çağrısını keşfetmek.
