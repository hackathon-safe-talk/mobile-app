import pandas as pd
import re
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    if not isinstance(text, str): return ""
    text = text.lower()
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    text = re.sub(r'[^\w\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def preprocess_v3():
    in_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v3.csv"
    out_path = base_dir / "data" / "processed" / "messages_cleaned_v3.csv"
    
    df = pd.read_csv(in_path)
    df['clean_text'] = df['text'].apply(clean_text)
    
    df = df[df['clean_text'].str.strip() != ""]
    df.to_csv(out_path, index=False, encoding='utf-8')
    print(f"Preprocessed V3 -> {len(df)} rows.")

if __name__ == "__main__":
    preprocess_v3()
