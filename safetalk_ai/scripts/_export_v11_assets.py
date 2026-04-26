import joblib
import json
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]
models_dir = base_dir / "models" / "v11"

# Load Python Artifacts
vectorizer = joblib.load(models_dir / "tfidf_vectorizer.pkl")
scaler = joblib.load(models_dir / "structured_scaler.pkl")

# Extract parameters
vocab = {k: int(v) for k, v in vectorizer.vocabulary_.items()}
idf = vectorizer.idf_.tolist()
max_abs = scaler.max_abs_.tolist() 
scale = scaler.scale_.tolist() 

tfidf_dim = len(vocab)
struct_dim = len(max_abs)
total_dim = tfidf_dim + struct_dim

metadata = {
    "version": "v11",
    "tfidf_features": tfidf_dim,
    "structured_features": struct_dim,
    "total_features": total_dim,
    "scaler_max_abs": max_abs,
    "scaler_scale": scale
}

# Save Android JSONs
with open(models_dir / "v11_tfidf_vocabulary.json", "w", encoding="utf-8") as f:
    json.dump(vocab, f, ensure_ascii=False)

with open(models_dir / "v11_tfidf_idf.json", "w") as f:
    json.dump(idf, f)

with open(models_dir / "v11_metadata.json", "w") as f:
    json.dump(metadata, f, indent=4)

print("Exported JSON artifacts for V11 successfully.")
