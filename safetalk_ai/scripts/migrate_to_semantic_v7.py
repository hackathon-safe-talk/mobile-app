import pandas as pd
import re
from pathlib import Path

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def migrate_v7():
    input_path = base_dir / "data" / "processed" / "messages_cleaned_v6.csv"
    output_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7.csv"
    seed_path = base_dir / "data" / "uzbek_policy_seeds.csv"
    
    if not input_path.exists():
        print(f"Error: V6 dataset not found at {input_path}")
        return

    print(f"Loading V6 dataset: {input_path}")
    df = pd.read_csv(input_path)
    
    # Simple language detection (Uzbek keywords)
    uz_keywords = [
        "kodi", "bermang", "yuboring", "ayting", "kiriting", "tasdiqlash", 
        "so'm", "yechildi", "tushum", "olindi", "uchrashamiz", "boraman",
        "yutuq", "sovrin", "bonus", "darhol", "shoshiling", "akkaunt", "bloklandi"
    ]
    
    def detect_lang(text):
        text_str = str(text).lower()
        if any(kw in text_str for kw in uz_keywords):
            return 'uz'
        # Basic check for Cyrillic (Uzbek also uses Cyrillic often)
        if re.search(r'[а-яА-ЯёЁ]', text_str):
            # If it has Cyrillic but few Russian-specific prepositions, it might be Uzbek-Cyrl
            # For simplicity, we'll mark as 'ru' if not 'uz' by keywords
            return 'ru'
        return 'en'
    
    print("Detecting languages...")
    df['language'] = df['clean_text'].apply(detect_lang)

    # Downsample English SAFE (ham) to improve balance
    # Currently SAFE is too dominant (60%) and English is ~80%
    ham_en = df[(df['label'] == 'ham') & (df['language'] == 'en')]
    ham_others = df[(df['label'] == 'ham') & (df['language'] != 'en')]
    scams = df[df['label'] == 'scam']
    
    # We want to reduce ham_en significantly to let Uzbek and scams stand out
    # Target: total SAFE ≈ 4000 (currently 7000)
    ham_en_sample = ham_en.sample(n=min(len(ham_en), 2500), random_state=42)
    
    df = pd.concat([ham_en_sample, ham_others, scams], ignore_index=True)

    # Label Migration Rules
    def map_labels(row):
        text = str(row['clean_text']).lower()
        old_label = row['label']
        
        risk = 'SAFE'
        intent = 'UNKNOWN'
        tags = []
        
        # Risk & Intent Mapping based on ScamSemanticPolicy
        if old_label == 'scam':
            # Priority 1: DANGEROUS (Active threats)
            # Requesting sensitive info OR malware payloads
            is_request = any(kw in text for kw in ['yuboring', 'ayting', 'kiriting', 'send', 'tell', 'enter', 'confirm', 'verify', 'submit'])
            has_sensitive = any(kw in text for kw in ['kod', 'code', 'card', 'karta', 'cvv', 'parol', 'password', 'login'])
            is_malware = any(kw in text for kw in ['.apk', '.exe', '.scr', '.msi', '.bat'])
            
            if (is_request and has_sensitive) or is_malware:
                risk = 'DANGEROUS'
                intent = 'REQUEST'
            elif is_request:
                risk = 'SUSPICIOUS'
                intent = 'REQUEST'
            else:
                risk = 'SUSPICIOUS'
                
            if is_malware: tags.append('malware')
            if 'http' in text or 'www' in text: tags.append('link')
            if any(kw in text for kw in ['winner', 'yutuq', 'sovrin', 'gift', 'prize', 'bonus']):
                tags.append('prize_bait')
        else:
            # Ham check for service messages (INFO/WARNING)
            is_otp_info = any(kw in text for kw in ['tasdiqlash kodi', 'sms kodi', 'verification code', 'your code is'])
            is_otp_warning = any(kw in text for kw in ['bermang', 'never share', 'don\'t share', 'hech kimga'])
            
            if is_otp_info:
                risk = 'SAFE'
                intent = 'INFO'
                tags.append('otp')
            elif is_otp_warning:
                risk = 'SAFE'
                intent = 'WARNING'
                tags.append('otp_warning')
            else:
                risk = 'SAFE'
        
        return pd.Series([risk, intent, ",".join(tags)])

    print("Mapping labels to Semantic V7 schema...")
    df[['risk_label', 'intent_label', 'tags']] = df.apply(map_labels, axis=1)
    
    # Load and append Seed Data
    if seed_path.exists():
        print(f"Appending seeds from {seed_path}")
        seeds = pd.read_csv(seed_path)
        # Ensure seeds have all columns
        for col in ['source', 'clean_text']:
            if col not in seeds.columns:
                seeds[col] = seeds['text'] if col == 'clean_text' else 'manual'
                
        seeds['source'] = 'seed'
        v7_final = pd.concat([df, seeds], ignore_index=True)
    else:
        v7_final = df

    # Final cleanup: keep only V7 schema columns
    final_cols = ['text', 'risk_label', 'intent_label', 'tags', 'language', 'source', 'clean_text']
    v7_final = v7_final[final_cols]
    
    v7_final.to_csv(output_path, index=False)
    print(f"V7 Dataset saved to: {output_path}")
    print(f"Total rows: {len(v7_final)}")
    
    print("\nFinal Risk distribution:")
    print(v7_final['risk_label'].value_counts())
    print("\nFinal Language distribution:")
    print(v7_final['language'].value_counts())
    print("\nFinal Intent distribution:")
    print(v7_final['intent_label'].value_counts())

if __name__ == "__main__":
    migrate_v7()
