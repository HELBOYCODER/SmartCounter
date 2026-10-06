"""Kivy UI — Farsi-friendly, clean layout."""
import os
import cv2
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
    "title": "شمارشگر صنعتی",
    "start": "▶ شروع",
    "stop": "■ توقف",
    "video": "🎬 ویدیو",
    "reset": "↺ صفر",
    "save": "💾 ذخیره",
    "export": "📤 CSV",
    "line_up": "خط ↑",
    "line_down": "خط ↓",
}


class CounterScreen(FloatLayout):
    def __init__(self, pipeline, **kw):
        super().__init__(**kw)
        self.pipeline = pipeline
        self._dragging = False
        self._tex = None          # reused texture
        self._last_count = -1

        # preview
        self.preview = Image(
            size_hint=(1, 0.80), pos_hint={"x": 0, "top": 1},
            allow_stretch=True, keep_ratio=True,
        )
        self.add_widget(self.preview)

        # big count
        self.big = Label(
            text="0", font_size="80sp", bold=True,
            color=(1, 0.66, 0.12, 1),
            outline_width=3, outline_color=(0, 0, 0, 0.85),
            size_hint=(0.4, 0.12),
            pos_hint={"center_x": 0.5, "top": 0.985},
        )
        self.add_widget(self.big)

        # direction label
        self.dir_lbl = Label(
            text="↑0 ↓0", font_size="14sp",
            color=(1, 1, 1, 0.85),
            size_hint=(0.5, 0.04),
            pos_hint={"center_x": 0.5, "top": 0.895},
        )
        self.add_widget(self.dir_lbl)

        # button bar
        bar = BoxLayout(
            size_hint=(1, 0.13), pos_hint={"x": 0, "y": 0.065},
            spacing=dp(4), padding=[dp(6), dp(4)],
        )
        colors = [
            (0.10, 0.62, 0.30, 1),   # start green
            (0.62, 0.16, 0.14, 1),   # stop red
            (0.15, 0.34, 0.55, 1),   # video blue
            (0.45, 0.34, 0.10, 1),   # reset amber
            (0.28, 0.30, 0.52, 1),   # save purple
            (0.40, 0.40, 0.40, 1),   # export gray
        ]
        btns = [FA["start"], FA["stop"], FA["video"],
                FA["reset"], FA["save"], FA["export"]]
        cbs = [self.on_start, self.on_stop, self.on_video,
               self.on_reset, self.on_save, self.on_export]
        for txt, col, cb in zip(btns, colors, cbs):
            b = Button(
                text=txt, bold=True, font_size="12sp",
                background_normal="", background_color=col,
                color=(1, 1, 1, 1),
            )
            b.bind(on_press=cb)
            bar.add_widget(b)
        self.add_widget(bar)

        # hint
        self.add_widget(Label(
            text="خط قرمز ← بکش · دکمه LINE ↑↓ ← جابه‌جا کن",
            font_size="11sp", color=(1, 1, 1, 0.5),
            size_hint=(1, 0.03), pos_hint={"x": 0, "y": 0.015},
        ))

        Window.bind(on_touch_down=self._down, on_touch_move=self._move)
        Clock.schedule_interval(self._pump, 1 / 30.0)

    # ---- buttons ----
    def on_start(self, *a):
        try:
            self.pipeline.start_camera(0)
        except Exception as e:
            self._toast("دوربین: " + str(e))

    def on_stop(self, *a):
        self.pipeline.stop()

    def on_video(self, *a):
        if IS_ANDROID:
            self._pick_video()
        else:
            self._open_local_video()

    def _pick_video(self):
        try:
            from jnius import autoclass
            Intent = autoclass("android.content.Intent")
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            activity = PythonActivity.mActivity
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.setType("video/*")
            # register result receiver
            activity.registerForActivityResult(
                autoclass("androidx.activity.result.contract"
                          ".ActivityResultContracts$OpenDocument"),
                self._on_video_result,
            ).launch(intent)
        except Exception as e:
            self._toast("فایل‌منیج: " + str(e))

    def _on_video_result(self, uri):
        """Called when user picks a video."""
        if uri is None:
            return
        try:
            from jnius import autoclass
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            activity = PythonActivity.mActivity
            uri_str = uri.toString()
            # copy uri to app dir for cv2.VideoCapture compatibility
            import os, shutil
            dst = os.path.join(activity.getFilesDir(), "clip.mp4")
            # use content resolver to open input stream
            content = activity.getContentResolver()
            inp = content.openInputStream(uri)
            with open(dst, "wb") as f:
                shutil.copyfileobj(inp, f)
            inp.close()
            self.pipeline.start_video(dst)
            self._toast("ویدیو بارگذاری ✓")
        except Exception as e:
            self._toast("خطای ویدیو: " + str(e))

    def _open_local_video(self):
        import os
        for c in ("vehicles.mp4", "test.mp4"):
            p = os.path.join(os.getcwd(), c)
            if os.path.exists(p):
                self.pipeline.start_video(p)
                return
        self._toast("فایل ویدیو پیدا نشد")

    def on_reset(self, *a):
        self.pipeline.counter.reset()
        self.pipeline.tracker.reset()
        self.big.text = "0"
        self.dir_lbl.text = "↑0 ↓0"

    def on_save(self, *a):
        try:
            from reports.store import ReportStore
            app = App.get_running_app()
            store = ReportStore(os.path.join(app.data_dir, "reports.db"))
            c = self.pipeline.counter
            store.save(c.count, c.count_up, c.count_down)
            self._toast("ذخیره ✓  %d" % c.count)
        except Exception as e:
            self._toast("ذخیره خطا: " + str(e))

    def on_export(self, *a):
        try:
            import os
            from reports.store import ReportStore
            app = App.get_running_app()
            store = ReportStore(os.path.join(app.data_dir, "reports.db"))
            path = os.path.join(app.data_dir, "reports.csv")
            store.export_csv(path)
            self._toast("CSV ✓ " + path)
        except Exception as e:
            self._toast("CSV خطا: " + str(e))

    # ---- callbacks from pipeline ----
    def _on_count(self, count, dirs):
        self.big.text = str(count)
        self.dir_lbl.text = "↑%d ↓%d" % (dirs.get("up", 0),
                                         dirs.get("down", 0))
        self._last_count = count

    def _on_frame(self, frame, fps):
        if frame is None:
            return
        # reuse texture
        h, w = frame.shape[:2]
        if self._tex is None or self._tex.width != w or self._tex.height != h:
            self._tex = Texture.create(size=(w, h), colorfmt="bgr")
        buf = cv2.flip(frame, 0).tobytes()
        self._tex.blit_buffer(buf, colorfmt="bgr", bufferfmt="ubyte")
        self.preview.texture = self._tex

    # ---- touch line ----
    def _down(self, win, touch):
        if touch.y < Window.height * 0.82:
            self._dragging = True
            self._line(touch.y / Window.height)

    def _move(self, win, touch):
        if self._dragging:
            self._line(touch.y / Window.height)

    def _line(self, r):
        self.pipeline.counter.set_line(r)

    # ---- pump ----
    def _pump(self, dt):
        if self.pipeline.running:
            self.pipeline.process_frame()

    def _toast(self, msg):
        Popup(title="شمارشگر",
              content=Label(text=msg, font_size="13sp"),
              size_hint=(0.78, 0.30)).open()


