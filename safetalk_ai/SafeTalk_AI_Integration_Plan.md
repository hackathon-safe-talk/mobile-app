# SafeTalk AI → Android Integration Architecture Plan

This document outlines the architectural blueprint for integrating the trained V6 Multilingual ML model into the SafeTalk Android application.

## 1. Current ML System (V6 Baseline)
- **Algorithm**: Logistic Regression + TF-IDF Vectorization.
- **Feature Space**: 9,000 N-grams (1, 2).
- **Model Weights**: `safetalk_message_classifier_v6.pkl` (~73 KB).
- **Vectorizer**: `tfidf_vectorizer_v6.pkl` (~341 KB).
- **Total Payload**: **~414 KB** (Extremely lightweight).
- **CPU Inference**: **~0.18ms** per message.

## 2. Deployment Options Evaluation

| Option | Strategy | Latency | Privacy | Offline | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Option A** | **ONNX Runtime (Local)** | **Ultra-Low** | **Maximum** | **Yes** | **Recommended** |
| Option B | TensorFlow Lite (Local) | Low | Maximum | Yes | Alternative |
| Option C | Backend API (Cloud) | High | Low | No | Not suited for SafeTalk |

**Recommendation**: **Option A (ONNX Runtime)**. 
Using ONNX allows for a small footprint, high-performance C++/Kotlin integration, and guarantees that user messages never leave the device.

## 3. ML Inference Pipeline

1. **NotificationListenerService**: Intercepts incoming SMS or Telegram notification.
2. **Message Extraction**: Extracts the raw `text` and `source`.
3. **SafeTalkAnalyzer (Standard)**: Runs existing rule-based checks (Signal scoring).
4. **MLInferenceModule (New)**:
    - Text Preprocessing (Standardized logic matching `preprocess_text_v6.py`).
    - TF-IDF Vectorization.
    - Logistic Regression Inference.
5. **Synergistic Scoring**: Combines Rule-based signals + ML Probability.
6. **ResultScreen**: Presents the unified risk assessment to the user.

## 4. Risk Decision Logic
The final risk assessment should be a weighted combination:
`Final_Score = (0.4 * Rule_Based_Signals) + (0.6 * ML_Scam_Probability)`

### Unified Thresholds:
- **Safe (0 - 19%)**: Standard notification behavior.
- **Suspicious (20 - 54%)**: Yellow warning, advice to exercise caution.
- **Dangerous (55 - 100%)**: Red alert, clear instruction to avoid links/sharing info.

## 5. Explainable AI (XAI) Design
SafeTalk will explain **why** a message was flagged using the TF-IDF feature weights:
- **Keyword Extraction**: The ML module identifies the top contributing features (e.g., "bloklandi", "tasdiqlash", "shubhali").
- **UI Display**: Under the Risk Score, show "Suspicious Keywords: [list]".

## 6. Android Integration Blueprint
- **`MLInferenceModule.kt`**: A new singleton utility wrapping the ONNX environment.
- **`SafeTalkAnalyzer.kt`**: Modified to call `MLInferenceModule` and pass results to the `ResultViewModel`.
- **Assets**: Model files placed in `app/src/main/assets` and loaded into memory on app startup.

## 7. Security and Privacy
- **Zero Cloud Dependency**: Inference is 100% local.
- **Encrypted Models**: Model artifacts can be encrypted at rest within the APK.
- **Low Footprint**: Minimal CPU/Battery impact due to the simplicity of Logistic Regression.

## 8. Summary Confirmation
✅ **No Android code changed during this planning phase.**
✅ **ML Model remains strictly offline.**
✅ **Privacy-First design ensures no user data leakage.**
