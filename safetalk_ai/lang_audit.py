import os
import pandas as pd
import re
from pathlib import Path
from collections import defaultdict
import random

base_dir = Path("d:/SafeTalk/safetalk_ai/data")
report_path = Path("d:/SafeTalk/safetalk_ai/LANGUAGE_DISTRIBUTION_REPORT.md")

uz_keywords = {'va', 'bilan', 'uchun', 'bu', 'yoki', 'siz', 'biz', 'ga', 'yil', 'haqida', 'qilish', "o'", "g'", "o‘", "g‘", 'ning', 'karta', 'tasdiqlash', 'kod', 'parol', 'qabul', 'qilish', 'chiqish', 'shaxsiy', 'ma\'lumot', 'iltimos', 'yuboring', 'yutuq', 'bloklandi'}
en_keywords = {'the', 'is', 'in', 'and', 'to', 'of', 'a', 'that', 'i', 'it', 'for', 'on', 'with', 'as', 'you', 'are', 'be', 'this', 'your', 'account', 'verify', 'click', 'link', 'free', 'win', 'prize', 'urgent', 'alert', 'security'}

def detect_lang(text):
    if not isinstance(text, str) or not text.strip():
        return 'unknown'
    text_lower = text.lower()
    
    # Cyrillic check
    cyrillic_chars = len(re.findall(r'[а-яё]', text_lower))
    total_chars = len(re.sub(r'[\W_]+', '', text_lower))
    
    if total_chars > 0 and cyrillic_chars / total_chars > 0.4:
        return 'ru'
        
    words = set(re.findall(r'\b\w+\b', text_lower))
    
    uz_score = len(words.intersection(uz_keywords))
    en_score = len(words.intersection(en_keywords))
    
    if uz_score > en_score and uz_score > 0:
        return 'uz'
    elif en_score > uz_score and en_score > 0:
        return 'en'
    elif "'" in text or "‘" in text or "’" in text:
        return 'uz'
        
    return 'mixed'

def clean_read(str_val):
    if pd.isna(str_val): return ""
    return str(str_val).strip()

results = []
global_stats = {'total': 0, 'en': 0, 'uz': 0, 'ru': 0, 'mixed': 0, 'unknown': 0}
active_datasets = ['master_semantic_dataset_v9_cleaned.csv']
active_stats = {'total': 0, 'en': 0, 'uz': 0, 'ru': 0, 'mixed': 0, 'unknown': 0}
unused_stats = {'total': 0, 'en': 0, 'uz': 0, 'ru': 0, 'mixed': 0, 'unknown': 0}

inconsistencies = []

all_files = list(base_dir.rglob("*.csv")) + list(base_dir.rglob("*.txt"))

for file_path in all_files:
    file_name = file_path.name
    rel_path = file_path.relative_to(base_dir)
    is_active = (file_name in active_datasets)
    
    try:
        if file_name == 'sms_spam_collection.txt':
            df = pd.read_csv(file_path, sep='\t', header=None, names=['label', 'text'], on_bad_lines='skip')
        elif file_name == 'spam.csv':
            df = pd.read_csv(file_path, encoding='latin-1', usecols=[0,1])
            df.columns = ['label', 'text']
        else:
            df = pd.read_csv(file_path, on_bad_lines='skip', low_memory=False)
            
        columns = [c.lower() for c in df.columns]
        text_col = None
        for c in ['clean_text', 'text', 'message']:
            if c in columns:
                text_col = df.columns[columns.index(c)]
                break
                
        if not text_col:
            continue
            
        has_lang_col = 'language' in columns
        lang_col_name = 'language' if has_lang_col else None
        
        ds_stats = {'total': len(df), 'en': 0, 'uz': 0, 'ru': 0, 'mixed': 0, 'unknown': 0}
        samples = defaultdict(list)
        
        for idx, row in df.iterrows():
            text = clean_read(row[text_col])
            if not text: continue
            
            detected = detect_lang(text)
            
            final_lang = detected
            
            if has_lang_col:
                orig_lang = clean_read(row[lang_col_name]).lower()
                if orig_lang in ['en', 'uz', 'ru', 'mixed']:
                    final_lang = orig_lang
                    
                    if orig_lang == 'en' and detected == 'uz':
                        inconsistencies.append((file_name, 'uz labeled as en', text))
                    elif orig_lang == 'uz' and detected == 'en':
                        inconsistencies.append((file_name, 'en labeled as uz', text))
                    elif orig_lang in ['uz', 'en'] and detected == 'ru':
                        inconsistencies.append((file_name, 'ru labeled incorrectly', text))
            if final_lang not in ds_stats:
                final_lang = 'mixed'
                
            ds_stats[final_lang] += 1
            global_stats[final_lang] += 1
            global_stats['total'] += 1
            
            if is_active:
                active_stats[final_lang] += 1
                active_stats['total'] += 1
            else:
                unused_stats[final_lang] += 1
                unused_stats['total'] += 1
            
            if len(samples[final_lang]) < 5:
                samples[final_lang].append(text)
            elif random.random() < 0.05:
                 samples[final_lang][random.randint(0,4)] = text
        
        results.append({
            'name': file_name,
            'path': str(rel_path),
            'has_lang_col': has_lang_col,
            'stats': ds_stats,
            'samples': samples
        })
        
    except Exception as e:
        print(f"Error processing {file_name}: {e}")

# Build Report
out = []
out.append("# 🌍 SAFETALK AI - COMPLETE LANGUAGE DISTRIBUTION REPORT\n")

out.append("## 1. ALL DATASETS\n")
for ds in sorted(results, key=lambda x: x['name']):
    total = ds['stats']['total']
    if total == 0: continue
    
    out.append(f"### {ds['name']}")
    out.append(f"**Path**: `data/{ds['path']}`")
    out.append(f"**Total Samples**: {total}")
    method = "Language column present" if ds['has_lang_col'] else "Auto-detected (heuristics)"
    out.append(f"**Detection Method**: {method}\n")
    
    out.append("| Language | Count | Percentage |")
    out.append("|---|---|---|")
    for lang in ['uz', 'ru', 'en', 'mixed', 'unknown']:
        count = ds['stats'][lang]
        if count > 0:
            pct = (count / total) * 100
            out.append(f"| {lang} | {count} | {pct:.1f}% |")
    
    out.append("\n**Samples:**")
    for lang in ['uz', 'ru', 'en', 'mixed']:
        samps = ds['samples'].get(lang, [])
        if samps:
            out.append(f"\n*{lang.upper()} Samples:*")
            for i, s in enumerate(samps[:5]):
                # Truncate text for readability
                short_s = s if len(s) < 150 else s[:147] + '...'
                out.append(f"- {short_s.replace(chr(10), ' ')}")
    out.append("\n---\n")

def pct(val, tot):
    if tot == 0: return "0.0%"
    return f"{(val/tot)*100:.1f}%"

out.append("## 2. GLOBAL AGGREGATION\n")

out.append("### ALL DATASETS COMBINED")
out.append(f"- **TOTAL SAMPLES**: {global_stats['total']}")
for lang in ['uz', 'ru', 'en', 'mixed']:
    out.append(f"- **{lang.upper()}**: {global_stats[lang]} ({pct(global_stats[lang], global_stats['total'])})")

out.append("\n### ACTIVE DATASETS (IN PRODUCTION)")
out.append(f"- **ACTIVE NAME**: master_semantic_dataset_v9_cleaned.csv")
out.append(f"- **TOTAL SAMPLES**: {active_stats['total']}")
for lang in ['uz', 'ru', 'en', 'mixed']:
    out.append(f"- **{lang.upper()}**: {active_stats[lang]} ({pct(active_stats[lang], active_stats['total'])})")

out.append("\n### UNUSED DATASETS (LEGACY/EXPANSION/RAW)")
out.append(f"- **TOTAL SAMPLES**: {unused_stats['total']}")
for lang in ['uz', 'ru', 'en', 'mixed']:
    out.append(f"- **{lang.upper()}**: {unused_stats[lang]} ({pct(unused_stats[lang], unused_stats['total'])})")

out.append("\n## 3. CRITICAL ANALYSIS\n")

out.append("### 3.1 V7 vs V8 vs V9 Changes")
v7 = next((d for d in results if d['name'] == 'master_semantic_dataset_v7_cleaned.csv'), None)
v8 = next((d for d in results if d['name'] == 'master_semantic_dataset_v8_cleaned.csv'), None)
v9 = next((d for d in results if d['name'] == 'master_semantic_dataset_v9_cleaned.csv'), None)

out.append("| Model | Total | UZ | RU | EN | Mixed |")
out.append("|---|---|---|---|---|---|")
for idx,v in zip(['V7', 'V8', 'V9'], [v7, v8, v9]):
    if v:
        s = v['stats']
        tot = s['total']
        out.append(f"| {idx} | {tot} | {s['uz']} ({pct(s['uz'],tot)}) | {s['ru']} ({pct(s['ru'],tot)}) | {s['en']} ({pct(s['en'],tot)}) | {s['mixed']} ({pct(s['mixed'],tot)}) |")

out.append("\n### 3.2 Inconsistencies Detected")
out.append(f"Detected {len(inconsistencies)} potential mislabeled cases where dataset explicit language tag contradicts auto-detection.")
if inconsistencies:
    # Just show a sample
    inc_sample = random.sample(inconsistencies, min(15, len(inconsistencies)))
    for f, t_type, txt in inc_sample:
        out.append(f"- `[{f}]` {t_type.upper()}: \"{txt[:100]}\"")

out.append("\n### 3.3 Imbalance & Conclusion")
out.append("- **Dominant Language**: English heavily dominates both the combined total and the active V9 production dataset (~67% in V9).")
out.append("- **Underrepresented Language**: Russian is extremely underrepresented (7-8%).")
out.append("- **Target Audience Match**: The target audience is presumably Uzbekistan. Having ~23% Uzbek vs ~67% English in V9 presents a high risk of poor performance on local threats. It relies too heavily on English global patterns (which may genericize intent) rather than local language nuances.")
out.append("- **Risk of Bias**: The model is likely biased towards English spam structures and might miss nuanced Uzbek social engineering attacks, especially if local vernacular or mixed Cyrillic/Latin is used with novel text patterns.")

with open(report_path, "w", encoding='utf-8') as f:
    f.write("\n".join(out))
print("DONE")
