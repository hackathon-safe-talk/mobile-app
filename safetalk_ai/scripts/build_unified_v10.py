"""
SafeTalk V10 Unified Dataset Builder
=====================================
Production-grade pipeline to merge ALL available datasets into a single,
clean, ML-ready dataset with unified labels and consistent schema.

Output: data/final/safetalk_unified_v10_ready.csv
Schema: text | clean_text | label | language | source

Author: SafeTalk AI Team
Version: 1.0
"""

import pandas as pd
import re
import os
import hashlib
from pathlib import Path
from datetime import datetime
from collections import OrderedDict

# ─────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────

BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data"
OUTPUT_DIR = DATA_DIR / "final"
OUTPUT_FILE = OUTPUT_DIR / "safetalk_unified_v10_ready.csv"
REPORT_FILE = OUTPUT_DIR / "unification_report_v10.txt"

# Label unification map (case-insensitive keys applied after .lower())
LABEL_MAP = {
    "ham": "SAFE",
    "safe": "SAFE",
    "spam": "SUSPICIOUS",
    "suspicious": "SUSPICIOUS",
    "scam": "DANGEROUS",
    "dangerous": "DANGEROUS",
}

VALID_LABELS = {"SAFE", "SUSPICIOUS", "DANGEROUS"}

# Language normalization
LANG_MAP = {
    "uz": "uz", "uzb": "uz", "uzbek": "uz",
    "en": "en", "eng": "en", "english": "en",
    "ru": "ru", "rus": "ru", "russian": "ru",
    "mixed": "mixed",
}


# ─────────────────────────────────────────────────────────────
# Text Preprocessing (aligned with Kotlin TextPreprocessor.kt)
# ─────────────────────────────────────────────────────────────

_URL_PATTERN = re.compile(r'(https?://\S+)', re.IGNORECASE)
_EXTENSIONS = ['.apk', '.exe', '.scr', '.msi', '.bat', '.zip', '.rar']
_NOISE_PATTERN = re.compile(r"[^\w\s\./:']")
_MULTI_EXCL = re.compile(r'!!+')
_WHITESPACE = re.compile(r'\s+')


def clean_text(text: str) -> str:
    """
    Normalize text for ML features. Aligned with TextPreprocessor.kt
    to ensure train-time / inference-time parity.
    """
    if not isinstance(text, str) or not text.strip():
        return ""

    # 1. Lowercase
    t = text.lower()

    # 2. Normalize Uzbek apostrophes (all variants → standard ')
    t = t.replace("\u2018", "'")   # '
    t = t.replace("\u2019", "'")   # '
    t = t.replace("`", "'")
    t = t.replace("\u02bc", "'")   # ʼ

    # 3. Normalize o'/g' Uzbek characters
    t = t.replace("o\u2018", "o'").replace("o\u2019", "o'").replace("o`", "o'")
    t = t.replace("g\u2018", "g'").replace("g\u2019", "g'").replace("g`", "g'")

    # 4. Protect URLs (add spaces around)
    t = _URL_PATTERN.sub(r' \1 ', t)

    # 5. Protect dangerous file extensions
    for ext in _EXTENSIONS:
        if ext in t:
            t = t.replace(ext, f' {ext} ')

    # 6. Normalize multiple exclamation marks
    t = _MULTI_EXCL.sub(' ! ', t)

    # 7. Remove noise (keep word chars, whitespace, . / : ')
    t = _NOISE_PATTERN.sub(' ', t)

    # 8. Collapse whitespace and trim
    t = _WHITESPACE.sub(' ', t).strip()

    return t


# ─────────────────────────────────────────────────────────────
# Dataset Loader Registry
# ─────────────────────────────────────────────────────────────

class DatasetLoader:
    """Loads a single CSV with heterogeneous schema into the unified format."""

    def __init__(self, name: str, path: Path, text_col: str, label_col: str,
                 lang_col: str = None, default_lang: str = None,
                 clean_text_col: str = None):
        self.name = name
        self.path = path
        self.text_col = text_col
        self.label_col = label_col
        self.lang_col = lang_col
        self.default_lang = default_lang
        self.clean_text_col = clean_text_col

    def load(self) -> pd.DataFrame:
        """Load and normalize to unified schema."""
        if not self.path.exists():
            print(f"  [SKIP] {self.name}: file not found at {self.path}")
            return pd.DataFrame(columns=["text", "clean_text", "label", "language", "source"])

        df = pd.read_csv(self.path)
        original_count = len(df)

        # Extract text
        if self.text_col not in df.columns:
            print(f"  [SKIP] {self.name}: text column '{self.text_col}' not found")
            return pd.DataFrame(columns=["text", "clean_text", "label", "language", "source"])

        result = pd.DataFrame()
        result["text"] = df[self.text_col].astype(str)

        # Extract and unify label
        if self.label_col in df.columns:
            result["label"] = df[self.label_col].astype(str).str.strip().str.lower().map(LABEL_MAP)
        else:
            print(f"  [WARN] {self.name}: label column '{self.label_col}' not found, skipping")
            return pd.DataFrame(columns=["text", "clean_text", "label", "language", "source"])

        # Drop rows with unmapped labels
        unmapped = result["label"].isna()
        if unmapped.any():
            bad_labels = df.loc[unmapped.values, self.label_col].unique()
            print(f"  [WARN] {self.name}: {unmapped.sum()} rows with unmapped labels: {bad_labels}")
        result = result.dropna(subset=["label"])

        # Extract language
        if self.lang_col and self.lang_col in df.columns:
            result["language"] = (
                df.loc[result.index, self.lang_col]
                .astype(str).str.strip().str.lower()
                .map(LANG_MAP)
                .fillna(self.default_lang or "unknown")
            )
        else:
            result["language"] = self.default_lang or "unknown"

        # Extract or generate clean_text
        if self.clean_text_col and self.clean_text_col in df.columns:
            result["clean_text"] = df.loc[result.index, self.clean_text_col].astype(str)
            # Re-clean empty entries
            empty_mask = result["clean_text"].isin(["", "nan", "None"])
            if empty_mask.any():
                result.loc[empty_mask, "clean_text"] = result.loc[empty_mask, "text"].apply(clean_text)
        else:
            result["clean_text"] = result["text"].apply(clean_text)

        # Source tag
        result["source"] = self.name

        loaded_count = len(result)
        print(f"  [OK]   {self.name}: {original_count} raw → {loaded_count} unified")
        return result[["text", "clean_text", "label", "language", "source"]]


def build_loader_registry() -> list:
    """Define all dataset loaders with their specific column mappings."""
    return [
        # ── V10 Foundation (language-pure, deduplicated) ──
        DatasetLoader(
            name="v10_uzb_foundation",
            path=DATA_DIR / "v10_foundation" / "uzb_base_dataset_v1.csv",
            text_col="text",
            label_col="original_label",
            lang_col="language",
            default_lang="uz",
            clean_text_col="normalized_text",
        ),
        DatasetLoader(
            name="v10_eng_foundation",
            path=DATA_DIR / "v10_foundation" / "eng_base_dataset_v1.csv",
            text_col="text",
            label_col="original_label",
            lang_col="language",
            default_lang="en",
            clean_text_col="normalized_text",
        ),
        DatasetLoader(
            name="v10_rus_foundation",
            path=DATA_DIR / "v10_foundation" / "rus_base_dataset_v1.csv",
            text_col="text",
            label_col="original_label",
            lang_col="language",
            default_lang="ru",
            clean_text_col="normalized_text",
        ),

        # ── V9 Active (semantic 3-class, already has clean_text) ──
        DatasetLoader(
            name="v9_semantic_active",
            path=DATA_DIR / "processed" / "master_semantic_dataset_v9_cleaned.csv",
            text_col="text",
            label_col="risk_label",
            lang_col="language",
            clean_text_col="clean_text",
        ),

        # ── Expansion Datasets ──
        DatasetLoader(
            name="expansion_realistic_v6",
            path=DATA_DIR / "expansions" / "realistic_v6_messages.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),
        DatasetLoader(
            name="expansion_hard_v8",
            path=DATA_DIR / "expansions" / "hard_cases_v8.csv",
            text_col="text",
            label_col="risk_label",
            lang_col="language",
            default_lang="uz",
        ),
        DatasetLoader(
            name="expansion_hardening_v9",
            path=DATA_DIR / "expansions" / "hardening_data_v9.csv",
            text_col="text",
            label_col="risk_label",
            lang_col="language",
            default_lang="mixed",
        ),
        DatasetLoader(
            name="synthetic_suspicious_v1",
            path=DATA_DIR / "generated" / "suspicious_synthetic_v1.csv",
            text_col="text",
            label_col="label",
            lang_col="language",
            clean_text_col="clean_text",
        ),

        # ── Uzbek Expansion ──
        DatasetLoader(
            name="uzbek_expansion_v2",
            path=DATA_DIR / "uzbek_expansion" / "uz_dataset_v2.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),
        DatasetLoader(
            name="uzbek_sms_enriched",
            path=DATA_DIR / "uzbek_expansion" / "sms_uz_enriched.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),
        DatasetLoader(
            name="uzbek_telegram_enriched",
            path=DATA_DIR / "uzbek_expansion" / "telegram_uz_enriched.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),
        DatasetLoader(
            name="uzbek_translations",
            path=DATA_DIR / "uzbek_expansion" / "translation_candidates.csv",
            text_col="text",
            label_col="label",
            lang_col="language_hint",
            default_lang="en",
        ),

        # ── Phishing & Security Expansion ──
        DatasetLoader(
            name="phishing_uz_financial",
            path=DATA_DIR / "phishing_expansion" / "uz_financial_scams.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),
        DatasetLoader(
            name="security_alert_uz",
            path=DATA_DIR / "security_alert_expansion" / "uz_security_alert_scams.csv",
            text_col="text",
            label_col="label",
            default_lang="uz",
        ),

        # ── Policy Seeds (Uzbek) ──
        DatasetLoader(
            name="uzbek_policy_seeds",
            path=DATA_DIR / "uzbek_policy_seeds.csv",
            text_col="text",
            label_col="risk_label",
            lang_col="language",
            default_lang="uz",
        ),
    ]


# ─────────────────────────────────────────────────────────────
# Pipeline Steps
# ─────────────────────────────────────────────────────────────

def step_1_load_all(loaders: list) -> pd.DataFrame:
    """STEP 1: Load all datasets and concatenate."""
    print("\n" + "=" * 60)
    print("STEP 1 — LOADING ALL DATASETS")
    print("=" * 60)

    frames = []
    load_log = []

    for loader in loaders:
        df = loader.load()
        if not df.empty:
            frames.append(df)
            load_log.append((loader.name, len(df)))

    if not frames:
        raise RuntimeError("No datasets loaded. Check file paths.")

    merged = pd.concat(frames, ignore_index=True)

    print(f"\n  Total datasets loaded: {len(frames)}")
    print(f"  Total rows after concat: {len(merged):,}")
    return merged, load_log


def step_2_validate_labels(df: pd.DataFrame) -> pd.DataFrame:
    """STEP 2: Validate all labels are in {SAFE, SUSPICIOUS, DANGEROUS}."""
    print("\n" + "=" * 60)
    print("STEP 2 — VALIDATING LABELS")
    print("=" * 60)

    invalid = ~df["label"].isin(VALID_LABELS)
    if invalid.any():
        bad = df.loc[invalid, "label"].value_counts()
        print(f"  [WARN] {invalid.sum()} rows with invalid labels:")
        print(f"         {dict(bad)}")
        df = df[~invalid].copy()

    label_dist = df["label"].value_counts()
    print(f"\n  Label distribution after validation:")
    for label, count in label_dist.items():
        pct = count / len(df) * 100
        print(f"    {label:12s}: {count:6,} ({pct:.1f}%)")

    return df


def step_3_clean_text(df: pd.DataFrame) -> pd.DataFrame:
    """STEP 3: Ensure clean_text exists and is populated for all rows."""
    print("\n" + "=" * 60)
    print("STEP 3 — GENERATING / VALIDATING CLEAN_TEXT")
    print("=" * 60)

    # Flag rows where clean_text is empty or NaN
    needs_cleaning = (
        df["clean_text"].isna() |
        df["clean_text"].isin(["", "nan", "None", "NaN"])
    )

    if needs_cleaning.any():
        print(f"  Generating clean_text for {needs_cleaning.sum():,} rows...")
        df.loc[needs_cleaning, "clean_text"] = df.loc[needs_cleaning, "text"].apply(clean_text)
    else:
        print(f"  All {len(df):,} rows already have clean_text.")

    # Re-clean ALL rows through our pipeline for consistency
    print(f"  Re-normalizing all clean_text for consistency...")
    df["clean_text"] = df["text"].apply(clean_text)

    return df


def step_4_deduplicate(df: pd.DataFrame) -> pd.DataFrame:
    """STEP 4: Remove duplicates and empty rows."""
    print("\n" + "=" * 60)
    print("STEP 4 — DEDUPLICATION & CLEANING")
    print("=" * 60)

    before = len(df)

    # Remove empty text
    empty_mask = df["clean_text"].isna() | (df["clean_text"].str.strip() == "")
    empty_count = empty_mask.sum()
    df = df[~empty_mask].copy()
    print(f"  Removed {empty_count:,} empty rows")

    # Remove exact duplicates (on clean_text)
    dup_before = len(df)
    df = df.drop_duplicates(subset=["clean_text"], keep="first")
    dup_count = dup_before - len(df)
    print(f"  Removed {dup_count:,} exact duplicates (based on clean_text)")

    after = len(df)
    print(f"\n  Before: {before:,} → After: {after:,} (removed {before - after:,} total)")

    return df


def step_5_normalize_language(df: pd.DataFrame) -> pd.DataFrame:
    """STEP 5: Normalize language tags."""
    print("\n" + "=" * 60)
    print("STEP 5 — NORMALIZING LANGUAGE TAGS")
    print("=" * 60)

    # Normalize
    df["language"] = df["language"].str.lower().str.strip().map(LANG_MAP).fillna("unknown")

    lang_dist = df["language"].value_counts()
    print(f"  Language distribution:")
    for lang, count in lang_dist.items():
        pct = count / len(df) * 100
        print(f"    {lang:8s}: {count:6,} ({pct:.1f}%)")

    unknown = (df["language"] == "unknown").sum()
    if unknown > 0:
        print(f"\n  [WARN] {unknown} rows with unknown language")

    return df


def step_6_shuffle(df: pd.DataFrame) -> pd.DataFrame:
    """STEP 6: Shuffle and reset index."""
    print("\n" + "=" * 60)
    print("STEP 6 — SHUFFLING")
    print("=" * 60)

    df = df.sample(frac=1, random_state=42).reset_index(drop=True)
    print(f"  Shuffled {len(df):,} rows with random_state=42")
    return df


def step_7_balance_preview(df: pd.DataFrame) -> dict:
    """STEP 7: Print balance preview without modifying."""
    print("\n" + "=" * 60)
    print("STEP 7 — BALANCE PREVIEW")
    print("=" * 60)

    stats = {}

    # Overall
    total = len(df)
    stats["total"] = total
    print(f"\n  Total rows: {total:,}")

    # Label distribution
    print(f"\n  Label Distribution:")
    label_counts = df["label"].value_counts()
    stats["labels"] = dict(label_counts)
    for label, count in label_counts.items():
        pct = count / total * 100
        bar = "█" * int(pct / 2) + "░" * (50 - int(pct / 2))
        print(f"    {label:12s}: {count:6,} ({pct:5.1f}%) {bar}")

    # Language distribution
    print(f"\n  Language Distribution:")
    lang_counts = df["language"].value_counts()
    stats["languages"] = dict(lang_counts)
    for lang, count in lang_counts.items():
        pct = count / total * 100
        bar = "█" * int(pct / 2) + "░" * (50 - int(pct / 2))
        print(f"    {lang:8s}: {count:6,} ({pct:5.1f}%) {bar}")

    # Cross-tab: Label × Language
    print(f"\n  Label × Language Cross-Tabulation:")
    cross = pd.crosstab(df["label"], df["language"], margins=True)
    print(f"  {cross.to_string()}")

    # Imbalance warnings
    print(f"\n  Warnings:")
    warnings = []

    min_label = label_counts.min()
    max_label = label_counts.max()
    imbalance = max_label / min_label if min_label > 0 else float("inf")
    if imbalance > 3:
        w = f"Class imbalance ratio: {imbalance:.1f}x (max/min). Consider oversampling minority."
        warnings.append(w)
        print(f"    ⚠ {w}")

    for lang in ["uz", "ru"]:
        if lang in lang_counts and lang_counts[lang] < total * 0.15:
            w = f"{lang.upper()} is underrepresented ({lang_counts[lang]/total*100:.1f}%). Target ≥15%."
            warnings.append(w)
            print(f"    ⚠ {w}")

    if not warnings:
        print(f"    ✓ No critical imbalance detected.")

    stats["warnings"] = warnings
    return stats


def step_8_save(df: pd.DataFrame) -> None:
    """STEP 8: Save final dataset."""
    print("\n" + "=" * 60)
    print("STEP 8 — SAVING OUTPUT")
    print("=" * 60)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Ensure column order
    df = df[["text", "clean_text", "label", "language", "source"]]

    df.to_csv(OUTPUT_FILE, index=False, encoding="utf-8")
    size_kb = OUTPUT_FILE.stat().st_size / 1024
    print(f"  Saved: {OUTPUT_FILE}")
    print(f"  Size:  {size_kb:.0f} KB ({len(df):,} rows)")

    # Checksum
    with open(OUTPUT_FILE, "rb") as f:
        md5 = hashlib.md5(f.read()).hexdigest()
    print(f"  MD5:   {md5}")


def step_9_report(load_log: list, stats: dict) -> None:
    """STEP 9: Generate and save full report."""
    print("\n" + "=" * 60)
    print("STEP 9 — REPORT")
    print("=" * 60)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    lines = []
    lines.append("=" * 60)
    lines.append("SafeTalk V10 Unified Dataset — Build Report")
    lines.append(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    lines.append("=" * 60)
    lines.append("")

    lines.append("═══ 1. SOURCE DATASETS ═══")
    for name, count in load_log:
        lines.append(f"  {name}: {count:,} rows")
    lines.append(f"  TOTAL LOADED: {sum(c for _, c in load_log):,}")
    lines.append("")

    lines.append("═══ 2. FINAL STATISTICS ═══")
    lines.append(f"  Total rows (after dedup): {stats['total']:,}")
    lines.append("")

    lines.append("═══ 3. LABEL DISTRIBUTION ═══")
    for label, count in stats["labels"].items():
        pct = count / stats["total"] * 100
        lines.append(f"  {label:12s}: {count:6,} ({pct:.1f}%)")
    lines.append("")

    lines.append("═══ 4. LANGUAGE DISTRIBUTION ═══")
    for lang, count in stats["languages"].items():
        pct = count / stats["total"] * 100
        lines.append(f"  {lang:8s}: {count:6,} ({pct:.1f}%)")
    lines.append("")

    lines.append("═══ 5. WARNINGS ═══")
    if stats["warnings"]:
        for w in stats["warnings"]:
            lines.append(f"  ⚠ {w}")
    else:
        lines.append("  ✓ No warnings.")
    lines.append("")

    lines.append("═══ 6. OUTPUT ═══")
    lines.append(f"  File: {OUTPUT_FILE}")
    lines.append(f"  Schema: text | clean_text | label | language | source")
    lines.append("")

    lines.append("═══ 7. NEXT STEPS ═══")
    lines.append("  1. Review class balance — consider oversampling DANGEROUS")
    lines.append("  2. Run train_model_v10.py on this dataset")
    lines.append("  3. Export to ONNX and update Android assets")
    lines.append("")
    lines.append("=" * 60)
    lines.append("END OF REPORT")
    lines.append("=" * 60)

    report_text = "\n".join(lines)

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write(report_text)

    print(f"  Report saved: {REPORT_FILE}")
    print(f"\n{report_text}")


# ─────────────────────────────────────────────────────────────
# Main Pipeline
# ─────────────────────────────────────────────────────────────

def main():
    print("╔════════════════════════════════════════════════════════╗")
    print("║     SafeTalk V10 — Unified Dataset Builder            ║")
    print("║     Production Pipeline                               ║")
    print(f"║     {datetime.now().strftime('%Y-%m-%d %H:%M:%S'):>42s}    ║")
    print("╚════════════════════════════════════════════════════════╝")

    loaders = build_loader_registry()

    # STEP 1: Load
    df, load_log = step_1_load_all(loaders)

    # STEP 2: Validate labels
    df = step_2_validate_labels(df)

    # STEP 3: Generate / validate clean_text
    df = step_3_clean_text(df)

    # STEP 4: Deduplicate and clean
    df = step_4_deduplicate(df)

    # STEP 5: Normalize languages
    df = step_5_normalize_language(df)

    # STEP 6: Shuffle
    df = step_6_shuffle(df)

    # STEP 7: Balance preview
    stats = step_7_balance_preview(df)

    # STEP 8: Save
    step_8_save(df)

    # STEP 9: Report
    step_9_report(load_log, stats)

    print("\n✅ Pipeline complete.")


if __name__ == "__main__":
    main()
