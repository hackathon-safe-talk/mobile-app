import pandas as pd
import numpy as np
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from preprocess_text_v8 import preprocess_v8

base_dir = Path(__file__).resolve().parents[1]

def build_v9():
    v8_path = base_dir / "data" / "processed" / "master_semantic_dataset_v8_cleaned.csv"
    v9_hardening_path = base_dir / "data" / "expansions" / "hardening_data_v9.csv"
    output_path = base_dir / "data" / "processed" / "master_semantic_dataset_v9_cleaned.csv"

    if not v8_path.exists() or not v9_hardening_path.exists():
        print("Required datasets not found.")
        return

    print("Loading datasets...")
    df_v8 = pd.read_csv(v8_path)
    df_v9_new = pd.read_csv(v9_hardening_path)

    # Preprocess new V9 hard cases
    print("Preprocessing V9 hardening data...")
    df_v9_new['clean_text'] = df_v9_new['text'].apply(preprocess_v8)
    
    # Merge
    print("Merging V8 and V9 hardening data...")
    combined_df = pd.concat([df_v8, df_v9_new], ignore_index=True)
    
    # Remove exact duplicates
    before = len(combined_df)
    combined_df = combined_df.drop_duplicates(subset=['clean_text'])
    print(f"Removed {before - len(combined_df)} exact duplicates.")

    # Near-duplicate removal (Cosine Similarity)
    print("Performing near-duplicate detection (Threshold: 0.95)...")
    # We do this per class to be efficient
    final_df = []
    for label in combined_df['risk_label'].unique():
        subset = combined_df[combined_df['risk_label'] == label].copy()
        if len(subset) < 2:
            final_df.append(subset)
            continue
            
        vectorizer = TfidfVectorizer(ngram_range=(1, 2), max_features=5000)
        tfidf_matrix = vectorizer.fit_transform(subset['clean_text'].astype(str))
        
        sim_matrix = cosine_similarity(tfidf_matrix)
        
        # Identify indices to drop
        to_drop = set()
        for i in range(len(sim_matrix)):
            if i in to_drop: continue
            for j in range(i + 1, len(sim_matrix)):
                if sim_matrix[i, j] > 0.95:
                    to_drop.add(j)
        
        print(f"Class {label}: Dropping {len(to_drop)} near-duplicates out of {len(subset)}.")
        subset_cleaned = subset.iloc[list(set(range(len(subset))) - to_drop)]
        final_df.append(subset_cleaned)

    master_df = pd.concat(final_df, ignore_index=True)

    # Rebalance DANGEROUS to ~8%
    print("\n--- REBALANCING ---")
    counts = master_df['risk_label'].value_counts()
    print("Current counts:")
    print(counts)
    
    dang_count = counts.get('DANGEROUS', 0)
    total_needed = dang_count / 0.08  # Target 8%
    
    if len(master_df) > total_needed:
        # We need to downsample SAFE and SUSPICIOUS
        non_dang = master_df[master_df['risk_label'] != 'DANGEROUS']
        dang = master_df[master_df['risk_label'] == 'DANGEROUS']
        
        # Calculate how many non-dangerous we can keep
        non_dang_target = int(total_needed * 0.92)
        
        if len(non_dang) > non_dang_target:
            print(f"Downsampling SAFE/SUSPICIOUS from {len(non_dang)} to {non_dang_target} to reach 8% DANGEROUS ratio.")
            non_dang_sampled = non_dang.sample(n=non_dang_target, random_state=42)
            master_df = pd.concat([dang, non_dang_sampled], ignore_index=True)
        else:
            print("No downsampling needed, already at or above 8% DANGEROUS.")
    else:
        print("Note: Dataset is small enough that DANGEROUS is already > 8% or total is small.")

    print("\nFinal V9 Stats:")
    print(master_df['risk_label'].value_counts())
    print(f"Total rows: {len(master_df)}")
    
    master_df.to_csv(output_path, index=False, encoding='utf-8')
    print(f"V9 cleaned dataset saved to {output_path}")

if __name__ == "__main__":
    build_v9()
