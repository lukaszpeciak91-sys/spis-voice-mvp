package com.example.myapplication.whisper

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriber(private val context: Context) {
    companion object {
        private const val TAG = "WhisperTranscriber"
    }

    suspend fun transcribe(audioFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val modelFile = WhisperModelProvider.getModelFile(context)
                val conversion = try {
                    WhisperAudioConverter.convertToWav(context, audioFile)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Transcription failed: unsupported audio format",
                        e
                    )
                }
                val wavFile = conversion.outputFile
                try {
                    WhisperEngine(modelFile).use { engine ->
                        val transcriptionStart = SystemClock.elapsedRealtime()
                        val result = engine.transcribe(wavFile)
                        val transcriptionMs = SystemClock.elapsedRealtime() - transcriptionStart
                        Log.i(
                            TAG,
                            "Transcription complete in ${transcriptionMs}ms for ${conversion.outputSamples} samples"
                        )
                        result
                    }
                } finally {
                    wavFile.delete()
                }
            }
        }
    }
}
