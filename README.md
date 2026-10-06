# شمارشگر هوشمند (SmartCounter) 📱🏭

اپلیکیشن بومی و صنعتی اندروید **شمارش دقیق و خودکار انواع محصولات** (ظروف غذا، قرقره‌های نخ و سیم، قطعات، بسته‌بندی‌ها، بطری‌ها و لوله‌ها) با دوربین موبایل بدون خطا و بدون نیاز به اینترنت.

طراحی‌شده با معماری مدرن بومی اندروید (**Kotlin + Jetpack Compose + CameraX + ONNX Runtime**).

---

## 🚀 ویژگی‌های کلیدی
- 🎯 **پیش‌نمایش زنده دوربین ۶۰ فریم (CameraX):** تصویر کاملاً شفاف و بلادرنگ بدون لگ و بدون صفحه سفید.
- ✋ **خط شمارش لمسی و شناور (Draggable Laser Line):** جابه‌جایی آزاد خط قرمز شمارش با انگشت روی تصویر.
- ⚡ **دو حالت کاری هوشمند:**
  1. **حالت نوار نقاله (Flow / Conveyor Mode):** شمارش لحظه‌ای اجسام در حال حرکت هنگام عبور از خط با جلوگیری قطعی از دوبارشماری (Hysteresis Tracking).
  2. **حالت بسته‌بندی و سینی (Batch / Tray Mode):** شمارش درجا تمام اجسام چیده‌شده کنار هم در کادر (مناسب جعبه‌ها، سینی‌های غذا و قرقره‌ها).
- 📳 **فیدبک لمسی لرزشی (Haptic Feedback):** لرزش کوتاه گوشی همزمان با ثبت هر شمارش.
- 🔦 **چراغ‌قوه داخلی (Torch):** کارکرد مطمئن در محیط‌های کارگاهی و کارخانه‌ای کم‌نور.
- 📤 **خروجی و گزارش‌گیری CSV:** ذخیره سوابق هر شیفت کاری در دیتابیس محلی SQLite با قابلیت اشتراک‌گذاری مستقیم فایل CSV.
- 🛡️ **مدیریت هوشمند دسترسی‌ها:** درخواست استاندارد مجوز دوربین (Runtime Permissions) با کارت راهنمای فارسی.

---

## 🏗️ معماری نرم‌افزار
```
app/src/main/
├── java/com/helboy/smartcounter/
│   ├── MainActivity.kt               # نقطه ورود Compose و Edge-to-Edge
│   ├── SmartCounterApp.kt            # Application Class
│   ├── core/
│   │   ├── CameraManager.kt          # مدیریت چرخه حیات CameraX و فلش و فوکوس
│   │   └── HapticManager.kt          # ویبراتور و فیدبک لمسی دقیق
│   ├── engine/
│   │   ├── TrackedObject.kt          # مدل شیء ردیابی‌شده با شناسه یکتا
│   │   ├── IouTracker.kt             # ردیاب سبک و دقیق هندسی با معیار IoU
│   │   ├── LineCounter.kt            # منطق عبور از خط و تفکیک جهت ورود/خروج
│   │   ├── IndustrialVisionAnalyzer.kt # پردازش بلادرنگ فریم‌ها با سرعت ۶۰ فریم
│   │   └── OnnxYoloAnalyzer.kt       # موتور هوش مصنوعی YOLOv8 ONNX Runtime
│   ├── data/
│   │   ├── CounterSession.kt         # مدل داده جلسات شمارش
│   │   └── SessionRepository.kt      # دیتابیس SQLite و اکسپورت CSV
│   └── ui/
│       ├── theme/                    # تم صنعتی مشکی آبسیدین و سبز سایبری
│       ├── components/
│       │   ├── CountingOverlay.kt    # رندر خط لیزر شناور و کادرهای تشخیص
│       │   ├── CounterHud.kt         # نمایشگر دیجیتال بزرگ و پنل کنترل لمسی
│       │   └── PermissionCard.kt     # کارت استاندارد فعال‌سازی دسترسی دوربین
│       └── SmartCounterScreen.kt     # صفحه اصلی اپلیکیشن
└── res/                              # آیکون اختصاصی و منابع بصری
```

---

## 🛠️ بیلد و نصب
این پروژه به صورت خودکار توسط **GitHub Actions** بیلد شده و فایل نصبی در بخش [Releases](https://github.com/HELBOYCODER/SmartCounter/releases) قرار می‌گیرد:
```bash
./gradlew assembleRelease
```
خروجی در مسیر `app/build/outputs/apk/release/` تولید می‌شود.
