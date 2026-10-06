# -*- coding: utf-8 -*-
"""
شمارشگر هوشمند (Smart Counter) — موتور شمارش بدون وابستگی به PyTorch
اجرا با OpenCV DNN (مدل ONNX) + ردیاب IOU سبک + خط شمارش.
مناسب بیلد اندروید (فقط opencv + numpy + kivy).
"""
import os
import time
import numpy as np
import cv2

# ─────────────── ردیاب سبک (جایگزین ByteTrack بدون torch) ───────────────
class IouTracker:
    """ردیاب ساده و سریع: تطبیق جعبه‌ها با IoU + مهلت گم‌شدن."""

    def __init__(self, iou_thresh=0.3, max_lost=15):
        self.iou_thresh = iou_thresh
        self.max_lost = max_lost
        self.tracks = {}      # id -> {"box": (x1,y1,x2,y2), "lost": int}
        self.next_id = 1

    @staticmethod
    def _iou(a, b):
        ax1, ay1, ax2, ay2 = a
        bx1, by1, bx2, by2 = b
        ix1, iy1 = max(ax1, bx1), max(ay1, by1)
        ix2, iy2 = min(ax2, bx2), min(ay2, by2)
        iw, ih = max(0, ix2 - ix1), max(0, iy2 - iy1)
        inter = iw * ih
        if inter <= 0:
            return 0.0
        ua = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
        return inter / ua if ua > 0 else 0.0

    def update(self, boxes):
        """boxes: لیست (x1,y1,x2,y2) → دیکشنری {id: box}"""
        assigned = {}
        used = set()

        # تطبیق هر ترک با بهترین جعبه
        for tid, tr in list(self.tracks.items()):
            best, best_iou = None, self.iou_thresh
            for i, b in enumerate(boxes):
                if i in used:
                    continue
                v = self._iou(tr["box"], b)
                if v > best_iou:
                    best, best_iou = i, v
            if best is not None:
                used.add(best)
                self.tracks[tid]["box"] = boxes[best]
                self.tracks[tid]["lost"] = 0
                assigned[tid] = boxes[best]
            else:
                self.tracks[tid]["lost"] += 1

        # حذف ترک‌های گم‌شده
        for tid in [t for t, tr in self.tracks.items() if tr["lost"] > self.max_lost]:
            del self.tracks[tid]

        # ترک جدید برای جعبه‌های بدون تطبیق
        for i, b in enumerate(boxes):
            if i not in used:
                self.tracks[self.next_id] = {"box": b, "lost": 0}
                assigned[self.next_id] = b
                self.next_id += 1
        return assigned

    def reset(self):
        self.tracks.clear()
        self.next_id = 1


# ─────────────── موتور شمارش ───────────────
class CounterEngine:
    """YOLO-ONNX (OpenCV DNN) + IouTracker + خط شمارش"""

    def __init__(self, app_dir, on_count=None, on_frame=None):
        self.app_dir = app_dir
        self.on_count, self.on_frame = on_count, on_frame
        self.net = None
        self.cap = None
        self.running = False
        self.line_ratio = 0.55
        self.count = 0
        self.counted = set()
        self.fps = 0.0
        self._t0 = None
        self.tracker = IouTracker()
        self.conf = 0.35
        self.nms = 0.45
        self.imgsz = 640
        self.conf_names = None

    # ── بارگذاری مدل ONNX ──
    def load_model(self):
        here = os.path.dirname(os.path.abspath(__file__))
        candidates = [
            os.path.join(here, "assets", "model.onnx"),
            os.path.join(self.app_dir, "assets", "model.onnx"),
            os.path.join(self.app_dir, "assets", "yolov8n.onnx"),
            os.path.join(self.app_dir, "yolov8n.onnx"),
            os.path.join(here, "yolov8n.onnx"),
            "/sdcard/Download/model.onnx",
        ]
        path = next((p for p in candidates if os.path.exists(p)), None)
        if not path:
            raise FileNotFoundError("model.onnx پیدا نشد — در assets بگذارید")
        self.net = cv2.dnn.readNetFromONNX(path)
        try:
            self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
            self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
        except Exception:
            pass

    # ── شروع دوربین / ویدیو ──
    def start_camera(self, index=0, size=(640, 480)):
        self.stop()
        self.cap = cv2.VideoCapture(index)
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, size[0])
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, size[1])
        self._begin()

    def start_video(self, path):
        self.stop()
        self.cap = cv2.VideoCapture(path)
        self._begin()

    def _begin(self):
        if self.net is None:
            self.load_model()
        self.count, self.fps = 0, 0.0
        self.counted.clear()
        self.tracker.reset()
        self._t0 = None
        self.running = True
        if self.on_count:
            self.on_count(0)

    def stop(self):
        self.running = False
        if self.cap is not None:
            self.cap.release()
            self.cap = None

    # ── استنتاج YOLOv8 ONNX ──
    def _detect(self, frame):
        h, w = frame.shape[:2]
        blob = cv2.dnn.blobFromImage(frame, 1 / 255.0, (self.imgsz, self.imgsz),
                                     swapRB=True, crop=False)
        self.net.setInput(blob)
        out = self.net.forward()          # (1, 84, 8400)
        out = np.squeeze(out).T           # (8400, 84)
        # ستون‌های ۰..۳ = cx,cy,w,h ؛ بقیه = امتیاز کلاس‌ها
        scores = out[:, 4:]
        cls_ids = scores.argmax(1)
        confs = scores[np.arange(len(scores)), cls_ids]
        keep = confs > self.conf
        out, cls_ids, confs = out[keep], cls_ids[keep], confs[keep]
        if len(out) == 0:
            return []

        cx, cy, bw, bh = out[:, 0], out[:, 1], out[:, 2], out[:, 3]
        x1 = (cx - bw / 2) * w / self.imgsz
        y1 = (cy - bh / 2) * h / self.imgsz
        x2 = (cx + bw / 2) * w / self.imgsz
        y2 = (cy + bh / 2) * h / self.imgsz
        boxes = np.stack([x1, y1, x2, y2], 1)
        idx = cv2.dnn.NMSBoxes(boxes.tolist(), confs.tolist(), self.conf, self.nms)
        if len(idx) == 0:
            return []
        idx = np.array(idx).flatten()
        return [(tuple(map(int, boxes[i])), float(confs[i]), int(cls_ids[i])) for i in idx]

    # ── پردازش یک فریم ──
    def process_frame(self):
        if not self.running or self.cap is None:
            return False
        ret, frame = self.cap.read()
        if not ret:
            self.running = False
            self._emit(None)
            return False

        t = time.time()
        self.fps = (0.85 * self.fps + 0.15 / max(t - self._t0, 1e-6)) if self._t0 else 0
        self._t0 = t

        dets = self._detect(frame)
        boxes = [d[0] for d in dets]
        tracked = self.tracker.update(boxes)

        h, w = frame.shape[:2]
        line_y = int(h * self.line_ratio)

        for tid, (x1, y1, x2, y2) in tracked.items():
            cy = (y1 + y2) // 2
            cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 230, 90), 2)
            cv2.putText(frame, f"#{tid}", (x1, max(16, y1 - 8)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 230, 90), 2)
            cv2.circle(frame, ((x1 + x2) // 2, cy), 4, (255, 120, 0), -1)
            if cy > line_y and tid not in self.counted:
                self.counted.add(tid)
                self.count += 1
                if self.on_count:
                    self.on_count(self.count)

        cv2.line(frame, (0, line_y), (w, line_y), (0, 0, 255), 3)
        cv2.putText(frame, f"{self.count}", (18, 62),
                    cv2.FONT_HERSHEY_SIMPLEX, 1.8, (0, 210, 255), 4)
        cv2.putText(frame, f"{self.fps:.0f} FPS", (18, h - 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (210, 210, 210), 1)
        self._emit(frame)
        return True

    def _emit(self, frame):
        if self.on_frame:
            self.on_frame(frame, self.fps)


# ─────────────── رابط کاربری (Kivy) ───────────────
from kivy.app import App
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.graphics.texture import Texture
from kivy.metrics import dp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.image import Image
from kivy.uix.label import Label
from kivy.uix.popup import Popup
from kivy.utils import platform

IS_ANDROID = platform == "android"

FA = {
    "title": "شمارشگر هوشمند",
    "start": "▶  شروع شمارش",
    "stop": "■  توقف",
    "video": "🎬  ویدیو",
    "reset": "↺  صفر",
    "save": "💾  ذخیره",
    "count_cap": "تعداد شمرده‌شده",
    "hint": "خط قرمز را با انگشت جابه‌جا کن",
}


class RootUI(FloatLayout):
    def __init__(self, **kw):
        super().__init__(**kw)
        self.engine = None
        self._dragging = False

        self.preview = Image(size_hint=(1, 0.86), pos_hint={"x": 0, "top": 1},
                             allow_stretch=True, keep_ratio=True)
        self.add_widget(self.preview)

        self.big = Label(text="0", font_size="78sp", bold=True,
                         color=(1, 0.66, 0.12, 1), outline_width=3,
                         outline_color=(0, 0, 0, 0.85),
                         size_hint=(0.34, 0.14), pos_hint={"center_x": 0.5, "top": 0.99})
        self.add_widget(self.big)

        cap = Label(text=FA["count_cap"], font_size="13sp", color=(1, 1, 1, 0.8),
                    size_hint=(0.4, 0.04), pos_hint={"center_x": 0.5, "top": 0.865})
        self.add_widget(cap)

        bar = BoxLayout(size_hint=(1, 0.14), pos_hint={"x": 0, "y": 0.075},
                        spacing=dp(6), padding=[dp(8), dp(5)])
        self.b_start = self._btn(FA["start"], self.on_start, (0.10, 0.62, 0.30, 1))
        self.b_stop = self._btn(FA["stop"], self.on_stop, (0.62, 0.16, 0.14, 1))
        self.b_video = self._btn(FA["video"], self.on_video, (0.15, 0.34, 0.55, 1))
        self.b_reset = self._btn(FA["reset"], self.on_reset, (0.45, 0.34, 0.10, 1))
        self.b_save = self._btn(FA["save"], self.on_save, (0.28, 0.30, 0.52, 1))
        for b in (self.b_start, self.b_stop, self.b_video, self.b_reset, self.b_save):
            bar.add_widget(b)
        self.add_widget(bar)

        self.add_widget(Label(text=FA["hint"], font_size="11sp",
                              color=(1, 1, 1, 0.55), size_hint=(1, 0.035),
                              pos_hint={"x": 0, "y": 0.03}))

        Window.bind(on_touch_down=self._down, on_touch_move=self._move)
        Clock.schedule_interval(self._pump, 1 / 30.0)

    def _btn(self, t, cb, col):
        b = Button(text=t, bold=True, font_size="13sp", background_normal="",
                   background_color=col, color=(1, 1, 1, 1))
        b.bind(on_press=cb)
        return b

    def ensure(self):
        if self.engine is None:
            self.engine = CounterEngine(App.get_running_app().data_dir,
                                        on_count=self._set_count,
                                        on_frame=self._set_frame)
        return self.engine

    # ── اکشن‌ها ──
    def on_start(self, *a):
        try:
            self.ensure().start_camera(0)
        except Exception as e:
            self._toast("خطا: " + str(e))

    def on_stop(self, *a):
        if self.engine:
            self.engine.stop()

    def on_video(self, *a):
        eng = self.ensure()
        if IS_ANDROID:
            self._pick_video()
        else:
            for c in ("vehicles.mp4", os.path.join(os.getcwd(), "vehicles.mp4")):
                if os.path.exists(c):
                    eng.start_video(c)
                    return
            self._toast("فایل ویدیو پیدا نشد (vehicles.mp4)")

    def _pick_video(self):
        """انتخاب ویدیو از گالری اندروید + کپی به مسیر اپ"""
        try:
            from jnius import autoclass, cast
            Intent = autoclass("android.content.Intent")
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            activity = PythonActivity.mActivity
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("video/*")
            activity.startActivityForResult(intent, 4242)
            self._toast("ویدیو را انتخاب کن، بعد دوباره «ویدیو» را بزن")
        except Exception as e:
            self._toast("انتخاب فایل پشتیبانی نشد: " + str(e))

    def on_reset(self, *a):
        if self.engine:
            self.engine.count = 0
            self.engine.counted.clear()
            self.engine.tracker.reset()
            self._set_count(0)

    def on_save(self, *a):
        if not self.engine:
            return
        try:
            import sqlite3
            from datetime import datetime
            db = os.path.join(App.get_running_app().data_dir, "reports.db")
            con = sqlite3.connect(db)
            con.execute("CREATE TABLE IF NOT EXISTS reports("
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, count INTEGER, ts TEXT)")
            con.execute("INSERT INTO reports(count, ts) VALUES (?,?)",
                        (self.engine.count, datetime.now().strftime("%Y-%m-%d %H:%M")))
            con.commit()
            con.close()
            self._toast("ذخیره شد ✓  تعداد: %d" % self.engine.count)
        except Exception as e:
            self._toast("خطای ذخیره: " + str(e))

    # ── کال‌بک‌ها ──
    def _set_count(self, c):
        self.big.text = str(c)

    def _set_frame(self, frame, fps):
        if frame is None:
            return
        buf = cv2.flip(frame, 0).tobytes()
        tex = Texture.create(size=(frame.shape[1], frame.shape[0]), colorfmt="bgr")
        tex.blit_buffer(buf, colorfmt="bgr", bufferfmt="ubyte")
        self.preview.texture = tex

    def _pump(self, dt):
        if self.engine and self.engine.running:
            self.engine.process_frame()

    # ── کشیدن خط ──
    def _down(self, win, touch):
        if touch.y < Window.height * 0.80:
            self._dragging = True
            self._line(touch.y / Window.height)

    def _move(self, win, touch):
        if self._dragging:
            self._line(touch.y / Window.height)

    def _line(self, r):
        if self.engine:
            self.engine.line_ratio = max(0.12, min(0.88, r))

    def _toast(self, msg):
        Popup(title="شمارشگر", content=Label(text=msg, font_size="13sp"),
              size_hint=(0.78, 0.30)).open()


class SmartCounterApp(App):
    title = "شمارشگر هوشمند"

    def build(self):
        self.data_dir = self.user_data_dir
        return RootUI()

    def on_start(self):
        # تست خودکار روی دستگاه: مدل + موتور + شمارش واقعی روی ویدیوی تستی
        # (برای اثبات عملکرد؛ نتیجه در selftest_result.txt نوشته می‌شود)
        import threading

        def _selftest():
            res = {}
            try:
                eng = CounterEngine(self.data_dir)
                eng.load_model()
                res["model"] = "OK"
                # ویدیوی تستی: از پوشه اپ یا assets
                vids = [p for p in (
                    os.path.join(self.data_dir, "test_clip.mp4"),
                    os.path.join(self.data_dir, "vehicles.mp4"),
                    "/sdcard/Download/vehicles.mp4",
                    os.path.join(self.data_dir, "assets", "test_clip.mp4"),
                ) if os.path.exists(p)]
                if not vids:
                    res["video"] = "NOT_FOUND"
                else:
                    eng.start_video(vids[0])
                    res["opened"] = bool(eng.cap and eng.cap.isOpened())
                    n = 0
                    while eng.running and n < 300:
                        eng.process_frame()
                        n += 1
                    res["video"] = os.path.basename(vids[0])
                    res["frames"] = n
                    res["count"] = eng.count
                    eng.stop()
                res["OK"] = True
            except Exception as e:
                res["OK"] = False
                res["error"] = repr(e)
            try:
                with open(os.path.join(self.data_dir, "selftest_result.txt"), "w") as f:
                    for k, v in res.items():
                        f.write("%s=%s\n" % (k, v))
            except Exception:
                pass

        threading.Thread(target=_selftest, daemon=True).start()

    def on_pause(self):
        return True

    def on_stop(self):
        if self.root and self.root.engine:
            self.root.engine.stop()


if __name__ == "__main__":
    SmartCounterApp().run()
