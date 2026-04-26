#!/usr/bin/env python3
"""
SafeTalk Consolidated Dataset — Full Audit
===========================================
Analyzes all 3 final datasets + conflict logs.
Outputs a structured report covering:
  1. Label analysis
  2. Language conflict analysis
  3. Label distribution
  4. Hard case extraction
  5. Duplicate/pattern analysis
  6. Cross-language leakage check
  7. Final verification
"""

import pandas as pd
import os
import re
from collections import Counter, defaultdict

BASE = r"d:\SafeTalk\safetalk_ai\data\consolidated"
OUT = os.path.join(BASE, "full_audit_report.txt")

def norm(t):
    t = str(t).lower().strip()
    t = re.sub(r'\s+', ' ', t)
    t = t.replace('\u201c', '"').replace('\u201d', '"')
    t = t.replace('\u2018', "'").replace('\u2019', "'")
    return t.strip()

def section(title, lines):
    out = []
    out.append("")
    out.append("=" * 80)
    out.append(f"  {title}")
    out.append("=" * 80)
    out.extend(lines)
    return out

def main():
    report = []
    report.append("SafeTalk Consolidated Dataset — Full Audit Report")
    report.append("=" * 80)

    # Load datasets
    uz = pd.read_csv(os.path.join(BASE, "uzb_final1_dataset.csv"))
    en = pd.read_csv(os.path.join(BASE, "eng_final1_dataset.csv"))
    ru = pd.read_csv(os.path.join(BASE, "rus_final1_dataset.csv"))
    datasets = {"UZ": uz, "EN": en, "RU": ru}

    # Load conflict log
    conflicts_path = os.path.join(BASE, "language_conflicts_log.csv")
    conflicts = pd.read_csv(conflicts_path) if os.path.exists(conflicts_path) else pd.DataFrame()

    # ================================================================
    # 1) LABEL ANALYSIS
    # ================================================================
    lines = []
    all_labels = pd.concat([uz["original_label"], en["original_label"], ru["original_label"]])
    label_counts = all_labels.value_counts(dropna=False)

    lines.append("")
    lines.append("  All unique labels across ALL datasets:")
    lines.append(f"  Total unique labels: {label_counts.shape[0]}")
    lines.append("")
    lines.append(f"  {'Label':<60} {'Count':>8}")
    lines.append("  " + "-" * 70)
    for label, count in label_counts.items():
        label_str = str(label)[:58] if pd.notna(label) else "<NaN/missing>"
        lines.append(f"  {label_str:<60} {count:>8}")

    lines.append("")
    lines.append("  --- Per-dataset breakdown ---")
    for name, df in datasets.items():
        lines.append(f"")
        lines.append(f"  [{name}] ({len(df)} rows):")
        vc = df["original_label"].value_counts(dropna=False)
        for label, count in vc.items():
            label_str = str(label)[:55] if pd.notna(label) else "<NaN>"
            pct = count / len(df) * 100
            lines.append(f"    {label_str:<55} {count:>6}  ({pct:5.1f}%)")

    report.extend(section("1) LABEL ANALYSIS", lines))

    # ================================================================
    # 2) LANGUAGE CONFLICT ANALYSIS
    # ================================================================
    lines = []
    if len(conflicts) > 0:
        lines.append(f"")
        lines.append(f"  Total conflicts: {len(conflicts)}")
        lines.append(f"  Columns: {list(conflicts.columns)}")

        # Count per language pair
        lines.append("")
        lines.append("  Conflict pairs (existing_lang -> detected_lang):")
        lines.append("")
        if "existing_lang" in conflicts.columns and "detected_lang" in conflicts.columns:
            pair_counts = conflicts.groupby(["existing_lang", "detected_lang"]).size()
            pair_counts = pair_counts.sort_values(ascending=False)
            lines.append(f"  {'From':<10} {'To':<10} {'Count':>8}")
            lines.append("  " + "-" * 30)
            for (fr, to), cnt in pair_counts.items():
                lines.append(f"  {fr:<10} {to:<10} {cnt:>8}")

        # 100 sample rows
        lines.append("")
        lines.append("  --- 100 Sample Conflict Rows ---")
        lines.append("")
        sample = conflicts.head(100)
        for i, row in sample.iterrows():
            text_prev = str(row.get("text_preview", ""))[:70]
            existing = str(row.get("existing_lang", "?"))
            detected = str(row.get("detected_lang", "?"))
            source = str(row.get("source", "?"))
            lines.append(f"  [{i:>5}] {existing:>3}->{detected:<3} [{source:<45}] {text_prev}")
    else:
        lines.append("  No conflicts log found.")

    report.extend(section("2) LANGUAGE CONFLICT ANALYSIS", lines))

    # ================================================================
    # 3) LABEL DISTRIBUTION (detailed)
    # ================================================================
    lines = []
    for name, df in datasets.items():
        lines.append(f"")
        lines.append(f"  [{name}] — {len(df)} total rows")
        lines.append("")

        vc = df["original_label"].value_counts(dropna=False)
        total = len(df)

        lines.append(f"  {'Label':<55} {'Count':>6} {'Pct':>7}  {'Status'}")
        lines.append("  " + "-" * 80)

        for label, count in vc.items():
            pct = count / total * 100
            label_str = str(label)[:53] if pd.notna(label) else "<NaN>"

            # Flag imbalances
            status = ""
            if pct > 70:
                status = "⚠ DOMINANT (>70%)"
            elif pct < 10:
                status = "⚠ UNDERREPRESENTED (<10%)"

            lines.append(f"  {label_str:<55} {count:>6} {pct:>6.1f}%  {status}")

    report.extend(section("3) LABEL DISTRIBUTION (FINAL DATASETS)", lines))

    # ================================================================
    # 4) HARD CASE EXTRACTION
    # ================================================================
    lines = []
    hard_cases = []

    # Patterns that make text ambiguous
    ambiguous_patterns = [
        (r"(bank|karta|card|hisobingiz|account).{0,30}(xavfsiz|secure|safe|himoya|protect)", "mentions security but could be phishing or legit"),
        (r"(tasdiqlang|confirm|verify).{0,20}(kod|code|parol|password)", "verification request - could be real or scam"),
        (r"(yutuq|prize|won|bonus|aksiya).{0,30}(tabrik|congrat)", "prize notification - classic scam pattern but could be legit"),
        (r"(click|bosing|tugmani|link|havola|url).{0,30}(http|www|\.com|\.net|\.uz)", "contains link with action verb"),
        (r"(urgent|shoshilinch|tezkor|срочн).{0,40}(transfer|o.tkazish|yuborish|перевод)", "urgency + transfer - classic pressure tactic"),
        (r"(salom|hello|hi|privet|привет).{0,30}(offer|taklif|предлож)", "greeting + offer - could be spam or friendly"),
        (r"(free|bepul|бесплатн).{0,20}(download|yuklab|скачать)", "free download - could be legit or malware"),
    ]

    all_data = pd.concat([uz, en, ru], ignore_index=True)
    for pattern, reason in ambiguous_patterns:
        matches = all_data[all_data["text"].astype(str).str.contains(pattern, case=False, regex=True, na=False)]
        for _, row in matches.head(5).iterrows():
            text = str(row["text"])[:120]
            label = str(row.get("original_label", "?"))
            hard_cases.append((text, label, reason, row.get("language", "?")))

    # Also find texts with short length (inherently ambiguous)
    short_texts = all_data[all_data["text"].astype(str).str.len().between(10, 40)].sample(
        n=min(10, len(all_data[all_data["text"].astype(str).str.len().between(10, 40)])),
        random_state=42
    )
    for _, row in short_texts.iterrows():
        text = str(row["text"])[:120]
        label = str(row.get("original_label", "?"))
        hard_cases.append((text, label, "very short text - insufficient context for classification", row.get("language", "?")))

    lines.append("")
    lines.append(f"  Extracted {min(30, len(hard_cases))} hard/ambiguous cases:")
    lines.append("")
    lines.append(f"  {'#':>3} {'Lang':>4} {'Label':<30} {'Reason':<45} Text")
    lines.append("  " + "-" * 130)

    for i, (text, label, reason, lang) in enumerate(hard_cases[:30], 1):
        label_short = label[:28]
        reason_short = reason[:43]
        text_short = text[:55].replace('\n', ' ').replace('\r', ' ')
        lines.append(f"  {i:>3} {str(lang):>4} {label_short:<30} {reason_short:<45} {text_short}")

    report.extend(section("4) HARD CASE EXTRACTION (30 AMBIGUOUS SAMPLES)", lines))

    # ================================================================
    # 5) DUPLICATE / PATTERN ANALYSIS
    # ================================================================
    lines = []

    # Since we already deduplicated, look at patterns in the final data
    # Top 100 most common normalized_text entries in the pre-dedup source
    # But since post-dedup they're unique within each lang, look for cross-lang and within-dataset patterns

    lines.append("")
    lines.append("  --- Scam Pattern Analysis ---")
    lines.append("")

    scam_patterns = defaultdict(int)
    pattern_examples = defaultdict(list)

    scam_regexes = {
        "CVV/Card Request": r"(cvv|karta raqam|card number|номер карт)",
        "Code/OTP Request": r"(kod.{0,10}(yubor|ber|ayting|send)|otp|одноразов)",
        "Account Blocked": r"(bloklan|block|заблокир|hisobingiz.{0,10}xavf)",
        "Prize/Lottery": r"(yutuq|yutib|prize|won|lottery|розыгрыш|aksiya)",
        "Financial Transfer": r"(pul.{0,10}(o.tkazish|yubor)|transfer|перевод)",
        "Fake Login/Verify": r"(tasdiqlang|verify|confirm|подтверд).{0,15}(link|havola|ссылк|http)",
        "Urgency Pressure": r"(shoshilinch|urgent|tezkor|срочн|hoziroq|immediately)",
        "Impersonation": r"(bank xodim|bank officer|сотрудник банк|administrat)",
        "Job Scam": r"(ish taklif|job offer|работа.{0,10}предлаг|vakansiya)",
        "Investment Scam": r"(investitsiya|invest|dividend|крипт|crypto|bitcoin|доход)",
        "URL/Link Pattern": r"(https?://|www\.|\.com/|\.net/|\.uz/|\.click|\.link)",
        "Safe Greeting": r"^(salom|hello|hi |hey|privet|привет|assalom)",
        "Safe Daily Chat": r"(ko.rishamiz|see you|bugun|today|maroqli|yaxshi)",
    }

    for name, pattern in scam_regexes.items():
        for ds_name, df in datasets.items():
            matches = df[df["text"].astype(str).str.contains(pattern, case=False, regex=True, na=False)]
            scam_patterns[(name, ds_name)] = len(matches)
            if len(matches) > 0 and len(pattern_examples[name]) < 3:
                for _, row in matches.head(1).iterrows():
                    pattern_examples[name].append(str(row["text"])[:80])

    lines.append(f"  {'Pattern':<25} {'UZ':>6} {'EN':>6} {'RU':>6} {'Total':>7}")
    lines.append("  " + "-" * 55)
    for name in scam_regexes:
        uz_c = scam_patterns.get((name, "UZ"), 0)
        en_c = scam_patterns.get((name, "EN"), 0)
        ru_c = scam_patterns.get((name, "RU"), 0)
        total = uz_c + en_c + ru_c
        lines.append(f"  {name:<25} {uz_c:>6} {en_c:>6} {ru_c:>6} {total:>7}")

    lines.append("")
    lines.append("  --- Pattern Examples ---")
    for name, examples in pattern_examples.items():
        lines.append(f"  [{name}]:")
        for ex in examples:
            lines.append(f"    {ex}")

    # Top frequent normalized texts in each dataset
    lines.append("")
    lines.append("  --- Top 20 Texts Per Dataset (post-dedup, by source frequency) ---")
    for ds_name, df in datasets.items():
        lines.append(f"")
        lines.append(f"  [{ds_name}]:")
        src_counts = df.groupby("source_dataset").size().sort_values(ascending=False)
        for src, cnt in src_counts.head(20).items():
            lines.append(f"    {src:<50} {cnt:>6}")

    report.extend(section("5) DUPLICATE / PATTERN ANALYSIS", lines))

    # ================================================================
    # 6) CROSS-LANGUAGE LEAKAGE CHECK
    # ================================================================
    lines = []

    uz_normed = set(uz["normalized_text"].dropna())
    en_normed = set(en["normalized_text"].dropna())
    ru_normed = set(ru["normalized_text"].dropna())

    uz_en = uz_normed & en_normed
    uz_ru = uz_normed & ru_normed
    en_ru = en_normed & ru_normed
    all_three = uz_normed & en_normed & ru_normed

    lines.append("")
    lines.append("  Cross-Language Overlap Summary:")
    lines.append(f"    UZ ∩ EN:     {len(uz_en):>6} texts")
    lines.append(f"    UZ ∩ RU:     {len(uz_ru):>6} texts")
    lines.append(f"    EN ∩ RU:     {len(en_ru):>6} texts")
    lines.append(f"    UZ ∩ EN ∩ RU: {len(all_three):>5} texts")
    total_overlap = len(uz_en | uz_ru | en_ru)
    lines.append(f"    Total unique overlapping texts: {total_overlap}")

    if uz_en:
        lines.append("")
        lines.append("  --- UZ ∩ EN Sample Overlaps (up to 30) ---")
        for i, txt in enumerate(sorted(uz_en)[:30], 1):
            # Get original text from UZ
            uz_row = uz[uz["normalized_text"] == txt].iloc[0]
            en_row = en[en["normalized_text"] == txt].iloc[0]
            uz_src = str(uz_row.get("source_dataset", "?"))
            en_src = str(en_row.get("source_dataset", "?"))
            uz_lbl = str(uz_row.get("original_label", "?"))[:20]
            en_lbl = str(en_row.get("original_label", "?"))[:20]
            orig = str(uz_row["text"])[:65].replace('\n', ' ')
            lines.append(f"  {i:>3}. [{uz_src[:25]:<25}|{en_src[:25]:<25}] UZ:{uz_lbl:<20} EN:{en_lbl:<20} {orig}")

    if uz_ru:
        lines.append("")
        lines.append("  --- UZ ∩ RU Sample Overlaps (up to 20) ---")
        for i, txt in enumerate(sorted(uz_ru)[:20], 1):
            uz_row = uz[uz["normalized_text"] == txt].iloc[0]
            ru_row = ru[ru["normalized_text"] == txt].iloc[0]
            orig = str(uz_row["text"])[:70].replace('\n', ' ')
            lines.append(f"  {i:>3}. {orig}")

    if en_ru:
        lines.append("")
        lines.append("  --- EN ∩ RU Sample Overlaps (up to 20) ---")
        for i, txt in enumerate(sorted(en_ru)[:20], 1):
            en_row = en[en["normalized_text"] == txt].iloc[0]
            orig = str(en_row["text"])[:70].replace('\n', ' ')
            lines.append(f"  {i:>3}. {orig}")

    report.extend(section("6) CROSS-LANGUAGE LEAKAGE CHECK", lines))

    # ================================================================
    # 7) FINAL VERIFICATION
    # ================================================================
    lines = []

    total_uz = len(uz)
    total_en = len(en)
    total_ru = len(ru)
    grand_total = total_uz + total_en + total_ru

    # Check unique text counts
    uz_unique = uz["normalized_text"].nunique()
    en_unique = en["normalized_text"].nunique()
    ru_unique = ru["normalized_text"].nunique()

    lines.append("")
    lines.append("  Dataset Sizes:")
    lines.append(f"    UZ: {total_uz:>8} rows, {uz_unique:>8} unique normalized texts")
    lines.append(f"    EN: {total_en:>8} rows, {en_unique:>8} unique normalized texts")
    lines.append(f"    RU: {total_ru:>8} rows, {ru_unique:>8} unique normalized texts")
    lines.append(f"    TOTAL: {grand_total:>6} rows")
    lines.append("")

    # Uniqueness within each dataset
    uz_dupes_within = total_uz - uz_unique
    en_dupes_within = total_en - en_unique
    ru_dupes_within = total_ru - ru_unique
    lines.append("  Intra-Dataset Duplicates (should be 0):")
    status_uz = "PASS" if uz_dupes_within == 0 else f"FAIL ({uz_dupes_within} dupes)"
    status_en = "PASS" if en_dupes_within == 0 else f"FAIL ({en_dupes_within} dupes)"
    status_ru = "PASS" if ru_dupes_within == 0 else f"FAIL ({ru_dupes_within} dupes)"
    lines.append(f"    UZ: {status_uz}")
    lines.append(f"    EN: {status_en}")
    lines.append(f"    RU: {status_ru}")
    lines.append("")

    # Check for empty texts
    uz_empty = uz["text"].isna().sum() + (uz["text"].astype(str).str.strip() == "").sum()
    en_empty = en["text"].isna().sum() + (en["text"].astype(str).str.strip() == "").sum()
    ru_empty = ru["text"].isna().sum() + (ru["text"].astype(str).str.strip() == "").sum()
    lines.append("  Empty Texts (should be 0):")
    lines.append(f"    UZ: {uz_empty}")
    lines.append(f"    EN: {en_empty}")
    lines.append(f"    RU: {ru_empty}")
    lines.append("")

    # Check language column consistency
    lines.append("  Language Column Consistency:")
    for name, df in datasets.items():
        lang_vals = df["language"].unique()
        expected = {"UZ": "uz", "EN": "en", "RU": "ru"}[name]
        all_correct = all(v == expected for v in lang_vals)
        status = "PASS" if all_correct else f"FAIL (found: {lang_vals})"
        lines.append(f"    {name}: {status} (expected all '{expected}', found {lang_vals})")
    lines.append("")

    # Check source_dataset column
    lines.append("  Source Dataset Coverage:")
    all_sources = set()
    for name, df in datasets.items():
        srcs = df["source_dataset"].unique()
        all_sources.update(srcs)
        lines.append(f"    {name}: {len(srcs)} unique sources")
    lines.append(f"    Total unique sources across all datasets: {len(all_sources)}")
    lines.append("")

    # Cross-language unique count
    all_normed = set()
    all_normed.update(uz["normalized_text"].dropna())
    all_normed.update(en["normalized_text"].dropna())
    all_normed.update(ru["normalized_text"].dropna())
    lines.append(f"  Total unique normalized texts across all 3 datasets: {len(all_normed)}")
    lines.append("")

    # Overall status
    all_pass = (uz_dupes_within == 0 and en_dupes_within == 0 and ru_dupes_within == 0
                and uz_empty == 0 and en_empty == 0 and ru_empty == 0)
    lines.append("  " + "=" * 40)
    if all_pass:
        lines.append("  OVERALL STATUS: ALL CHECKS PASSED")
    else:
        lines.append("  OVERALL STATUS: SOME CHECKS FAILED")
    lines.append("  " + "=" * 40)

    report.extend(section("7) FINAL VERIFICATION", lines))

    # ================================================================
    # WRITE REPORT
    # ================================================================
    report_text = "\n".join(report)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(report_text)

    print(report_text)
    print(f"\n\nReport saved to: {OUT}")


if __name__ == "__main__":
    main()
