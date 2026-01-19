package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriber(private val context: Context) {
    suspend fun transcribe(audioFile: File): Result<String> {
        return withContext(Dispatchers.Default) {
            runCatching {
                try {
                    Log.i(TAG, "Preparing transcription for ${audioFile.name}.")
                    val modelFile = WhisperModelProvider.getModelFile(context)
                    val samples = try {
                        WhisperAudioConverter.convertToPcm(audioFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Audio conversion failed.", e)
                        throw IllegalArgumentException(
                            "Transcription failed: unsupported audio format",
                            e
                        )
                    }
                    Log.i(TAG, "Starting JNI transcription with ${samples.size} samples.")
                    withTimeout(TRANSCRIPTION_TIMEOUT_MS) {
                        WhisperEngine(modelFile).use { engine ->
                            val result = engine.transcribe(samples)
                            Log.i(TAG, "JNI transcription returned ${result.length} chars.")
                            result
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Transcription timed out.", e)
                    throw IllegalStateException("Transcription timeout", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed.", e)
                    throw e
                } finally {
                    Log.i(TAG, "Transcription attempt completed for ${audioFile.name}.")
                }
            }
        }
    }

    private companion object {
        private const val TAG = "WhisperTranscriber"
        private const val TRANSCRIPTION_TIMEOUT_MS = 30_000L
    }
}
