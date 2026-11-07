# XCamera + CameraAI — Xiaomi 15 Ultra

Xiaomi 15 Ultra (`xuanyuan`, HyperOS 4) için **bağımsız, deneysel LSPosed modül projesi**. FCMFix ile ilişkili değildir.

## Amaç
- Xiaomi 18 Pro Kamera `6.8.002090.2` (`com.android.camera`) ile XCamera 1.2 uyumluluk kancalarını korumak.
- CameraAI: `phone_model=madrid`, AI sahne `motion=0` ve ilgili AI zaman aşımını 15 saniyeye yükseltmek.
- 200 MP (mod kimliği `175`) için **1x / 3x / 4.3x** zoom düğmelerini göstermek.
- **Hedef:** 1x başlangıç; **cihaz testiyle doğrulanan mevcut davranış:** 200 MP moduna girişte uygulama 4.3x seçiyor. V1.7 başlangıç değerini zorla değiştirmez.
- LSPosed kapsamı: yalnızca `com.android.camera`.

## Doğrulama durumu
- XCamera 1.2: `29/29 15 Ultra values set` log ile doğrulandı.
- Birleşik API 102 modülündeki CameraAI kancalarının kurulumu log ile doğrulandı.
- Önceki **tek düğmeli** [4.3] deneyinde 12.288 × 16.320 (200,54 MP) fotoğraf doğrulandı.
- **Üç düğmeli [1, 3, 4.3] profil cihazda test edildi ve gerçek zoom'da BAŞARISIZ.** Düğmeler algılanıp oranlar 1/3/4.3'e değişse de kameraya her seferinde 1.121951 zoom oranı gönderiliyor; gerçek kamera kimliği 5 olarak kalıyor. 200 MP modu açıldığında uygulama 4.3x ile başlıyor.
- **v1.7**, zoom dönüştürücü `i9.g.nh(float)` yöntemini salt-okunur izler; gerçek zoom düzeltmesi değil, teşhis sürümüdür.
- **v1.8 hibrit lens yönlendirmesi — CİHAZ TESTİNDE BAŞARISIZ.** `175→167` önerisi hem geçişi tamamlayamadı hem de **167'nin Pro modu** olduğu sonradan doğrulandı. v1.9'da bu yanlış deney kaldırıldı. [Hata raporu](HYBRID_LENS_v1.8_TEST.md).
- **v1.9 PixelModule lens izni testi — HENÜZ DOĞRULANMADI.** Gerçek 200 MP `PixelModule` (175) için zoom ve lens değiştirme izinlerini **isteğe bağlı** olarak açmayı dener. Kameranın sensör kimliklerini zorlamaz, 1x başlangıcı değiştirmez. [Deneme ve geri dönüş](PIXEL_ZOOM_v1.9_TEST.md).

## Kaynak kod ve üçüncü taraf dosyalar
`app/` bizim geliştirdiğimiz modern LSPosed API 102 köprüsüdür. XCamera 1.2 APK'sı ve Xiaomi Kamera APK'sı depoda **yer almaz**. `tools/merge_and_sign.py`, kullanıcı tarafından sağlanan XCamera 1.2 APK'sına kendi derlediğimiz köprünün DEX dosyasını ekler. XCamera'nın `classes.dex` kodunu değiştirmez.

## Derleme
GitHub Actions → **Build CameraAI LSPosed Bridge** çalıştırın; derleme sonrası artifact olarak `app-debug.apk` alın. Bu APK **tek başına kurulacak birleşik modül değildir**.

Lokal derleme:

```bash
gradle :app:assembleDebug
python -m pip install -r requirements.txt
python tools/merge_and_sign.py --xcamera XCamera-1.2.apk --bridge app/build/outputs/apk/debug/app-debug.apk --output XCamera-CameraAI-v1.9-TEST.apk --version-name 1.9
```

Birleştirici ilk çalışmada `keys/` altında **yerel özel imza anahtarı** üretir. Güncellemelerde aynı anahtarı koruyun; depoya veya başkasına göndermeyin. Farklı imzalı XCamera'nın üzerine APK yüklenemeyeceğinden önceki modül/kurulum yedeğini alın. **Sistem Kamera uygulamasını kaldırmayın**.

## 200 MP zoom profilleri
`debug.cameraai.zoomprofile` sistem özelliği, Kamera işlemi açılırken okunur:

| Profil | 200 MP seçenekleri | Durum |
|---|---|---|
| `0` | XCamera orijinal: 0.6x / 1x / 3x / 4.3x | Geri dönüş |
| `1` | 4.3x | Daha önce doğrulanmış tek düğmeli test |
| `2` | 1x / 3x / 4.3x | **Varsayılan üç düğmeli TEST** |

Örnek geri dönüş:
```powershell
& "C:\platform-tools\adb.exe" shell setprop debug.cameraai.zoomprofile 0
& "C:\platform-tools\adb.exe" shell am force-stop com.android.camera
```

Önceki `debug.cameraai.zoomfix` ayarı bu sürümde kullanılmaz.

## Sınırlar
[Xiaomi'nin resmî teknik özelliklerine](https://www.mi.com/tr/product/xiaomi-15-ultra/specs/) göre **200 MP modu periskop kameraya özgüdür**; 1x ana kamera ve 3x telefoto 50 MP sensörler kullanır. 200 MP sensör 15 Ultra'da yaklaşık 4.3x doğal optik görüş açısına sahiptir. 1x veya 3x'in görünmesi, o kadrajda sensörden **gerçek 200 MP ayrıntı** alınacağı anlamına gelmez. Bu kod Kamera HAL'ini ya da fiziksel sensörü değiştirmez. Cihazda test etmeden nihai sürüm olarak yayımlamayın.

Ayrıntılı test akışı: [TEST_PLANI.md](TEST_PLANI.md).
