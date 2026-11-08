# Orijinal Xiaomi 15 Ultra 200 MP — video incelemesi

**Kaynak:** Kullanıcının 8 Kasım 2026'da sağladığı `8115.mp4`, 44,63 saniye, 1440×3200 ekran kaydı. Videonun görüntüleri/ham dosyası GitHub'a eklenmedi.

## Ekranda doğrudan gözlenenler

- Çekim sırasında üst-orta alanda `200MP görüntü işleniyor...` metni.
- Beyaz deklanşör dairesi işlem sırasında gri oluyor, bitince beyaza dönüyor. Bu görünür etkinlik göstergesidir; düğmenin tıklamayı yazılımsal olarak engellediği videoda ayrıca denenmiyor.
- Önizleme büyük ölçüde bulanıklaşıyor, kısmi dikdörtgen/kare bölgeler hâlinde yeniden netleşiyor.
- Çekim bitiminde yazı kaybolup görüntü normale dönüyor.

| Olay | İlk çekim | İkinci çekim |
| --- | --- | --- |
| Gri deklanşör | ~14,5 sn | ~34 sn |
| `200MP görüntü işleniyor...` | ~15 sn | ~34,5 sn |
| Bölge bölge netleşme | ~17–23 sn | ~36–42 sn |
| Yazı kaybolur, beyaz deklanşör | ~23,5–24 sn | ~42,5–43 sn |

Video içindeki iki UI döngüsü yaklaşık 8–9 saniye sürüyor. Ekran kaydı veya sahnenin performans etkileri bilinmediğinden bunu genellenmiş kamera işlem süresi olarak değerlendirmemek gerekir.

## Daha önceki gerçek APK karşılaştırmasıyla birlikte yorum

- Orijinal 15 Ultra 6.7.000180.0 `PixelModule.onEarlyImageAvailable()` kodunda `stopFaceDetection()` ve `pausePreview()` çağrıları bulunuyor; 18 Pro 6.8.002090.2 portu eşdeğerinde yok.
- Orijinal `onFinalImageAvailable()` içinde `Handler.obtainMessage()`/`sendToTarget()` bulunuyor, 18 Pro eşdeğeri farklı.
- Her ikisinin `restoreUiState()` metodunda `resumePreview()` bulunuyor.
- Videodaki kareli geçiş, `TiledImageRevealAnimator` hipoteziyle uyumlu; videodan sınıf düzeyinde nedensellik kanıtlanmaz.
- Alpha 3/Alpha 4 cihaz testlerinde `EARLY_IMAGE` ve `FINAL_IMAGE` callback'leri tetiklenmedi: doğrudan bu kancalardan orijinal geçişi zorlamak uygun değil.

## Alpha 4 ile fark

Alpha 4'te kullanıcı tarafından doğrulanan **deklanşör üzerinde küçük popup** çıkıyor ve kayboluyor. Önceki iki Alpha 4 çekim logunda `MIVI_SUCCESS` ~1,882 / 1,782 saniye, `CAPTURE-END` ~50 ms sonra. Orijinal videoda işleme arayüzü 8–9 saniye; testler farklı kamera APK'larında ve koşullarda yapıldığından mutlak hız kıyaslaması yapılmamalı.

**Eksik kalan iş:** OEM'e özgü önizleme durumu ve görünür kademeli geçiş, yazının konumu, deklanşörün geçici UI durumu ve asıl final-kayıt tetikleyicisi.

## Sıradaki güvenli entegrasyon

1. Alpha 4'te `UI_SHOW`, `UI_HIDE`, `UI_TIMEOUT` kayıtlarını fotoğraf ID'si, monotonic zaman ve neden bilgisiyle (MIVI_SUCCESS/error/timeout) sun. Alpha 4'ün çalıştığı doğrulanmış göstergesini bozma.
2. Orijinal `onFinalImageAvailable` → Handler/preview state zincirinin porttaki gerçek yolunu karşılaştır; JPEG `CAPTURE-END` ile eşleşebilen geri çağrı bulunmadan 200 MP JPEG tamamlandı iddiası veya kamera durum değişikliği yapma.
3. Üst-orta etiket konumu gibi salt sunum farklarını ayrı, seçime bağlı modda dene.
4. `pausePreview()`, `resumePreview()`, `isBlockSnap()`, RAW/AISP, sensör remosaic veya HAL tamponlarını **varsayımsal olarak değiştirme**; mevcut aralıklı `miHalBufferReceiveError` hatasına çözüm olduğu henüz kanıtlanmadı.
5. Birleşik CameraAIMerged + XCamera v2.3 ve Alpha 4 üretim dalı değiştirilmeden kalsın; deneyler yeni dalda ve kolay geri alınabilir olsun.

## Sınırlamalar

Bu **video ekran davranışı kanıtıdır**, sensör/ISP algoritması kanıtı değildir. Orijinal 15 Ultra'nın görüntü işleme algoritmalarının tamamı Java APK içinde bulunmak zorunda değildir.
