import joblib
import json
from pathlib import Path

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def export_metadata_v9():
    vec_path = base_dir / "models" / "tfidf_vectorizer_v9.pkl"
    clf_path = base_dir / "models" / "safetalk_message_classifier_v9.pkl"
    
    if not vec_path.exists() or not clf_path.exists():
        print("Required artifacts not found!")
        return
        
    vectorizer = joblib.load(vec_path)
    model = joblib.load(clf_path)
    
    # 1. Export IDF weights
    idf_weights = vectorizer.idf_.tolist()
    
    # 2. Export Vocabulary (Word -> Index)
    vocabulary = {str(k): int(v) for k, v in vectorizer.vocabulary_.items()}
    
    # 3. Export Classes
    class_order = model.classes_.tolist()
    class_map = {int(i): str(label) for i, label in enumerate(class_order)}
    
    # 4. Extract XAI Weights (Coefficients for DANGEROUS class)
    # Multiclass check: find index of 'DANGEROUS'
    try:
        danger_idx = class_order.index('DANGEROUS')
        weights = model.coef_[danger_idx].tolist()
    except ValueError:
        weights = model.coef_[0].tolist() # Fallback

    # 5. Packaging Metadata (aligned with TfidfVectorizerLite.kt expectations)
    metadata = {
        "modelVersion": "v9",
        "vectorizer_features": len(vocabulary),
        "class_order": class_order,
        "training_schema": "semantic_risk_v9",
        "ngram_range": vectorizer.ngram_range,
        "use_idf": vectorizer.use_idf,
        "smooth_idf": vectorizer.smooth_idf,
        "sublinear_tf": vectorizer.sublinear_tf
    }
    
    # Save files
    models_dir = base_dir / "models"
    
    with open(models_dir / "tfidf_vocabulary_v9.json", "w", encoding="utf-8") as f:
        json.dump(vocabulary, f, ensure_ascii=False)
        
    with open(models_dir / "tfidf_idf_v9.json", "w", encoding="utf-8") as f:
        json.dump(idf_weights, f)
        
    with open(models_dir / "class_map_v9.json", "w", encoding="utf-8") as f:
        json.dump(class_map, f)
        
    with open(models_dir / "xai_weights_v9.json", "w", encoding="utf-8") as f:
        json.dump(weights, f)
        
    with open(models_dir / "model_metadata_v9.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f)
        
    print("V9 Metadata export complete (including XAI weights).")
    print(f"Vocabulary terms: {len(vocabulary)}")
    print(f"Classes: {metadata['class_order']}")

if __name__ == "__main__":
    export_metadata_v9()
