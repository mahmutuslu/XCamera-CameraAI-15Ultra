# XCamera + CameraAI v1.8 — Hibrit 50 MP / 200 MP Lens Yönlendirmesi (DENEY)

> **SONUÇ — CİHAZDA BAŞARISIZ (7 Kasım 2026, 23:16).** Hibrit yama, gerçek 1x/3x zoom veya 1x başlangıç sağlayamadı. `175 → 167` isteği `PreFixCamera2Setup` aşamasında `actualCameraId: 2` oluşturuyor ama `Camera2OpenOnSubScribe` hâlâ `actualCameraId: 5` açıyor. Bu nedenle **v1.8 gerçek lens yönlendirme çözümü değildir**. Deneysel `debug.cameraai.hybridroute=1` ayarını artık kullanmayın; `0` yapın. 

## Arka plan

Xiaomi 15 Ultra, HyperOS 4, Xiaomi 18 Pro Camera 6.8.002090.2, LSPosed API 102.

Cihazın logunda XCamera'nın 200 MP `mode=175` arayüzü 1x / 3x / 4.3x tıklamalarını algılarken fiziksel kamera **hep ID=5** ve donanıma uygulanan oran **hep 1.1219512**. Bu durum v1.5, v1.6 ve v1.7 cihaz testlerinde doğrulandı.

Stok Camera 6.7.000480.0 ile port Camera 6.8.002090.2'nin zoom dönüşümü ve mode-175 kamera seçme dalları karşılaştırıldı:

- Port: `i9.g.nh(float)` → `com.android.camera.data.data.F.j(float)` ve 200 MP kamera kimliği `B2.c.c(int,int,boolean)`.
- Stok: `k9.h.Nh(float)` → `com.android.camera.data.data.D.j(float)` ve 200 MP kamera kimliği `C2.c.c(int,int,boolean)`.
- Her iki sürüm de mode 175 kamera seçimini zoom listesinden bağımsız cihaz yetenekleri/role koşullarıyla yapıyor. 6.7'deki tek fonksiyonu 6.8'e kopyalamak üç optik lensi açmaya yetmez.
- Xiaomi 15 Ultra için gerçek 200 MP sensör 4.3x periskoptur; 1x ve 3x lensleri 50 MP'dir.

## v1.8 yaması

**Opt-in** `debug.cameraai.hybridroute=1` koşuluyla şu kod yollarına müdahale eder:

1. `i9.g.setZoomRatio(float)` içinde mode 175 veya 167 için kullanıcının seçtiği zoom oranını gözlemler.
2. Kamera uygulamasının kendisine ait `com.android.camera.module.loader.base.StartControl.create(int)` çağrısını yalnızca **optik lens geçişi** `Ph.b.e` tarafından yapıldığında değiştirir:
   - Mode 175 ve seçilen 1x / 3x → `StartControl.create(167)` (50 MP yüksek çözünürlük modu).
   - Mode 167 ve seçilen 4.3x → `StartControl.create(175)` (200 MP periskop).
3. XCamera `mode 167` zoom listesini de **1x / 3x / 4.3x** olarak sunar; mode 175 düğmeleri korunur.

Normal mod seçimi, lens seçme işlemi dışındaki açılışlar, kamera kimliği, zoom crop, HAL, sensör çıktısı ve JPEG/HEIC boyutu **zorlanmaz**. En fazla 18 hibrit yönlendirme için koruma sınırı vardır. Mod seçimleri kamera uygulamasının kendi yüksek çözünürlük yollarından yapılır.

**Varsayılan kapalıdır.** Telefon üstündeki sonuç henüz bilinmiyor: 167'ye geçildiğinde arayüzde 50 MP modu görünebilir, 4.3x'e geri dönüş desteklenmeyebilir veya mod geçişi başarısız olabilir. Bunu **nihai çözüm olarak yayımlamayın.**

## Windows PowerShell cihaz testi

v1.8 birleşik LSPosed APK'yı kurup modülün açık olduğundan emin olun. Ardından:

```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell setprop debug.cameraai.hybridroute 1
& $adb shell getprop debug.cameraai.hybridroute
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```

200 MP modunu açın, **4.3x → 1x → 3x → 4.3x** seçimlerini deneyin. Ekrandaki değişimi, mod etiketini ve gerçekten çekilen fotoğrafın çözünürlüğünü kontrol edin.

```powershell
& $adb logcat -d -v time |
  Select-String -Pattern "HYBRID:|StartControl|PreFixCamera2Setup|ActualOpenCameraId|applyZoomRatio-R|CameraAIMerged|Camera2Module" |
  Select-Object -Last 100
```

Özellikle `HYBRID: physical lens route via Camera's own high-res modes 175 -> 167` kaydı ve sonrasında yeni cameraId olup olmadığını kontrol edin.

### Geri dönüş — kamera açılmazsa veya davranış bozulursa

```powershell
& $adb shell setprop debug.cameraai.hybridroute 0
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell am force-stop com.android.camera
```

Bu özellik parametresi sıfırlandığında yama devre dışı kalır; bu koşullar LSPosed uygulama başlatmasında okunur. Gerekirse önceki çalışan birleşik v1.7 APK'ya geri dönün. **Sistem Kamera uygulamasını kaldırmayın.**

## Çalışma durumu

- **Statik karşılaştırma:** tamamlandı.
- **Modern API 102 derlemesi:** GitHub Actions başarılı.
- **APK bütünlüğü/imza:** doğrulandı, v1.7 ile aynı imza.
- **Gerçek lens geçişi ve fotoğraf çözünürlüğü:** *cihazda henüz doğrulanmadı*.

## Cihaz testi — 23:16

```text
HYBRID: zoom requested, mode=175, zoom=1.0
HYBRID: physical lens route via Camera's own high-res modes 175 -> 167 for 1.0x
PreFixCamera2Setup: mPendingOpenModule=167, actualCameraId=2
Camera2OpenOnSubScribe: request to open bogusCameraId=0, actualCameraId=5
HYBRID: zoom requested, mode=167, zoom=1.0
```

1x ve 3x gerçek zoom sağlamadı; 200 MP modu 4.3x ile açılmaya devam etti. Özellikle 3x seçiminde `175→167` geçişine ait başarı kaydı görülmedi. Bu durum, `StartControl.create(int)` parametresinin optik geçişin tamamı olmadığını kanıtlıyor. Yeni uygulamada yalnızca `StartControl` hedef modunu değiştirmek yerine gerçek kamera açma/önhazırlık ve fotoğraf boyutu rotası bütünleşik incelenmeli. Bu deney tekrar **varsayılan açık** yapılmamalıdır.
