"""IOU-based tracker — no external deps (replaces ByteTrack without torch)."""


class IouTracker:
    """Simple but effective: match detections to tracks by IoU,
    expire lost tracks, create new ones."""

    def __init__(self, iou_thresh=0.3, max_lost=30):
        self.iou_thresh = iou_thresh
        self.max_lost = max_lost
        self.tracks = {}    # id -> {"box": (x1,y1,x2,y2), "lost": int, "hits": int}
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
        """boxes: list of (x1,y1,x2,y2). Returns {id: box}."""
        assigned = {}
        used = set()

        # match existing tracks
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
                self.tracks[tid]["hits"] += 1
                assigned[tid] = boxes[best]
            else:
                self.tracks[tid]["lost"] += 1

        # expire lost
        for tid in [t for t, tr in self.tracks.items() if tr["lost"] > self.max_lost]:
            del self.tracks[tid]

        # new tracks for unmatched boxes (only if they've been seen >= 2 frames)
        for i, b in enumerate(boxes):
            if i not in used:
                tid = self.next_id
                self.next_id += 1
                self.tracks[tid] = {"box": b, "lost": 0, "hits": 1}
                assigned[tid] = b
        return assigned

    def get_active(self):
        """Return tracks with hits >= 2 (stable)."""
        return {
            tid: tr for tid, tr in self.tracks.items() if tr["hits"] >= 2
        }

    def reset(self):
        self.tracks.clear()
        self.next_id = 1

    def __len__(self):
        return len(self.tracks)