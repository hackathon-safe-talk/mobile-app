import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def build_candidates():
    input_path = base_dir / "data" / "final" / "safetalk_unified_dataset.csv"
    output_dir = base_dir / "data" / "uzbek_expansion"
    output_path = output_dir / "translation_candidates.csv"
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    if not input_path.exists():
        print(f"Error: Could not find {input_path}")
        return
        
    df = pd.read_csv(input_path)
    
    candidates = []
    
    # Identify obvious high-priority English scam patterns
    high_priority_keywords = [
        'win', 'prize', 'claim', 'reward', 'urgent', 'call now', 
        'click', 'link', 'account blocked', 'payment', 'verify', 
        'crypto', 'investment', 'guaranteed'
    ]
    
    for _, row in df.iterrows():
        text = str(row['text'])
        text_lower = text.lower()
        label = row['label']
        source = row['source']
        
        # Simple Language detection heuristic based on Cyrillic/Uzbek hints
        if any(c in text_lower for c in 'абвгдеёжзийклмнопрстуфхцчшщъыьэюя'):
            lang_hint = 'ru'
        elif any(w in text_lower for w in ['salom', 'qalay', 'yaxshi', 'bugun', 'ertaga', 'rahmat', 'bilan']):
            lang_hint = 'uz'
        else:
            lang_hint = 'en'
            
        priority = 'low'
        reason = 'noisy or weak example'
        
        if label == 'scam':
            if any(k in text_lower for k in high_priority_keywords):
                priority = 'high'
                if 'win' in text_lower or 'prize' in text_lower or 'claim' in text_lower:
                    reason = 'strong prize scam pattern'
                elif 'account' in text_lower or 'payment' in text_lower or 'verify' in text_lower:
                    reason = 'urgent financial scam pattern'
                elif 'crypto' in text_lower or 'investment' in text_lower:
                    reason = 'crypto/investment scam'
                elif source == 'telegram':
                    reason = 'telegram promotional scam style'
                else:
                    reason = 'high priority general scam'
            else:
                priority = 'medium'
                reason = 'medium priority scam style'
        else:
            # We want a smaller sample of ham messages
            # Let's pick some random short ones as medium/low priority
            if len(text.split()) < 15:
                if lang_hint == 'uz':
                    priority = 'high'
                    reason = 'uzbek seed example'
                else:
                    priority = 'medium'
                    reason = 'useful ham example for balance'
            
        # We don't want to translate everything. Let's sample carefully.
        # Save all high and some mediums
        if priority == 'high' or (priority == 'medium' and hash(text) % 5 == 0):
            candidates.append({
                'text': text,
                'source': source,
                'label': label,
                'language_hint': lang_hint,
                'priority': priority,
                'reason': reason
            })
            
    cand_df = pd.DataFrame(candidates)
    
    cand_df.to_csv(output_path, index=False, encoding='utf-8')
    
    print("=== Translation Candidates Report ===")
    print(f"Total candidate rows: {len(cand_df)}")
    print("\nPriority Counts:")
    print(cand_df['priority'].value_counts().to_string())
    print("\nLabel Counts:")
    print(cand_df['label'].value_counts().to_string())
    print("\nSource Breakdown:")
    print(cand_df['source'].value_counts().to_string())
    print(f"\nSaved translation candidates to: {output_path}")

if __name__ == "__main__":
    build_candidates()
