"""Download a default YOLOv8n ONNX model for testing."""
import os, sys, urllib.request

URL = (
    "https://github.com/ultralytics/assets/releases/download/v8.2.0/"
    "yolov8n.onnx"
)

def main():
    here = os.path.dirname(os.path.abspath(__file__))
    assets = os.path.join(here, "assets")
    os.makedirs(assets, exist_ok=True)
    path = os.path.join(assets, "yolov8n.onnx")
    if os.path.exists(path):
        print(f"already exists: {path}")
        return
    print(f"downloading {URL} ...")
    urllib.request.urlretrieve(URL, path)
    print(f"saved → {path} ({os.path.getsize(path)//1024//1024} MB)")

if __name__ == "__main__":
    main()