# SafeTalk Android ML Module Implementation (Phase 1)

This document details the implementation of the Phase 1 isolated ML module for the SafeTalk Android application.

## 1. Files Created
The following components were added to the `com.snow.safetalk.ml` package:

- **[TextPreprocessor.kt](file:///d:/SafeTalk/app/src/main/java/com/snow/safetalk/ml/TextPreprocessor.kt)**: Reproduces the Python cleaning pipeline (lowercase, URL/Email/Phone removal, punctuation stripping).
- **[TfidfVectorizerLite.kt](file:///d:/SafeTalk/app/src/main/java/com/snow/safetalk/ml/TfidfVectorizerLite.kt)**: A custom implementation of scikit-learn's `TfidfVectorizer` with N-gram (1,2) support and L2 normalization.
- **[MLInferenceModule.kt](file:///d:/SafeTalk/app/src/main/java/com/snow/safetalk/ml/MLInferenceModule.kt)**: A thread-safe singleton that manages the local ONNX Runtime session with lazy loading.
- **[XAIHelper.kt](file:///d:/SafeTalk/app/src/main/java/com/snow/safetalk/ml/XAIHelper.kt)**: Extracts top suspicious tokens by calculating the contribution of active features using model weights.
- **[RiskFusion.kt](file:///d:/SafeTalk/app/src/main/java/com/snow/safetalk/ml/RiskFusion.kt)**: Implements the 0.3 Signal / 0.7 ML weighting logic with critical safety overrides.
- **[MLModuleTest.kt](file:///d:/SafeTalk/app/src/test/java/com/snow/safetalk/ml/MLModuleTest.kt)**: JUnit validation harness for the 10 probe messages.

## 2. Dependencies Added
- **Microsoft ONNX Runtime**: `com.microsoft.onnxruntime:onnxruntime-android:1.17.1` (pinned version).

## 3. Implementation Details

### Lazy Loading
The `MLInferenceModule` uses a `synchronized` initialization block triggered only on the first call to `analyze()`. This ensures that assets (Model, Vocabulary, Weights) are only loaded into memory when a message analysis is actually required, keeping app startup performance untouched.

### TF-IDF Parity
Parity with the V6 Python pipeline is achieved by:
1. Exporting exact **IDF weights** and **Vocabulary indexes** from the scikit-learn vectorizer.
2. Implementing the exact **L2 Normalization** formula used by scikit-learn.
3. Matching the **Regex patterns** for text cleaning.

### Safety & Fallback
The system is designed for **Zero-Touch Stability**:
- If ONNX fails to initialize or assets are missing, `analyze()` returns `null`.
- `RiskFusion.fuse()` handles `null` ML results by falling back strictly to the original signal risk.
- No crashes occur in the event of ML runtime failures.

### Risk Fusion Logic
- **Formula**: `Final = (0.3 * Signal) + (0.7 * ML_Prob * 100)`
- **Safety Overrides**:
    - If Signal Risk is ≥ 80%, the fused result will never drop below "SUSPICIOUS" (40%), even if ML disagrees.
    - If ML and Signals both show high confidence (≥ 70% Signal, ≥ 55% ML), the result is promoted to "DANGEROUS" (85%).

## 4. Phase 1 Validation (Probe Results)
The `TextPreprocessor` was verified to match the expected "token-only" output for mixed-language strings. The `RiskFusion` logic was validated against edge cases for safety promotion and stability.

---
✅ **Phase 1 Status**: Complete. Module is stable, isolated, and ready for minimal integration in Phase 2.
