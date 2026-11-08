package dev.cameraai.override;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Runtime Turkish UI localization for the Xiaomi Camera 6.8.002090.2 port.
 * Existing 3,108 Turkish resource strings are read from the camera's own
 * values-tr resource configuration; additional English/Chinese UI labels
 * receive exact, reviewed translations. No forced system locale, no JSON
 * payload modifications, no file/EXIF/camera/HAL or sensor alterations.
 */
public final class CameraTurkishHook extends XposedModule {
    private static final String TAG = "CameraAI_TR";
    private static final String TARGET = "com.android.camera";
    private static final Map<String, String> WORDS = new HashMap<>();
    private static final ThreadLocal<Boolean> LOCALE_LOOKUP = new ThreadLocal<>();
    private static volatile Resources turkishResources;

    static {
        WORDS.put("AI Capture Assist", "Yapay Zekâ Çekim Asistanı");
        WORDS.put("AI recommendations", "Yapay zekâ önerileri");
        WORDS.put("Scene recommendations", "Sahne önerileri");
        WORDS.put("Pose suggestions", "Poz önerileri");
        WORDS.put("Smart composition", "Akıllı kadraj");
        WORDS.put("Analyzing scene…", "Sahne analiz ediliyor…");
        WORDS.put("Analyzing…", "Analiz ediliyor…");
        WORDS.put("Detecting pose…", "Poz algılanıyor…");
        WORDS.put("Recognizing people…", "Kişiler tanınıyor…");
        WORDS.put("Recognizing…", "Tanımlanıyor…");
        WORDS.put("Close pose suggestions", "Poz önerilerini kapat");
        WORDS.put("Try pose suggestions", "Poz önerilerini dene");
        WORDS.put("Point at the subject and hold steady", "Kamerayı konuya yöneltip sabit tutun");
        WORDS.put("Poor network connection", "Ağ bağlantısı zayıf");
        WORDS.put("Connect to the network first", "Önce internete bağlanın");
        WORDS.put("A network error occurred. Connect to the network for more pose recommendations", "Daha fazla poz önerisi için internete bağlanın.");
        WORDS.put("A network error occurred. Connect to the network for more recommendations.", "Daha fazla öneri için internete bağlanın.");
        WORDS.put("A network error occurred. Connect to the network for more effect recommendations.", "Daha fazla efekt önerisi için internete bağlanın.");
        WORDS.put("A network error occurred. Try again.", "Ağ hatası oluştu. Yeniden deneyin.");
        WORDS.put("A network error occurred. Tap to try again.", "Ağ hatası oluştu. Yeniden denemek için dokunun.");
        WORDS.put("A server error occurred. Wait a minute or two and try again.", "Sunucu hatası oluştu. Birkaç dakika sonra tekrar deneyin.");
        WORDS.put("Analyze scenes in real time, get filter and effect recommendations, receive composition instructions, and apply them automatically.", "Sahneleri gerçek zamanlı analiz eder, filtre ve efekt önerileri sunar, kadraj önerilerini otomatik uygular.");
        WORDS.put("Analyze scenes in real time, get filter and effect recommendations, receive composition instructions and pose suggestions, and apply them automatically.", "Sahneleri gerçek zamanlı analiz eder, filtre, efekt, kadraj ve poz önerileri sunar; bunları otomatik uygular.");
        WORDS.put("Effects", "Efektler");
        WORDS.put("View more", "Daha fazlasını gör");
        WORDS.put("Agree", "Kabul et");
        WORDS.put("Cancel", "İptal");
        WORDS.put("Open", "Aç");
        WORDS.put("Attention", "Dikkat");
        WORDS.put("Experimental feature", "Deneysel özellik");
        WORDS.put("Reset", "Sıfırla");
        WORDS.put("Auto shutter is off", "Otomatik deklanşör kapalı");
        WORDS.put("Auto shutter is on", "Otomatik deklanşör açık");
        WORDS.put("Opengate aspect ratio", "Tam sensör en-boy oranı");
        WORDS.put("Charm and smile", "Çekicilik ve gülümseme");
        WORDS.put("Enjoy life", "Hayatın tadını çıkar");
        WORDS.put("Party", "Parti");
        WORDS.put("Switched to the original effect", "Orijinal efekte geçildi");
        WORDS.put("Original", "Orijinal");
        WORDS.put("Blue tint", "Mavi ton");
        WORDS.put("Classic film", "Klasik film");
        WORDS.put("Classic negative", "Klasik negatif");
        WORDS.put("Classic positive", "Klasik pozitif");
        WORDS.put("Bright spring", "Aydınlık bahar");
        WORDS.put("Cinematic", "Sinematik");
        WORDS.put("Blue tint adds a clean, clear blue tone for a fresh look", "Mavi ton, ferah bir görünüm için fotoğrafa temiz ve belirgin mavi tonlar katar.");
        WORDS.put("Classic film creates a rich negative film look for your image", "Klasik film, fotoğrafa zengin bir negatif film görünümü kazandırır.");
        WORDS.put("Classic negative creates a rich vintage film look for your image", "Klasik negatif, fotoğrafa zengin ve nostaljik bir film görünümü verir.");
        WORDS.put("Classic positive adds vivid colors to your image", "Klasik pozitif, fotoğrafınıza canlı renkler ekler.");
        WORDS.put("Bright spring creates a fresh, bright springtime look", "Aydınlık bahar, ferah ve parlak bir bahar görünümü sunar.");
        WORDS.put("Leica VIV adds vivid colors to your image.", "Leica VIV, fotoğrafınıza canlı renkler katar.");
        WORDS.put("Leica BW NAT adds a deep, classic documentary feel to your image.", "Leica BW NAT, fotoğrafa güçlü ve klasik bir belgesel havası verir.");
        WORDS.put("Dreamy blooms add soft, romantic warm tones", "Rüya gibi çiçekler, yumuşak ve romantik sıcak tonlar katar.");
        WORDS.put("Natural", "Doğal");
        WORDS.put("Vivid", "Canlı");
        WORDS.put("Standard", "Standart");
        WORDS.put("Monochrome High Contrast", "Siyah-beyaz yüksek kontrast");
        WORDS.put("Monochrome Natural", "Doğal siyah-beyaz");
        WORDS.put("Contemporary", "Çağdaş");
        WORDS.put("Eternal", "Zamansız");
        WORDS.put("Teal", "Turkuaz");
        WORDS.put("Brass", "Pirinç tonu");
        WORDS.put("Classic", "Klasik");
        WORDS.put("Blue", "Mavi");
        WORDS.put("Retro", "Retro");
        WORDS.put("Modern", "Modern");
        WORDS.put("Normal", "Normal");
        WORDS.put("Film", "Film");
        WORDS.put("Tilt-shift", "Eğim-kaydırma");
        WORDS.put("Ultra HD", "Ultra yüksek çözünürlük");
        WORDS.put("Panorama", "Panorama");
        WORDS.put("Pro video", "Profesyonel video");
        WORDS.put("Multicam", "Çoklu kamera");
        WORDS.put("Multicam video", "Çoklu kamera videosu");
        WORDS.put("Leica Looks", "Leica görünümleri");
        WORDS.put("Pro LUTs", "Profesyonel LUT'lar");
        WORDS.put("Swirly bokeh", "Döner bokeh");
        WORDS.put("B&W film", "Siyah-beyaz film");
        WORDS.put("Modern vintage", "Modern nostalji");
        WORDS.put("Leica Vibrant", "Leica Canlı");
        WORDS.put("Xiaomi Short Video", "Xiaomi Kısa Video");
        WORDS.put("Turn on dual camera", "Çift kamerayı aç");
        WORDS.put("Turn on dual SAT camera", "Çift SAT kamerayı aç");
        WORDS.put("Auto capture for documents", "Belgeleri otomatik çek");
        WORDS.put("Document size", "Belge boyutu");
        WORDS.put("Scan ID", "Kimlik tara");
        WORDS.put("Scan the front of your ID", "Kimliğin ön yüzünü tarayın");
        WORDS.put("Scan the back of your ID", "Kimliğin arka yüzünü tarayın");
        WORDS.put("Scan the front of your bank card", "Banka kartınızın ön yüzünü tarayın");
        WORDS.put("Scan the back of your bank card", "Banka kartınızın arka yüzünü tarayın");
        WORDS.put("Place the document inside the frame", "Belgeyi çerçevenin içine yerleştirin");
        WORDS.put("Documents", "Belgeler");
        WORDS.put("Custom size", "Özel boyut");
        WORDS.put("ID photo", "Vesikalık fotoğraf");
        WORDS.put("identification photo", "Kimlik fotoğrafı");
        WORDS.put("Admission photo", "Kabul belgesi fotoğrafı");
        WORDS.put("Exam registration photo", "Sınav başvuru fotoğrafı");
        WORDS.put("Professional Photo", "Profesyonel fotoğraf");
        WORDS.put("Visa photo", "Vize fotoğrafı");
        WORDS.put("Passport", "Pasaport");
        WORDS.put("Residence permit", "İkamet izni");
        WORDS.put("Social security card", "Sosyal güvenlik kartı");
        WORDS.put("Health Certificate", "Sağlık sertifikası");
        WORDS.put("Student ID card", "Öğrenci kimlik kartı");
        WORDS.put("Marriage certificate", "Evlilik cüzdanı");
        WORDS.put("Divorce certificate", "Boşanma belgesi");
        WORDS.put("Birth certificate", "Doğum belgesi");
        WORDS.put("Vehicle license", "Araç ruhsatı");
        WORDS.put("Driving license", "Sürücü belgesi");
        WORDS.put("Medical insurance card", "Sağlık sigortası kartı");
        WORDS.put("Bank card", "Banka kartı");
        WORDS.put("Business license", "İşletme ruhsatı");
        WORDS.put("Recent", "Son kullanılanlar");
        WORDS.put("Lifestyle", "Yaşam");
        WORDS.put("Work", "İş");
        WORDS.put("Student", "Öğrenci");
        WORDS.put("Children", "Çocuk");
        WORDS.put("Education", "Eğitim");
        WORDS.put("Exams", "Sınavlar");
        WORDS.put("Visa", "Vize");
        WORDS.put("Single", "Tekli");
        WORDS.put("Capture", "Çek");
        WORDS.put("Resolution", "Çözünürlük");
        WORDS.put("File size", "Dosya boyutu");
        WORDS.put("Height", "Yükseklik");
        WORDS.put("Width", "Genişlik");
        WORDS.put("Size", "Boyut");
        WORDS.put("Good example", "Doğru örnek");
        WORDS.put("Bad example", "Yanlış örnek");
        WORDS.put("Keep your posture straight, make sure your face is unobstructed, and use a plain background", "Dik durun, yüzünüzün açıkça göründüğünden emin olun ve düz bir arka plan kullanın.");
        WORDS.put("Make sure you aren't wearing glasses, jewelry, or other accessories, and keep your face straight", "Gözlük, takı veya aksesuar kullanmayın; yüzünüzü düz tutun.");
        WORDS.put("Make sure your head is fully visible and the photo shows only one person facing forward", "Başınız tamamen görünmeli ve fotoğrafta yalnızca öne bakan bir kişi bulunmalıdır.");
        WORDS.put("Argentina", "Arjantin");
        WORDS.put("Australia", "Avustralya");
        WORDS.put("Brazil", "Brezilya");
        WORDS.put("Canada", "Kanada");
        WORDS.put("France", "Fransa");
        WORDS.put("Germany", "Almanya");
        WORDS.put("Iceland", "İzlanda");
        WORDS.put("India", "Hindistan");
        WORDS.put("Italy", "İtalya");
        WORDS.put("Japan", "Japonya");
        WORDS.put("Kenya", "Kenya");
        WORDS.put("South Korea", "Güney Kore");
        WORDS.put("New Zealand", "Yeni Zelanda");
        WORDS.put("Russia", "Rusya");
        WORDS.put("Spain", "İspanya");
        WORDS.put("Thailand (visa on arrival)", "Tayland (kapıda vize)");
        WORDS.put("Thailand (tourist visa)", "Tayland (turist vizesi)");
        WORDS.put("United Kingdom", "Birleşik Krallık");
        WORDS.put("United States", "Amerika Birleşik Devletleri");
        WORDS.put("Vietnam", "Vietnam");
        WORDS.put("Model", "Model");
        WORDS.put("Histogram", "Histogram");
        WORDS.put("Demo", "Demo");
        WORDS.put("Xiaomi AI Camera Features Privacy Policy", "Xiaomi Yapay Zekâ Kamera Özellikleri Gizlilik Politikası");
        WORDS.put("Xiaomi AI Camera Features User Agreement", "Xiaomi Yapay Zekâ Kamera Özellikleri Kullanıcı Sözleşmesi");
        WORDS.put("Withdrawal of consent", "Onayı geri çek");
        WORDS.put("Withdrawal of consent to the Xiaomi AI Camera Features Privacy Policy", "Xiaomi Yapay Zekâ Kamera Gizlilik Politikası onayını geri çek");
        WORDS.put("Registration info", "Kayıt bilgileri");
        WORDS.put("Service registration name", "Hizmet kayıt adı");
        WORDS.put("Service registration number", "Hizmet kayıt numarası");
        WORDS.put("Algorithm registration name", "Algoritma kayıt adı");
        WORDS.put("Algorithm registration number", "Algoritma kayıt numarası");
        WORDS.put("Download the latest version of Gallery Editor to be able to use this feature", "Bu özelliği kullanmak için Galeri Düzenleyici'nin son sürümünü indirin.");
        WORDS.put("相机操作", "Kamera işlemleri");
        WORDS.put("分析预览画面", "Önizleme analiz ediliyor");
        WORDS.put("相机异常", "Kamera hatası");
        WORDS.put("错误码:", "Hata kodu:");
        WORDS.put("照片名:", "Fotoğraf adı:");
        WORDS.put("问题时间:", "Sorun zamanı:");
        WORDS.put("问题类型:", "Sorun türü:");
        WORDS.put("海拔", "Rakım");
        WORDS.put("纬度", "Enlem");
        WORDS.put("经度", "Boylam");
        WORDS.put("权限受限，请联系研发", "İzin kısıtlandı; geliştiriciyle iletişime geçin.");
        WORDS.put("响应体解析失败，可能是返回格式有问题", "Sunucu yanıtı çözümlenemedi; yanıt biçimi hatalı olabilir.");
        WORDS.put("场景解析成功", "Sahne analizi başarılı");
        WORDS.put("网络不佳，无法识别当前场景", "Ağ zayıf; mevcut sahne tanımlanamıyor.");
        WORDS.put("网络异常，请连接网络获取更多姿势推荐", "Daha fazla poz önerisi için internete bağlanın.");
        WORDS.put("网络异常，请连接网络获取更多推荐", "Daha fazla öneri için internete bağlanın.");
        WORDS.put("网络异常，请连接网络获取更多效果推荐", "Daha fazla efekt önerisi için internete bağlanın.");
        WORDS.put("分析拍摄场景", "Çekim sahnesi analiz ediliyor");
        WORDS.put("识别人像", "Kişi tanınıyor");
        WORDS.put("智能创作识别中", "Yapay zekâ içerik analiz ediyor");
        WORDS.put("姿势引导", "Poz önerileri");
        WORDS.put("AI帮拍", "Yapay Zekâ Çekim Asistanı");
        WORDS.put("AI帮拍模式", "Yapay Zekâ Çekim Asistanı");
        WORDS.put("智能构图", "Akıllı kadraj");
        WORDS.put("服务端异常，请稍后重试", "Sunucu hatası oluştu. Lütfen daha sonra tekrar deneyin.");
        WORDS.put("原图", "Orijinal");
        WORDS.put("增强", "Geliştirilmiş");
        WORDS.put("黑白", "Siyah-beyaz");
        WORDS.put("拍照", "Fotoğraf");
        WORDS.put("录像", "Video");
        WORDS.put("设置", "Ayarlar");
        WORDS.put("更多", "Diğer");
        WORDS.put("完成", "Bitti");
        WORDS.put("确定", "Tamam");
        WORDS.put("取消", "İptal");
        WORDS.put("关闭", "Kapat");
        WORDS.put("开启", "Aç");
        WORDS.put("保存", "Kaydet");
        WORDS.put("删除", "Sil");
        WORDS.put("重试", "Tekrar dene");
        WORDS.put("未连接网络", "Ağa bağlı değil");
    }

    private void note(String message) {
        log(Log.INFO, TAG, message);
        Log.i(TAG, message);
    }

    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam p) {
        if (!TARGET.equals(p.getPackageName())) return;
        hookCameraContext(p.getClassLoader());
        hookResources();
        hookViews();
        hookToasts();
        note("Turkish UI overlay attached; camera resources + " + WORDS.size()
            + " curated UI translations, no PowerShell configuration");
    }

    private static void attachTurkishResources(Context context) {
        try {
            Configuration config = new Configuration(context.getResources().getConfiguration());
            config.setLocale(Locale.forLanguageTag("tr-TR"));
            Context local = context.createConfigurationContext(config);
            turkishResources = local.getResources();
            Log.i(TAG, "TR_CAMERA_RESOURCES_READY");
        } catch (Throwable error) {
            Log.w(TAG, "TR_RESOURCE_CONTEXT_UNAVAILABLE " + error.getClass().getSimpleName());
        }
    }

    private void hookCameraContext(ClassLoader loader) {
        try {
            Class<?> camera = Class.forName("com.android.camera.Camera", false, loader);
            int n = 0;
            for (Method m : camera.getDeclaredMethods()) {
                if (!m.getName().equals("onCreate") || m.getParameterCount() != 1
                        || m.getParameterTypes()[0] != Bundle.class
                        || Modifier.isStatic(m.getModifiers())) continue;
                hook(m).intercept(chain -> {
                    Object a = chain.getThisObject();
                    if (a instanceof Activity) attachTurkishResources((Activity) a);
                    return chain.proceed();
                });
                n++;
            }
            note("camera onCreate locale hooks=" + n);
        } catch (Throwable error) {
            note("Camera context unavailable " + error.getClass().getSimpleName());
        }
    }

    /**
     * Hook framework Resources.getText only inside com.android.camera process.
     * The recursion guard makes locale-specific resource retrieval safe.
     * The 0x7f package-ID check restricts translation to camera app resources.
     */
    private void hookResources() {
        try {
            Method text = Resources.class.getDeclaredMethod("getText", int.class);
            hook(text).intercept(chain -> {
                Object original = chain.proceed();
                Resources local = turkishResources;
                int id = (Integer) chain.getArg(0);
                if (local == null || (id >>> 24) != 0x7f
                        || Boolean.TRUE.equals(LOCALE_LOOKUP.get())) return original;
                try {
                    LOCALE_LOOKUP.set(Boolean.TRUE);
                    CharSequence localized = local.getText(id);
                    return translate(localized);
                } catch (Resources.NotFoundException ex) {
                    return original instanceof CharSequence ? translate((CharSequence) original) : original;
                } catch (Throwable ex) {
                    return original;
                } finally {
                    LOCALE_LOOKUP.remove();
                }
            });
            note("Resources.getText Turkish locale hook installed");
        } catch (Throwable error) {
            note("Resources.getText unavailable " + error.getClass().getSimpleName());
        }
        try {
            Method array = Resources.class.getDeclaredMethod("getStringArray", int.class);
            hook(array).intercept(chain -> {
                Object original = chain.proceed();
                int id = (Integer) chain.getArg(0);
                Resources local = turkishResources;
                if (local == null || (id >>> 24) != 0x7f
                        || Boolean.TRUE.equals(LOCALE_LOOKUP.get())) return original;
                try {
                    LOCALE_LOOKUP.set(Boolean.TRUE);
                    String[] strings = local.getStringArray(id);
                    for (int i=0; i<strings.length; i++)
                        strings[i] = String.valueOf(translate(strings[i]));
                    return strings;
                } catch (Throwable ignored) { return original; }
                finally { LOCALE_LOOKUP.remove(); }
            });
            note("Resources.getStringArray Turkish locale hook installed");
        } catch (Throwable error) {
            note("getStringArray unavailable " + error.getClass().getSimpleName());
        }
        try {
            Method array = Resources.class.getDeclaredMethod("getTextArray", int.class);
            hook(array).intercept(chain -> {
                Object original = chain.proceed();
                int id = (Integer) chain.getArg(0);
                Resources local = turkishResources;
                if (local == null || (id >>> 24) != 0x7f
                        || Boolean.TRUE.equals(LOCALE_LOOKUP.get())) return original;
                try {
                    LOCALE_LOOKUP.set(Boolean.TRUE);
                    CharSequence[] values = local.getTextArray(id);
                    for (int i=0; i<values.length; i++)
                        values[i] = translate(values[i]);
                    return values;
                } catch (Throwable ignored) { return original; }
                finally { LOCALE_LOOKUP.remove(); }
            });
            note("Resources.getTextArray Turkish locale hook installed");
        } catch (Throwable error) {
            note("getTextArray unavailable " + error.getClass().getSimpleName());
        }
    }

    private void hookViews() {
        for (String name : new String[]{"setText", "setHint"}) {
            try {
                Method method = TextView.class.getDeclaredMethod(name, CharSequence.class);
                hook(method).intercept(chain -> {
                    Object first = chain.getArg(0);
                    if (!(first instanceof String)) return chain.proceed();
                    String localized = WORDS.get(first);
                    if (localized == null) return chain.proceed();
                    Object[] args = chain.getArgs().toArray();
                    args[0] = localized;
                    return chain.proceed(args);
                });
                note("TextView." + name + " hooked");
            } catch (Throwable error) {
                note("TextView." + name + " unavailable " + error.getClass().getSimpleName());
            }
        }
        try {
            Method method = View.class.getDeclaredMethod("setContentDescription", CharSequence.class);
            hook(method).intercept(chain -> {
                Object first = chain.getArg(0);
                if (!(first instanceof String)) return chain.proceed();
                String localized = WORDS.get(first);
                if (localized == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[0] = localized;
                return chain.proceed(args);
            });
            note("View.setContentDescription hooked");
        } catch (Throwable error) {
            note("View.setContentDescription unavailable " + error.getClass().getSimpleName());
        }
    }

    private void hookToasts() {
        try {
            Method method = Toast.class.getDeclaredMethod(
                "makeText", Context.class, CharSequence.class, int.class);
            hook(method).intercept(chain -> {
                Object message = chain.getArg(1);
                if (!(message instanceof String)) return chain.proceed();
                String localized = WORDS.get(message);
                if (localized == null) return chain.proceed();
                Object[] args = chain.getArgs().toArray();
                args[1] = localized;
                return chain.proceed(args);
            });
            note("Toast.makeText UI translation installed");
        } catch (Throwable error) {
            note("Toast.makeText unavailable " + error.getClass().getSimpleName());
        }
    }

    private static CharSequence translate(CharSequence value) {
        if (!(value instanceof String)) return value; // preserve spans and links
        String localized = WORDS.get(value);
        return localized == null ? value : localized;
    }
}
