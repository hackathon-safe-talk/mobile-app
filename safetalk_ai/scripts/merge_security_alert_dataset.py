import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def merge_v4():
    v3_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v3.csv"
    sec_alert_path = base_dir / "data" / "security_alert_expansion" / "uz_security_alert_scams.csv"
    
    if not v3_path.exists() or not sec_alert_path.exists():
        print("Required datasets not found!")
        return
        
    df_v3 = pd.read_csv(v3_path)
    df_sec = pd.read_csv(sec_alert_path)
    
    initial_v3_size = len(df_v3)
    initial_sec_size = len(df_sec)
    
    # Normalize schema
    df_sec['source'] = 'sms'
    df_sec = df_sec[['text', 'source', 'label']]
    df_v3 = df_v3[['text', 'source', 'label']]
    
    # Merge
    df_merged = pd.concat([df_v3, df_sec], ignore_index=True)
    
    # Clean and dedup
    df_merged = df_merged.dropna(subset=['text', 'label'])
    df_merged = df_merged[df_merged['text'].str.strip() != ""]
    df_merged = df_merged[df_merged['label'].isin(['ham', 'scam'])]
    
    df_merged = df_merged.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    out_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v4.csv"
    df_merged.to_csv(out_path, index=False, encoding='utf-8')
    
    print("=== Dataset V4 Merge Report ===")
    print(f"Total rows in v4 dataset: {len(df_merged)}")
    print("\nRows by label:")
    print(df_merged['label'].value_counts().to_string())
    print("\nRows by source:")
    print(df_merged['source'].value_counts().to_string())
    print("\nContributions:")
    print(f"- Base dataset V3: {initial_v3_size} rows")
    print(f"- Security alert dataset: {initial_sec_size} rows")
    print(f"\nSaved to: {out_path}")

if __name__ == "__main__":
    merge_v4()
