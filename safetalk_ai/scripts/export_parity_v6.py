import joblib
import json
import numpy as np
from pathlib import Path

# Resolve base directory
base_dir = Path("d:/SafeTalk/safetalk_ai")

def export_full_parity_v6():
    vec_path = base_dir / "models" / "tfidf_vectorizer_v6.pkl"
    clf_path = base_dir / "models" / "safetalk_message_classifier_v6.pkl"
    
    if not vec_path.exists() or not clf_path.exists():
        print("Model V6 artifacts not found!")
        return
        
    vectorizer = joblib.load(vec_path)
    model = joblib.load(clf_path)
    
    # 1. Export IDF weights
    # TfidfVectorizer.idf_ attribute contains the IDF vector
    idf_weights = vectorizer.idf_.tolist()
    
    # 2. Export Vocabulary (Word -> Index)
    vocabulary = {str(k): int(v) for k, v in vectorizer.vocabulary_.items()}
    
    # 3. Export Mapping for XAI (Index -> Weight)
    # The coefficients are for the 'scam' class
    # model.coef_ is [1, 9000]
    weights = model.coef_[0].tolist()
    
    # 4. Packaging for Android
    # We'll create a single "model_metadata_v6.json" for the vectorizer properties
    metadata = {
        "version": "v6",
        "ngram_range": vectorizer.ngram_range,
        "norm": vectorizer.norm,
        "use_idf": vectorizer.use_idf,
        "smooth_idf": vectorizer.smooth_idf,
        "sublinear_tf": vectorizer.sublinear_tf
    }
    
    # Save files
    model_data_dir = base_dir / "models"
    
    with open(model_data_dir / "tfidf_vocabulary_v6.json", "w", encoding="utf-8") as f:
        json.dump(vocabulary, f, ensure_ascii=False)
        
    with open(model_data_dir / "tfidf_idf_v6.json", "w", encoding="utf-8") as f:
        json.dump(idf_weights, f)
        
    with open(model_data_dir / "xai_weights_v6.json", "w", encoding="utf-8") as f:
        json.dump(weights, f)
        
    with open(model_data_dir / "model_metadata_v6.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f)
        
    print("Full parity export complete.")
    print(f"Vocabulary: {len(vocabulary)} terms")
    print(f"IDF Weights: {len(idf_weights)} entries")
    print(f"XAI Weights: {len(weights)} entries")

if __name__ == "__main__":
    export_full_parity_v6()
