import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def merge_datasets():
    # Load old unified dataset
    base_dataset = base_dir / "data" / "final" / "safetalk_unified_dataset.csv"
    df_base = pd.read_csv(base_dataset)
    initial_base_size = len(df_base)
    
    # Load expansions
    uz_dir = base_dir / "data" / "uzbek_expansion"
    sms_uz_path = uz_dir / "sms_uz_enriched.csv"
    tel_uz_path = uz_dir / "telegram_uz_enriched.csv"
    
    df_sms_uz = pd.read_csv(sms_uz_path)
    df_tel_uz = pd.read_csv(tel_uz_path)
    
    init_sms_uz = len(df_sms_uz)
    init_tel_uz = len(df_tel_uz)
    
    # Normalize schema
    df_sms_uz['source'] = 'sms'
    df_tel_uz['source'] = 'telegram'
    
    df_sms_uz = df_sms_uz[['text', 'source', 'label']]
    df_tel_uz = df_tel_uz[['text', 'source', 'label']]
    df_base = df_base[['text', 'source', 'label']]
    
    # Merge all
    df_merged = pd.concat([df_base, df_sms_uz, df_tel_uz], ignore_index=True)
    
    # Clean and deduplicate
    df_merged = df_merged.dropna(subset=['text', 'label'])
    df_merged = df_merged[df_merged['text'].str.strip() != ""]
    df_merged = df_merged[df_merged['label'].isin(['ham', 'scam'])]
    
    df_merged = df_merged.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    # Save v2
    out_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v2.csv"
    df_merged.to_csv(out_path, index=False, encoding='utf-8')
    
    # Analytics
    print("=== Dataset Merge Report ===")
    print(f"Total rows in v2 dataset: {len(df_merged)}")
    print("\nRows by source:")
    print(df_merged['source'].value_counts().to_string())
    print("\nRows by label:")
    print(df_merged['label'].value_counts().to_string())
    print(f"\nContributions:")
    print(f"- Base dataset (initially {initial_base_size})")
    print(f"- sms_uz_enriched (initially {init_sms_uz})")
    print(f"- telegram_uz_enriched (initially {init_tel_uz})")
    print(f"\nOutput saved to: {out_path}")

if __name__ == "__main__":
    merge_datasets()
