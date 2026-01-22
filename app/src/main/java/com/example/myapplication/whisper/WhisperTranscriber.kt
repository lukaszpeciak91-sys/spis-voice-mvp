package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class WhisperTranscriber(private val context: Context) {
    suspend fun transcribe(audioFile: File): Result<String> {
        return withContext(Dispatchers.Default) {
            if (!transcriptionInFlight.compareAndSet(false, true)) {
                Log.w(TAG, "Busy: transcription already running.")
                return@withContext Result.failure(IllegalStateException(ERROR_BUSY))
            }

            var deferred: Deferred<String>? = null
            try {
                Log.i(TAG, "Preparing transcription for ${audioFile.name}.")
                val modelFile = WhisperModelProvider.getModelFile(context)
                val samples = try {
                    WhisperAudioConverter.convertToPcm(audioFile)
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
                Log.i(
                    TAG,
                    "Decoded samples=${samples.size} durationMs≈${durationMs}"
                )
                if (samples.size > MAX_SAMPLES) {
                    Log.w(TAG, "Rejected TOO_LONG (limit=${MAX_SAMPLES}).")
                    return@withContext Result.failure(IllegalArgumentException(ERROR_TOO_LONG))
                }

                deferred = transcriptionScope.async {
                    try {
                        Log.i(
                            TAG,
                            "Starting JNI transcription with ${samples.size} samples " +
                                "(lang=${WhisperEngine.DEFAULT_LANGUAGE}, threads=${WhisperEngine.DEFAULT_THREADS})."
                        )
                        WhisperEngine(modelFile).use { engine ->
                            val result = engine.transcribe(
                                samples,
                                WhisperEngine.DEFAULT_LANGUAGE,
                                WhisperEngine.DEFAULT_THREADS
                            )
                            Log.i(TAG, "JNI transcription returned ${result.length} chars.")
                            result
                        }
                    } finally {
                        Log.i(TAG, "Native transcription finished for ${audioFile.name}.")
                    }
                }
                activeTranscription.set(deferred)
                deferred.invokeOnCompletion { cause ->
                    transcriptionInFlight.set(false)
                    activeTranscription.compareAndSet(deferred, null)
                    val status = if (cause == null) "success" else "error"
                    Log.i(TAG, "Transcription attempt completed for ${audioFile.name} ($status).")
                }

                return@withContext try {
                    val result = withTimeout(TRANSCRIPTION_TIMEOUT_MS) { deferred.await() }
                    Log.i(TAG, "Completed (success) for ${audioFile.name}.")
                    Result.success(result)
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "Timeout after ${TRANSCRIPTION_TIMEOUT_MS}ms.", e)
                    Log.i(TAG, "Completed (error) for ${audioFile.name}.")
                    Result.failure(IllegalStateException(ERROR_TIMEOUT, e))
                } catch (e: Exception) {
                    Log.e(TAG, "Transcription failed.", e)
                    Log.i(TAG, "Completed (error) for ${audioFile.name}.")
                    Result.failure(e)
                }
            } finally {
                if (deferred == null) {
                    transcriptionInFlight.set(false)
                    activeTranscription.set(null)
                    Log.i(TAG, "Transcription attempt completed for ${audioFile.name} (error).")
                } else if (deferred?.isCompleted == false) {
                    Log.w(TAG, "Transcription still running after timeout; keeping busy flag.")
                }
            }
        }
    }

    private companion object {
        private const val TAG = "WhisperTranscriber"
        private const val TRANSCRIPTION_TIMEOUT_MS = 30_000L
        private const val ERROR_TIMEOUT = "ERROR_TIMEOUT"
        private const val ERROR_BUSY = "ERROR_BUSY"
        private const val ERROR_TOO_LONG = "TOO_LONG: offline limit 2.5s"
        private const val TARGET_SAMPLE_RATE_HZ = 16_000
        private const val MAX_DURATION_SECONDS = 2.5
        private val MAX_SAMPLES = (TARGET_SAMPLE_RATE_HZ * MAX_DURATION_SECONDS).toInt()
    }

    private val transcriptionInFlight = AtomicBoolean(false)
    private val activeTranscription = AtomicReference<Deferred<String>?>(null)
    private val transcriptionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
