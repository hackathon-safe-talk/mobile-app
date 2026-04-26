# SafeTalk AI

This folder is used for future offline ML training for SMS and Telegram scam detection.

## Dataset Schema


`text,source,label`

- `source` = `sms` or `telegram`
- `label` = `ham` or `scam`

## Source Datasets
The unified dataset is built using:
- `sms_spam_collection.txt`
- `spam.csv`
- `telegram_spam_cleaned.csv`
- `sms_uz.csv`

The current unified dataset is still imbalanced. In the future, we will add more manually created Uzbek SMS and Telegram ham/scam examples to balance and enrich the training data.

## Text Preprocessing Stage
Raw text usually contains unpredictable characters, URLs, and numeric identifiers that interfere with Natural Language Processing. To prepare messages for Machine Learning:
- We convert the text to lowercase.
- We strip out URLs, phone numbers, and email addresses.
- Punctuation, extra whitespace, and emojis are completely removed.
- Valid Uzbek and Latin characters are safely retained.
  
The output is stored in a `clean_text` column. The raw `text` should not be used directly for ML training because its high structural variance (varying capitalizations, special characters, arbitrary links) prevents models from generalizing effectively on the purely semantic meaning of words.

## Model Training
The first offline SafeTalk model employs a **Logistic Regression** classifier operating on **TF-IDF (Term Frequency-Inverse Document Frequency)** features.
- We extract TF-IDF features strictly from the `clean_text` column, entirely ignoring the schema's `source` column to prevent platform-specific bias. 
- Using TF-IDF allows the model to map individual words and bigrams (`ngram_range=(1,2)`) to continuous importance scorings, penalizing frequent ubiquitous words while rewarding unique scam-related terminology.
- **Logistic Regression** was chosen for its exceptional performance in binary text classification (spam vs ham), its rapid training time, inherently linear interpretability, and lightweight disk footprint.

The resulting compiled artifacts are serialized with `joblib` and reside within the `safetalk_ai/models/` directory:
- `tfidf_vectorizer.pkl`
- `safetalk_message_classifier.pkl`



