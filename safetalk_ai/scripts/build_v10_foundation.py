"""
SafeTalk V10 Foundation Dataset Builder
Stage 1: Merge -> Clean -> Language Split

Loads ALL datasets from ALL versions (V1-V9), normalizes text,
detects language, deduplicates by exact normalized_text match,
and produces 3 clean language-specific base datasets.

Author: SafeTalk AI Pipeline
Date: 2026-04-04
"""
import sys
import io

# Force UTF-8 output to avoid cp1251 encoding errors
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import pandas as pd
import numpy as np
import os
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime

# =================================================================
# CONFIGURATION
# =================================================================

DATA_ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'data')
OUTPUT_DIR = os.path.join(DATA_ROOT, 'v10_foundation')

# =================================================================
# DATASET REGISTRY -- every single dataset file
# =================================================================

# Each entry: (relative_path, text_col, label_col, lang_col, separator, has_header)
DATASET_REGISTRY = [
    # === SOURCES ===
    ("sources/sms_spam_collection.txt", 1, 0, None, "\t", False),  # tab-separated, no header, col0=label, col1=text
    ("sources/spam.csv", "Message", "spamORham", None, ",", True),
    ("sources/sms_uz.csv", "text", "label", None, ",", True),
    ("sources/sms_uz_clean.csv", "text", "label", None, ",", True),
    ("sources/telegram_ham_samples.csv", "text", "label", None, ",", True),
    ("sources/telegram_spam_cleaned.csv", "text", "label", None, ",", True),

    # === PROCESSED — messages_cleaned v1–v6 ===
    ("processed/messages_cleaned.csv", "text", "label", None, ",", True),
    ("processed/messages_cleaned_v2.csv", "text", "label", None, ",", True),
    ("processed/messages_cleaned_v3.csv", "text", "label", None, ",", True),
    ("processed/messages_cleaned_v4.csv", "text", "label", None, ",", True),
    ("processed/messages_cleaned_v5.csv", "text", "label", None, ",", True),
    ("processed/messages_cleaned_v6.csv", "text", "label", None, ",", True),

    # === PROCESSED — master semantic datasets ===
    ("processed/master_semantic_dataset_v7.csv", "text", "risk_label", "language", ",", True),
    ("processed/master_semantic_dataset_v7_cleaned.csv", "text", "risk_label", "language", ",", True),
    ("processed/master_semantic_dataset_v8.csv", "text", "risk_label", "language", ",", True),
    ("processed/master_semantic_dataset_v8_cleaned.csv", "text", "risk_label", "language", ",", True),
    ("processed/master_semantic_dataset_v9_cleaned.csv", "text", "risk_label", "language", ",", True),

    # === PROCESSED — unified raw ===
    ("processed/unified_messages_raw.csv", "text", "label", None, ",", True),

    # === FINAL — unified datasets v1–v6 ===
    ("final/safetalk_unified_dataset.csv", "text", "label", None, ",", True),
    ("final/safetalk_unified_dataset_v2.csv", "text", "label", None, ",", True),
    ("final/safetalk_unified_dataset_v3.csv", "text", "label", None, ",", True),
    ("final/safetalk_unified_dataset_v4.csv", "text", "label", None, ",", True),
    ("final/safetalk_unified_dataset_v5.csv", "text", "label", None, ",", True),
    ("final/safetalk_unified_dataset_v6.csv", "text", "label", None, ",", True),

    # === CONSOLIDATED ===
    ("consolidated/en_final_v2.csv", "text", "label_unified", "language_fixed", ",", True),
    ("consolidated/eng_final1_dataset.csv", "text", "original_label", "language", ",", True),
    ("consolidated/ru_final_v2.csv", "text", "label_unified", "language_fixed", ",", True),
    ("consolidated/rus_final1_dataset.csv", "text", "original_label", "language", ",", True),
    ("consolidated/uz_final_v2.csv", "text", "label_unified", "language_fixed", ",", True),
    ("consolidated/uzb_final1_dataset.csv", "text", "original_label", "language", ",", True),
    ("consolidated/language_conflicts_resolved.csv", "text", "original_label", "language_fixed", ",", True),
    ("consolidated/removed_leakage.csv", "text", "original_label", "language_fixed", ",", True),

    # === EXPANSIONS ===
    ("expansions/hard_cases_v8.csv", "text", "risk_label", "language", ",", True),
    ("expansions/hardening_data_v9.csv", "text", "risk_label", "language", ",", True),
    ("expansions/realistic_v6_messages.csv", "text", "label", None, ",", True),

    # === PHISHING EXPANSION ===
    ("phishing_expansion/uz_financial_scams.csv", "text", "label", None, ",", True),

    # === SECURITY ALERT EXPANSION ===
    ("security_alert_expansion/uz_security_alert_scams.csv", "text", "label", None, ",", True),

    # === UZBEK EXPANSION ===
    ("uzbek_expansion/sms_uz_enriched.csv", "text", "label", None, ",", True),
    ("uzbek_expansion/telegram_uz_enriched.csv", "text", "label", None, ",", True),
    ("uzbek_expansion/translation_candidates.csv", "text", "label", None, ",", True),
    ("uzbek_expansion/uz_dataset_v2.csv", "text", "label", None, ",", True),

    # === SEEDS ===
    ("uzbek_policy_seeds.csv", "text", "risk_label", "language", ",", True),

    # === EMPTY (included for completeness — will produce 0 rows) ===
    ("sms_messages_raw.csv", "text", "label", None, ",", True),
    ("telegram_messages_raw.csv", "text", "label", None, ",", True),
]

# =================================================================
# TEXT NORMALIZATION (for dedup ONLY)
# =================================================================

def normalize_text(text):
    """Normalize text for deduplication comparison only.
    
    Rules:
    - lowercase
    - trim leading/trailing whitespace
    - collapse multiple spaces to single
    - normalize quote characters
    
    Does NOT:
    - remove words
    - change meaning
    - aggressive cleaning
    """
    if pd.isna(text) or not isinstance(text, str):
        return ""
    
    t = str(text).lower().strip()
    
    # Normalize various quote characters to standard ASCII
    t = t.replace('\u201c', '"').replace('\u201d', '"')  # " "
    t = t.replace('\u2018', "'").replace('\u2019', "'")  # ' '
    t = t.replace('\u00ab', '"').replace('\u00bb', '"')  # « »
    t = t.replace('\u201e', '"').replace('\u201f', '"')  # „ ‟
    
    # Collapse multiple whitespace to single space
    t = re.sub(r'\s+', ' ', t).strip()
    
    return t


# =================================================================
# LANGUAGE DETECTION
# =================================================================

# Uzbek-specific Latin markers
UZ_SPECIFIC_CHARS = set("o'g'O'G'")  # oʻ gʻ with apostrophe
UZ_DIGRAPHS = ['sh', 'ch', 'ng', "o'", "g'", "o`", "g`", "oʻ", "gʻ"]
UZ_COMMON_WORDS = {
    # Very common Uzbek words that distinguish from English
    'salom', 'rahmat', 'kerak', 'uchun', 'bilan', 'haqida', 'bo\'ldi',
    'qilish', 'qiling', 'qilib', 'bosing', 'kirish', 'chiqish',
    'parol', 'karta', 'hisobingiz', 'kartangiz', 'telefon',
    'xabar', 'yuborish', 'olish', 'berish', 'ko\'rish',
    'iltimos', 'marhamat', 'hurmatli', 'assalomu', 'alaykum',
    'sizning', 'sizga', 'siz', 'men', 'biz', 'ular',
    'va', 'ham', 'yoki', 'lekin', 'chunki', 'shu',
    'bir', 'bu', 'u', 'har', 'hamma', 'barcha',
    'yo\'q', 'bor', 'emas', 'ekan', 'edi', 'bo\'lsa',
    'qanday', 'nima', 'kim', 'qaerda', 'qachon',
    'tiklash', 'bloklandi', 'tasdiqlash', 'kod', 'kodi',
    'buyurtma', 'to\'lov', 'pullik', 'pul', 'so\'m', 'summa',
    'mijoz', 'foydalanuvchi', 'xizmat', 'dastur',
    'ma\'lumot', 'shaxsiy', 'maxfiy', 'xavfsiz',
    'uchun', 'orqali', 'tekshirish', 'yutuq', 'sovg\'a',
    'ishlamayapti', 'ishlamoqda', 'ishlaydi',
    'boraman', 'kelaman', 'ketaman', 'turaman',
    'bugun', 'ertaga', 'kecha', 'hozir',
    'aka', 'opa', 'uka', 'singil', 'do\'stim',
    'yaxshi', 'yomon', 'katta', 'kichik',
    'maktab', 'universitet', 'ish', 'uy',
    'qalay', 'qalaysan', 'qalaysiz',
    'kechirasiz', 'uzr',
    # Common Uzbek Cyrillic
    'салом', 'рахмат', 'керак', 'учун', 'билан',
    'ҳақида', 'бўлди', 'қилиш', 'босинг', 'кириш',
    'хабар', 'юбориш', 'олиш', 'бериш',
    'илтимос', 'марҳамат', 'ҳурматли',
    'сизнинг', 'сизга', 'сиз', 'мен', 'биз',
    'ва', 'ҳам', 'ёки', 'лекин', 'чунки',
    'бир', 'бу', 'ҳар', 'ҳамма', 'барча',
    'йўқ', 'бор', 'эмас', 'экан', 'эди',
    'қандай', 'нима', 'ким', 'қаерда', 'қачон',
}

# Russian common words (to distinguish from Uzbek Cyrillic)
RU_COMMON_WORDS = {
    'и', 'в', 'на', 'не', 'что', 'это', 'он', 'она', 'они',
    'был', 'была', 'были', 'быть', 'для', 'как', 'так',
    'все', 'вы', 'мы', 'его', 'ее', 'их', 'от', 'до',
    'но', 'по', 'из', 'за', 'то', 'да', 'нет',
    'можно', 'нужно', 'только', 'уже', 'еще', 'тоже',
    'если', 'когда', 'где', 'кто', 'чем', 'или',
    'бы', 'же', 'ли', 'вот', 'тут', 'там',
    'свой', 'этот', 'тот', 'один', 'два', 'три',
    'очень', 'потом', 'сейчас', 'здесь', 'сюда',
    'деньги', 'рублей', 'рубль', 'перевод', 'счет', 'банк',
    'срочно', 'требуется', 'прибыль', 'удалённая', 'работа',
    'привет', 'здравствуйте', 'спасибо', 'пожалуйста',
}

# Cyrillic character ranges
CYRILLIC_PATTERN = re.compile(r'[\u0400-\u04FF]')
LATIN_PATTERN = re.compile(r'[a-zA-Z]')

# Uzbek-specific Cyrillic characters not in Russian
UZ_CYRILLIC_CHARS = set('ўғқҳ')  # ў ғ қ ҳ — unique to Uzbek Cyrillic


def tokenize_simple(text):
    """Simple whitespace + punctuation tokenization."""
    return re.findall(r'[a-zA-Z\u0400-\u04FF\'`\u2018\u2019\u02BB\u02BC]+', text.lower())


def detect_language(text, lang_hint=None):
    """Detect language of text using token-based heuristics.
    
    Returns: (language_code, confidence, method)
        language_code: 'uz', 'ru', 'en'
        confidence: 'high', 'medium', 'low'
        method: description of how detection was done
    """
    if pd.isna(text) or not isinstance(text, str) or len(str(text).strip()) == 0:
        return ('en', 'low', 'empty_text')
    
    text = str(text)
    tokens = tokenize_simple(text)
    
    if len(tokens) == 0:
        return ('en', 'low', 'no_tokens')
    
    # Count character types
    cyrillic_count = len(CYRILLIC_PATTERN.findall(text))
    latin_count = len(LATIN_PATTERN.findall(text))
    total_alpha = cyrillic_count + latin_count
    
    if total_alpha == 0:
        return ('en', 'low', 'no_alpha_chars')
    
    cyrillic_ratio = cyrillic_count / total_alpha
    latin_ratio = latin_count / total_alpha
    
    # ─── CYRILLIC-DOMINANT TEXT ───
    if cyrillic_ratio >= 0.70:
        # Check for Uzbek Cyrillic characters
        has_uz_cyrillic = any(c in text for c in UZ_CYRILLIC_CHARS)
        
        # Count Uzbek vs Russian word matches
        lower_tokens = set(t.lower() for t in tokens)
        uz_word_hits = len(lower_tokens & UZ_COMMON_WORDS)
        ru_word_hits = len(lower_tokens & RU_COMMON_WORDS)
        
        if has_uz_cyrillic and uz_word_hits > ru_word_hits:
            return ('uz', 'high', 'cyrillic_uz_chars_and_words')
        elif has_uz_cyrillic and uz_word_hits > 0:
            return ('uz', 'medium', 'cyrillic_uz_chars')
        elif ru_word_hits > 0:
            return ('ru', 'high', 'cyrillic_ru_words')
        else:
            # Default Cyrillic to Russian
            return ('ru', 'medium', 'cyrillic_default')
    
    # ─── LATIN-DOMINANT TEXT ───
    if latin_ratio >= 0.70:
        # Check for Uzbek Latin markers
        text_lower = text.lower()
        lower_tokens_set = set(t.lower() for t in tokens)
        
        # Count Uzbek markers
        uz_markers = 0
        
        # Check for Uzbek-specific apostrophe characters
        if "o'" in text_lower or "g'" in text_lower or "o`" in text_lower or "g`" in text_lower:
            uz_markers += 3
        if "oʻ" in text_lower or "gʻ" in text_lower:
            uz_markers += 3
        
        # Check Uzbek word matches
        uz_word_hits = len(lower_tokens_set & UZ_COMMON_WORDS)
        uz_markers += uz_word_hits * 2
        
        # Check for Uzbek digraph density
        for digraph in ['sh', 'ch']:
            if digraph in text_lower:
                uz_markers += 0.5  # mild signal, also common in English
        
        en_word_count = 0
        # Simple English detection: common English words
        EN_COMMON = {'the', 'is', 'are', 'was', 'were', 'have', 'has', 'had',
                     'do', 'does', 'did', 'will', 'would', 'could', 'should',
                     'can', 'may', 'might', 'shall', 'must',
                     'a', 'an', 'this', 'that', 'these', 'those',
                     'i', 'you', 'he', 'she', 'it', 'we', 'they',
                     'my', 'your', 'his', 'her', 'its', 'our', 'their',
                     'am', 'be', 'been', 'being',
                     'not', 'no', 'yes', 'ok',
                     'at', 'by', 'for', 'from', 'in', 'of', 'on', 'to', 'with',
                     'and', 'but', 'or', 'if', 'so', 'as', 'than',
                     'just', 'also', 'very', 'too', 'only', 'now',
                     'here', 'there', 'where', 'when', 'how', 'what', 'who',
                     'all', 'each', 'every', 'both', 'few', 'more', 'most',
                     'other', 'some', 'such', 'than', 'then', 'well',
                     'free', 'win', 'call', 'text', 'send', 'reply',
                     'claim', 'prize', 'cash', 'offer', 'click', 'now',
                     'please', 'dear', 'hi', 'hello', 'thanks', 'thank',
                     'sorry', 'good', 'great', 'nice', 'love', 'like', 'want', 'need',
                     'get', 'got', 'go', 'going', 'come', 'coming',
                     'know', 'think', 'see', 'look', 'find', 'give', 'tell',
                     'try', 'ask', 'work', 'seem', 'feel', 'leave', 'call'}
        
        en_word_count = len(lower_tokens_set & EN_COMMON)
        
        # Decision
        total_tokens = len(tokens)
        uz_token_ratio = uz_word_hits / total_tokens if total_tokens > 0 else 0
        en_token_ratio = en_word_count / total_tokens if total_tokens > 0 else 0
        
        if uz_markers >= 3 and uz_token_ratio > en_token_ratio:
            return ('uz', 'high', 'latin_uz_markers_and_words')
        elif uz_markers >= 2:
            return ('uz', 'medium', 'latin_uz_markers')
        elif uz_word_hits >= 2 and en_word_count <= 1:
            return ('uz', 'medium', 'latin_uz_words_dominant')
        elif en_word_count >= 2:
            return ('en', 'high', 'latin_en_words')
        elif en_word_count >= 1 and uz_word_hits == 0:
            return ('en', 'medium', 'latin_en_default')
        elif uz_word_hits >= 1:
            return ('uz', 'low', 'latin_uz_weak')
        else:
            return ('en', 'low', 'latin_default')
    
    # ─── MIXED SCRIPT TEXT ───
    # Neither Cyrillic nor Latin dominates (≥70%)
    lower_tokens_set = set(t.lower() for t in tokens)
    uz_hits = len(lower_tokens_set & UZ_COMMON_WORDS)
    ru_hits = len(lower_tokens_set & RU_COMMON_WORDS)
    en_common_set = {'the', 'is', 'are', 'was', 'have', 'has', 'do', 'does',
                     'a', 'an', 'this', 'that', 'i', 'you', 'he', 'she',
                     'not', 'and', 'but', 'or', 'if', 'for', 'from', 'to', 'with'}
    en_hits = len(lower_tokens_set & en_common_set)
    
    # Assign to dominant language
    scores = {'uz': uz_hits, 'ru': ru_hits, 'en': en_hits}
    dominant = max(scores, key=scores.get)
    
    if scores[dominant] == 0:
        # No word matches at all — use script ratio
        if cyrillic_ratio > latin_ratio:
            return ('ru', 'low', 'mixed_cyrillic_dominant')
        else:
            return ('en', 'low', 'mixed_latin_dominant')
    
    return (dominant, 'low', f'mixed_script_{dominant}_words')


def resolve_language(detected_lang, detected_conf, lang_hint, text):
    """Resolve final language, considering hint and detection.
    
    If a language column exists in the source data, we use it as a hint
    but validate against detection. Detection wins if hint seems wrong.
    """
    if lang_hint is None or pd.isna(lang_hint) or str(lang_hint).strip() == '':
        return detected_lang, detected_conf, 'detected_only'
    
    hint = str(lang_hint).strip().lower()
    
    # Normalize hint values
    hint_map = {
        'en': 'en', 'eng': 'en', 'english': 'en',
        'uz': 'uz', 'uzb': 'uz', 'uzbek': 'uz',
        'ru': 'ru', 'rus': 'ru', 'russian': 'ru',
        'mixed': None,  # mixed hints are not reliable
    }
    
    normalized_hint = hint_map.get(hint, None)
    
    if normalized_hint is None:
        # Unknown or 'mixed' hint — rely on detection
        return detected_lang, detected_conf, 'hint_unknown_using_detected'
    
    if normalized_hint == detected_lang:
        # Hint and detection agree — high confidence
        return detected_lang, 'high', 'hint_and_detected_agree'
    
    # Disagreement — detection overrides if confident
    if detected_conf == 'high':
        return detected_lang, 'medium', f'detected_overrides_hint_{normalized_hint}'
    elif detected_conf == 'medium':
        # Toss-up — slight preference for detection (it uses actual text analysis)
        return detected_lang, 'low', f'detected_vs_hint_{normalized_hint}'
    else:
        # Low confidence detection — trust the hint
        return normalized_hint, 'low', f'hint_used_low_detection'


# =================================================================
# DATASET LOADER
# =================================================================

def load_dataset(rel_path, text_col, label_col, lang_col, sep, has_header):
    """Load a single dataset and normalize to standard schema."""
    full_path = os.path.join(DATA_ROOT, rel_path)
    filename = os.path.basename(rel_path)
    
    if not os.path.exists(full_path):
        print(f"  [WARN] File not found: {full_path}")
        return pd.DataFrame()
    
    try:
        if has_header:
            df = pd.read_csv(full_path, sep=sep, encoding='utf-8', on_bad_lines='skip')
        else:
            df = pd.read_csv(full_path, sep=sep, header=None, encoding='utf-8', on_bad_lines='skip')
        
        if len(df) == 0:
            print(f"  [INFO] Empty file: {filename}")
            return pd.DataFrame()
        
        # Extract text
        if isinstance(text_col, int):
            text_series = df.iloc[:, text_col]
        else:
            if text_col not in df.columns:
                print(f"  [WARN] Text column '{text_col}' not found in {filename}. Columns: {list(df.columns)}")
                return pd.DataFrame()
            text_series = df[text_col]
        
        # Extract label
        if isinstance(label_col, int):
            label_series = df.iloc[:, label_col]
        else:
            if label_col in df.columns:
                label_series = df[label_col]
            else:
                label_series = pd.Series(['unknown'] * len(df))
        
        # Extract language hint
        if lang_col is not None and lang_col in df.columns:
            lang_series = df[lang_col]
        else:
            lang_series = pd.Series([None] * len(df))
        
        result = pd.DataFrame({
            'text': text_series.astype(str),
            'original_label': label_series.astype(str),
            'language_hint': lang_series,
            'source_dataset': filename,
        })
        
        # Drop rows with empty/nan text
        result = result[result['text'].str.strip().str.len() > 0]
        result = result[result['text'] != 'nan']
        
        return result
    
    except Exception as e:
        print(f"  [ERROR] Failed to load {filename}: {e}")
        return pd.DataFrame()


# =================================================================
# MAIN PIPELINE
# =================================================================

def main():
    start_time = datetime.now()
    print("=" * 70)
    print("SafeTalk V10 Foundation Dataset Builder")
    print(f"Started: {start_time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # ─── PHASE 1: Load all datasets ───
    print("\n" + "─" * 70)
    print("PHASE 1: Loading ALL datasets")
    print("─" * 70)
    
    all_frames = []
    source_stats = {}
    
    for i, (rel_path, text_col, label_col, lang_col, sep, has_header) in enumerate(DATASET_REGISTRY):
        filename = os.path.basename(rel_path)
        print(f"\n  [{i+1}/{len(DATASET_REGISTRY)}] Loading: {rel_path}")
        
        df = load_dataset(rel_path, text_col, label_col, lang_col, sep, has_header)
        
        if len(df) > 0:
            all_frames.append(df)
            source_stats[filename] = len(df)
            print(f"    → {len(df)} rows loaded")
        else:
            source_stats[filename] = 0
            print(f"    → 0 rows (empty or error)")
    
    # Merge all
    merged = pd.concat(all_frames, ignore_index=True)
    total_before = len(merged)
    
    print(f"\n{'=' * 70}")
    print(f"TOTAL ROWS LOADED (before any processing): {total_before:,}")
    print(f"{'=' * 70}")
    
    # ─── PHASE 2: Normalize text ───
    print("\n" + "─" * 70)
    print("PHASE 2: Normalizing text for deduplication")
    print("─" * 70)
    
    merged['normalized_text'] = merged['text'].apply(normalize_text)
    
    # Remove rows with empty normalized text
    empty_count = (merged['normalized_text'].str.len() == 0).sum()
    merged = merged[merged['normalized_text'].str.len() > 0].reset_index(drop=True)
    print(f"  Removed {empty_count} rows with empty normalized text")
    print(f"  Remaining: {len(merged):,} rows")
    
    total_after_norm = len(merged)
    
    # ─── PHASE 3: Language detection ───
    print("\n" + "─" * 70)
    print("PHASE 3: Detecting languages")
    print("─" * 70)
    
    lang_results = []
    uncertain_cases = []
    
    for idx, row in merged.iterrows():
        if idx % 10000 == 0:
            print(f"  Processing row {idx:,}/{len(merged):,}...")
        
        # Detect from text
        detected_lang, detected_conf, detected_method = detect_language(row['text'])
        
        # Resolve with hint
        final_lang, final_conf, resolve_method = resolve_language(
            detected_lang, detected_conf, row['language_hint'], row['text']
        )
        
        lang_results.append({
            'language': final_lang,
            'lang_confidence': final_conf,
            'lang_method': resolve_method,
        })
        
        # Log uncertain cases
        if final_conf == 'low' or 'overrides' in resolve_method or 'mixed' in resolve_method:
            text_preview = str(row['text'])[:100]
            uncertain_cases.append({
                'text_preview': text_preview,
                'detected_lang': detected_lang,
                'detected_conf': detected_conf,
                'detected_method': detected_method,
                'language_hint': row['language_hint'],
                'final_lang': final_lang,
                'final_conf': final_conf,
                'resolve_method': resolve_method,
                'source_dataset': row['source_dataset'],
            })
    
    lang_df = pd.DataFrame(lang_results)
    merged['language'] = lang_df['language']
    merged['lang_confidence'] = lang_df['lang_confidence']
    merged['lang_method'] = lang_df['lang_method']
    
    print(f"\n  Language distribution (before dedup):")
    lang_counts = merged['language'].value_counts()
    for lang, count in lang_counts.items():
        print(f"    {lang}: {count:,}")
    
    print(f"\n  Uncertain cases: {len(uncertain_cases):,}")
    
    # Save uncertain cases log
    if uncertain_cases:
        uncertain_df = pd.DataFrame(uncertain_cases)
        uncertain_path = os.path.join(OUTPUT_DIR, 'uncertain_language_log.csv')
        uncertain_df.to_csv(uncertain_path, index=False, encoding='utf-8-sig')
        print(f"  Saved uncertain cases to: {uncertain_path}")
    
    # ─── PHASE 4: Deduplication ───
    print("\n" + "─" * 70)
    print("PHASE 4: Deduplicating by exact normalized_text match")
    print("─" * 70)
    
    # Find duplicate counts before removal
    dup_counts = merged.groupby('normalized_text').size()
    dup_texts = dup_counts[dup_counts > 1]
    
    # Top 10 most duplicated
    top_dups = dup_texts.sort_values(ascending=False).head(10)
    
    print(f"\n  Unique normalized texts: {len(dup_counts):,}")
    print(f"  Texts appearing more than once: {len(dup_texts):,}")
    print(f"\n  Top 10 most duplicated texts:")
    for i, (text, count) in enumerate(top_dups.items()):
        preview = text[:80] + "..." if len(text) > 80 else text
        print(f"    {i+1}. [{count}x] \"{preview}\"")
    
    # Deduplicate: keep first occurrence (which preserves source traceability)
    before_dedup = len(merged)
    merged = merged.drop_duplicates(subset=['normalized_text'], keep='first').reset_index(drop=True)
    after_dedup = len(merged)
    duplicates_removed = before_dedup - after_dedup
    
    print(f"\n  Before dedup: {before_dedup:,}")
    print(f"  After dedup:  {after_dedup:,}")
    print(f"  Duplicates removed: {duplicates_removed:,}")
    
    # ─── PHASE 5: Split by language and save ───
    print("\n" + "─" * 70)
    print("PHASE 5: Splitting by language and saving")
    print("─" * 70)
    
    output_columns = ['text', 'language', 'source_dataset', 'original_label', 'normalized_text']
    
    lang_datasets = {}
    lang_file_map = {
        'uz': 'uzb_base_dataset_v1.csv',
        'ru': 'rus_base_dataset_v1.csv', 
        'en': 'eng_base_dataset_v1.csv',
    }
    
    for lang_code, filename in lang_file_map.items():
        subset = merged[merged['language'] == lang_code][output_columns].reset_index(drop=True)
        output_path = os.path.join(OUTPUT_DIR, filename)
        subset.to_csv(output_path, index=False, encoding='utf-8-sig')
        lang_datasets[lang_code] = subset
        print(f"  {filename}: {len(subset):,} rows → {output_path}")
    
    # ─── PHASE 6: Generate comprehensive report ───
    print("\n" + "─" * 70)
    print("PHASE 6: Generating report")
    print("─" * 70)
    
    report_lines = []
    report_lines.append("=" * 70)
    report_lines.append("SafeTalk V10 Foundation Dataset — Build Report")
    report_lines.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report_lines.append("=" * 70)
    
    report_lines.append("")
    report_lines.append("═══ 1. OVERALL STATISTICS ═══")
    report_lines.append(f"  Total datasets loaded:           {len(DATASET_REGISTRY)}")
    report_lines.append(f"  Total samples BEFORE dedup:      {total_before:,}")
    report_lines.append(f"  Removed (empty text):            {empty_count:,}")
    report_lines.append(f"  After normalization:             {total_after_norm:,}")
    report_lines.append(f"  Total samples AFTER dedup:       {after_dedup:,}")
    report_lines.append(f"  Exact duplicates removed:        {duplicates_removed:,}")
    report_lines.append(f"  Dedup ratio:                     {duplicates_removed/total_after_norm*100:.1f}%")
    
    report_lines.append("")
    report_lines.append("═══ 2. FINAL DATASET SIZES ═══")
    for lang_code, filename in lang_file_map.items():
        count = len(lang_datasets[lang_code])
        report_lines.append(f"  {filename}: {count:,} rows")
    report_lines.append(f"  TOTAL: {sum(len(v) for v in lang_datasets.values()):,} rows")
    
    report_lines.append("")
    report_lines.append("═══ 3. SOURCE CONTRIBUTION ═══")
    
    # Sort sources by contribution
    sorted_sources = sorted(source_stats.items(), key=lambda x: x[1], reverse=True)
    for filename, count in sorted_sources:
        if count > 0:
            report_lines.append(f"  {filename}: {count:,} rows")
    
    report_lines.append("")
    report_lines.append("═══ 4. PER-SOURCE UNIQUE CONTRIBUTION (after dedup) ═══")
    
    # Count how many unique texts each source contributed to final dataset
    source_contribution = merged.groupby('source_dataset').size().sort_values(ascending=False)
    for source, count in source_contribution.items():
        report_lines.append(f"  {source}: {count:,} unique rows in final")
    
    report_lines.append("")
    report_lines.append("═══ 5. LANGUAGE DISTRIBUTION (after dedup) ═══")
    final_lang_counts = merged['language'].value_counts()
    for lang, count in final_lang_counts.items():
        pct = count / len(merged) * 100
        report_lines.append(f"  {lang}: {count:,} ({pct:.1f}%)")
    
    report_lines.append("")
    report_lines.append("═══ 6. LANGUAGE CONFIDENCE DISTRIBUTION ═══")
    conf_counts = merged['lang_confidence'].value_counts()
    for conf, count in conf_counts.items():
        report_lines.append(f"  {conf}: {count:,}")
    
    report_lines.append("")
    report_lines.append("═══ 7. LANGUAGE DETECTION ISSUES ═══")
    report_lines.append(f"  Total uncertain/mixed cases: {len(uncertain_cases):,}")
    if uncertain_cases:
        # Breakdown by type
        uncertain_df_report = pd.DataFrame(uncertain_cases)
        method_counts = uncertain_df_report['resolve_method'].value_counts()
        for method, count in method_counts.items():
            report_lines.append(f"    {method}: {count:,}")
        
        # Sample mislabeled texts (hint != detected)
        mislabeled = uncertain_df_report[uncertain_df_report['resolve_method'].str.contains('overrides', na=False)]
        if len(mislabeled) > 0:
            report_lines.append(f"\n  Mislabeled texts (detection overrode hint): {len(mislabeled):,}")
            for _, row in mislabeled.head(10).iterrows():
                report_lines.append(f"    [{row['language_hint']}→{row['final_lang']}] \"{row['text_preview'][:60]}...\"")
    
    report_lines.append("")
    report_lines.append("═══ 8. TOP 10 MOST DUPLICATED TEXTS ═══")
    for i, (text, count) in enumerate(top_dups.items()):
        preview = text[:100] + "..." if len(text) > 100 else text
        report_lines.append(f"  {i+1}. [{count}x] \"{preview}\"")
    
    report_lines.append("")
    report_lines.append("═══ 9. LABEL DISTRIBUTION (per language, after dedup) ═══")
    for lang_code in ['uz', 'ru', 'en']:
        subset = lang_datasets[lang_code]
        report_lines.append(f"\n  --- {lang_code.upper()} ---")
        label_counts = subset['original_label'].value_counts()
        for label, count in label_counts.items():
            report_lines.append(f"    {label}: {count:,}")
    
    report_lines.append("")
    report_lines.append("═══ 10. VERIFICATION CHECKSUMS ═══")
    for lang_code, filename in lang_file_map.items():
        subset = lang_datasets[lang_code]
        # Verify no exact dups remain
        n_unique = subset['normalized_text'].nunique()
        n_total = len(subset)
        has_dups = "NO DUPLICATES ✓" if n_unique == n_total else f"DUPLICATES FOUND ✗ ({n_total - n_unique})"
        report_lines.append(f"  {filename}: {n_total:,} rows, {n_unique:,} unique — {has_dups}")
    
    report_lines.append("")
    report_lines.append("=" * 70)
    report_lines.append("END OF REPORT")
    report_lines.append("=" * 70)
    
    # Save report
    report_text = "\n".join(report_lines)
    report_path = os.path.join(OUTPUT_DIR, 'foundation_report.txt')
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(report_text)
    
    print(report_text)
    
    end_time = datetime.now()
    elapsed = (end_time - start_time).total_seconds()
    print(f"\n\nPipeline completed in {elapsed:.1f} seconds")
    print(f"Output directory: {OUTPUT_DIR}")


if __name__ == '__main__':
    main()
