package com.snow.safetalk.ml

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.Collections

/**
 * Singleton managing the ONNX Runtime Environment and Session lifetime for SafeTalk V11 Model Base.
 */
class OnnxModelManagerV11 private constructor(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    var ortSession: OrtSession? = null
        private set
        
    var classOrder: List<String> = emptyList()
        private set

    private var isLoaded = false

    companion object {
        @Volatile
        private var INSTANCE: OnnxModelManagerV11? = null

        fun getInstance(context: Context): OnnxModelManagerV11 {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OnnxModelManagerV11(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    @Synchronized
    fun load() {
        if (isLoaded) return
        try {
            android.util.Log.d("SafeTalkML", "Initializing ONNX Model V11 Context Engine...")
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Explicitly map over to V11 safetalk baseline binary
            val modelBytes = context.assets.open("ml/safetalk_classifier_v11.onnx").readBytes()
            ortSession = ortEnv?.createSession(modelBytes)
            
            // Extract label alignments securely mapping output layers
            val classOrderJson = context.assets.open("ml/label_encoder.json").use { it.bufferedReader().readText() }
            val classObj = JSONObject(classOrderJson)
            
            val keys = classObj.keys()
            val indexMap = mutableMapOf<Int, String>()
            keys.forEach { key ->
                val idx = classObj.getInt(key)
                indexMap[idx] = key
            }
            
            val sortedClasses = mutableListOf<String>()
            for (i in 0 until indexMap.size) {
                sortedClasses.add(indexMap[i] ?: "UNKNOWN")
            }
            classOrder = sortedClasses
            
            isLoaded = true
            android.util.Log.d("SafeTalkML", "ONNX Model V11 Loaded Successfully. Class Mapping: $classOrder")
        } catch (e: Exception) {
            android.util.Log.e("SafeTalkML", "Failed to initialize V11 Model Engine", e)
        }
    }

    /**
     * Executes standard Session.run safely encapsulating tensor execution buffers across C++ heap borders.
     */
    fun runInference(features: FloatArray): Map<String, Float>? {
        val env = ortEnv ?: return null
        val session = ortSession ?: return null
        
        var tensor: OnnxTensor? = null
        var results: OrtSession.Result? = null

        try {
            val shape = longArrayOf(1, features.size.toLong())
            tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(features), shape)

            // V11 execution tensor mappings
            results = session.run(Collections.singletonMap("float_input", tensor))
            val probOutput = results[1]

            val resultMap = mutableMapOf<String, Float>()
            
            if (probOutput.value is List<*>) {
                @Suppress("UNCHECKED_CAST")
                val probMapList = probOutput.value as List<Map<String, Float>>
                val scores = probMapList[0]
                resultMap["DANGEROUS"] = scores["DANGEROUS"] ?: 0f
                resultMap["SUSPICIOUS"] = scores["SUSPICIOUS"] ?: 0f
                resultMap["SAFE"] = scores["SAFE"] ?: 0f
            } else if (probOutput.value is Array<*>) {
                val probArray = probOutput.value as Array<FloatArray>
                val row = probArray[0]
                for (i in row.indices) {
                    val className = classOrder.getOrNull(i) ?: continue
                    resultMap[className] = row[i]
                }
            } else {
                android.util.Log.e("SafeTalkML", "Unknown ONNX result type during evaluation.")
                return null
            }
            return resultMap
        } finally {
            // Guarantee C++ tensor heap is destroyed post-execution aggressively avoiding native memory leaks.
            tensor?.close()
            results?.close()
        }
    }
}
