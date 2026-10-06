"""Unit tests for engine modules — run: python3 test/test_counter.py"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import numpy as np
import cv2

from engine.tracker import IouTracker
from engine.counter import ConveyorCounter
from engine.detector import YOLODetector


def test_tracker_basic():
    t = IouTracker(iou_thresh=0.3, max_lost=5)
    boxes = [(10,10,50,50), (100,100,150,150)]
    r1 = t.update(boxes)
    assert len(r1) == 2, f"expected 2, got {len(r1)}"
    r2 = t.update(boxes)
    assert set(r1.keys()) == set(r2.keys())
    assert all(tr["hits"] >= 2 for tr in t.tracks.values())
    print("[tracker] basic OK")

def test_tracker_lost_expire():
    t = IouTracker(iou_thresh=0.3, max_lost=2)
    t.update([(10,10,50,50)])
    for _ in range(10):
        t.update([])
    assert len(t.tracks) == 0
    print("[tracker] expire OK")

def test_counter_first_frame_no_count():
    """Object starting above line should NOT count on first frame."""
    c = ConveyorCounter(line_ratio=0.5, min_hits=1)
    tracked = {1: {"box": (0, 400, 50, 450), "hits": 2}}  # cy=425 < 500
    total, dirs = c.update(tracked, 1000)
    assert total == 0, f"expected 0, got {total}"
    print("[counter] first-frame-no-count OK")

def test_counter_crossing():
    """Object crossing from above to below → count 1."""
    c = ConveyorCounter(line_ratio=0.5, min_hits=1)
    tracked = {1: {"box": (0, 400, 50, 450), "hits": 2}}  # above
    c.update(tracked, 1000)  # record side
    tracked[1]["box"] = (0, 550, 50, 600)  # below
    total, dirs = c.update(tracked, 1000)
    assert total == 1 and dirs["down"] == 1, f"got {total} {dirs}"
    print("[counter] crossing OK")

def test_counter_no_double():
    """Same box staying below line → count only once."""
    c = ConveyorCounter(line_ratio=0.5, min_hits=1)
    tracked = {1: {"box": (0, 550, 50, 600), "hits": 2}}
    c.update(tracked, 1000)  # first: records side='below'
    total, _ = c.update(tracked, 1000)  # second: side didn't change
    assert total == 0, f"expected 0, got {total}"
    print("[counter] no-double OK")

def test_counter_both_directions():
    """Item going up then another going down."""
    c = ConveyorCounter(line_ratio=0.5, min_hits=1)
    # item 1: above → below (down)
    t1 = {1: {"box": (0, 400, 50, 450), "hits": 2}}
    c.update(t1, 1000)
    t1[1]["box"] = (0, 550, 50, 600)
    c.update(t1, 1000)
    # item 2: below → above (up)
    t2 = {2: {"box": (0, 550, 50, 600), "hits": 2}}
    c.update(t2, 1000)
    t2[2]["box"] = (0, 400, 50, 450)
    c.update(t2, 1000)
    assert c.count == 2 and c.count_down == 1 and c.count_up == 1
    print("[counter] both-directions OK")

def test_detector_import():
    d = YOLODetector(conf=0.3, nms=0.4)
    assert d.net is None
    print("[detector] import OK")

if __name__ == "__main__":
    test_tracker_basic()
    test_tracker_lost_expire()
    test_counter_first_frame_no_count()
    test_counter_crossing()
    test_counter_no_double()
    test_counter_both_directions()
    test_detector_import()
    print("\n✅ ALL TESTS PASSED")