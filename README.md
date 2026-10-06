# شمارشگر صنعتی (SmartCounter) 🏭

اپلیکیشن اندروید **شمارش دقیق محصولات روی خط انتقال** (conveyor belt).
برای افراد بدون سواد فنی: دوربین را رو به خط بگیرید، خط قرمز را با انگشت
جابه‌جا کنید، شمارش دقیق را ببینید.

## ویژگی‌ها
- 🎯 YOLOv8 ONNX + IOU Tracker: هر محصول دقیقاً یک‌بار
- ↑↓ شمارش دوطرفه (ورودی/خروجی)
- ✋ خط شمارش لمسی (drag)
- 💾 SQLite گزارش + 📤 خروجی CSV
- 📷 دوربین زنده + 🎬 ویدیو از گالری
- 🧪 تست خودکار: unit + e2e

## معماری
```
engine/
  detector.py   — YOLOv8 ONNX (OpenCV DNN, بدون PyTorch)
  tracker.py    — IOU tracker (جایگزین ByteTrack)
  counter.py    — شمارش عبور خط (side-change, ضد دوبارشماری)
  pipeline.py   — اتصال detector→tracker→counter→draw
reports/store.py — SQLite + CSV
ui/screen.py    — Kivy UI فارسی
main.py         — نقطه ورود
```

## اجرا (دسکتاپ)
```bash
pip install kivy opencv-python numpy
python3 test/test_counter.py   # unit
python3 test/test_full.py      # e2e (نیاز به assets/yolov8n.onnx)
python3 main.py
```

## مدل
`assets/yolov8n.onnx` (COCO 80-class).
برای شمارش اختصاصی (قرقره، بسته، ...) فاین‌تیون کنید:
```bash
yolo train model=yolov8n.pt data=dataset.yaml epochs=50 imgsz=640
yolo export model=runs/.../best.pt format=onnx
# → assets/model.onnx
```

## بیلد APK
```bash
pip install buildozer
buildozer android debug
```