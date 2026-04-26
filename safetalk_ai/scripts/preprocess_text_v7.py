import re
import pandas as pd
from pathlib import Path

def preprocess_v7(text):
    if not isinstance(text, str):
        return ""
    
    # Lowercase
    text = text.lower()
    
    # Protect links by adding spaces around them
    # This ensures they are treated as distinct tokens by TF-IDF
    text = re.sub(r'(https?://\S+)', r' \1 ', text)
    
    # Protect file extensions common in scams (.apk, .exe, .scr, etc.)
    # We do this by ensuring they are not stripped or merged
    extensions = ['.apk', '.exe', '.scr', '.msi', '.bat', '.zip', '.rar']
    for ext in extensions:
        if ext in text:
            text = text.replace(ext, f' {ext} ')

    # Normalize whitespace but keep basic structural punctuation
    # We remove emojis and weird symbols but keep . / : for technical context
    text = re.sub(r'[^\w\s\.\/:]', ' ', text)
    
    # Normalize multiple spaces
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

if __name__ == "__main__":
    base_dir = Path(__file__).resolve().parents[1]
    input_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7.csv"
    output_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7_cleaned.csv"
    
    if input_path.exists():
        df = pd.read_csv(input_path)
        print("Cleaning text for V7 (Preserving scam signals)...")
        # Ensure 'text' column exists and is string
        df['text'] = df['text'].astype(str)
        df['clean_text'] = df['text'].apply(preprocess_v7)
        df.to_csv(output_path, index=False)
        print(f"Preprocessed dataset saved to: {output_path}")
    else:
        print(f"Error: Original V7 dataset not found at {input_path}")
