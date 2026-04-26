import joblib
import numpy as np
from pathlib import Path
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def export_onnx_v7():
    clf_path = base_dir / "models" / "safetalk_message_classifier_v7.pkl"
    onnx_path = base_dir / "models" / "safetalk_classifier_v7.onnx"
    
    if not clf_path.exists():
        print(f"Error: V7 classifier not found at {clf_path}")
        return

    print(f"Loading V7 classifier: {clf_path}")
    model = joblib.load(clf_path)
    
    # Identify number of features
    # Based on train_model_v7.py, it should be 10000
    n_features = model.n_features_in_
    print(f"Model expects {n_features} features.")

    # Define input type
    initial_type = [('float_input', FloatTensorType([None, n_features]))]
    
    print("Converting to ONNX...")
    # options ensure we get probabilities in a specific format
    options = {id(model): {'zipmap': False}} 
    onx = convert_sklearn(model, initial_types=initial_type, options=options, target_opset=12)
    
    with open(onnx_path, "wb") as f:
        f.write(onx.SerializeToString())
        
    print(f"ONNX model saved to: {onnx_path}")

if __name__ == "__main__":
    export_onnx_v7()
