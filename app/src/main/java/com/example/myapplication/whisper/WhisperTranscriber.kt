package com.example.myapplication.whisper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriber(private val context: Context) {
    suspend fun transcribe(audioFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val modelFile = WhisperModelProvider.getModelFile(context)
                val samples = try {
                    WhisperAudioConverter.convertToPcm(audioFile)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Transcription failed: unsupported audio format",
                        e
                    )
                }
                WhisperEngine(modelFile).use { engine ->
                    engine.transcribe(samples)
                }
            }
        }
    }
}
