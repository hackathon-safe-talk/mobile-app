import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]
dataset_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7.csv"

def analyze():
    if not dataset_path.exists():
        print(f"Dataset not found at {dataset_path}")
        return
    
    df = pd.read_csv(dataset_path)
    total = len(df)
    
    print(f"Total samples: {total}")
    
    print("\nRisk Label Distribution:")
    print(df['risk_label'].value_counts(normalize=True))
    print(df['risk_label'].value_counts())
    
    print("\nLanguage Distribution:")
    print(df['language'].value_counts(normalize=True))
    print(df['language'].value_counts())
    
    print("\nIntent Label Distribution:")
    print(df['intent_label'].value_counts(normalize=True))
    print(df['intent_label'].value_counts())

if __name__ == "__main__":
    analyze()
