import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def merge_v3():
    v2_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v2.csv"
    phishing_path = base_dir / "data" / "phishing_expansion" / "uz_financial_scams.csv"
    
    if not v2_path.exists() or not phishing_path.exists():
        print("Required datasets not found!")
        return
        
    df_v2 = pd.read_csv(v2_path)
    df_phish = pd.read_csv(phishing_path)
    
    # Normalize schema for phishing dataset
    # Schema: text,label,category,notes -> text,source,label
    df_phish['source'] = 'sms'
    df_phish = df_phish[['text', 'source', 'label']]
    
    df_v2 = df_v2[['text', 'source', 'label']]
    
    # Merge
    df_merged = pd.concat([df_v2, df_phish], ignore_index=True)
    
    # Clean and dedup
    df_merged = df_merged.dropna(subset=['text', 'label'])
    df_merged = df_merged[df_merged['text'].str.strip() != ""]
    df_merged = df_merged[df_merged['label'].isin(['ham', 'scam'])]
    
    df_merged = df_merged.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    out_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v3.csv"
    df_merged.to_csv(out_path, index=False, encoding='utf-8')
    
    print("=== Dataset V3 Merge Report ===")
    print(f"Total rows in v3 dataset: {len(df_merged)}")
    print("\nRows by label:")
    print(df_merged['label'].value_counts().to_string())
    print("\nRows by source:")
    print(df_merged['source'].value_counts().to_string())
    print(f"\nSaved to: {out_path}")

if __name__ == "__main__":
    merge_v3()
