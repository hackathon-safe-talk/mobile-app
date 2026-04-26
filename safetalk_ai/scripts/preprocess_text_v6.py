import pandas as pd
import re
from pathlib import Path

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    if not isinstance(text, str):
        return ""
    
    # Lowercase
    text = text.lower()
    # Remove URLs
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    # Remove emails
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    # Remove phone numbers
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    # Remove punctuation
    text = re.sub(r'[^\w\s]', ' ', text)
    # Trim spaces
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

def preprocess_v6():
    in_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v6.csv"
    out_path = base_dir / "data" / "processed" / "messages_cleaned_v6.csv"
    
    if not in_path.exists():
        print(f"Error: Dataset V6 not found at {in_path}")
        return

    print(f"Loading dataset for cleaning: {in_path}")
    df = pd.read_csv(in_path)
    
    initial_rows = len(df)
    
    print("Applying text cleaning pipeline...")
    df['clean_text'] = df['text'].apply(clean_text)
    
    # Remove rows that became empty after cleaning
    df = df[df['clean_text'].str.strip() != ""]
    final_rows = len(df)
    removed_rows = initial_rows - final_rows
    
    # Save
    out_path.parent.mkdir(parents=True, exist_ok=True)
    df[['text', 'source', 'label', 'clean_text']].to_csv(out_path, index=False, encoding='utf-8')
    
    print("\n=== Text Preprocessing V6 ===")
    print(f"Number of rows processed: {initial_rows}")
    print(f"Rows removed (empty after cleaning): {removed_rows}")
    print(f"Final dataset size: {final_rows}")
    print(f"Cleaned dataset saved to: {out_path}")

if __name__ == "__main__":
    preprocess_v6()
