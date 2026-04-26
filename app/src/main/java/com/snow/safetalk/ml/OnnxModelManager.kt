package com.snow.safetalk.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.Collections

/**
 * Singleton managing the ONNX Runtime Environment and Session lifetime for SafeTalk V10.
 */
class OnnxModelManager private constructor(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    var ortSession: OrtSession? = null
        private set
        
    var classOrder: List<String> = emptyList()
        private set

    private var isLoaded = false

    companion object {
        @Volatile
        private var INSTANCE: OnnxModelManager? = null

        fun getInstance(context: Context): OnnxModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnnxModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Synchronized
    fun load() {
        if (isLoaded) return
        try {
            android.util.Log.d("SafeTalkML", "Initializing ONNX Model V10 Runtime...")
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("ml/safetalk_classifier_v10.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
            
            // Load Class Order Mapping from the python label encoder
            val classOrderJson = context.assets.open("ml/label_encoder.json").use { it.bufferedReader().readText() }
            val classObj = JSONObject(classOrderJson)
            
            val keys = classObj.keys()
            val indexMap = mutableMapOf<Int, String>()
            keys.forEach { key ->
                val idx = classObj.getInt(key)
                indexMap[idx] = key
            }
            
            // Ensure determinism in order 0, 1, 2...
            val sortedClasses = mutableListOf<String>()
            for (i in 0 until indexMap.size) {
                sortedClasses.add(indexMap[i] ?: "UNKNOWN")
            }
            classOrder = sortedClasses
            
            isLoaded = true
            android.util.Log.d("SafeTalkML", "ONNX Model V10 Loaded Successfully. Class Order: $classOrder")
        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "Failed to initialize ONNX Model Manager", e)
        }
    }

    fun runInference(features: FloatArray): Map<String, Float>? {
        val env = ortEnv ?: return null
        val session = ortSession ?: return null

        try {
            // V10 ONNX explicitly expects shape: [1, FeatureCount]
            val shape = longArrayOf(1, features.size.toLong())
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape)

            val results = session.run(Collections.singletonMap("float_input", tensor))
            val probOutput = results[1] // probability mapping tensor usually at index 1 in scikit-learn onnx export

            val resultMap = mutableMapOf<String, Float>()
            
            // Scikit-learn outputs a Map<String, Float> natively using options={'zipmap': True}
            if (probOutput.value is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val probMapList = probOutput.value as List<Map<String, Float>>
                val scores = probMapList[0]
                resultMap["DANGEROUS"] = scores["DANGEROUS"] ?: 0f
                resultMap["SUSPICIOUS"] = scores["SUSPICIOUS"] ?: 0f
                resultMap["SAFE"] = scores["SAFE"] ?: 0f
            } else if (probOutput.value is Array<*>) {
                // Failsafe format array bounding logic
                val probArray = probOutput.value as Array<FloatArray>
                val row = probArray[0]
                for (i in row.indices) {
                    val className = classOrder.getOrNull(i) ?: continue
                    resultMap[className] = row[i]
                }
            } else {
                android.util.Log.e("SafeTalkML", "Unknown ONNX output format type: ${probOutput.info}")
                return null
            }
            return resultMap
        } finally {
            // ORT inherently manages tensor destruction per session run, but keeping scope clean.
        }
    }
}
