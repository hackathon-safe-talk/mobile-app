import pandas as pd
import re
import os

BASE = r"d:\SafeTalk\safetalk_ai\data\consolidated"

# Step 1: MERGE DATASETS
def load_datasets():
    uz = pd.read_csv(os.path.join(BASE, "uzb_final1_dataset.csv"))
    en = pd.read_csv(os.path.join(BASE, "eng_final1_dataset.csv"))
    ru = pd.read_csv(os.path.join(BASE, "rus_final1_dataset.csv"))
    
    uz['source_lang_dataset'] = 'uz'
    en['source_lang_dataset'] = 'en'
    ru['source_lang_dataset'] = 'ru'
    
    df = pd.concat([uz, en, ru], ignore_index=True)
    df.rename(columns={'language': 'language_orig'}, inplace=True)
    return df

# Step 2: LABEL UNIFICATION
def map_label(orig_label):
    if pd.isna(orig_label):
        return "SUSPICIOUS"
    lbl = str(orig_label).lower()
    
    if any(k in lbl for k in ['phishing', 'fraud', 'scam', 'malware', 'dangerous']):
        return "DANGEROUS"
    if any(k in lbl for k in ['ham', 'legit', 'normal', 'safe']):
        return "SAFE"
    
    return "SUSPICIOUS"

# Step 3: LANGUAGE DETECTION + FIX
CYRILLIC_RE = re.compile(r'[\u0400-\u04FF]')
LATIN_RE = re.compile(r'[a-zA-Z]')
UZ_TOKENS = ["siz", "pul", "hisob", "kod", "yuboring"]

def detect_lang(text):
    text = str(text)
    c_count = len(CYRILLIC_RE.findall(text))
    l_count = len(LATIN_RE.findall(text))
    tot = c_count + l_count
    if tot == 0:
        return "en"
    
    c_ratio = c_count / tot
    l_ratio = l_count / tot
    
    t_lower = text.lower()
    has_uz = any(re.search(r'\b' + t + r'\b', t_lower) for t in UZ_TOKENS) or any(c in t_lower for c in 'ўқғҳ')
    
    if c_ratio > 0.5:
        if has_uz:
            return "uz"
        return "ru"
    elif l_ratio > 0.5:
        if has_uz:
            return "uz"
        return "en"
    
    if c_count > l_count:
        if has_uz: return "uz"
        return "ru"
    if has_uz: return "uz"
    return "en"

def process_language(df):
    df['language_detected'] = df['text'].apply(detect_lang)
    
    def resolve(row):
        orig = str(row['language_orig']).strip().lower()
        det = row['language_detected']
        if orig == det:
            return det, "high"
        elif orig not in ['uz', 'en', 'ru']:
            return det, "medium"
        else:
            return det, "low"
            
    res = df.apply(resolve, axis=1)
    df['language_fixed'] = [r[0] for r in res]
    df['language_confidence'] = [r[1] for r in res]
    return df

def deduplicate_leakage(df):
    modes = df.groupby('normalized_text')['language_fixed'].apply(lambda x: x.mode()[0] if not x.empty else 'en')
    df['is_maj'] = df.apply(lambda r: r['language_fixed'] == modes.get(r['normalized_text'], 'en'), axis=1)
    
    lprio = {'uz': 1, 'en': 2, 'ru': 3}
    df['lp'] = df['language_fixed'].map(lprio).fillna(4)
    cprio = {'high': 1, 'medium': 2, 'low': 3}
    df['cp'] = df['language_confidence'].map(cprio).fillna(4)
    
    sorted_df = df.sort_values(by=['is_maj', 'cp', 'lp'], ascending=[False, True, True])
    
    deduped = sorted_df.drop_duplicates(subset=['normalized_text'], keep='first')
    
    dropped = sorted_df[~sorted_df.index.isin(deduped.index)]
    return deduped, dropped

def main():
    print("Loading datasets...")
    df = load_datasets()
    initial_rows = len(df)
    
    print("Unifying labels...")
    df['label_unified'] = df['original_label'].apply(map_label)
    
    print("Detecting language...")
    df = process_language(df)
    
    conflicts = df[df['language_detected'] != df['language_orig'].str.lower()]
    
    print("Deduplicating leakage...")
    deduped, dropped = deduplicate_leakage(df)
    final_rows = len(deduped)
    
    print("Saving removed leakage and conflicts...")
    dropped.to_csv(os.path.join(BASE, "removed_leakage.csv"), index=False)
    conflicts.to_csv(os.path.join(BASE, "language_conflicts_resolved.csv"), index=False)
    
    print("Cleaning final dataset...")
    final_cols = ["text", "normalized_text", "label_unified", "language_fixed", "language_confidence", "source_dataset"]
    clean_df = deduped[final_cols]
    
    print("Splitting dataset...")
    uz_df = clean_df[clean_df['language_fixed'] == 'uz']
    en_df = clean_df[clean_df['language_fixed'] == 'en']
    ru_df = clean_df[clean_df['language_fixed'] == 'ru']
    
    uz_df.to_csv(os.path.join(BASE, "uz_final_v2.csv"), index=False)
    en_df.to_csv(os.path.join(BASE, "en_final_v2.csv"), index=False)
    ru_df.to_csv(os.path.join(BASE, "ru_final_v2.csv"), index=False)
    
    print("\n" + "=" * 50)
    print("REPORTING")
    print("=" * 50)
    print(f"Total Rows Before:      {initial_rows}")
    print(f"Total Rows After:       {final_rows}")
    print(f"Leakage Cases Removed:  {len(dropped)}")
    print(f"Conflicts Resolved:     {len(conflicts)}")
    
    print("-" * 50)
    print("Label Distribution (Final):")
    label_dist = clean_df['label_unified'].value_counts()
    for lbl, count in label_dist.items():
        print(f"  {lbl:<15} {count}")
        
    print("-" * 50)
    print("Language Distribution (Final):")
    lang_dist = clean_df['language_fixed'].value_counts()
    for lng, count in lang_dist.items():
        print(f"  {lng:<5} {count}")
        
    print("-" * 50)
    print(f"uz_final_v2.csv: {len(uz_df)}")
    print(f"en_final_v2.csv: {len(en_df)}")
    print(f"ru_final_v2.csv: {len(ru_df)}")

if __name__ == "__main__":
    main()
