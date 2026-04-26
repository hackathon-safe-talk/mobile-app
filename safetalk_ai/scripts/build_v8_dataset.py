import pandas as pd
from pathlib import Path
from preprocess_text_v8 import preprocess_v8

base_dir = Path(__file__).resolve().parents[1]

def build_v8_dataset():
    v7_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7.csv"
    hard_cases_path = base_dir / "data" / "expansions" / "hard_cases_v8.csv"
    output_path = base_dir / "data" / "processed" / "master_semantic_dataset_v8.csv"
    output_cleaned_path = base_dir / "data" / "processed" / "master_semantic_dataset_v8_cleaned.csv"

    if not v7_path.exists():
        print(f"Error: V7 dataset not found at {v7_path}")
        return

    print(f"Loading V7 dataset and expansion cases...")
    df_v7 = pd.read_csv(v7_path)
    
    if hard_cases_path.exists():
        df_hard = pd.read_csv(hard_cases_path)
        # Ensure source is marked
        df_hard['source'] = 'hard_cases_v8'
        df_v8 = pd.concat([df_v7, df_hard], ignore_index=True)
        print(f"Merged {len(df_hard)} hard cases.")
    else:
        df_v8 = df_v7
        print("No hard cases found to merge.")

    # Drop duplicates
    initial_count = len(df_v8)
    df_v8 = df_v8.drop_duplicates(subset=['text'], keep='last')
    print(f"Dropped {initial_count - len(df_v8)} duplicates.")

    # Save raw V8
    df_v8.to_csv(output_path, index=False)
    print(f"V8 Raw Dataset saved to: {output_path}")

    # Process and save cleaned V8
    print("Applying V8 Preprocessing...")
    df_v8['clean_text'] = df_v8['text'].astype(str).apply(preprocess_v8)
    df_v8.to_csv(output_cleaned_path, index=False)
    print(f"V8 Cleaned Dataset saved to: {output_cleaned_path}")
    
    print("\nFinal Risk Distribution (V8):")
    print(df_v8['risk_label'].value_counts())

if __name__ == "__main__":
    build_v8_dataset()
