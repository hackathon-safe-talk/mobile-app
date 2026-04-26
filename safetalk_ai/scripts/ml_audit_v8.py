import pandas as pd
import re
from pathlib import Path

def detect_lang(text):
    text = str(text).lower()
    # Uzbek Latin specific combinations
    uz_lat_patterns = [r"o'", r"g'", r"o‘", r"g‘", r"o`", r"g`", "sh", "ch", "ng"]
    # Uzbek Cyrillic specific characters (ў, қ, ғ, ҳ) vs Russian
    uz_cyrl_chars = ['ў', 'қ', 'ғ', 'ҳ']
    ru_chars = ['ы', 'щ', 'ъ', 'ё']
    
    is_cyrillic = bool(re.search(r'[а-яА-ЯёЁ]', text))
    is_latin = bool(re.search(r'[a-zA-Z]', text))
    
    if is_cyrillic and is_latin:
        return 'Mixed'
    
    if is_cyrillic:
        if any(c in text for c in uz_cyrl_chars):
            return 'Uzbek (Cyrillic)'
        if any(c in text for c in ru_chars):
            return 'Russian'
        return 'Cyrillic (Probable RU/UZ)'

    if is_latin:
        # Check for Common Uzbek Latin words/patterns
        uz_keywords = ["boladi", "uchun", "bilan", "nima", "qanaqa", "yaxshi", "bolsa"]
        if any(kw in text for kw in uz_keywords) or any(re.search(pat, text) for pat in uz_lat_patterns):
            return 'Uzbek (Latin)'
        return 'English'
    
    return 'Unknown'

def audit():
    base_dir = Path("d:/SafeTalk/safetalk_ai")
    v8_path = base_dir / "data" / "processed" / "master_semantic_dataset_v8.csv"
    
    if not v8_path.exists():
        print(f"V8 dataset not found at {v8_path}")
        return

    df = pd.read_csv(v8_path)

    print("--- SECTION 1 & 3: CLASS DISTRIBUTION & DEDUPLICATION ---")
    results = []
    for label in ['SAFE', 'SUSPICIOUS', 'DANGEROUS']:
        subset = df[df['risk_label'] == label]
        total = len(subset)
        unique = subset['text'].nunique()
        dup_ratio = (total - unique) / total if total > 0 else 0
        results.append({
            "Class": label,
            "Total": total,
            "Unique": unique,
            "Duplicate %": f"{dup_ratio:.1%}"
        })
    print(pd.DataFrame(results))

    print("\n--- SECTION 2: LANGUAGE DISTRIBUTION ---")
    df['detected_lang'] = df['text'].apply(detect_lang)
    lang_dist = df.groupby(['risk_label', 'detected_lang']).size().unstack(fill_value=0)
    lang_pct = lang_dist.div(lang_dist.sum(axis=1), axis=0) * 100
    print("\nLanguage Counts per Class:")
    print(lang_dist)
    print("\nLanguage Percentages per Class:")
    print(lang_pct.round(1))

    print("\n--- SECTION 5: HARD CASE COVERAGE (Heuristic) ---")
    def check_hard_cases(row):
        text = str(row['text']).lower()
        label = row['risk_label']
        tags = []
        
        # Hard Safe
        if label == 'SAFE':
            if 'login' in text: tags.append('SAFE:login')
            if any(k in text for k in ['kod', 'otp', 'code']): tags.append('SAFE:otp')
            if any(k in text for k in ['to\'lov', 'pay', 'to’lov', 'tolov']): tags.append('SAFE:payment')
            if any(k in text for k in ['http', 'havola', 'www']): tags.append('SAFE:link')
            if any(k in text for k in ['akkaunt', 'profil', 'account']): tags.append('SAFE:account')
        
        # Suspicious
        if label == 'SUSPICIOUS':
            if any(k in text for k in ['yutuq', 'sovg\'a', 'prize', 'gift']): tags.append('SUSP:bait')
            if any(k in text for k in ['shoshiling', 'darhol', 'aksiya', 'bugunoq']): tags.append('SUSP:promo')
            if any(k in text for k in ['kutilmagan', 'ssilka', 'koʻring', 'ko\'ring']): tags.append('SUSP:curiosity')
            if 'yangilang' in text: tags.append('SUSP:account_update')
            
        # Dangerous
        if label == 'DANGEROUS':
            # Phishing Login
            if 'login' in text and ('parol' in text or 'verify' in text or 'password' in text or 'auth' in text):
                 tags.append('DANG:phishing_login')
            # Install / APK
            if any(k in text for k in ['.apk', 'install', 'yuklab', 'yuklang']):
                 tags.append('DANG:apk')
            # OTP Harvesting
            if 'kod' in text and 'yuboring' in text:
                 tags.append('DANG:otp_harvesting')
            # TG Scam
            if any(k in text for k in ['premium', 'stars', 'sovg\'a']):
                 tags.append('DANG:tg_scam')
            # Payment
            if any(k in text for k in ['to’lov', 'tolov', 'pay', 'karta', 'card']):
                 tags.append('DANG:payment')

        return tags

    df['hard_tags'] = df.apply(check_hard_cases, axis=1)
    all_tags = [tag for sublist in df['hard_tags'] for tag in sublist]
    tag_counts = pd.Series(all_tags).value_counts()
    print("\nHard Case Coverage Counts:")
    print(tag_counts)

if __name__ == "__main__":
    audit()
