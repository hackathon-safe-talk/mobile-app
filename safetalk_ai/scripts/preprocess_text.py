import pandas as pd
import re
from pathlib import Path

# Resolve base_dir dynamically relative to script location
base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    if not isinstance(text, str):
        return ""
    
    # convert text to lowercase
    text = text.lower()
    
    # remove URLs (http, https, www links)
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    
    # remove email addresses
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    
    # remove phone numbers (e.g. 08002986030 or formatted with +, -, spaces, parentheses)
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    
    # remove punctuation and emojis. \w keeps alphanumeric and _, \s keeps whitespace
    # This also naturally retains standard Uzbek/Latin characters while dropping emojis/symbols
    text = re.sub(r'[^\w\s]', ' ', text)
    
    # remove extra whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

def main():
    input_path = base_dir / "data" / "final" / "safetalk_unified_dataset.csv"
    output_path = base_dir / "data" / "processed" / "messages_cleaned.csv"
    
    print(f"Loading dataset from: {input_path}")
    if not input_path.exists():
        print("Input dataset not found. Please run the build_unified_dataset.py script first.")
        return

    df = pd.read_csv(input_path)
    
    # Safety Checks
    print("Running safety checks...")
    required_columns = ['text', 'source', 'label']
    for col in required_columns:
        if col not in df.columns:
            print(f"WARNING: Required column missing: '{col}'")
            
    if not df['label'].dropna().isin(['ham', 'scam']).all():
        print("WARNING: Invalid labels found! Only 'ham' or 'scam' expected.")
        
    if not df['source'].dropna().isin(['sms', 'telegram']).all():
        print("WARNING: Invalid sources found! Only 'sms' or 'telegram' expected.")
        
    # Apply cleaning
    print("Applying text preprocessing...")
    df['clean_text'] = df['text'].apply(clean_text)
    
    # Calculate drops
    initial_rows = len(df)
    
    # Remove empty results
    df = df[df['clean_text'] != ""]
    
    removed_empty_rows = initial_rows - len(df)
    
    # Retain layout
    df = df[['text', 'source', 'label', 'clean_text']]
    
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False, encoding='utf-8')
    
    # Print statistics
    print("\n=== Preprocessing Statistics ===")
    print(f"Total rows processed: {len(df)}")
    print(f"Rows removed because of empty clean_text: {removed_empty_rows}")
    
    print("\nLabel distribution:")
    print(df['label'].value_counts().to_string())
    
    print("\nSource distribution:")
    print(df['source'].value_counts().to_string())
    
    print("\nFirst 10 cleaned samples:")
    safe_head = df.head(10).to_string(index=False)
    print(safe_head.encode('ascii', 'replace').decode('ascii'))
    
    print(f"\nSaved cleaned dataset to: {output_path}")

if __name__ == "__main__":
    main()
