import re
import pandas as pd
from pathlib import Path

def preprocess_v8(text):
    if not isinstance(text, str):
        return ""
    
    # Lowercase
    text = text.lower()
    
    # Normalize Uzbek Cyrillic if present (basic mapping)
    # Note: Full normalization requires a mapping, but for now we rely on TF-IDF learning 
    # the Cyrillic tokens directly.
    
    # Normalize common Uzbek/SMS abbreviations and variations
    text = text.replace("o'", "o‘").replace("o`", "o‘").replace("g'", "g‘").replace("g`", "g‘")
    text = text.replace("raxmat", "rahmat").replace("nima ssilka", "nima havola")
    
    # Protect links by adding spaces around them and keeping common scam tokens
    # We want to keep the domain but maybe mask the specific long paths?
    # For now, we keep it simple but ensure link context is isolated.
    text = re.sub(r'(https?://\S+)', r' \1 ', text)
    
    # Protect file extensions common in scams
    extensions = ['.apk', '.exe', '.scr', '.msi', '.bat', '.zip', '.rar', '.pdf']
    for ext in extensions:
        if ext in text:
            text = text.replace(ext, f' {ext} ')

    # Protect technical keywords from being merged
    tech_keywords = ['login', 'parol', 'password', 'karta', 'card', 'cvv', 'kod', 'code', 'otp']
    for kw in tech_keywords:
        if kw in text:
            text = text.replace(kw, f' {kw} ')

    # Normalize punctuation but keep . / : for technical context (URLs/Files)
    # We remove exclamation marks but they might be useful for 'urgency'
    # Actually, keep ! as a separate token if it's multiple !!!
    text = re.sub(r'!!+', ' multi_excl ', text)
    
    # Final cleanup of non-essential symbols
    text = re.sub(r'[^\w\s\.\/:]', ' ', text)
    
    # Normalize multiple spaces
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

if __name__ == "__main__":
    base_dir = Path(__file__).resolve().parents[1]
    
    # We merge logic should happen before this, but this serves as the V8 preprocessor
    print("Preprocess V8 logic defined.")
