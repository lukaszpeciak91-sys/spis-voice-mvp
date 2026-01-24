package com.example.myapplication.vosk

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import org.vosk.Model

object VoskModelProvider {
    private const val TAG = "VoskModel"
    private const val MODEL_ASSET_PATH = "models/vosk-model-small-pl-0.22"
    private const val MODEL_DIR_NAME = "vosk-model-small-pl-0.22"
    private const val REQUIRED_FINAL_MDL = "am/final.mdl"
    private const val REQUIRED_MFCC = "conf/mfcc.conf"
    private const val REQUIRED_GR_FST = "graph/Gr.fst"
    private const val REQUIRED_HCLR_FST = "graph/HCLr.fst"
    private const val MIN_FINAL_MDL_BYTES = 1_000_000L
    private val modelLock = Any()
    @Volatile private var cachedModel: Model? = null
    @Volatile private var cachedModelDir: File? = null

    fun getModel(context: Context): Model {
        cachedModel?.let { return it }
        synchronized(modelLock) {
            cachedModel?.let { return it }
            val modelDir = getModelDir(context)
            val model = Model(modelDir.absolutePath)
            cachedModel = model
            cachedModelDir = modelDir
            Log.i(TAG, "Vosk model initialized at ${modelDir.absolutePath}")
            return model
        }
    }

    fun getCachedModelDir(): File? = cachedModelDir

    fun getModelDir(context: Context): File {
        val destinationDir = File(context.filesDir, "models/$MODEL_DIR_NAME")
        if (!destinationDir.exists()) {
            copyModelAssets(context, destinationDir)
        }
        if (!isModelSane(destinationDir)) {
            if (destinationDir.exists()) {
                deleteRecursively(destinationDir)
            }
            Log.w(TAG, "Vosk model sanity check failed; re-copied model due to failed sanity check")
            copyModelAssets(context, destinationDir)
        }
        if (!isModelSane(destinationDir)) {
            throw IllegalStateException("Vosk model sanity check failed after copy.")
        }
        logModelSizes(destinationDir)

        return destinationDir
    }

    private fun copyModelAssets(context: Context, destinationDir: File) {
        Log.i(TAG, "Vosk model copy start: $MODEL_ASSET_PATH -> ${destinationDir.absolutePath}")
        copyAssetDir(context, MODEL_ASSET_PATH, destinationDir)
    }

    private fun isModelSane(destinationDir: File): Boolean {
        val finalMdl = File(destinationDir, REQUIRED_FINAL_MDL)
        val mfcc = File(destinationDir, REQUIRED_MFCC)
        val grFst = File(destinationDir, REQUIRED_GR_FST)
        val hclrFst = File(destinationDir, REQUIRED_HCLR_FST)
        val hasGraph = (grFst.exists() && grFst.length() > 0L) || (hclrFst.exists() && hclrFst.length() > 0L)
        if (!finalMdl.exists() || finalMdl.length() < MIN_FINAL_MDL_BYTES) {
            return false
        }
        if (!mfcc.exists() || mfcc.length() <= 0L) {
            return false
        }
        if (!hasGraph) {
            return false
        }
        return true
    }

    private fun logModelSizes(destinationDir: File) {
        val finalMdl = File(destinationDir, REQUIRED_FINAL_MDL)
        val mfcc = File(destinationDir, REQUIRED_MFCC)
        val grFst = File(destinationDir, REQUIRED_GR_FST)
        val hclrFst = File(destinationDir, REQUIRED_HCLR_FST)
        val finalSize = if (finalMdl.exists()) finalMdl.length() else -1L
        val mfccSize = if (mfcc.exists()) mfcc.length() else -1L
        val grSize = if (grFst.exists()) grFst.length() else -1L
        val hclrSize = if (hclrFst.exists()) hclrFst.length() else -1L
        Log.i(
            TAG,
            "Vosk model ready: ${destinationDir.absolutePath}, final.mdl size=$finalSize " +
                "mfcc.conf size=$mfccSize graph/Gr.fst size=$grSize graph/HCLr.fst size=$hclrSize"
        )
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
