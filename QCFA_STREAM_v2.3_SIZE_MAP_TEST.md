# v2.3 — OEM fotoğraf/önizleme çözünürlüğünü doğrudan Map üzerinden izleme

**Durum: tanılama, optik 1x/3x düzeltmesi değil.** Referans cihaz Xiaomi 15 Ultra (xuanyuan), port Xiaomi 18 Pro Kamera 6.8.002090.2, XCamera 1.2 + CameraAI modern LSPosed API102.

## v2.2'de gerçek cihazın verdiği kanıt

Kullanıcının `QCFA_Stream_22.txt` dosyasından:

- 200 MP PixelModule mode175 fiziksel `cameraId 5` üzerinde açılıyor; zoom tuşundan sonra da aynı kimlik.
- `m6.o.f(175,0,...ratio=1.3333334,...)` iki çağrıda `1440×1080` döndürüyor. **Bu değer nihai 200 MP fotoğraf boyutunu göstermez.** Bir OEM ara/önizleme boyut seçimidir; tek başına 200 MP still output'u açıklamaz.
- `updatePictureAndPreviewSize()` ve `updateConfigQcfa()` gerçek cihazda çalışıyor; ancak v2.2'nin `mLoadStreamSizeBase` alanlarını nesnenin runtime sınıfında `getDeclaredField` ile araması `y=? z=? v=? ctx=?` yazdırıyor. Bu reflection hatası, uygulama kodunun çalışmadığı anlamına gelmez.
- Cihaz logunda 4.3x fotoğraf çekiminden sonra `updateConfigQcfa` çalışması gözlendi.

## v2.3 teknik düzeltmesi

Kamera 6.8 `classes.dex` içinden şu gerçek metot imzası doğrulandı:
- `com.android.camera.module.Camera2Module.updateSizeResult(java.util.Map):void` (private).
- `m6.o.f(int,int,List,float,Size,boolean):Size` (public static).
- `Camera2Module.mLoadStreamSizeBase:m6.o`.
- `m6.o` içindeki birçok alan `android.util.Size` tipinde; `E:m6.o$a` context alanı.

**Yeni hook** `updateSizeResult(Map)` çağrısındaki özgün `Map` girdisini **değiştirmeden** yazdırır. Map'in `android.util.Size` ve sayısal değerleri loglanır; `m6.o$b` enum anahtarları da görüntülenir. `mLoadStreamSizeBase` alanları artık runtime alt sınıf yerine doğrudan DEX tarafından doğrulanmış `m6.o` tipinde incelenir. OEM metotları tek kez çalıştırılır; asla return/kamera ID/akış yazımı değiştirilmez.

Opt-in: `debug.cameraai.streamprobe=1` (normalde KAPALI).

## Kurulum ve tek test

Aynı imzalı birleşik v2.3 APK'yı v2.2 üzerine kur.

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

200 MP modunu aç. 4.3x'te fotoğraf çek, 1x ve 3x'e dokun, 4.3x'e dönüp tekrar fotoğraf çek. Sonra:

```powershell
$dosya = "$env:USERPROFILE\Desktop\QCFA_Stream_23.txt"
& $adb logcat -d -v time |
    Select-String -Pattern "PIXEL175 OEM updateSizeResult map|PIXEL175 output-size|PIXEL175 updateConfigQcfa|PIXEL175 m6.o.f|actualCameraId|AndroidRuntime" |
    Out-File $dosya -Encoding UTF8 -Width 3000
Write-Host "Kaydedildi: $dosya"
```

### Kapatma

```powershell
& $adb shell setprop debug.cameraai.streamprobe 0
& $adb shell am force-stop com.android.camera
```

## Hedef

Gerçek yüksek çözünürlük Camera2 still-output boyutu ile önizleme boyutunu doğru ayırmak. HAL fiziksel sensör/Quadra CFA haritası şimdiden doğrulandı: 1x ID2 / 8192×6144, 3x ID4 / 8192×6144, 4.3x ID5 / 16320×12288. **50/200 MP karma session yeniden oluşturma ve gerçek lens değiştirme henüz yapılmadı.**

Derleme: GitHub Actions run `42419315615` success. Yerel APK v2 imza doğrulandı, özgün XCamera classes.dex korundu. APK SHA256: `8fa09887f3fef9e9f6f92eb01774f4d7e40b35b125c10ac20565b95b8f43f95d`.
