[app]
title = شمارشگر هوشمند
package.name = smartcounter
package.domain = ir.ersaz
source.dir = .
source.include_exts = py,png,jpg,kv,onnx,tflite,yaml
version = 1.0.0
requirements = python3,kivy==2.3.1,opencv,numpy,sqlite3
orientation = portrait
fullscreen = 0
android.permissions = CAMERA,READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE,READ_MEDIA_VIDEO
android.api = 34
android.minapi = 26
android.archs = arm64-v8a
android.allow_backup = True

# مدل‌های بزرگ نباید داخل APK فشرده شوند
android.add_src = assets/

[buildozer]
log_level = 2
warn_on_root = 1

# p4a release tag (avoid p4a master's python 3.14, incompatible with kivy 2.3.0)
p4a.branch = v2026.05.09
