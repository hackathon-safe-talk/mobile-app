# SafeTalk Uzbek-First Expansion

The purpose of this subdirectory is to guide the offline machine learning model towards strong detection capabilities within the primary target language: **Uzbek**.
While datasets mapped heavily to English or Russian remain exceptionally useful as broad pattern-recognition baselines, prioritizing natively phrased Uzbek conversational content prevents language-specific detection biases and improves classification recall within localized operational environments.

### What exists here?
- `translation_candidates.csv`: A highly curated subset of scams from our larger general dataset intended to act as high-value candidate prompts for future contextual translation directly into Uzbek.
- `sms_uz_enriched.csv` & `telegram_uz_enriched.csv`: Next-generation offline datasets populated entirely with natural-sounding localized text mapped aggressively against modern regional scam tactics (e.g., Click/Payme phishing, Ovoz scams, Telegram Premium/Stars impersonation, etc).

### Future Pipeline Instructions
These enriched datasets are created in preparation for the next offline model retraining cycles. English/Russian patterns will be maintained as secondary supporting architecture.
Risk scoring and probabilistic outputs will be updated as the expanded data is processed into `clean_text` arrays.
