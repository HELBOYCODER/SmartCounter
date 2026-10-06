"""YOLOv8 ONNX detector via OpenCV DNN — no PyTorch on device."""
import os
import numpy as np
import cv2


class YOLODetector:
    def __init__(self, model_path=None, conf=0.35, nms=0.45, imgsz=640,
                 class_filter=None):
        self.conf = conf
        self.nms = nms
        self.imgsz = imgsz
        self.class_filter = class_filter or []  # empty = all classes
        self.net = None
        self.model_path = model_path
        self.class_names = None

    def load(self):
        path = self.model_path
        if not path or not os.path.exists(path):
            # search common locations
            here = os.path.dirname(os.path.abspath(__file__))
            candidates = [
                os.path.join(here, "..", "assets", "model.onnx"),
                os.path.join(here, "..", "assets", "yolov8n.onnx"),
                os.path.join(os.getcwd(), "assets", "model.onnx"),
                "/sdcard/Download/model.onnx",
            ]
            path = next((p for p in candidates if os.path.exists(p)), None)
        if not path:
            raise FileNotFoundError(
                "model.onnx not found — place in assets/ or set model_path"
            )
        self.net = cv2.dnn.readNetFromONNX(path)
        self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
        self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
        return self

    def detect(self, frame):
        """Returns list of (x1, y1, x2, y2, conf, class_id)."""
        if self.net is None:
            self.load()
        h, w = frame.shape[:2]
        blob = cv2.dnn.blobFromImage(
            frame, 1 / 255.0, (self.imgsz, self.imgsz),
            swapRB=True, crop=False
        )
        self.net.setInput(blob)
        out = self.net.forward()           # (1, 84, 8400)
        out = np.squeeze(out).T            # (8400, 84)

        scores = out[:, 4:]
        cls_ids = scores.argmax(1)
        confs = scores[np.arange(len(scores)), cls_ids]
        keep = confs > self.conf
        out, cls_ids, confs = out[keep], cls_ids[keep], confs[keep]
        if len(out) == 0:
            return []

        cx, cy, bw, bh = out[:, 0], out[:, 1], out[:, 2], out[:, 3]
        x1 = np.clip((cx - bw / 2) * w / self.imgsz, 0, w)
        y1 = np.clip((cy - bh / 2) * h / self.imgsz, 0, h)
        x2 = np.clip((cx + bw / 2) * w / self.imgsz, 0, w)
        y2 = np.clip((cy + bh / 2) * h / self.imgsz, 0, h)
        boxes = np.stack([x1, y1, x2, y2], 1)

        # class filter
        if self.class_filter:
            mask = np.isin(cls_ids, self.class_filter)
            boxes, confs, cls_ids = boxes[mask], confs[mask], cls_ids[mask]
            if len(boxes) == 0:
                return []

        idx = cv2.dnn.NMSBoxes(
            boxes.tolist(), confs.tolist(), self.conf, self.nms
        )
        if len(idx) == 0:
            return []
        idx = np.array(idx).flatten()
        return [
            (tuple(map(int, boxes[i])), float(confs[i]), int(cls_ids[i]))
            for i in idx
        ]