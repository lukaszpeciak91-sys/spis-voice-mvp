package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import java.io.File

object WhisperModelProvider {
    private const val MODEL_ASSET_PATH = "models/ggml-tiny.bin"
    private const val MODEL_FILE_NAME = "ggml-tiny.bin"
    private const val MIN_MODEL_BYTES = 1_000_000L
    private const val TAG = "WhisperModelProvider"

    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        if (!modelFile.exists() || modelFile.length() < MIN_MODEL_BYTES) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            copyAssetToFile(context, MODEL_ASSET_PATH, modelFile)
            Log.i(TAG, "Copied model to ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            if (modelFile.length() < MIN_MODEL_BYTES) {
                throw IllegalStateException("Model copy failed (size too small)")
            }
        }
        return modelFile
    }

    private fun copyAssetToFile(context: Context, assetPath: String, destination: File) {
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
