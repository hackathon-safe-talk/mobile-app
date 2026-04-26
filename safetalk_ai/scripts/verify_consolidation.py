"""Verify consolidation coverage and identify any misclassified texts."""
import pandas as pd
import re
import os

BASE = r"d:\SafeTalk\safetalk_ai\data"
CONS = os.path.join(BASE, "consolidated")

def norm(t):
    t = str(t).lower().strip()
    t = re.sub(r'\s+', ' ', t)
    t = t.replace('\u201c', '"').replace('\u201d', '"')
    t = t.replace('\u2018', "'").replace('\u2019', "'")
    t = t.replace('\u00ab', '"').replace('\u00bb', '"')
    t = t.replace('`', "'")
    return t.strip()

# Load finals
uz = pd.read_csv(os.path.join(CONS, "uzb_final1_dataset.csv"))
en = pd.read_csv(os.path.join(CONS, "eng_final1_dataset.csv"))
ru = pd.read_csv(os.path.join(CONS, "rus_final1_dataset.csv"))

uz_texts = set(uz["normalized_text"].dropna())
en_texts = set(en["normalized_text"].dropna())
ru_texts = set(ru["normalized_text"].dropna())
all_final = uz_texts | en_texts | ru_texts

print(f"Final UZ: {len(uz_texts)}, EN: {len(en_texts)}, RU: {len(ru_texts)}")
print(f"Total unique in all finals: {len(all_final)}")

# Check UZ-specific source files
uz_sources = [
    ("uzbek_policy_seeds.csv", os.path.join(BASE, "uzbek_policy_seeds.csv")),
    ("uz_financial_scams.csv", os.path.join(BASE, "phishing_expansion", "uz_financial_scams.csv")),
    ("uz_security_alert_scams.csv", os.path.join(BASE, "security_alert_expansion", "uz_security_alert_scams.csv")),
    ("uz_dataset_v2.csv", os.path.join(BASE, "uzbek_expansion", "uz_dataset_v2.csv")),
    ("sms_uz_enriched.csv", os.path.join(BASE, "uzbek_expansion", "sms_uz_enriched.csv")),
    ("telegram_uz_enriched.csv", os.path.join(BASE, "uzbek_expansion", "telegram_uz_enriched.csv")),
    ("sms_uz.csv", os.path.join(BASE, "sources", "sms_uz.csv")),
    ("sms_uz_clean.csv", os.path.join(BASE, "sources", "sms_uz_clean.csv")),
    ("realistic_v6_messages.csv", os.path.join(BASE, "expansions", "realistic_v6_messages.csv")),
    ("telegram_ham_samples.csv", os.path.join(BASE, "sources", "telegram_ham_samples.csv")),
    ("telegram_spam_cleaned.csv", os.path.join(BASE, "sources", "telegram_spam_cleaned.csv")),
    ("spam.csv", os.path.join(BASE, "sources", "spam.csv")),
    ("sms_spam_collection.txt", os.path.join(BASE, "sources", "sms_spam_collection.txt")),
    ("translation_candidates.csv", os.path.join(BASE, "uzbek_expansion", "translation_candidates.csv")),
]

print("\n=== SOURCE FILE COVERAGE CHECK ===")
for name, fp in uz_sources:
    try:
        if fp.endswith(".txt"):
            rows = []
            with open(fp, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split("\t", 1)
                    if len(parts) == 2:
                        rows.append(parts[1])
                    else:
                        rows.append(line)
            texts = set(norm(t) for t in rows)
        elif name == "spam.csv":
            df = pd.read_csv(fp, encoding="latin-1")
            texts = set(norm(str(t)) for t in df["Message"].dropna())
        else:
            df = pd.read_csv(fp)
            texts = set(norm(str(t)) for t in df["text"].dropna())

        in_uz = len(texts & uz_texts)
        in_en = len(texts & en_texts)
        in_ru = len(texts & ru_texts)
        missing = len(texts - all_final)
        print(f"  {name}: {len(texts)} unique -> UZ:{in_uz} EN:{in_en} RU:{in_ru} MISSING:{missing}")
    except Exception as e:
        print(f"  {name}: ERROR {e}")

# Check if any texts truly disappeared
print("\n=== MISSING TEXT ANALYSIS ===")
total_missing = 0
for name, fp in uz_sources:
    try:
        if fp.endswith(".txt"):
            rows = []
            with open(fp, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    parts = line.split("\t", 1)
                    if len(parts) == 2:
                        rows.append(parts[1])
                    else:
                        rows.append(line)
            texts = set(norm(t) for t in rows)
        elif name == "spam.csv":
            df = pd.read_csv(fp, encoding="latin-1")
            texts = set(norm(str(t)) for t in df["Message"].dropna())
        else:
            df = pd.read_csv(fp)
            texts = set(norm(str(t)) for t in df["text"].dropna())

        missing = texts - all_final
        if missing:
            total_missing += len(missing)
            samples = list(missing)[:3]
            print(f"  {name}: {len(missing)} missing texts")
            for s in samples:
                print(f"    sample: {s[:80]}")
    except:
        pass

print(f"\nTotal texts missing from all finals: {total_missing}")
