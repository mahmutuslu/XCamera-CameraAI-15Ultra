# Xiaomi 15 Ultra — 200 MP fiziksel kamera seçimi (v2.0 inceleme)

## İncelenen özgün dosyalar

Kullanıcının sağladığı iki APK, yerel statik DEX karşılaştırmasına tabi tutuldu:
- Xiaomi 15 Ultra Kamera 6.7.000480.0: `camera_6.7_base.apk` (11 DEX).
- Xiaomi 18 Pro Kamera portu 6.8.002090.2: `camera_6.8_base.apk` (10 DEX).

Bu dosyalar lisans ve büyüklük nedeniyle GitHub deposuna dahil edilmez. Aşağıdaki metotların API adları sürümler arası birebir aynı değildir.

## Teknik kanıt

| İşlev | 15 Ultra Kamera 6.7 | Port Kamera 6.8 |
|---|---|---|
| 200 MP mod kimliği | `PixelModule` 175 | `PixelModule` 175 |
| Fiziksel kamera seçici | `C2.c.c(int,int,boolean)` | `B2.c.c(int,int,boolean)` |
| 175 karar dalı | `classes.dex`, `const/16 0xAF` yakın 0x0130, koşullu dallanma 0x0B56 | `classes.dex`, `const/16 0xAF` yakın 0x0119, koşullu dallanma 0x0A3D |
| Kameranın zoom dönüşümü | `k9.h.Nh(float)` | `i9.g.nh(float)` |
| Lens durumu/yetenekleri | modelin özellik/yetenek denetimlerinden geçer | `Pe.b.x1()`, `l9.r0.e/f/g/h()` gibi koşullardan geçer |

**Bulgu:** 200 MP için iki sürümde de **özel fiziksel kamera karar dalı** var. Bu nedenle 6.7'den tek bir metot kopyalamak veya UI'nın `[1,3,4.3]` listesini değiştirmek, 1x/3x kameraların gerçek açılmasını sağlamaz.

## Cihaz deneylerinin desteklediği sonuç

- v1.5–v1.7: UI'da `1x/3x/4.3x` seçilse de `i9.g.nh` hepsini `1.1219512` oranına çeviriyor.
- v1.8: yanlış `175→167` denemesi `167`nin **Pro modu** olması nedeniyle kaldırıldı; gerçek kamera açılışı ID 5 kaldı.
- v1.9: `isCameraSwitchingDuringZoomingAllowed=true` zaten geçerliydi; bu izin sorun değil, fiziksel kamera yine ID 5.
- v2.0: doğru `B2.c.c` kararını ve lens-yetenek durumunu **yalnızca kaydeden** kanca. Gerçek kamera kimliğini veya çıktı çözünürlüğünü değiştirmez; kullanıcı cihazında henüz test edilmedi.

## v2.0 okuma amaçlı tanılama

```powershell
$adb = "C:\platform-tools\adb.exe"
& $adb shell setprop debug.cameraai.hybridroute 0
& $adb shell setprop debug.cameraai.realzoom 0
& $adb shell setprop debug.cameraai.pixelzoom 0
& $adb shell setprop debug.cameraai.zoomprofile 2
& $adb shell setprop debug.cameraai.pixelrouteprobe 1
& $adb shell am force-stop com.android.camera
& $adb logcat -c
```

200 MP modunu açıp `4.3x → 1x → 3x → 4.3x` düğmelerini deneyin. Daha sonra:

```powershell
& $adb logcat -d -v time |
    Select-String -Pattern "PIXEL175 physical selector|PIXEL175 requested physical-lens zoom|actualCameraId|applyZoomRatio-R|CameraAIMerged" |
    Select-Object -Last 100
```

Aranan satır:
`PIXEL175 physical selector: inputId=..., mode=175, flag=..., zoom=..., nativeCameraId=..., roleFlags=e=... f=... g=... h=... x1=...`

Bu, 1x ve 3x'te hangi yetenek durumunun 200 MP periskop sensörü seçtirdiğini açığa çıkarmaya yöneliktir. **Kamera istekleri hiç değiştirilmiyor**, kanca yalnızca log üretiyor.

Geri dönüş:
```powershell
& $adb shell setprop debug.cameraai.pixelrouteprobe 0
& $adb shell am force-stop com.android.camera
```

## Çözüm kriteri

Gerçek bir çözüm, 1x/3x için uygun 50 MP lensin fiziksel açılmasını, **o lense uygun HAL stream ve çıktı çözünürlüğünü**, 4.3x periskop için 200 MP `12288×16320` fotoğrafın korunmasını gerektirir. Bunlar aynı anda cihaz testleriyle doğrulanmadan "zoom düzeltildi" denilmeyecektir. 1x ilk açılış hedefi de henüz sağlanmadı.

**Durum: çözüm doğrulanmadı; v2.0 yalnızca fiziksel seçiciyi hedefleyen güvenli tanılama.**
