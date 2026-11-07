# DeepSeek önerilerinin gerçek Xiaomi Kamera 6.7 / 6.8 DEX koduyla karşılaştırılması

Tarih: 8 Kasım 2026. Kaynaklar: kullanıcıdan alınan stok Kamera 6.7.000480.0 APK ve Xiaomi 18 Pro Kamera 6.8.002090.2 APK. APK'lar depoya yüklenmemiştir.

## DeepSeek'in önerdiği 3 değişiklik

| Öneri | Statik DEX kanıtı | Karar |
|---|---|---|
| `pixel:` zoom dizisini `1:3:4.3` yapmak | 6.7/6.8 DEX içinde düz `pixel:` metni bulunmadı. Üstelik `mode 175` için `[1,3,4.3]` dizisi v1.5'ten beri runtime'da zaten var. | Sorunu çözmez, UI zaten çalışıyor |
| `PixelModuleEntry.support()` sonucunu değiştirmek | İki APK'daki `support()` yalnızca genel yüksek çözünürlük modu desteğinin açık olup olmadığını belirleyen cihaz-feature koşullarını test ediyor. Lensler arası geçiş yapmıyor | Ana hata noktasını çözmez |
| 1x/3x yüksek çözünürlük çekimini doğru 50 MP sensöre/stream'e yönlendirmek | Evet: gerçek `mode 175` kamera seçicisi `B2.c.c(I,I,Z)I` (6.8), stok eşleniği `C2.c.c(I,I,Z)I` (6.7). PixelModule ve Camera2 output akışının da eşleşmesi gerekiyor | Araştırılması gereken gerçek çözüm |

## Yeni doğrulanmış kod yolları

### Başlangıç zoom oranı

6.8'de `com.android.camera.data.data.i.n(int)` (classes.dex), `mode 175 (0xAF)` için açık dal içeriyor. Dal `com.android.camera.data.data.F.e0()` ve `F.k(...)` gibi koşullardan geçiyor, bazı durumlarda `v2.o0.n()` üzerinden değer döndürüyor. `i9.g.B7()` ise bu başlangıç değerini `i9.g.setZoomRatio(float)` ile ZoomManager'a uygulayan akışlardan biri.

Bu, DeepSeek'in önerdiği doğrulanmamış `getPixelDefaultZoom` isimleri yerine **somut inceleme noktasıdır**. Ancak hangi alt dalın 4.3x ürettiği henüz runtime loguyla izole edilmedi; 1x varsayılanı çözülmüş değildir.

### Fiziksel kamera seçimi

6.8 `B2.c.c(int,int,boolean)` (classes.dex, `mode 175` dalı yakın `0x0A3D`) aşağıdaki kaynakları okur:

- `Pe.b.x1()`
- `l9.r0.e/f/g/h()` (v2.0 cihaz logunda hepsi `true`)
- `com.android.camera.data.data.i.O(int)` (kameranın seçili zoom/lens verisini okuyup hesaplayan yöntem)
- `Pe.b.p1()` (cihaz özelliği)
- `v2.H0.F()`, `Kr.i.h/i()` gibi ek dallar

Önemli: v2.0 sadece `e/f/g/h/x1` bayraklarını loglamıştı. **Bunların hep `true` olması tek başına `cameraId=5` sonucuna hangisinin neden olduğunu kanıtlamaz.** Fiziksel kamera seçicisinin diğer dalları ve seçilen zoom veri kaynağı birlikte değerlendirilmelidir.

### Zoom → donanım dönüşümü

6.8 `i9.g.nh(float)` içinde `com.android.camera.data.data.F.j(float)` yolu var. 6.7'deki eşlenik `k9.h.Nh(float)` → `com.android.camera.data.data.D.j(float)`. Her iki veri yolu da `v2.o0 / w2.j0` gibi per-mode zoom aralığı nesnelerini kullanır. 1x/3x/4.3x için kullanıcının loglarında 6.8 her seferinde `1.1219512` üretiyor. Sadece bu dönüşümün sonucunu değiştirmek optik 1x'i açmaz.

## Cihazda doğrulanmış kısıtlar

- Kullanıcı logu, mode175 ZoomManager'a `1.0/3.0/4.3` oranlarının doğru aktarıldığını kanıtlıyor.
- `B2.c.c` her seçimde **ID 5** döndürüyor; gerçek HAL açma isteği de ID 5.
- v1.8 `175→167` hatalıydı: `167` Pro modudur, 50 MP modu değildir. Yama v1.9'da kaldırıldı.
- v1.9 `isCameraSwitchingDuringZoomingAllowed()` izni zaten `true`.
- v2.0 salt-okunur selector logu 200 MP capture'ı değiştirmiyor.

## Mühendislik sonucu

Mevcut durumda `mode 175` içinde tüm lenslere 50/200 MP uygun capture stream üretilebildiği ispatlanmadı. Bu yüzden yalnızca `B2.c.c` dönüş ID'sini zorlamak, desteklenmeyen Camera2 stream boyutlarıyla siyah ekran, yanlış kamera veya yakalama hatası oluşturabilir. **Yeni çalışan lens çözümü olarak yayınlanmayacak.**

İzlenecek güvenli sıra: gerçek başlangıç zoom'u üreten `i.n(175)` kararını koddan çözümle → 200 MP selector `i.O(175)` ve tüm alt koşullarını incele → 1x/3x fiziksel lenslerin özgün 50 MP output stream desteğini tespit et → ancak ondan sonra atomik mod/sensör/stream değiştirme yaması tasarla. 4.3x orijinal 200 MP görüntü yolu korunacak.

Durum: **teknik inceleme ilerledi; çalışan 1x/3x geçişi ve varsayılan 1x henüz sağlanmadı.**
