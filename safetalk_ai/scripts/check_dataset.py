import pandas as pd
import sys
import os

def check_dataset(file_path):
    print(f"Checking dataset: {file_path}")
    if not os.path.exists(file_path):
        print(f"File not found: {file_path}")
        return

    # Load CSV dataset
    try:
        df = pd.read_csv(file_path)
    except Exception as e:
        print(f"Error loading dataset: {e}")
        return

    # Check that columns exist
    required_columns = ['text', 'label']
    missing_columns = [col for col in required_columns if col not in df.columns]
    
    if missing_columns:
        print(f"Error: Missing columns: {missing_columns}")
    else:
        print("Required columns check: PASSED (contains 'text' and 'label')")

    # Check that labels are only "ham" or "scam"
    if 'label' in df.columns:
        invalid_labels = df[~df['label'].isin(['ham', 'scam']) & df['label'].notna()]['label'].unique()
        if len(invalid_labels) > 0:
            print(f"Error: Found invalid labels. Only 'ham' or 'scam' are allowed. Invalid labels found: {invalid_labels}")
        else:
            print("Labels check: PASSED (only 'ham' or 'scam')")

    # Print dataset statistics
    print("\nDataset Statistics:")
    print(f"Total rows: {len(df)}")
    if 'label' in df.columns:
        print("Label counts:")
        print(df['label'].value_counts())
    print("\n" + "-"*40 + "\n")

if __name__ == "__main__":
    if len(sys.argv) > 1:
        for file_path in sys.argv[1:]:
            check_dataset(file_path)
    else:
        # Default behavior: test the two files in the data dir
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        check_dataset(os.path.join(base_dir, "data", "sms_messages_raw.csv"))
        check_dataset(os.path.join(base_dir, "data", "telegram_messages_raw.csv"))
