import pandas as pd
from pathlib import Path

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def merge_v6():
    v5_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v5.csv"
    v6_exp_path = base_dir / "data" / "expansions" / "realistic_v6_messages.csv"
    output_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v6.csv"
    
    if not v5_path.exists():
        print(f"Error: V5 dataset not found at {v5_path}")
        return
    if not v6_exp_path.exists():
        print(f"Error: Realistic V6 expansion not found at {v6_exp_path}")
        return

    print("Loading datasets...")
    df_v5 = pd.read_csv(v5_path)
    df_v6_new = pd.read_csv(v6_exp_path)
    
    initial_v5_count = len(df_v5)
    
    # Merge
    print("Merging V5 and Realistic V6...")
    df_merged = pd.concat([df_v5, df_v6_new], ignore_index=True)
    
    # Cleaning
    print("Normalizing and deduplicating...")
    df_merged = df_merged.dropna(subset=['text', 'label'])
    df_merged['text'] = df_merged['text'].str.strip()
    df_merged = df_merged[df_merged['text'] != ""]
    df_merged = df_merged[df_merged['label'].isin(['ham', 'scam'])]
    
    # Deduplicate on text + source + label
    df_merged = df_merged.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    final_count = len(df_merged)
    added_rows = final_count - initial_v5_count
    
    # Save
    df_merged.to_csv(output_path, index=False, encoding='utf-8')
    
    print("\n=== Dataset V6 Merge Report ===")
    print(f"Total rows in V6 dataset: {final_count}")
    print(f"Rows added from V6 realistic expansion: {added_rows}")
    print("\nRows by label:")
    print(df_merged['label'].value_counts().to_string())
    print("\nRows by source:")
    print(df_merged['source'].value_counts().to_string())
    print(f"\nFinal V6 dataset saved to: {output_path}")

if __name__ == "__main__":
    merge_v6()
