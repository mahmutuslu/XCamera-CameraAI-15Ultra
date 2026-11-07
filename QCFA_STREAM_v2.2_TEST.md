# v2.2: 200 MP modunda gerçek OEM akış boyutunu bulma

**Özet:** HAL kamera kimlikleri ve 50/200 MP QCFA output destekleri artık doğrulandı, ama PixelModule 175'in doğru 50MP stream'lerle başka sensöre açılabildiği henüz gösterilmedi. v2.2 yalnızca mevcut **çalışan 200MP hattının** stream kararlarını gözlemler.

## Cihazdan HAL ile doğruladığımız eşleme

- Fiziksel 2: 1x ana, QCFA 8192×6144.
- Fiziksel 4: 3x tele, QCFA 8192×6144.
- Fiziksel 5: 4.3x periskop, QCFA 16320×12288.
- Mantıksal 6: fiziksel [3,2,4,5]; Photo mode 163 altında kullanılıyor.

**Yanlış varsayım yapma:** Foto modunda logical 6'nın fotoğraf zoom isteğini uygulaması, PixelModule 175 için HAL'nin 200MP output stream'ini 2/4 üzerinde kabul edeceği anlamına gelmez.

## 6.8 gerçek DEX metot zinciri

`com.android.camera.module.Camera2Module.updatePictureAndPreviewSize():void` (private, **712 disassembly instruction**), görüntü/önizleme çözünürlüklerini ve streaming tercihlerini belirliyor. İçinde:
- `mCameraManager.getActualCameraId():int`
- `com.android.camera.data.data.i.O(int):float`
- `m6.o.f(int,int,List,float,Size,boolean):Size`
- `m6.o.e(l9.g,int,List,double,boolean):Size`
- `updateSizeResult(Map)`

`Camera2Module.updateConfigQcfa():void` (private), camera/feature destek koşulları ve JPEG/RAW/preview stream boyutu üzerine QCFA tercihini ayarlıyor. `setupCameraConfigForSessionIfNeed(k6.l):void` içinde `i.O(mode)` doğrudan `l9.j0.G3` alanına aktarılıyor.

6.8 `m6.o` (stream size helper) yöntemleri:
- `m6.o.f(...)` OEM'in gerçek seçtiği `Size` döndürür.
- `m6.o` context alanı `E: m6.o$a`; burada `d` mod kimliği, `u` seçilen kamera kimliği, `l` diğer size parametresidir. (Alanların anlamları on-device logla doğrulanacak.)
- `mLoadStreamSizeBase` alanında `y,z,v` gibi `Size` değerleri saklanır.

## v2.2 modül kodu

Önceki CameraAI/200MP kancaları korunur. `debug.cameraai.streamprobe=1` opt-in ile:
- `updatePictureAndPreviewSize()` **öncesi/sonrası** OEM'in hesapladığı `m6.o` stream size değerlerini loglar.
- `updateConfigQcfa()` **öncesi/sonrası** aynı stream alanlarını loglar.
- `m6.o.f(...)` sonuçlarını `updatePictureAndPreviewSize` çağrı zinciri içinde loglar.

**Tüm hook'lar özgün fonksiyonları bir kez çalıştırır ve dönüş değerini aynen iade eder.** Kamera ID/size, zoom, çözünürlük, HAL/vendor özellik değişimi YOK. Yeni çalışan hibrit zoom sürümü DEĞİL.

## Tek test

v2.2 birleşik modülü **aynı imza ile** v2.1 üzerine kur. Ardından:

```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.hybridroute 0
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell setprop debug.cameraai.pixelzoom 0
& $adb shell setprop debug.cameraai.pixelrouteprobe 0
& $adb shell setprop debug.cameraai.zoomdecisionprobe 0
& $adb shell setprop debug.cameraai.streamprobe 1
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```

Kamerayı açıp 200 MP moduna gir. 4.3x'te **bir fotoğraf çek**, sonra (hala fotoğraf çekmeden) 1x ve 3x düğmesine bas, 4.3x'e dönüp bir fotoğraf çek. (İlk aşamada 1x/3x 200MP fotoğraf çekimi zorlanmaz.)

```powershell
$dosya = "$env:USERPROFILE\Desktop\QCFA_Stream_22.txt"
& $adb logcat -d -v time |
  Select-String -Pattern "PIXEL175 output-size|PIXEL175 updateConfigQcfa|PIXEL175 m6.o.f|actualCameraId|applyZoomRatio-R|AndroidRuntime" |
  Out-File $dosya -Encoding UTF8 -Width 3000
Write-Host "Kaydedildi: $dosya"
```

Deney bitince:
```powershell
& $adb shell setprop debug.cameraai.streamprobe 0
& $adb shell am force-stop com.android.camera
```

## Sonraki kod değişikliğinin kabul koşulları

- 1x ve 3x fiziksel ID2/ID4 üzerinden doğru **50MP** QCFA output stream açıyor.
- 4.3x ID5 üzerinden önceki gibi **200MP** fotoğraf çekiyor.
- Her lens için preview + still capture session ayrı ve boyutları tutarlı.
- İlk açılış 1x hem UI hem gerçek sensör olarak doğrulanıyor.
- XCamera ve CameraAI hook'ları bozulmuyor.
