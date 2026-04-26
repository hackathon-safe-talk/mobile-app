import joblib
import numpy as np
from pathlib import Path
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
import json

base_dir = Path(__file__).resolve().parents[1]

def export_v9():
    clf_path = base_dir / "models" / "safetalk_message_classifier_v9.pkl"
    onnx_path = base_dir / "models" / "safetalk_classifier_v9.onnx"
    # Added metadata export for Android robustness
    meta_path = base_dir / "models" / "model_metadata_v9.json"
    
    if not clf_path.exists():
        print(f"Error: V9 classifier not found.")
        return

    print(f"Loading V9 classifier...")
    model = joblib.load(clf_path)
    
    n_features = model.n_features_in_
    print(f"Model classes: {model.classes_}")
    print(f"Features: {n_features}")

    initial_type = [('float_input', FloatTensorType([None, n_features]))]
    options = {id(model): {'zipmap': False}} 
    onx = convert_sklearn(model, initial_types=initial_type, options=options, target_opset=12)
    
    with open(onnx_path, "wb") as f:
        f.write(onx.SerializeToString())
        
    # Export class order metadata
    metadata = {
        "version": "v9",
        "class_order": model.classes_.tolist(),
        "n_features": n_features,
        "ngram_range": [1, 2] # From best experiment
    }
    with open(meta_path, "w") as f:
        json.dump(metadata, f, indent=4)

    print(f"V9 ONNX and Metadata exported successfully.")

if __name__ == "__main__":
    export_v9()
