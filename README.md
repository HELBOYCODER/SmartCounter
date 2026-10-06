# شمارشگر هوشمند (Smart Counter) 📱

اپلیکیشن اندروید **شمارش خودکار اشیا با دقت بالا** — برای افراد بدون سواد فنی.
فقط دوربین را رو به اجسام بگیرید؛ خط قرمز را با انگشت جابه‌جا کنید و شمارش را ببینید.

بر پایه پروژه اینستاگرام @pythongorjizadeh (شمارش قرقره با YOLO + ByteTrack) — بهبودیافته و تبدیل‌شده به اپ موبایل.

## ویژگی‌ها
- ▶ شمارش زنده از دوربین یا ویدیو
- ✋ جابه‌جایی خط شمارش با لمس
- ↺ صفر کردن / 💾 ذخیره گزارش / 📤 خروجی CSV
- 🧠 YOLO + ByteTrack: شمارش دقیق بدون تکرار (هر شیء فقط یک‌بار)

## اجرا روی اندروید
```bash
pip install buildozer
buildozer android debug        # خروجی: bin/*.apk
```

## اجرا روی دسکتاپ (تست)
```bash
pip install kivy opencv-python ultralytics
python test_engine.py          # تست منطق شمارش
python main.py                 # اجرای اپ
```

## ساختار
- `main.py` — UI فارسی + موتور شمارش
- `test_engine.py` — تست خودکار منطق
- `buildozer.spec` — تنظیمات بیلد APK
- `assets/` — مدل (`model.pt` یا `model.tflite`)

## مدل
پیش‌فرض: `yolov8n.pt` عمومی. برای شمارش اختصاصی (قرقره، پیچ، بسته و...) با چند ده عکس خود اجسام فاین‌تیون کنید:
```bash
yolo train model=yolov8n.pt data=dataset.yaml epochs=50 imgsz=640
```
