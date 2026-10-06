"""Main pipeline — ties detector + tracker + counter together."""
import time
import cv2
import numpy as np

from engine.tracker import IouTracker
from engine.counter import ConveyorCounter


class CounterPipeline:
    def __init__(self, detector, on_count=None, on_frame=None, on_fps=None):
        self.detector = detector
        self.tracker = None   # created after first frame (knows frame size)
        self.counter = None
        self.on_count = on_count
        self.on_frame = on_frame
        self.on_fps = on_fps
        self.running = False
        self.cap = None
        self.fps = 0.0
        self._t0 = None
        self._frame_count = 0

    # ---- camera / video ----
    def start_camera(self, index=0, size=(640, 480)):
        self.stop()
        self.cap = cv2.VideoCapture(index)
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, size[0])
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, size[1])
        self._start()

    def start_video(self, path):
        self.stop()
        self.cap = cv2.VideoCapture(path)
        self._start()

    def _start(self):
        if self.detector.net is None:
            self.detector.load()
        self.tracker = IouTracker(iou_thresh=0.35, max_lost=25)
        self.counter = ConveyorCounter(line_ratio=0.55, hysteresis=0.05)
        self.fps = 0.0
        self._t0 = None
        self._frame_count = 0
        self.running = True

    def stop(self):
        self.running = False
        if self.cap is not None:
            self.cap.release()
            self.cap = None

    # ---- main loop ----
    def process_frame(self):
        if not self.running or self.cap is None:
            return False
        ret, frame = self.cap.read()
        if not ret:
            self.running = False
            if self.on_count:
                self.on_count(self.counter.count)
            return False

        t = time.time()
        if self._t0:
            dt = t - self._t0
            self.fps = 0.9 * self.fps + 0.1 / dt if dt > 0 else self.fps
        else:
            self.fps = 0.0
        self._t0 = t
        self._frame_count += 1

        # detect
        dets = self.detector.detect(frame)  # list of (box, conf, cls)
        boxes = [d[0] for d in dets]

        # track
        tracked_raw = self.tracker.update(boxes)
        tracked = {
            tid: {"box": tr["box"], "hits": tr["hits"]}
            for tid, tr in self.tracker.tracks.items()
            if tr["hits"] >= self.counter.min_hits
        }

        # count
        h, w = frame.shape[:2]
        total, dirs = self.counter.update(tracked, h)

        if self._frame_count % 10 == 0 and self.on_count:
            self.on_count(total, dirs)

        # draw & emit
        self._draw(frame, tracked, total, dirs)
        if self.on_frame:
            self.on_frame(frame, self.fps)
        return True

    def _draw(self, frame, tracked, total, dirs):
        h, w = frame.shape[:2]
        line_y = int(self.counter.line_ratio * h)

        # line
        cv2.line(frame, (0, line_y), (w, line_y), (0, 0, 255), 2)
        cv2.putText(frame, "LINE", (8, line_y - 6),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 255), 1)

        # boxes
        for tid, obj in tracked.items():
            x1, y1, x2, y2 = obj["box"]
            cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 230, 90), 2)
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
            cv2.circle(frame, (cx, cy), 4, (255, 120, 0), -1)
            cv2.putText(frame, f"#{tid}", (x1, max(16, y1 - 6)),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.55, (0, 230, 90), 2)

        # count
        cv2.putText(frame, str(total), (18, 70),
                    cv2.FONT_HERSHEY_SIMPLEX, 2.0, (0, 210, 255), 5)
        cv2.putText(frame, f"↑{dirs['up']} ↓{dirs['down']}", (18, 110),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (220, 220, 220), 2)
        cv2.putText(frame, f"{self.fps:.0f} FPS", (18, h - 14),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 1)


