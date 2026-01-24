package com.example.myapplication.vosk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object VoskModelProvider {
    private const val TAG = "VoskModel"
    private const val MODEL_ASSET_PATH = "models/vosk-model-small-pl-0.22"
    private const val MODEL_DIR_NAME = "vosk-model-small-pl-0.22"
    private const val REQUIRED_FILE = "am/final.mdl"

    fun getModelDir(context: Context): File {
        val destinationDir = File(context.filesDir, "models/$MODEL_DIR_NAME")
        val requiredFile = File(destinationDir, REQUIRED_FILE)
        if (!requiredFile.exists() || requiredFile.length() <= 0L) {
            if (destinationDir.exists()) {
                deleteRecursively(destinationDir)
            }
            Log.i(TAG, "Vosk model copy start: $MODEL_ASSET_PATH -> ${destinationDir.absolutePath}")
            copyAssetDir(context, MODEL_ASSET_PATH, destinationDir)
        }

        if (!requiredFile.exists() || requiredFile.length() <= 0L) {
            throw IllegalStateException(
                "Vosk model missing required file: ${requiredFile.absolutePath}"
            )
        }

        val finalSize = requiredFile.length()
        val mfccFile = File(destinationDir, "conf/mfcc.conf")
        val wordsFile = File(destinationDir, "graph/words.txt")
        val mfccSize = if (mfccFile.exists()) mfccFile.length() else -1L
        val wordsSize = if (wordsFile.exists()) wordsFile.length() else -1L
        Log.i(
            TAG,
            "Vosk model ready: ${destinationDir.absolutePath}, final.mdl size=$finalSize " +
                "mfcc.conf size=$mfccSize words.txt size=$wordsSize"
        )

        return destinationDir
    }

    private fun copyAssetDir(context: Context, assetPath: String, destination: File) {
        val assets = context.assets
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        if (!destination.exists()) {
            destination.mkdirs()
        }
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childDest = File(destination, entry)
            copyAssetDir(context, childAssetPath, childDest)
        }
    }

    private fun deleteRecursively(target: File) {
        if (target.isDirectory) {
            target.listFiles()?.forEach { child -> deleteRecursively(child) }
        }
        target.delete()
    }
}
