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
                val wavFile = WhisperAudioConverter.convertToWav(context, audioFile)
                try {
                    WhisperEngine(modelFile).use { engine ->
                        engine.transcribe(wavFile)
                    }
                } finally {
                    wavFile.delete()
                }
            }
        }
    }
}
