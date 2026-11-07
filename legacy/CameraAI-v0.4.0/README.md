# CameraAI v0.4.0 — Eski çalışan sürümün kaynak arşivi

Bu klasör, CameraAI Model Override v0.4.0'ın **kendi geliştirdiğimiz** Java kanca kodunu ve sürüm notlarını korur.

- `app/src/main/java/dev/cameraai/override/ModelHook.java`: eski Xposed API 82 tabanlı bağımsız CameraAI kancaları.
- Yeni birleşik modülün modern API 102 kodu: `../../app/src/main/java/dev/cameraai/override/ModernCameraAIHook.java`.
- Buradaki eski Gradle projesi bağımsız olarak yeniden derlenebilir. Önceden kurulmuş eski APK'yı ayrıca yedekleyin.
- Bu proje FCMFix'ten bağımsızdır. Yeni birleşik sürümde 200 MP üçlü menü henüz cihazda doğrulanmamıştır.
