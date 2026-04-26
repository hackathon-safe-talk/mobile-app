"""Inventory all CSV datasets in the SafeTalk AI project."""
import pandas as pd
import os

data_root = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'safetalk_ai', 'data')
# We're running from safetalk_ai dir, so data is relative
data_root = 'data'

all_csvs = []
for root, dirs, files in os.walk(data_root):
    for f in files:
        if f.endswith('.csv') or f.endswith('.txt'):
            path = os.path.join(root, f)
            all_csvs.append(path)

print(f"Total data files found: {len(all_csvs)}")
print()

total_rows = 0
for csv_path in sorted(all_csvs):
    try:
        if csv_path.endswith('.txt'):
            # Try tab-separated
            df = pd.read_csv(csv_path, sep='\t', encoding='utf-8', on_bad_lines='skip')
        else:
            df = pd.read_csv(csv_path, encoding='utf-8', on_bad_lines='skip')
        
        rows = len(df)
        total_rows += rows
        cols = list(df.columns)
        print(f"=== {csv_path} ===")
        print(f"  Rows: {rows}")
        print(f"  Columns: {cols}")
        if rows > 0:
            # Show first row values truncated
            first = df.iloc[0]
            for col in cols:
                val = str(first[col])[:80]
                print(f"    {col}: {val}")
        print()
    except Exception as e:
        print(f"=== {csv_path} === ERROR: {e}")
        print()

print(f"\n{'='*60}")
print(f"GRAND TOTAL rows across all files: {total_rows}")
