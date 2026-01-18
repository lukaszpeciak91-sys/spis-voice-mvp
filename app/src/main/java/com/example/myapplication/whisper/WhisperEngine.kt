package com.example.myapplication.whisper

import java.io.Closeable
import java.io.File

/**
 * Offline Whisper transcription engine wrapper.
 *
 * Audio must be mono, 16kHz, PCM16. Convert input before calling transcribe.
 *
 * Example:
 * val engine = WhisperEngine(File(filesDir, "models/ggml-base.bin"))
 * val text = engine.transcribe(File(filesDir, "audio/sample.wav"))
 * engine.close()
 */
class WhisperEngine(private val modelFile: File) : Closeable {
    private val nativeHandle: Long

    init {
        require(modelFile.exists()) {
            "Model file not found at ${modelFile.absolutePath}. " +
                "Provide a local whisper.cpp model path."
        }
        require(modelFile.length() < 1_000_000) {
            "Model file exceeds safety limit: ${modelFile.length()} bytes."
        }
        nativeHandle = nativeInit(modelFile.absolutePath)
        check(nativeHandle != 0L) { "Failed to initialize whisper engine." }
    }

    fun transcribe(samples: FloatArray): String {
        require(samples.isNotEmpty()) { "Audio samples are empty." }
        return nativeTranscribe(nativeHandle, samples)
    }

    override fun close() {
        nativeRelease(nativeHandle)
    }

    private external fun nativeInit(modelPath: String): Long

    private external fun nativeTranscribe(handle: Long, samples: FloatArray): String

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }
}
