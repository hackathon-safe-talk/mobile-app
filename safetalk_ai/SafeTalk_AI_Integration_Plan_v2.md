# SafeTalk AI Integration Plan (Validated v2)

This document provides a technically validated and corrected architecture for integrating the SafeTalk V6 Multilingual ML model into the Android application.

## 1. Current ML System
- **Model**: Logistic Regression (V6).
- **Vectorizer**: TF-IDF (9,000 features).
- **Target Accuracy**: 97.85%.
- **Target Recall**: 94.88%.

## 2. ONNX Feasibility Results
Conversion tests confirm that the V6 model is highly optimized for Android:
- **Status**: ✅ **Feasible**.
- **Model Format**: ONNX (Open Neural Network Exchange).
- **ONNX Size**: **88.43 KB**.
- **CPU Inference Speed**: **< 0.2ms** (Desktop), estimated **< 5ms** on mobile.
- **Compatibility**: Logic is standard Linear model + Sigmoid, supported by all ONNX versions.

## 3. TF-IDF Android Compatibility
To ensure parity between Python training and Android inference:
- **Vocabulary Export**: `tfidf_vocabulary_v6.json` (9,000 entries).
- **Implementation**: Android will implement a custom `TfidfVectorizer` class that:
    1. Loads the JSON vocabulary (Word -> Index).
    2. Performs the same cleaning (Lowercase, URL/Email/Phone removal).
    3. Computes TF (Term Frequency) for input messages.
    4. Uses the pre-computed Weights to create the feature vector for ONNX input.

## 4. Runtime Performance Estimates (Android)

| Tier | Load Time | Inference Latency | RAM Usage |
| :--- | :--- | :--- | :--- |
| **Low-End** | ~1.5 - 2s | < 15ms | ~5-8 MB |
| **Mid-Range** | ~0.5s | < 5ms | ~5-8 MB |
| **High-End** | < 0.1s | < 1ms | ~5-8 MB |

## 5. Risk Fusion Calibration
After simulating multiple weighting strategies, we recommend a **ML-Dominant** fusion to leverage our 11,000 message training corpus:

**Final_Risk = (0.3 * Original_Signal_Score) + (0.7 * ML_Scam_Probability * 100)**

- **Rationale**: The ML model has learned complex multilingual patterns that the rule-based signals may miss.
- **Thresholds**: 
    - 0-19%: Safe
    - 20-54%: Suspicious
    - 55-100%: Dangerous

## 6. Explainable AI (XAI) Design
SafeTalk will provide human-readable "Reasons" using top TF-IDF coefficients:
- **Offline Dictionary**: `xai_keywords_v6.json`.
- **Logic**: When a message is flagged, the app scans for keywords with the highest weights in the vector.
- **Top Contributors Found**: `uchun`, `tasdiqlash`, `claim`, `free`, `bloklandi`.

## 7. Android Runtime Loading Strategy
- **Verdict**: **Lazy Loading (On-Demand)**.
- **Mechanism**: The ML model (`.onnx`) and Vocabulary (`.json`) are NOT loaded at app boot. They are loaded upon the FIRST message interception by `NotificationListenerService`.
- **Benefit**: Zero impact on app startup time. Subsequent checks will be near-instantaneous as the model remains in RAM.

## 8. Final Integration Architecture
1. **Interceptor**: `NotificationListenerService` captures text.
2. **Analyzer**: `SafeTalkAnalyzer` computes original signal scores.
3. **ML Module**: `MLInferenceModule` (Lazy) vectorizes text and runs ONNX inference.
4. **Fusion**: Aggregator combines scores using the **0.3 / 0.7** formula.
5. **Presentation**: `ResultScreen` displays detailed Risk + XAI "Reasons".

---
✅ **Verification**: All tests performed inside `safetalk_ai`. No Android code was modified.
