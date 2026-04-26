# SafeTalk V4 Official Baseline Model

This directory contains the frozen state of the SafeTalk V5 Machine Learning model. 

## Model Overview
- **Version**: V5
- **Type**: Logistic Regression
- **Vectorizer**: TF-IDF (7,000 features, n-gram 1-2)
- **Primary Language**: Uzbek (Enriched)

## Performance Metrics
- **Accuracy**: 97.67%
- **Scam Recall**: 93.15%
- **Scam Precision**: 99.84%

## Risk Calibration (Official)
The model outputs a scam probability that is mapped to the following risk bands:
- **0 - 19.99%**: Safe
- **20 - 54.99%**: Suspicious
- **55 - 100%**: Dangerous

## Integration Status
> [!IMPORTANT]
> This model is an **offline baseline**. It is NOT currently integrated into the SafeTalk Android application code. The Android application still relies on the native rule-based signal analyzer.

## Directory Structure
- `models/`: Pickled model artifacts (`.pkl`).
- `reports/`: Accuracy reports and example predictions.
- `config/`: JSON metadata and configuration.
