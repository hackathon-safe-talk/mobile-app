#!/usr/bin/env python3
"""
SafeTalk Dataset Consolidation Script
======================================
Produces 3 FINAL language-separated datasets:
  - uzb_final1_dataset.csv
  - rus_final1_dataset.csv
  - eng_final1_dataset.csv

Rules:
  - ALL datasets included (V1-V9, sources, expansions, unified, legacy)
  - Language detection via column or text analysis
  - Exact-duplicate removal only (after normalization)
  - Full traceability (source_dataset column)
  - Comprehensive report
"""

import pandas as pd
import numpy as np
import os
import re
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime

# ============================================================
# CONFIGURATION
# ============================================================
BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'data'))
OUTPUT_DIR = os.path.join(BASE_DIR, 'consolidated')
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ============================================================
# LANGUAGE DETECTION
# ============================================================

# Uzbek Cyrillic-specific characters (not in standard Russian)
UZ_CYRILLIC_CHARS = set('ўқғҳЎҚҒҲ')

# Uzbek Latin markers
UZ_LATIN_MARKERS = re.compile(
    r"(oʻ|gʻ|o\'|g\'|o\u2018|g\u2018|"
    r"\bsh\b|\bch\b|\bng\b|"
    r"\bsiz\b|\bsizning\b|\bsalom\b|\brahmat\b|\bkerak\b|\bbormi\b|"
    r"\bbilan\b|\bqiling\b|\bkiriting\b|\bkartangiz\b|\bhisobingiz\b|"
    r"\btasdiqlang\b|\bhavolani\b|\bxabar\b|\bxavfsizlik\b|"
    r"\byuborish\b|\byuboring\b|\btizimga\b|\bparol\b|"
    r"\bshaxsiy\b|\bma\'lumot\b|\bmalumot\b|\btugmani\b|"
    r"\bbosing\b|\bolish\b|\buchun\b|\borqali\b|"
    r"\bkodni\b|\btasdiqlash\b|\bbank\b|\bhisob\b|"
    r"\bkredit\b|\btransaksiya\b|\bshubhali\b|\bbloklandi\b|"
    r"\bkarta\b|\bbalans\b|\bmurojaat\b|\btelefon\b|"
    r"\braqamingiz\b|\bto.lov\b|\bpul\b|\bso.m\b)",
    re.IGNORECASE
)

# Common Russian word patterns
RU_WORD_MARKERS = re.compile(
    r"(\bваш\b|\bвашего\b|\bвнимание\b|\bсчет\b|\bкарта\b|"
    r"\bбанк\b|\bбезопасност\b|\bсрочно\b|\bперевод\b|"
    r"\bподтвердите\b|\bссылк\b|\bсообщени\b|\bномер\b|"
    r"\bоплат\b|\bакаунт\b|\bзаблокирован\b|\bпароль\b|"
    r"\bкод\b|\bвход\b|\bпожалуйста\b|\bздравствуйте\b|"
    r"\bдобрый\b|\bдень\b|\bспасибо\b|\bпривет\b|"
    r"\bнажмите\b|\bперейдите\b|\bуведомлени\b|\bвы\b|"
    r"\bне\b|\bна\b|\bи\b|\bв\b|\bот\b|\bс\b|\bпо\b|"
    r"\bэто\b|\bкак\b|\bбыл\b|\bбыть\b|\bесли\b)",
    re.IGNORECASE
)

CYRILLIC_RE = re.compile(r'[\u0400-\u04FF]')
LATIN_RE = re.compile(r'[a-zA-Z]')


def detect_language(text):
    """
    Detect language of text.
    Returns: ('uz', 'ru', 'en', or 'mixed') and confidence info.
    """
    if not isinstance(text, str) or len(text.strip()) == 0:
        return 'en', 'empty_default', 1.0

    text_clean = text.strip()

    # Count character types
    cyrillic_count = len(CYRILLIC_RE.findall(text_clean))
    latin_count = len(LATIN_RE.findall(text_clean))
    total_alpha = cyrillic_count + latin_count

    if total_alpha == 0:
        return 'en', 'no_alpha_default', 1.0

    cyrillic_ratio = cyrillic_count / total_alpha
    latin_ratio = latin_count / total_alpha

    # Predominantly Cyrillic
    if cyrillic_ratio >= 0.7:
        # Check for Uzbek Cyrillic markers
        uz_cyrillic_hits = sum(1 for c in text_clean if c in UZ_CYRILLIC_CHARS)
        if uz_cyrillic_hits > 0:
            return 'uz', 'cyrillic_uz_chars', cyrillic_ratio
        # Check Russian word markers
        ru_hits = len(RU_WORD_MARKERS.findall(text_clean))
        if ru_hits > 0:
            return 'ru', 'cyrillic_ru_words', cyrillic_ratio
        # Default Cyrillic to Russian
        return 'ru', 'cyrillic_default', cyrillic_ratio

    # Predominantly Latin
    if latin_ratio >= 0.7:
        # Check for Uzbek Latin markers
        uz_hits = len(UZ_LATIN_MARKERS.findall(text_clean))
        if uz_hits >= 1:
            return 'uz', 'latin_uz_markers', latin_ratio
        # Default Latin to English
        return 'en', 'latin_default', latin_ratio

    # Mixed - determine dominant
    if cyrillic_ratio > latin_ratio:
        uz_cyrillic_hits = sum(1 for c in text_clean if c in UZ_CYRILLIC_CHARS)
        if uz_cyrillic_hits > 0:
            return 'uz', 'mixed_uz_cyrillic', cyrillic_ratio
        return 'ru', 'mixed_cyrillic_dominant', cyrillic_ratio
    else:
        uz_hits = len(UZ_LATIN_MARKERS.findall(text_clean))
        if uz_hits >= 1:
            return 'uz', 'mixed_uz_latin', latin_ratio
        return 'en', 'mixed_latin_dominant', latin_ratio


def normalize_language_code(lang):
    """Normalize language codes from existing columns."""
    if not isinstance(lang, str):
        return None
    lang = lang.strip().lower()
    mapping = {
        'uz': 'uz', 'uzb': 'uz', 'uzbek': 'uz',
        'ru': 'ru', 'rus': 'ru', 'russian': 'ru',
        'en': 'en', 'eng': 'en', 'english': 'en',
        'mixed': 'mixed',
    }
    return mapping.get(lang, None)


# ============================================================
# NORMALIZATION FOR DEDUP
# ============================================================

def normalize_for_dedup(text):
    """Normalize text for deduplication comparison only."""
    if not isinstance(text, str):
        return ''
    t = text.lower().strip()
    # Normalize whitespace
    t = re.sub(r'\s+', ' ', t)
    # Normalize quotes
    t = t.replace('\u201c', '"').replace('\u201d', '"')
    t = t.replace('\u2018', "'").replace('\u2019', "'")
    t = t.replace('\u00ab', '"').replace('\u00bb', '"')
    t = t.replace('`', "'")
    return t.strip()


# ============================================================
# DATASET LOADERS
# ============================================================

def load_csv_safe(filepath, encoding='utf-8'):
    """Load CSV with fallback encoding."""
    try:
        return pd.read_csv(filepath, encoding=encoding)
    except UnicodeDecodeError:
        return pd.read_csv(filepath, encoding='latin-1')
    except Exception as e:
        print(f"  ERROR loading {filepath}: {e}")
        return pd.DataFrame()


def load_all_datasets():
    """Load ALL datasets and return list of (filename, DataFrame) with standardized columns."""
    datasets = []

    # -------------------------------------------------------
    # 1. PROCESSED: messages_cleaned v1-v6
    # -------------------------------------------------------
    mc_files = [
        ('messages_cleaned.csv', 'processed/messages_cleaned.csv'),
        ('messages_cleaned_v2.csv', 'processed/messages_cleaned_v2.csv'),
        ('messages_cleaned_v3.csv', 'processed/messages_cleaned_v3.csv'),
        ('messages_cleaned_v4.csv', 'processed/messages_cleaned_v4.csv'),
        ('messages_cleaned_v5.csv', 'processed/messages_cleaned_v5.csv'),
        ('messages_cleaned_v6.csv', 'processed/messages_cleaned_v6.csv'),
    ]
    for name, rel_path in mc_files:
        fp = os.path.join(BASE_DIR, rel_path)
        if os.path.exists(fp):
            df = load_csv_safe(fp)
            if len(df) > 0:
                out = pd.DataFrame()
                out['text'] = df['text']
                out['original_label'] = df.get('label', pd.Series([None]*len(df)))
                out['source_dataset'] = name
                out['_has_lang_col'] = False
                datasets.append((name, out))
                print(f"  Loaded {name}: {len(out)} rows")

    # -------------------------------------------------------
    # 2. PROCESSED: unified_messages_raw.csv
    # -------------------------------------------------------
    fp = os.path.join(BASE_DIR, 'processed', 'unified_messages_raw.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'unified_messages_raw.csv'
            out['_has_lang_col'] = False
            datasets.append(('unified_messages_raw.csv', out))
            print(f"  Loaded unified_messages_raw.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 3. PROCESSED: master_semantic v7, v7_cleaned, v8, v8_cleaned, v9_cleaned
    # -------------------------------------------------------
    sem_files = [
        ('master_semantic_dataset_v7.csv', 'processed/master_semantic_dataset_v7.csv'),
        ('master_semantic_dataset_v7_cleaned.csv', 'processed/master_semantic_dataset_v7_cleaned.csv'),
        ('master_semantic_dataset_v8.csv', 'processed/master_semantic_dataset_v8.csv'),
        ('master_semantic_dataset_v8_cleaned.csv', 'processed/master_semantic_dataset_v8_cleaned.csv'),
        ('master_semantic_dataset_v9_cleaned.csv', 'processed/master_semantic_dataset_v9_cleaned.csv'),
    ]
    for name, rel_path in sem_files:
        fp = os.path.join(BASE_DIR, rel_path)
        if os.path.exists(fp):
            df = load_csv_safe(fp)
            if len(df) > 0:
                out = pd.DataFrame()
                out['text'] = df['text']
                # Build composite label from risk_label + intent_label
                labels = []
                for _, row in df.iterrows():
                    parts = []
                    if pd.notna(row.get('risk_label')):
                        parts.append(str(row['risk_label']))
                    if pd.notna(row.get('intent_label')):
                        parts.append(str(row['intent_label']))
                    labels.append('|'.join(parts) if parts else None)
                out['original_label'] = labels
                out['source_dataset'] = name
                # Use existing language column
                if 'language' in df.columns:
                    out['_existing_lang'] = df['language']
                    out['_has_lang_col'] = True
                else:
                    out['_has_lang_col'] = False
                datasets.append((name, out))
                print(f"  Loaded {name}: {len(out)} rows (has language col: {'language' in df.columns})")

    # -------------------------------------------------------
    # 4. FINAL: safetalk_unified_dataset v1-v6
    # -------------------------------------------------------
    unified_files = [
        ('safetalk_unified_dataset.csv', 'final/safetalk_unified_dataset.csv'),
        ('safetalk_unified_dataset_v2.csv', 'final/safetalk_unified_dataset_v2.csv'),
        ('safetalk_unified_dataset_v3.csv', 'final/safetalk_unified_dataset_v3.csv'),
        ('safetalk_unified_dataset_v4.csv', 'final/safetalk_unified_dataset_v4.csv'),
        ('safetalk_unified_dataset_v5.csv', 'final/safetalk_unified_dataset_v5.csv'),
        ('safetalk_unified_dataset_v6.csv', 'final/safetalk_unified_dataset_v6.csv'),
    ]
    for name, rel_path in unified_files:
        fp = os.path.join(BASE_DIR, rel_path)
        if os.path.exists(fp):
            df = load_csv_safe(fp)
            if len(df) > 0:
                out = pd.DataFrame()
                out['text'] = df['text']
                out['original_label'] = df.get('label', pd.Series([None]*len(df)))
                out['source_dataset'] = name
                out['_has_lang_col'] = False
                datasets.append((name, out))
                print(f"  Loaded {name}: {len(out)} rows")

    # -------------------------------------------------------
    # 5. EXPANSIONS
    # -------------------------------------------------------
    # hard_cases_v8
    fp = os.path.join(BASE_DIR, 'expansions', 'hard_cases_v8.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            labels = []
            for _, row in df.iterrows():
                parts = []
                if pd.notna(row.get('risk_label')):
                    parts.append(str(row['risk_label']))
                if pd.notna(row.get('intent_label')):
                    parts.append(str(row['intent_label']))
                labels.append('|'.join(parts) if parts else None)
            out['original_label'] = labels
            out['source_dataset'] = 'hard_cases_v8.csv'
            if 'language' in df.columns:
                out['_existing_lang'] = df['language']
                out['_has_lang_col'] = True
            else:
                out['_has_lang_col'] = False
            datasets.append(('hard_cases_v8.csv', out))
            print(f"  Loaded hard_cases_v8.csv: {len(out)} rows")

    # hardening_data_v9
    fp = os.path.join(BASE_DIR, 'expansions', 'hardening_data_v9.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            labels = []
            for _, row in df.iterrows():
                parts = []
                if pd.notna(row.get('risk_label')):
                    parts.append(str(row['risk_label']))
                if pd.notna(row.get('intent_label')):
                    parts.append(str(row['intent_label']))
                labels.append('|'.join(parts) if parts else None)
            out['original_label'] = labels
            out['source_dataset'] = 'hardening_data_v9.csv'
            if 'language' in df.columns:
                out['_existing_lang'] = df['language']
                out['_has_lang_col'] = True
            else:
                out['_has_lang_col'] = False
            datasets.append(('hardening_data_v9.csv', out))
            print(f"  Loaded hardening_data_v9.csv: {len(out)} rows")

    # realistic_v6_messages
    fp = os.path.join(BASE_DIR, 'expansions', 'realistic_v6_messages.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'realistic_v6_messages.csv'
            out['_has_lang_col'] = False
            datasets.append(('realistic_v6_messages.csv', out))
            print(f"  Loaded realistic_v6_messages.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 6. PHISHING EXPANSION
    # -------------------------------------------------------
    fp = os.path.join(BASE_DIR, 'phishing_expansion', 'uz_financial_scams.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'uz_financial_scams.csv'
            out['_has_lang_col'] = False
            datasets.append(('uz_financial_scams.csv', out))
            print(f"  Loaded uz_financial_scams.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 7. SECURITY ALERT EXPANSION
    # -------------------------------------------------------
    fp = os.path.join(BASE_DIR, 'security_alert_expansion', 'uz_security_alert_scams.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'uz_security_alert_scams.csv'
            out['_has_lang_col'] = False
            datasets.append(('uz_security_alert_scams.csv', out))
            print(f"  Loaded uz_security_alert_scams.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 8. SOURCES
    # -------------------------------------------------------
    # sms_spam_collection.txt (tab-separated: label\ttext)
    fp = os.path.join(BASE_DIR, 'sources', 'sms_spam_collection.txt')
    if os.path.exists(fp):
        rows = []
        with open(fp, 'r', encoding='utf-8', errors='ignore') as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split('\t', 1)
                if len(parts) == 2:
                    rows.append({'text': parts[1], 'original_label': parts[0]})
                else:
                    rows.append({'text': line, 'original_label': None})
        if rows:
            out = pd.DataFrame(rows)
            out['source_dataset'] = 'sms_spam_collection.txt'
            out['_has_lang_col'] = False
            datasets.append(('sms_spam_collection.txt', out))
            print(f"  Loaded sms_spam_collection.txt: {len(out)} rows")

    # spam.csv (UCI format: Unnamed: 0, spamORham, Message)
    fp = os.path.join(BASE_DIR, 'sources', 'spam.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp, encoding='latin-1')
        if len(df) > 0:
            out = pd.DataFrame()
            # Find the text column
            text_col = 'Message' if 'Message' in df.columns else df.columns[-1]
            label_col = 'spamORham' if 'spamORham' in df.columns else df.columns[1]
            out['text'] = df[text_col]
            out['original_label'] = df[label_col]
            out['source_dataset'] = 'spam.csv'
            out['_has_lang_col'] = False
            datasets.append(('spam.csv', out))
            print(f"  Loaded spam.csv: {len(out)} rows")

    # sms_uz.csv
    fp = os.path.join(BASE_DIR, 'sources', 'sms_uz.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'sms_uz.csv'
            out['_has_lang_col'] = False
            datasets.append(('sms_uz.csv', out))
            print(f"  Loaded sms_uz.csv: {len(out)} rows")

    # sms_uz_clean.csv
    fp = os.path.join(BASE_DIR, 'sources', 'sms_uz_clean.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'sms_uz_clean.csv'
            out['_has_lang_col'] = False
            datasets.append(('sms_uz_clean.csv', out))
            print(f"  Loaded sms_uz_clean.csv: {len(out)} rows")

    # telegram_ham_samples.csv
    fp = os.path.join(BASE_DIR, 'sources', 'telegram_ham_samples.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'telegram_ham_samples.csv'
            out['_has_lang_col'] = False
            datasets.append(('telegram_ham_samples.csv', out))
            print(f"  Loaded telegram_ham_samples.csv: {len(out)} rows")

    # telegram_spam_cleaned.csv
    fp = os.path.join(BASE_DIR, 'sources', 'telegram_spam_cleaned.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'telegram_spam_cleaned.csv'
            out['_has_lang_col'] = False
            datasets.append(('telegram_spam_cleaned.csv', out))
            print(f"  Loaded telegram_spam_cleaned.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 9. UZBEK EXPANSION
    # -------------------------------------------------------
    # sms_uz_enriched.csv
    fp = os.path.join(BASE_DIR, 'uzbek_expansion', 'sms_uz_enriched.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'sms_uz_enriched.csv'
            out['_has_lang_col'] = False
            datasets.append(('sms_uz_enriched.csv', out))
            print(f"  Loaded sms_uz_enriched.csv: {len(out)} rows")

    # telegram_uz_enriched.csv
    fp = os.path.join(BASE_DIR, 'uzbek_expansion', 'telegram_uz_enriched.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'telegram_uz_enriched.csv'
            out['_has_lang_col'] = False
            datasets.append(('telegram_uz_enriched.csv', out))
            print(f"  Loaded telegram_uz_enriched.csv: {len(out)} rows")

    # translation_candidates.csv (has language_hint)
    fp = os.path.join(BASE_DIR, 'uzbek_expansion', 'translation_candidates.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'translation_candidates.csv'
            if 'language_hint' in df.columns:
                out['_existing_lang'] = df['language_hint']
                out['_has_lang_col'] = True
            else:
                out['_has_lang_col'] = False
            datasets.append(('translation_candidates.csv', out))
            print(f"  Loaded translation_candidates.csv: {len(out)} rows")

    # uz_dataset_v2.csv
    fp = os.path.join(BASE_DIR, 'uzbek_expansion', 'uz_dataset_v2.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            out['original_label'] = df.get('label', pd.Series([None]*len(df)))
            out['source_dataset'] = 'uz_dataset_v2.csv'
            out['_has_lang_col'] = False
            datasets.append(('uz_dataset_v2.csv', out))
            print(f"  Loaded uz_dataset_v2.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 10. ROOT DATA: uzbek_policy_seeds.csv
    # -------------------------------------------------------
    fp = os.path.join(BASE_DIR, 'uzbek_policy_seeds.csv')
    if os.path.exists(fp):
        df = load_csv_safe(fp)
        if len(df) > 0:
            out = pd.DataFrame()
            out['text'] = df['text']
            labels = []
            for _, row in df.iterrows():
                parts = []
                if pd.notna(row.get('risk_label')):
                    parts.append(str(row['risk_label']))
                if pd.notna(row.get('intent_label')):
                    parts.append(str(row['intent_label']))
                labels.append('|'.join(parts) if parts else None)
            out['original_label'] = labels
            out['source_dataset'] = 'uzbek_policy_seeds.csv'
            if 'language' in df.columns:
                out['_existing_lang'] = df['language']
                out['_has_lang_col'] = True
            else:
                out['_has_lang_col'] = False
            datasets.append(('uzbek_policy_seeds.csv', out))
            print(f"  Loaded uzbek_policy_seeds.csv: {len(out)} rows")

    # -------------------------------------------------------
    # 11. ROOT: sms_messages_raw.csv, telegram_messages_raw.csv (may be empty)
    # -------------------------------------------------------
    for fname in ['sms_messages_raw.csv', 'telegram_messages_raw.csv']:
        fp = os.path.join(BASE_DIR, fname)
        if os.path.exists(fp):
            df = load_csv_safe(fp)
            if len(df) > 0:
                out = pd.DataFrame()
                out['text'] = df['text']
                out['original_label'] = df.get('label', pd.Series([None]*len(df)))
                out['source_dataset'] = fname
                out['_has_lang_col'] = False
                datasets.append((fname, out))
                print(f"  Loaded {fname}: {len(out)} rows")
            else:
                print(f"  Skipped {fname}: empty (0 rows)")

    return datasets


# ============================================================
# MAIN CONSOLIDATION
# ============================================================

def main():
    print("=" * 70)
    print("SafeTalk Dataset Consolidation")
    print(f"Started: {datetime.now().isoformat()}")
    print("=" * 70)

    # --- PHASE 1: Load all datasets ---
    print("\n[PHASE 1] Loading ALL datasets...")
    datasets = load_all_datasets()

    total_datasets = len(datasets)
    print(f"\n  Total dataset files loaded: {total_datasets}")

    # --- PHASE 2: Concatenate all ---
    print("\n[PHASE 2] Concatenating all datasets...")
    all_dfs = []
    for name, df in datasets:
        all_dfs.append(df)

    master = pd.concat(all_dfs, ignore_index=True)
    total_raw = len(master)
    print(f"  Total raw rows (all datasets combined): {total_raw}")

    # Drop rows with empty text
    master = master.dropna(subset=['text'])
    master = master[master['text'].astype(str).str.strip().str.len() > 0]
    print(f"  After removing empty texts: {len(master)}")

    # --- PHASE 3: Language Detection ---
    print("\n[PHASE 3] Detecting languages...")

    lang_results = []
    lang_methods = []
    lang_conflicts = []
    mixed_cases = []

    for idx, row in master.iterrows():
        text = str(row['text'])
        has_lang = row.get('_has_lang_col', False)
        existing_lang = row.get('_existing_lang', None)

        # Detect language from text
        detected_lang, method, confidence = detect_language(text)

        if has_lang and pd.notna(existing_lang):
            normalized_existing = normalize_language_code(str(existing_lang))
            if normalized_existing and normalized_existing != 'mixed':
                # Use existing language column
                final_lang = normalized_existing
                # Log conflicts
                if detected_lang != normalized_existing and detected_lang != 'mixed':
                    lang_conflicts.append({
                        'idx': idx,
                        'text_preview': text[:80],
                        'existing_lang': str(existing_lang),
                        'detected_lang': detected_lang,
                        'method': method,
                        'source': row['source_dataset'],
                    })
            elif normalized_existing == 'mixed':
                # Mixed label - use detection
                final_lang = detected_lang
                mixed_cases.append({
                    'idx': idx,
                    'text_preview': text[:80],
                    'assigned_lang': detected_lang,
                    'source': row['source_dataset'],
                })
            else:
                final_lang = detected_lang
        else:
            final_lang = detected_lang

        if method.startswith('mixed'):
            mixed_cases.append({
                'idx': idx,
                'text_preview': text[:80],
                'assigned_lang': final_lang,
                'source': row['source_dataset'],
            })

        lang_results.append(final_lang)
        lang_methods.append(method)

    master['language'] = lang_results
    master['_detection_method'] = lang_methods

    print(f"  Language distribution:")
    lang_dist = master['language'].value_counts()
    for lang, count in lang_dist.items():
        print(f"    {lang}: {count}")
    print(f"  Language conflicts: {len(lang_conflicts)}")
    print(f"  Mixed cases: {len(mixed_cases)}")

    # --- PHASE 4: Normalization for dedup ---
    print("\n[PHASE 4] Normalizing for deduplication...")
    master['normalized_text'] = master['text'].apply(normalize_for_dedup)

    # --- PHASE 5: Split by language ---
    print("\n[PHASE 5] Splitting by language...")
    uz_df = master[master['language'] == 'uz'].copy()
    ru_df = master[master['language'] == 'ru'].copy()
    en_df = master[master['language'] == 'en'].copy()

    print(f"  Uzbek: {len(uz_df)} rows")
    print(f"  Russian: {len(ru_df)} rows")
    print(f"  English: {len(en_df)} rows")

    # --- PHASE 6: Deduplication ---
    print("\n[PHASE 6] Deduplicating (exact matches on normalized_text)...")

    dedup_stats = {}
    top_dupes = {}

    for lang_name, df in [('uz', uz_df), ('ru', ru_df), ('en', en_df)]:
        before = len(df)

        # Find duplicate counts
        dupe_counts = df['normalized_text'].value_counts()
        duplicated_texts = dupe_counts[dupe_counts > 1]

        total_dupes_removed = sum(duplicated_texts - 1)

        # Top 10 most duplicated
        top10 = duplicated_texts.head(10)
        top_dupes[lang_name] = []
        for txt, count in top10.items():
            # Get the original text for display
            orig = df[df['normalized_text'] == txt]['text'].iloc[0]
            top_dupes[lang_name].append((orig[:100], count))

        # Remove exact duplicates (keep first)
        df_deduped = df.drop_duplicates(subset=['normalized_text'], keep='first')
        after = len(df_deduped)

        dedup_stats[lang_name] = {
            'before': before,
            'after': after,
            'removed': before - after,
        }

        print(f"  {lang_name}: {before} -> {after} (removed {before - after})")

        # Update references
        if lang_name == 'uz':
            uz_df = df_deduped
        elif lang_name == 'ru':
            ru_df = df_deduped
        else:
            en_df = df_deduped

    # --- PHASE 7: Build output ---
    print("\n[PHASE 7] Building final output datasets...")

    output_cols = ['text', 'language', 'source_dataset', 'original_label', 'normalized_text']

    for lang_name, df, filename in [
        ('uz', uz_df, 'uzb_final1_dataset.csv'),
        ('ru', ru_df, 'rus_final1_dataset.csv'),
        ('en', en_df, 'eng_final1_dataset.csv'),
    ]:
        out_df = df[output_cols].copy()
        out_path = os.path.join(OUTPUT_DIR, filename)
        out_df.to_csv(out_path, index=False, encoding='utf-8')
        print(f"  Written {filename}: {len(out_df)} rows -> {out_path}")

    # --- PHASE 8: Source contribution analysis ---
    print("\n[PHASE 8] Analyzing source contributions...")

    source_contrib = {}
    for lang_name, df in [('uz', uz_df), ('ru', ru_df), ('en', en_df)]:
        contrib = df['source_dataset'].value_counts().to_dict()
        source_contrib[lang_name] = contrib

    # ============================================================
    # COMPREHENSIVE REPORT
    # ============================================================
    print("\n" + "=" * 70)
    print("CONSOLIDATION REPORT")
    print("=" * 70)

    report_lines = []
    report_lines.append("=" * 70)
    report_lines.append("SafeTalk Dataset Consolidation Report")
    report_lines.append(f"Generated: {datetime.now().isoformat()}")
    report_lines.append("=" * 70)

    # 1. Total samples per dataset
    report_lines.append("\n1. TOTAL SAMPLES PER FINAL DATASET")
    report_lines.append("-" * 40)
    report_lines.append(f"  uzb_final1_dataset: {len(uz_df)}")
    report_lines.append(f"  rus_final1_dataset: {len(ru_df)}")
    report_lines.append(f"  eng_final1_dataset: {len(en_df)}")
    report_lines.append(f"  GRAND TOTAL: {len(uz_df) + len(ru_df) + len(en_df)}")

    # 2. Before vs after dedup
    report_lines.append("\n2. DEDUPLICATION SUMMARY")
    report_lines.append("-" * 40)
    total_before = sum(s['before'] for s in dedup_stats.values())
    total_after = sum(s['after'] for s in dedup_stats.values())
    total_removed = sum(s['removed'] for s in dedup_stats.values())
    report_lines.append(f"  Total before dedup: {total_before}")
    report_lines.append(f"  Total after dedup:  {total_after}")
    report_lines.append(f"  Total removed:      {total_removed}")
    for lang in ['uz', 'ru', 'en']:
        s = dedup_stats[lang]
        report_lines.append(f"    {lang}: {s['before']} -> {s['after']} (removed {s['removed']})")

    # 3. Number of duplicates removed (same as above, repeated for clarity)
    report_lines.append("\n3. DUPLICATES REMOVED PER LANGUAGE")
    report_lines.append("-" * 40)
    for lang in ['uz', 'ru', 'en']:
        report_lines.append(f"  {lang}: {dedup_stats[lang]['removed']}")

    # 4. Top 10 most duplicated texts
    report_lines.append("\n4. TOP 10 MOST DUPLICATED TEXTS")
    report_lines.append("-" * 40)
    for lang in ['uz', 'ru', 'en']:
        report_lines.append(f"\n  [{lang.upper()}]:")
        if top_dupes[lang]:
            for i, (txt, count) in enumerate(top_dupes[lang], 1):
                report_lines.append(f"    {i}. (x{count}) {txt}")
        else:
            report_lines.append("    No duplicates found.")

    # 5. Language detection conflicts
    report_lines.append("\n5. LANGUAGE DETECTION CONFLICTS")
    report_lines.append("-" * 40)
    report_lines.append(f"  Total conflicts (existing vs detected): {len(lang_conflicts)}")
    report_lines.append(f"  Total mixed cases: {len(mixed_cases)}")
    if lang_conflicts:
        report_lines.append("\n  Sample conflicts (first 20):")
        for c in lang_conflicts[:20]:
            report_lines.append(f"    [{c['source']}] existing={c['existing_lang']}, detected={c['detected_lang']}: {c['text_preview']}")
    if mixed_cases:
        report_lines.append(f"\n  Sample mixed cases (first 20):")
        for m in mixed_cases[:20]:
            report_lines.append(f"    [{m['source']}] assigned={m['assigned_lang']}: {m['text_preview']}")

    # 6. Source contribution per dataset
    report_lines.append("\n6. SOURCE CONTRIBUTION PER FINAL DATASET")
    report_lines.append("-" * 40)
    for lang in ['uz', 'ru', 'en']:
        report_lines.append(f"\n  [{lang.upper()}] (total: {dedup_stats[lang]['after']}):")
        contrib = source_contrib[lang]
        for src, cnt in sorted(contrib.items(), key=lambda x: -x[1]):
            report_lines.append(f"    {src}: {cnt}")

    # 7. Dataset files processed
    report_lines.append("\n7. ALL DATASET FILES PROCESSED")
    report_lines.append("-" * 40)
    for name, df in datasets:
        report_lines.append(f"  {name}: {len(df)} rows")
    report_lines.append(f"  Total raw rows: {total_raw}")

    report_text = '\n'.join(report_lines)
    print(report_text)

    # Save report
    report_path = os.path.join(OUTPUT_DIR, 'consolidation_report.txt')
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(report_text)
    print(f"\nReport saved to: {report_path}")

    # Save conflicts log
    if lang_conflicts:
        conflicts_df = pd.DataFrame(lang_conflicts)
        conflicts_path = os.path.join(OUTPUT_DIR, 'language_conflicts_log.csv')
        conflicts_df.to_csv(conflicts_path, index=False, encoding='utf-8')
        print(f"Conflicts log saved to: {conflicts_path}")

    if mixed_cases:
        mixed_df = pd.DataFrame(mixed_cases)
        mixed_path = os.path.join(OUTPUT_DIR, 'mixed_language_log.csv')
        mixed_df.to_csv(mixed_path, index=False, encoding='utf-8')
        print(f"Mixed cases log saved to: {mixed_path}")

    print(f"\nDone. {datetime.now().isoformat()}")


if __name__ == '__main__':
    main()
