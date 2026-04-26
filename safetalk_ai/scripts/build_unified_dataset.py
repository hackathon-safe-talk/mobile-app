import pandas as pd
import os
from pathlib import Path

# Resolve base_dir dynamically relative to script location
# __file__ is scripts/build_unified_dataset.py
base_dir = Path(__file__).resolve().parents[1]
sources_dir = base_dir / "data" / "sources"

def load_sms_spam_collection(filepath):
    if not filepath.exists():
        print(f"Skipping: {filepath} not found.")
        return pd.DataFrame(), 0
    print(f"Loading {filepath}...")
    try:
        df = pd.read_csv(filepath, sep='\t', header=None, names=['label', 'text'])
        df['label'] = df['label'].map({'ham': 'ham', 'spam': 'scam'})
        df['source'] = 'sms'
        return df[['text', 'source', 'label']], len(df)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return pd.DataFrame(), 0

def load_spam_csv(filepath):
    if not filepath.exists():
        print(f"Skipping: {filepath} not found.")
        return pd.DataFrame(), 0
    print(f"Loading {filepath}...")
    try:
        df = pd.read_csv(filepath, encoding='latin-1')
        
        # Case insensitive column mapping
        col_names = {col.lower(): col for col in df.columns}
        
        label_col = col_names.get('spamORham'.lower()) or col_names.get('v1')
        text_col = col_names.get('message') or col_names.get('v2')
        
        if not label_col or not text_col:
            print(f"Error loading {filepath}: Required columns not found. Found: {df.columns}")
            return pd.DataFrame(), 0

        df = df.rename(columns={text_col: 'text'})
        df['label'] = df[label_col].map({'ham': 'ham', 'spam': 'scam'})
        df['source'] = 'sms'
        return df[['text', 'source', 'label']], len(df)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return pd.DataFrame(), 0

def load_telegram_spam(filepath):
    if not filepath.exists():
        print(f"Skipping: {filepath} not found.")
        return pd.DataFrame(), 0
    print(f"Loading {filepath}...")
    try:
        df = pd.read_csv(filepath)
        if 'text' not in df.columns:
            print(f"Error loading {filepath}: 'text' column missing.")
            return pd.DataFrame(), 0
        df['label'] = 'scam'
        df['source'] = 'telegram'
        return df[['text', 'source', 'label']], len(df)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return pd.DataFrame(), 0

def load_telegram_ham(filepath):
    if not filepath.exists():
        print(f"Skipping: {filepath} not found.")
        return pd.DataFrame(), 0
    print(f"Loading {filepath}...")
    try:
        df = pd.read_csv(filepath)
        if 'text' not in df.columns or 'label' not in df.columns:
            print(f"Error loading {filepath}: 'text' or 'label' missing.")
            return pd.DataFrame(), 0
        df['label'] = 'ham'
        df['source'] = 'telegram'
        return df[['text', 'source', 'label']], len(df)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return pd.DataFrame(), 0

def load_sms_uz(filepath):
    if not filepath.exists():
        print(f"Skipping: {filepath} not found.")
        return pd.DataFrame(), 0
    print(f"Loading {filepath}...")
    try:
        df = pd.read_csv(filepath)
        if 'text' not in df.columns or 'label' not in df.columns:
            print(f"Error loading {filepath}: 'text' or 'label' missing.")
            return pd.DataFrame(), 0
        df['label'] = df['label'].map({'ham': 'ham', 'scam': 'scam'})
        df['source'] = 'sms'
        return df[['text', 'source', 'label']], len(df)
    except Exception as e:
        print(f"Error loading {filepath}: {e}")
        return pd.DataFrame(), 0

def build_unified_dataset():
    origins = {}
    datasets = []
    
    # Load and track raw contributions
    def add_dataset(name, loader, filename):
        df, raw_count = loader(sources_dir / filename)
        if not df.empty:
            df['origin_db'] = name # Temporary column for tracing
            datasets.append(df)
        origins[name] = raw_count
    
    add_dataset("sms_spam_collection", load_sms_spam_collection, "sms_spam_collection.txt")
    add_dataset("spam_csv", load_spam_csv, "spam.csv")
    add_dataset("telegram_spam", load_telegram_spam, "telegram_spam_cleaned.csv")
    add_dataset("telegram_ham", load_telegram_ham, "telegram_ham_samples.csv")
    add_dataset("sms_uz", load_sms_uz, "sms_uz.csv")

    unified_df = pd.concat(datasets, ignore_index=True)
    print(f"\nTotal raw rows across all files: {sum(origins.values())}")
    
    for name, cnt in origins.items():
        print(f"- {name} delivered {cnt} raw rows")

    # Data Cleaning
    unified_df = unified_df.dropna(subset=['text', 'label', 'source'])
    unified_df = unified_df[unified_df['text'].str.strip() != '']
    unified_df['text'] = unified_df['text'].str.strip()
    
    # Count rows by origin before dedup
    origins_post_clean = unified_df['origin_db'].value_counts()
    
    # Remove duplicates matching exactly on [text, source, label]
    unified_df = unified_df.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    # Map surviving rows to origin datasets
    origins_final = unified_df['origin_db'].value_counts()
    
    # Drop temp column
    unified_df = unified_df[['text', 'source', 'label']]
    
    print("\n--- Final Report ---")
    print(f"1) Total valid, unique rows: {len(unified_df)}")
    print("\n2) Rows by source:")
    print(unified_df['source'].value_counts().to_string())
    print("\n3) Rows by label:")
    print(unified_df['label'].value_counts().to_string())
    print("\n4) How many rows came from each dataset (Final surviving count):")
    print(origins_final.to_string())
    
    print("\n5) First 10 sample rows:")
    safe_head = unified_df.head(10).to_string(index=False)
    print(safe_head.encode('ascii', 'replace').decode('ascii'))
    
    # Paths creation dynamically
    processed_val = base_dir / "data" / "processed" / "unified_messages_raw.csv"
    final_val = base_dir / "data" / "final" / "safetalk_unified_dataset.csv"
    
    processed_val.parent.mkdir(parents=True, exist_ok=True)
    final_val.parent.mkdir(parents=True, exist_ok=True)
    
    unified_df.to_csv(processed_val, index=False, encoding='utf-8')
    unified_df.to_csv(final_val, index=False, encoding='utf-8')
    print(f"\nSaved successfully to:\n- {processed_val}\n- {final_val}")

if __name__ == "__main__":
    build_unified_dataset()
