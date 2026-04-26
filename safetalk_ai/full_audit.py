#!/usr/bin/env python3
"""Full dataset audit for SafeTalk AI."""
import csv, os, json, sys

OUT = open('full_audit_output.txt', 'w', encoding='utf-8')

def p(s=''):
    OUT.write(str(s) + '\n')

def analyze_csv(path, name):
    p('=' * 100)
    p(f'DATASET: {name}')
    p(f'PATH: {path}')
    if not os.path.exists(path):
        p('FILE NOT FOUND')
        p()
        return
    p(f'FILE SIZE: {os.path.getsize(path)} bytes')
    p('=' * 100)
    
    try:
        with open(path, 'r', encoding='utf-8') as f:
            first_line = f.readline()
        
        # Detect delimiter
        delimiter = ','
        if '\t' in first_line and ',' not in first_line:
            delimiter = '\t'
        
        with open(path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f, delimiter=delimiter)
            rows = list(reader)
            headers = reader.fieldnames
            
        p(f'HEADERS: {headers}')
        p(f'TOTAL SAMPLES: {len(rows)}')
        
        # Detect label column
        label_col = None
        for c in ['risk_label', 'label', 'Label', 'v1', 'class', 'category']:
            if c in headers:
                label_col = c
                break
        
        # Detect text column
        text_col = None
        for c in ['text', 'message', 'v2', 'clean_text', 'content']:
            if c in headers:
                text_col = c
                break
        
        # Detect lang column
        lang_col = None
        for c in ['lang', 'language', 'Language']:
            if c in headers:
                lang_col = c
                break
        
        # Detect source column
        source_col = None
        for c in ['source', 'Source']:
            if c in headers:
                source_col = c
                break
        
        p(f'LABEL COLUMN: {label_col}')
        p(f'TEXT COLUMN: {text_col}')
        p(f'LANG COLUMN: {lang_col}')
        p(f'SOURCE COLUMN: {source_col}')
        
        # Distributions
        if label_col:
            labels = {}
            for r in rows:
                v = r.get(label_col, '')
                labels[v] = labels.get(v, 0) + 1
            p(f'LABEL DISTRIBUTION:')
            for k, v in sorted(labels.items(), key=lambda x: -x[1]):
                p(f'  {k}: {v} ({100*v/len(rows):.1f}%)')
        
        if lang_col:
            langs = {}
            for r in rows:
                v = r.get(lang_col, '')
                langs[v] = langs.get(v, 0) + 1
            p(f'LANGUAGE DISTRIBUTION:')
            for k, v in sorted(langs.items(), key=lambda x: -x[1]):
                p(f'  {k}: {v} ({100*v/len(rows):.1f}%)')
        
        if source_col:
            sources = {}
            for r in rows:
                v = r.get(source_col, '')
                sources[v] = sources.get(v, 0) + 1
            p(f'SOURCE DISTRIBUTION:')
            for k, v in sorted(sources.items(), key=lambda x: -x[1]):
                p(f'  {k}: {v} ({100*v/len(rows):.1f}%)')
        
        # Check for clean_text column
        has_clean = 'clean_text' in headers
        p(f'HAS clean_text COLUMN: {has_clean}')
        
        # Check for tags column
        has_tags = 'tags' in headers
        p(f'HAS tags COLUMN: {has_tags}')
        
        # Sample data - first 25
        p()
        p('--- FIRST 25 SAMPLES (RAW) ---')
        for i, r in enumerate(rows[:25]):
            txt = r.get(text_col, '') if text_col else ''
            lbl = r.get(label_col, '') if label_col else ''
            lng = r.get(lang_col, '') if lang_col else ''
            src = r.get(source_col, '') if source_col else ''
            clean = r.get('clean_text', '') if has_clean else ''
            tags = r.get('tags', '') if has_tags else ''
            p(f'  [{i:4d}] LABEL={lbl:12s} LANG={lng:4s} SRC={src:15s} TEXT="{txt[:150]}"')
            if has_clean:
                p(f'         CLEAN="{clean[:150]}"')
            if has_tags:
                p(f'         TAGS="{tags[:100]}"')
        
        # Last 5 samples
        p()
        p('--- LAST 5 SAMPLES ---')
        for i, r in enumerate(rows[-5:]):
            idx = len(rows)-5+i
            txt = r.get(text_col, '') if text_col else ''
            lbl = r.get(label_col, '') if label_col else ''
            lng = r.get(lang_col, '') if lang_col else ''
            src = r.get(source_col, '') if source_col else ''
            p(f'  [{idx:4d}] LABEL={lbl:12s} LANG={lng:4s} SRC={src:15s} TEXT="{txt[:150]}"')
        
        # Duplicate check
        if text_col:
            all_texts = [r.get(text_col, '') for r in rows]
            unique = len(set(all_texts))
            dupes = len(all_texts) - unique
            p(f'\nDUPLICATE TEXT COUNT: {dupes} (unique: {unique}, total: {len(all_texts)})')
            
            # Show some duplicates
            if dupes > 0:
                from collections import Counter
                counts = Counter(all_texts)
                dupe_texts = [(t, c) for t, c in counts.items() if c > 1]
                dupe_texts.sort(key=lambda x: -x[1])
                p(f'TOP 10 DUPLICATED TEXTS:')
                for t, c in dupe_texts[:10]:
                    p(f'  [{c}x] "{t[:120]}"')
        
        # Empty text check
        if text_col:
            empty = sum(1 for r in rows if not r.get(text_col, '').strip())
            p(f'EMPTY TEXT COUNT: {empty}')
        
    except Exception as e:
        p(f'ERROR: {e}')
        import traceback
        p(traceback.format_exc())
    
    p()

def analyze_txt(path, name):
    p('=' * 100)
    p(f'DATASET: {name}')
    p(f'PATH: {path}')
    if not os.path.exists(path):
        p('FILE NOT FOUND')
        p()
        return
    p(f'FILE SIZE: {os.path.getsize(path)} bytes')
    p('=' * 100)
    
    try:
        with open(path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        p(f'TOTAL LINES: {len(lines)}')
        
        labels = {}
        for line in lines:
            parts = line.strip().split('\t', 1)
            if len(parts) == 2:
                lbl = parts[0]
                labels[lbl] = labels.get(lbl, 0) + 1
        
        p(f'LABEL DISTRIBUTION (tab-delimited):')
        for k, v in sorted(labels.items(), key=lambda x: -x[1]):
            p(f'  {k}: {v}')
        
        p()
        p('--- FIRST 25 LINES ---')
        for i, line in enumerate(lines[:25]):
            p(f'  [{i:4d}] {line.rstrip()[:200]}')
        
        p()
        p('--- LAST 5 LINES ---')
        for i, line in enumerate(lines[-5:]):
            idx = len(lines)-5+i
            p(f'  [{idx:4d}] {line.rstrip()[:200]}')
    
    except Exception as e:
        p(f'ERROR: {e}')
    p()


# ===== PROCESSED DATASETS =====
p('#' * 100)
p('SECTION 1: PROCESSED DATASETS (data/processed/)')
p('#' * 100)
p()

for f in [
    'master_semantic_dataset_v9_cleaned.csv',
    'master_semantic_dataset_v8_cleaned.csv',
    'master_semantic_dataset_v8.csv',
    'master_semantic_dataset_v7_cleaned.csv',
    'master_semantic_dataset_v7.csv',
    'messages_cleaned.csv',
    'messages_cleaned_v2.csv',
    'messages_cleaned_v3.csv',
    'messages_cleaned_v4.csv',
    'messages_cleaned_v5.csv',
    'messages_cleaned_v6.csv',
    'unified_messages_raw.csv',
]:
    analyze_csv(f'data/processed/{f}', f)

# ===== FINAL DATASETS =====
p('#' * 100)
p('SECTION 2: FINAL/UNIFIED DATASETS (data/final/)')
p('#' * 100)
p()

for f in [
    'safetalk_unified_dataset.csv',
    'safetalk_unified_dataset_v2.csv',
    'safetalk_unified_dataset_v3.csv',
    'safetalk_unified_dataset_v4.csv',
    'safetalk_unified_dataset_v5.csv',
    'safetalk_unified_dataset_v6.csv',
]:
    analyze_csv(f'data/final/{f}', f)

# ===== SOURCE DATASETS =====
p('#' * 100)
p('SECTION 3: SOURCE DATASETS (data/sources/)')
p('#' * 100)
p()

analyze_txt('data/sources/sms_spam_collection.txt', 'sms_spam_collection.txt')
for f in ['spam.csv', 'sms_uz.csv', 'sms_uz_clean.csv', 'telegram_ham_samples.csv', 'telegram_spam_cleaned.csv']:
    analyze_csv(f'data/sources/{f}', f)

# ===== EXPANSION DATASETS =====
p('#' * 100)
p('SECTION 4: EXPANSION DATASETS')
p('#' * 100)
p()

for path, name in [
    ('data/expansions/hard_cases_v8.csv', 'hard_cases_v8'),
    ('data/expansions/hardening_data_v9.csv', 'hardening_data_v9'),
    ('data/expansions/realistic_v6_messages.csv', 'realistic_v6_messages'),
    ('data/phishing_expansion/uz_financial_scams.csv', 'uz_financial_scams'),
    ('data/security_alert_expansion/uz_security_alert_scams.csv', 'uz_security_alert_scams'),
    ('data/uzbek_expansion/sms_uz_enriched.csv', 'sms_uz_enriched'),
    ('data/uzbek_expansion/telegram_uz_enriched.csv', 'telegram_uz_enriched'),
    ('data/uzbek_expansion/translation_candidates.csv', 'translation_candidates'),
    ('data/uzbek_expansion/uz_dataset_v2.csv', 'uz_dataset_v2'),
    ('data/uzbek_policy_seeds.csv', 'uzbek_policy_seeds'),
]:
    analyze_csv(path, name)

# ===== STUB FILES =====
p('#' * 100)
p('SECTION 5: STUB/RAW FILES')
p('#' * 100)
p()
for f in ['data/sms_messages_raw.csv', 'data/telegram_messages_raw.csv']:
    p(f'FILE: {f}')
    p(f'SIZE: {os.path.getsize(f)} bytes')
    with open(f, 'r') as fh:
        p(f'CONTENT: "{fh.read()}"')
    p()

# ===== REGRESSION PACKS =====
p('#' * 100)
p('SECTION 6: REGRESSION PACKS (JSON)')
p('#' * 100)
p()
for f in ['data/regression_pack_v7.json', 'data/regression_pack_v8.json', 'data/regression_pack_v9.json']:
    p(f'FILE: {f}')
    p(f'SIZE: {os.path.getsize(f)} bytes')
    with open(f, 'r', encoding='utf-8') as fh:
        data = json.load(fh)
    if isinstance(data, list):
        p(f'TYPE: list, COUNT: {len(data)}')
        if data:
            p(f'SAMPLE KEYS: {list(data[0].keys()) if isinstance(data[0], dict) else "not dict"}')
            p('--- FIRST 10 ---')
            for i, item in enumerate(data[:10]):
                p(f'  [{i}] {json.dumps(item, ensure_ascii=False)[:200]}')
    elif isinstance(data, dict):
        p(f'TYPE: dict, KEYS: {list(data.keys())}')
        for k, v in data.items():
            if isinstance(v, list):
                p(f'  {k}: list of {len(v)}')
            else:
                p(f'  {k}: {v}')
    p()

# ===== MODEL METADATA =====
p('#' * 100)
p('SECTION 7: MODEL METADATA')
p('#' * 100)
p()
for f in ['models/model_metadata_v6.json', 'models/model_metadata_v7.json', 'models/model_metadata_v9.json',
          'models/class_map_v7.json', 'models/class_map_v9.json']:
    p(f'FILE: {f}')
    with open(f, 'r', encoding='utf-8') as fh:
        data = json.load(fh)
    p(f'CONTENT: {json.dumps(data, indent=2)}')
    p()

# ===== MODEL FILES =====
p('#' * 100)
p('SECTION 8: MODEL FILES INVENTORY')
p('#' * 100)
p()
for f in sorted(os.listdir('models')):
    full = os.path.join('models', f)
    p(f'  {f:50s} {os.path.getsize(full):>10d} bytes')

# ===== MODEL REPORTS =====
p('#' * 100)
p('SECTION 9: MODEL REPORTS')
p('#' * 100)
p()
for f in sorted(os.listdir('outputs')):
    full = os.path.join('outputs', f)
    p(f'--- {f} ---')
    with open(full, 'r', encoding='utf-8') as fh:
        p(fh.read())
    p()

OUT.close()
print('DONE - output written to full_audit_output.txt')
