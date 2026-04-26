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

def preprocess_v2():
    input_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v2.csv"
    output_path = base_dir / "data" / "processed" / "messages_cleaned_v2.csv"
    
    df = pd.read_csv(input_path)
    df['clean_text'] = df['text'].apply(clean_text)
    
    # Remove rows that became empty after cleaning
    df = df[df['clean_text'].str.strip() != ""]
    
    df.to_csv(output_path, index=False, encoding='utf-8')
    
    print(f"Preprocessed {len(df)} rows and saved to {output_path}")

if __name__ == "__main__":
    preprocess_v2()
