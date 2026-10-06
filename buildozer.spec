[app]
title = SmartCounter
package.name = smartcounter
package.domain = ir.ersaz
source.dir = .
source.include_exts = py,png,jpg,kv,onnx,tflite
version = 2.0.0
requirements = python3,kivy==2.3.1,opencv,numpy,sqlite3
orientation = portrait
fullscreen = 0
android.permissions = CAMERA,READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE,READ_MEDIA_VIDEO
android.api = 34
android.minapi = 26
android.archs = arm64-v8a
android.allow_backup = True

# assets (model) must NOT be compressed in APK
android.add_assets = assets/

[buildozer]
log_level = 2
warn_on_root = 1
p4a.branch = v2026.05.09