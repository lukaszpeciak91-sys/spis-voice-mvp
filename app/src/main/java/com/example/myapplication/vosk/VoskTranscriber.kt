package com.example.myapplication.vosk

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VoskTranscriber(private val context: Context) {
    suspend fun transcribe(audioFile: File): Result<String> {
        return withContext(Dispatchers.Default) {
            if (!transcriptionInFlight.compareAndSet(false, true)) {
                Log.w(TAG, "BUSY: transcription already running")
                return@withContext Result.failure(IllegalStateException(ERROR_BUSY))
            }

            val startTime = System.nanoTime()
            try {
                Log.i(TAG, "Preparing transcription for ${audioFile.name}")
                val modelDir = VoskModelProvider.getModelDir(context)
                Log.i(TAG, "Model ready at ${modelDir.absolutePath}")

                val samples = try {
                    VoskAudioConverter.convertToPcm16(audioFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Audio conversion failed: ${e.message}", e)
                    val message = e.message ?: "unknown error"
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Transcription failed: audio conversion error - $message",
                            e
                        )
                    )
                }

                val durationMs = (samples.size.toDouble() / TARGET_SAMPLE_RATE_HZ * 1000).toLong()
                Log.i(TAG, "Decoded samples=${samples.size} durationMs≈${durationMs}")

                return@withContext try {
                    val text = withTimeout(TRANSCRIPTION_TIMEOUT_MS) {
                        runTranscription(modelDir, samples)
                    }
                    val trimmed = text.trim()
                    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                    if (trimmed.isNotEmpty()) {
                        Log.i(TAG, "Completed (success) for ${audioFile.name} in ${elapsedMs}ms")
                        Result.success(trimmed)
                    } else {
                        Log.i(TAG, "Completed (no_segments) for ${audioFile.name} in ${elapsedMs}ms")
                        Result.failure(IllegalStateException(ERROR_NO_SEGMENTS))
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Timeout after ${TRANSCRIPTION_TIMEOUT_MS}ms.", e)
                    Log.i(TAG, "Completed (error) for ${audioFile.name}")
                    Result.failure(IllegalStateException(ERROR_TIMEOUT, e))
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed.", e)
                    Log.i(TAG, "Completed (error) for ${audioFile.name}")
                    Result.failure(e)
                }
            } finally {
                transcriptionInFlight.set(false)
            }
        }
    }

    private fun runTranscription(modelDir: File, samples: ShortArray): String {
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, TARGET_SAMPLE_RATE_HZ.toFloat()).use { recognizer ->
                recognizer.acceptWaveForm(samples, samples.size)
                val finalJson = recognizer.finalResult
                val parsed = gson.fromJson(finalJson, VoskFinalResult::class.java)
                return parsed.text.orEmpty()
            }
        }
    }

    private data class VoskFinalResult(
        val text: String? = null
    )

    private companion object {
        private const val TAG = "VoskTranscriber"
        private const val TRANSCRIPTION_TIMEOUT_MS = 20_000L
        private const val ERROR_TIMEOUT = "ERROR_TIMEOUT"
        private const val ERROR_BUSY = "ERROR_BUSY"
        private const val ERROR_NO_SEGMENTS = "NO_SEGMENTS"
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
        private val gson = Gson()
    }

    private val transcriptionInFlight = AtomicBoolean(false)
}
