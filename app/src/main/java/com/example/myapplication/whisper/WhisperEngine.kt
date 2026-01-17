package com.example.myapplication.whisper

import java.io.Closeable
import java.io.File

/**
 * Offline Whisper transcription engine wrapper.
 *
 * Audio must be mono, 16kHz, PCM16. Conversion is not implemented yet.
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
        nativeHandle = nativeInit(modelFile.absolutePath)
        check(nativeHandle != 0L) { "Failed to initialize whisper engine." }
    }

    fun transcribe(audioFile: File): String {
        require(audioFile.exists()) {
            "Audio file not found at ${audioFile.absolutePath}"
        }
        return nativeTranscribe(nativeHandle, audioFile.absolutePath)
    }

    override fun close() {
        nativeRelease(nativeHandle)
    }

    private external fun nativeInit(modelPath: String): Long

    private external fun nativeTranscribe(handle: Long, audioPath: String): String

    private external fun nativeRelease(handle: Long)

    companion object {
        init {
            System.loadLibrary("whisper_jni")
        }
    }
}
