"""End-to-end: real YOLOv8 ONNX + tracker + counter on a real image."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import cv2
from engine.detector import YOLODetector
from engine.tracker import IouTracker
from engine.counter import ConveyorCounter


def main():
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    model = os.path.join(here, "assets", "yolov8n.onnx")
    assert os.path.exists(model), f"model missing: {model}"

    d = YOLODetector(model_path=model)
    d.load()
    print("[1] model loaded")

    img = "/tmp/bus.jpg"
    assert os.path.exists(img), "download /tmp/bus.jpg first"
    frame = cv2.imread(img)
    dets = d.detect(frame)
    print(f"[2] detections: {len(dets)}")
    assert len(dets) > 0, "should detect objects in bus.jpg"

    # simulate frames: same detections → stable tracks, no false count
    tracker = IouTracker()
    counter = ConveyorCounter(line_ratio=0.5, min_hits=1)
    boxes = [b for b, _, _ in dets]
    for _ in range(5):
        raw = tracker.update(boxes)
        tracked = {
            tid: {"box": tracker.tracks[tid]["box"],
                  "hits": tracker.tracks[tid]["hits"]}
            for tid in raw
        }
        counter.update(tracked, frame.shape[0])
    print(f"[3] stable-frame count={counter.count} (should be 0-1, no double)")
    assert counter.count <= 1, "static objects should not multi-count!"

    print("[4] E2E OK")


if __name__ == "__main__":
    main()