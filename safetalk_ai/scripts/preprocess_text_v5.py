import pandas as pd
import re
from pathlib import Path

# Resolve base_dir dynamically relative to script location
base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    """
    Standard text cleaning pipeline used across all versions.
    """
    if not isinstance(text, str):
        return ""
    
    # 1. Convert to lowercase
    text = text.lower()
    
    # 2. Remove URLs
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    
    # 3. Remove email addresses
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    
    # 4. Remove phone numbers (simple pattern for mobile digits)
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    
    # 5. Remove punctuation (retaining words)
    text = re.sub(r'[^\w\s]', ' ', text)
    
    # 6. Remove extra whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

def run_preprocessing_v5():
    input_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v5.csv"
    output_path = base_dir / "data" / "processed" / "messages_cleaned_v5.csv"
    
    if not input_path.exists():
        print(f"Error: Unified dataset V5 not found at {input_path}")
        return

    print(f"Loading dataset from: {input_path}")
    df = pd.read_csv(input_path)
    
    print("Applying text cleaning...")
    df['clean_text'] = df['text'].apply(clean_text)
    
    # Remove any rows that became empty after cleaning
    initial_len = len(df)
    df = df[df['clean_text'].str.strip() != ""]
    removed_len = initial_len - len(df)
    
    print(f"Removed {removed_len} empty rows after cleaning.")
    
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False, encoding='utf-8')
    
    print(f"Preprocessed dataset saved to: {output_path}")
    print(f"Final usable rows: {len(df)}")

if __name__ == "__main__":
    run_preprocessing_v5()
