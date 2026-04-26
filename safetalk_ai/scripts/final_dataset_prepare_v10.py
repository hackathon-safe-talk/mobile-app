"""
SafeTalk V10 Final Dataset Hardening & Preparation
==================================================
Production-grade dataset preparation pipeline.
Prepares safetalk_unified_v10_ready.csv for ML training by:
- Adding metadata features (length, has_url, has_number, is_synthetic)
- Controlling synthetic data ratio (max 25-30%)
- Extracting complex edge cases for separate holistic evaluation
- Creating a strict, stratified 80/10/10 Train/Valid/Test split
- Preventing data leakage.

Author: SafeTalk AI Team
"""

import pandas as pd
import numpy as np
import re
import os
from pathlib import Path
from sklearn.model_selection import train_test_split
from datetime import datetime

# ─────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────

SEED = 42
np.random.seed(SEED)

BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data" / "final"
INPUT_FILE = DATA_DIR / "safetalk_unified_v10_ready.csv"

OUT_DIR = DATA_DIR / "v10_ready"
OUT_TRAIN = OUT_DIR / "train.csv"
OUT_VALID = OUT_DIR / "validation.csv"
OUT_TEST = OUT_DIR / "test.csv"
OUT_EDGE = OUT_DIR / "safetalk_edge_cases_v1.csv"
REPORT_FILE = OUT_DIR / "preparation_report_v10.txt"

MAX_SYNTHETIC_RATIO = 0.30

# ─────────────────────────────────────────────────────────────
# Step 1: Add Metadata Flags
# ─────────────────────────────────────────────────────────────

def add_metadata(df: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    print("\n[STEP 1] Adding Metadata Flags...")
    
    # Identify synthetic data (Stronger detection)
    synthetic_sources = ["synthetic", "generated", "augmentation", "ai_generated", "suspicious_v1"]
    pattern = '|'.join(synthetic_sources)
    df['is_synthetic'] = df['source'].str.contains(pattern, case=False, na=False)
    
    # Calculate length
    df['length'] = df['text'].astype(str).str.len()
    
    # Detect URLs (Advanced detection)
    url_pattern = re.compile(r'(https?://\S+|www\.\S+|\b[\w-]+\.(com|org|net|uz|ru|xyz|top|click|online|site|store)\b)', re.I)
    df['has_url'] = df['text'].astype(str).apply(lambda x: bool(url_pattern.search(x)))
    
    # Detect numbers (digits)
    df['has_number'] = df['text'].astype(str).str.contains(r'\d')
    
    meta_stats = {
        'synthetic_count': int(df['is_synthetic'].sum()),
        'url_count': int(df['has_url'].sum())
    }
    
    print(f"  Added: is_synthetic ({meta_stats['synthetic_count']} rows), length, has_url ({meta_stats['url_count']} rows), has_number")
    return df, meta_stats

# ─────────────────────────────────────────────────────────────
# Step 2: Synthetic Data Control
# ─────────────────────────────────────────────────────────────

def control_synthetic_data(df: pd.DataFrame) -> pd.DataFrame:
    print("\n[STEP 2] Regulating Synthetic Data Overfitting Risk...")
    
    real_df = df[~df['is_synthetic']]
    synth_df = df[df['is_synthetic']]
    
    real_count = len(real_df)
    synth_count = len(synth_df)
    total_count = len(df)
    
    current_ratio = synth_count / total_count if total_count > 0 else 0
    print(f"  Current Synthetic Ratio: {current_ratio:.1%} ({synth_count} rows)")
    
    if current_ratio > MAX_SYNTHETIC_RATIO:
        # Target formula: S_target / (R + S_target) = 0.30  ==>  S_target = (0.30 / 0.70) * R
        target_synth = int((MAX_SYNTHETIC_RATIO / (1.0 - MAX_SYNTHETIC_RATIO)) * real_count)
        print(f"  Ratio exceeds {MAX_SYNTHETIC_RATIO:.0%} threshold. Downsampling synthetic data to {target_synth} rows.")
        
        synth_downsampled = synth_df.sample(n=target_synth, random_state=SEED)
        df = pd.concat([real_df, synth_downsampled]).sample(frac=1, random_state=SEED).reset_index(drop=True)
        print(f"  New Synthetic Ratio: {len(synth_downsampled) / len(df):.1%} ({len(synth_downsampled)} rows)")
    else:
        print("  Synthetic ratio is within acceptable limits. No downsampling required.")
        
    return df

# ─────────────────────────────────────────────────────────────
# Step 3: Edge Case Extraction
# ─────────────────────────────────────────────────────────────

def extract_edge_cases(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, dict]:
    print("\n[STEP 3] Extracting Edge Cases for Holistic Evaluation...")
    
    # Definitions of tricky / edge cases
    # 1. Mixed language labels if any
    c1 = df['language'] == 'mixed'
    
    # 2. Short ambiguous texts
    c2 = (df['length'] < 30) & (df['label'] == 'SUSPICIOUS')
    
    # 3. Borderline tricky phishing (e.g. SAFE texts that contain URLs, or vice versa)
    c3 = (df['label'] == 'SAFE') & (df['has_url'])
    
    # 4. Anomalous safe-wordings built around urgent tone (heuristics fallback)
    c4 = (df['label'] == 'SUSPICIOUS') & (~df['has_url']) & (~df['has_number'])
    
    edge_mask = c1 | c2 | c3 | c4
    
    edge_df = df[edge_mask].copy()
    main_df = df[~edge_mask].copy()
    
    # We don't want to extract *too* many. Restrict to a hard max of ~10% of total if it overflows
    if len(edge_df) > len(df) * 0.15:
        edge_df = edge_df.sample(frac=1, random_state=SEED)
        keep = int(len(df) * 0.10)
        
        main_df = pd.concat([main_df, edge_df.iloc[keep:]])
        edge_df = edge_df.iloc[:keep]

    # Reintroduce 20% of the final edge cases back to the main training data
    edge_df = edge_df.sample(frac=1, random_state=SEED) # shuffle carefully
    reintro_count = int(len(edge_df) * 0.20)
    reintro_df = edge_df.iloc[:reintro_count]
    edge_df = edge_df.iloc[reintro_count:]
    
    main_df = pd.concat([main_df, reintro_df]).sample(frac=1, random_state=SEED).reset_index(drop=True)

    print(f"  Extracted {len(edge_df) + reintro_count:,} initial edge cases")
    print(f"  Reintroduced {reintro_count:,} edge cases (20%) back to training pool")
    print(f"  Retained {len(edge_df):,} edge cases for separate evaluation file")
    print(f"  Remaining rows for train/valid/test split: {len(main_df):,}")
    
    edge_stats = {
        'initial_extracted': len(edge_df) + reintro_count,
        'reintroduced': reintro_count,
        'reintroduced_pct': 20.0,
        'retained': len(edge_df)
    }
    
    return main_df, edge_df, edge_stats

# ─────────────────────────────────────────────────────────────
# Step 4: Stratified Train/Valid/Test Split + Leakage Prevention
# ─────────────────────────────────────────────────────────────

def create_splits(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    print("\n[STEP 4] Executing Stratified 80/10/10 Split (Leakage Free)...")
    
    # Leakage check sanity - Deduplicate on clean_text just in case
    before_len = len(df)
    df = df.drop_duplicates(subset=['clean_text'], keep='first')
    if len(df) < before_len:
        print(f"  [WARN] Dropped {before_len - len(df)} duplicate clean_text before splitting.")
        
    # Create stratification key
    # Ensure classes with extremely low frequency (<3) do not crash the stratifier
    df['strat_key'] = df['label'] + '_' + df['language']
    counts = df['strat_key'].value_counts()
    valid_keys = counts[counts >= 3].index
    
    # Find rows with invalid keys and handle them by dropping strat requirement for them
    rare_df = df[~df['strat_key'].isin(valid_keys)].copy()
    strat_df = df[df['strat_key'].isin(valid_keys)].copy()
    
    if len(rare_df) > 0:
        print(f"  [WARN] Found {len(rare_df)} rare stratification pairs. Moving to train set implicitly.")
    
    # 80% train, 20% temp
    try:
        train_df, temp_df = train_test_split(strat_df, test_size=0.20, stratify=strat_df['strat_key'], random_state=SEED)
    except ValueError as e:
        print(f"  [WARN] Stratified split failed ({e}). Falling back to stratify by label only.")
        train_df, temp_df = train_test_split(strat_df, test_size=0.20, stratify=strat_df['label'], random_state=SEED)
    
    # If using strat_key worked, drop the temp stratification keys that might drop below 2 instances
    temp_df['strat_key'] = temp_df['label'] + '_' + temp_df['language']
    counts_temp = temp_df['strat_key'].value_counts()
    valid_keys_temp = counts_temp[counts_temp >= 2].index
    
    rare_temp_df = temp_df[~temp_df['strat_key'].isin(valid_keys_temp)].copy()
    strat_temp_df = temp_df[temp_df['strat_key'].isin(valid_keys_temp)].copy()
    
    # 50% valid, 50% test (10% / 10% of total)
    try:
        valid_df, test_df = train_test_split(strat_temp_df, test_size=0.50, stratify=strat_temp_df['strat_key'], random_state=SEED)
    except ValueError:
        valid_df, test_df = train_test_split(strat_temp_df, test_size=0.50, stratify=strat_temp_df['label'], random_state=SEED)
        
    # Add back the rare items
    train_df = pd.concat([train_df, rare_df, rare_temp_df]).reset_index(drop=True)
    valid_df = valid_df.reset_index(drop=True)
    test_df = test_df.reset_index(drop=True)
    
    # Drop the temporary strat_key
    for d in [train_df, valid_df, test_df]:
        if 'strat_key' in d.columns:
            d.drop(columns=['strat_key'], inplace=True)
            
    # LEAKAGE VERIFICATION
    train_texts = set(train_df['clean_text'])
    valid_overlap = valid_df['clean_text'].isin(train_texts).sum()
    test_overlap = test_df['clean_text'].isin(train_texts).sum()
    
    print(f"  Train: {len(train_df):,} | Valid: {len(valid_df):,} | Test: {len(test_df):,}")
    print(f"  Leakage Validation -> Valid overlap: {valid_overlap} | Test overlap: {test_overlap}")
    
    if valid_overlap > 0 or test_overlap > 0:
        print("  [CRITICAL] Data leakage detected! Overlapping texts across splits.")
        
    return train_df, valid_df, test_df

# ─────────────────────────────────────────────────────────────
# Step 5: Quality Report & Output Saving
# ─────────────────────────────────────────────────────────────

def print_distribution(name: str, df: pd.DataFrame, total_lines: list):
    metrics = f"═══ {name.upper()} DATASET ({len(df):,}) ═══"
    print(f"\n{metrics}")
    total_lines.append(metrics)
    
    # Label
    label_dist = df['label'].value_counts()
    for lbl, count in label_dist.items():
        line = f"  {lbl:12s}: {count:5,} ({count/len(df)*100:5.1f}%)"
        print(line)
        total_lines.append(line)
        
    # Language
    total_lines.append("  Languages:")
    lang_dist = df['language'].value_counts()
    langs_str = " | ".join([f"{l.upper()}: {c} ({c/len(df)*100:.1f}%)" for l, c in lang_dist.items()])
    print(f"  Languages: {langs_str}")
    total_lines.append(f"    {langs_str}")
    
    # Synthetic ratio
    synth_ratio = df['is_synthetic'].mean() * 100
    syn_line = f"  Synthetic : {synth_ratio:.1f}%"
    print(syn_line)
    total_lines.append(syn_line)
    total_lines.append("")

def main():
    print("==================================================")
    print(" SafeTalk V10 Dataset Hardening Pipeline")
    print(f" Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("==================================================")
    
    if not INPUT_FILE.exists():
        raise FileNotFoundError(f"Missing unified dataset: {INPUT_FILE}")
        
    df = pd.read_csv(INPUT_FILE)
    print(f"Loaded records: {len(df):,}")
    
    df, meta_stats = add_metadata(df)
    df = control_synthetic_data(df)
    main_df, edge_df, edge_stats = extract_edge_cases(df)
    
    train_df, valid_df, test_df = create_splits(main_df)
    
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    
    # Print and collect report
    lines = []
    lines.append("SafeTalk V10 Hardened Split Report")
    lines.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append(f"Input: {INPUT_FILE}\n")
    
    lines.append("═══ PIPELINE STATS ═══")
    lines.append(f"  Synthetic Data Detected: {meta_stats['synthetic_count']:,} rows")
    lines.append(f"  URLs Detected: {meta_stats['url_count']:,} rows")
    lines.append(f"  Edge Cases Extracted: {edge_stats['initial_extracted']:,} rows")
    lines.append(f"  Edge Cases Reintroduced to Train: {edge_stats['reintroduced']:,} rows ({edge_stats['reintroduced_pct']}%)")
    lines.append("")
    
    print_distribution("TRAIN", train_df, lines)
    print_distribution("VALIDATION", valid_df, lines)
    print_distribution("TEST", test_df, lines)
    print_distribution("EDGE CASES", edge_df, lines)
    
    # Save
    print("\n[STEP 5] Saving Output Files...")
    
    train_df.to_csv(OUT_TRAIN, index=False, encoding='utf-8')
    valid_df.to_csv(OUT_VALID, index=False, encoding='utf-8')
    test_df.to_csv(OUT_TEST, index=False, encoding='utf-8')
    edge_df.to_csv(OUT_EDGE, index=False, encoding='utf-8')
    
    print(f"  Saved: {OUT_TRAIN.name} ({len(train_df):,} rows)")
    print(f"  Saved: {OUT_VALID.name} ({len(valid_df):,} rows)")
    print(f"  Saved: {OUT_TEST.name} ({len(test_df):,} rows)")
    print(f"  Saved: {OUT_EDGE.name} ({len(edge_df):,} rows)")
    
    with open(REPORT_FILE, "w", encoding='utf-8') as f:
        f.write("\n".join(lines))
        
    print(f"\n✅ Hardening and splitting complete. Files ready in {OUT_DIR.relative_to(BASE_DIR)}")

if __name__ == "__main__":
    main()
