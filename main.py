"""
SmartCounter — صنعتی شمارش محصولات روی خط انتقال (conveyor belt).
معماری مدولار: detector + tracker + counter + pipeline + UI + reports.
پشتیبانی: دوربین زنده، ویدیو، افقی/عمودی، CSV/SQLite گزارش.
"""
import os
import sys

from kivy.app import App
from kivy.utils import platform

# ---- engine ----
from engine.detector import YOLODetector
from engine.tracker import IouTracker
from engine.counter import ConveyorCounter
from engine.pipeline import CounterPipeline
from reports.store import ReportStore
from ui.screen import CounterScreen


class SmartCounterApp(App):
    title = "شمارشگر صنعتی"

    def build(self):
        self.data_dir = self.user_data_dir
        os.makedirs(self.data_dir, exist_ok=True)

        # detector (class_filter: [] = همه انواع)
        detector = YOLODetector(conf=0.35, nms=0.45)
        pipeline = CounterPipeline(
            detector,
            on_count=self._on_count,
            on_frame=self._on_frame,
        )
        self.store = ReportStore(os.path.join(self.data_dir, "reports.db"))
        self.screen = CounterScreen(pipeline)
        return self.screen

    def _on_count(self, count, dirs):
        # called every 10 frames — update UI
        pass  # UI pulls from pipeline.counter directly

    def _on_frame(self, frame, fps):
        pass

    def on_start(self):
        # auto selftest if model found
        import threading
        def _test():
            try:
                d = YOLODetector()
                d.load()
                with open(os.path.join(self.data_dir, "model_status.txt"), "w") as f:
                    f.write("OK\n")
            except Exception as e:
                with open(os.path.join(self.data_dir, "model_status.txt"), "w") as f:
                    f.write("MISSING: " + str(e) + "\n")
        threading.Thread(target=_test, daemon=True).start()

    def on_pause(self):
        if self.screen.pipeline.running:
            self.screen.pipeline.stop()
        return True

    def on_stop(self):
        if self.screen and self.screen.pipeline:
            self.screen.pipeline.stop()


def main():
    # desktop launch helper
    if "--selftest" in sys.argv:
        d = YOLODetector()
        try:
            d.load()
            print("[OK] model loaded")
        except Exception as e:
            print("[FAIL]", e)
            sys.exit(1)
        return
    SmartCounterApp().run()


if __name__ == "__main__":
    main()