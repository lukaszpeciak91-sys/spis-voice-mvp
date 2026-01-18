package com.example.myapplication.whisper

import android.content.Context
import java.io.File

object WhisperModelProvider {
    private const val MODEL_ASSET_PATH = "models/ggml-tiny.bin"
    private const val MODEL_FILE_NAME = "ggml-tiny.bin"

    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        if (!modelFile.exists() || modelFile.length() == 0L) {
            copyAssetToFile(context, MODEL_ASSET_PATH, modelFile)
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
