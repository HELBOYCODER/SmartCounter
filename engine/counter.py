"""Conveyor belt line counter — counts items crossing a virtual line.

Tracks each object's side relative to the line; a count fires ONLY when
the centroid CROSSES the line (side changes). Each track ID counted once.
"""


class ConveyorCounter:
    def __init__(self, line_ratio=0.5, min_hits=2):
        self.line_ratio = line_ratio
        self.min_hits = min_hits
        self._side = {}     # tid -> 'above' | 'below'
        self.counted = set()  # tids already counted
        self.count = 0
        self.count_down = 0   # top→bottom (entry)
        self.count_up = 0     # bottom→top (exit)

    @staticmethod
    def _cy(box):
        _, y1, _, y2 = box
        return (y1 + y2) / 2.0

    def update(self, tracked, frame_h):
        line_y = self.line_ratio * frame_h

        for tid, obj in tracked.items():
            if obj.get("hits", 0) < self.min_hits:
                continue
            if tid in self.counted:
                continue

            side = "below" if self._cy(obj["box"]) >= line_y else "above"
            prev = self._side.get(tid)

            if prev is None:
                self._side[tid] = side
                continue

            if prev != side:
                # actual crossing!
                self.counted.add(tid)
                self.count += 1
                if side == "below":
                    self.count_down += 1
                else:
                    self.count_up += 1

        # cleanup disappeared tracks
        for tid in list(self._side):
            if tid not in tracked:
                del self._side[tid]

        return self.count, {"down": self.count_down, "up": self.count_up}

    def reset(self):
        self._side.clear()
        self.counted.clear()
        self.count = 0
        self.count_down = 0
        self.count_up = 0

    def set_line(self, ratio):
        self.line_ratio = max(0.1, min(0.9, ratio))