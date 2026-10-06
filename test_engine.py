# -*- coding: utf-8 -*-
"""تست موتور (OpenCV DNN + ONNX + IouTracker) روی ویدیوی واقعی — همان منطق اپ اندروید."""
import os
import sys
import numpy as np
import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from main import CounterEngine  # noqa: E402


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    vid = os.path.join(here, "vehicles.mp4")
    assert os.path.exists(vid), "vehicles.mp4 missing"

    eng = CounterEngine(here, on_count=lambda c: None, on_frame=lambda f, s: None)
    eng.start_video(vid)
    print("[1] engine started with ONNX:", os.path.exists(os.path.join(here, "yolov8n.onnx")))

    frames, final = 0, 0
    while eng.running and frames < 500:
        eng.process_frame()
        frames += 1
        final = eng.count
    eng.stop()
    print(f"[2] frames={frames} count={final} unique={len(eng.counted)}")
    assert final > 0, "counter must register crossings"
    print("[3] ONNX DNN ENGINE OK — no PyTorch needed on device")


if __name__ == "__main__":
    main()
