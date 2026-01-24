package com.example.myapplication.vosk

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TranscriptionState {
    data object Idle : TranscriptionState()
    data class Running(val jobId: String, val audioPath: String) : TranscriptionState()
    data class Success(val jobId: String, val audioPath: String, val text: String) : TranscriptionState()
    data class Error(val jobId: String, val audioPath: String, val message: String) : TranscriptionState()
    data class Cancelled(val jobId: String, val audioPath: String) : TranscriptionState()
}

sealed class TranscriptionStartResult {
    data class Started(val jobId: String) : TranscriptionStartResult()
    data class Busy(val message: String) : TranscriptionStartResult()
}

object VoskTranscriptionManager {
    private const val TAG = "VoskTranscriptionMgr"
    private const val ERROR_BUSY = "ERROR_BUSY"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    private val busy = AtomicBoolean(false)
    private var transcriber: VoskTranscriber? = null

    val transcriptionState: StateFlow<TranscriptionState> = state.asStateFlow()

    fun startTranscription(context: Context, audioFile: File): TranscriptionStartResult {
        if (!busy.compareAndSet(false, true)) {
            Log.w(TAG, "BUSY: transcription already running")
            if (audioFile.exists() && audioFile.delete()) {
                Log.w(TAG, "BUSY -> deleted temp audio ${audioFile.absolutePath}")
            } else {
                Log.w(TAG, "BUSY -> failed to delete temp audio ${audioFile.absolutePath}")
            }
            return TranscriptionStartResult.Busy(ERROR_BUSY)
        }

        val jobId = UUID.randomUUID().toString()
        val audioPath = audioFile.absolutePath
        state.value = TranscriptionState.Running(jobId = jobId, audioPath = audioPath)

        scope.launch {
            try {
                val result = getTranscriber(context).transcribe(audioFile)
                val trimmed = result.getOrNull()?.trim().orEmpty()
                if (result.isSuccess && trimmed.isNotEmpty()) {
                    state.value = TranscriptionState.Success(
                        jobId = jobId,
                        audioPath = audioPath,
                        text = trimmed
                    )
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Transcription failed."
                    state.value = TranscriptionState.Error(
                        jobId = jobId,
                        audioPath = audioPath,
                        message = message
                    )
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Transcription cancelled (lifecycle) jobId=$jobId")
                state.value = TranscriptionState.Cancelled(jobId = jobId, audioPath = audioPath)
            } finally {
                busy.set(false)
            }
        }

        return TranscriptionStartResult.Started(jobId)
    }

    private fun getTranscriber(context: Context): VoskTranscriber {
        val appContext = context.applicationContext
        return transcriber ?: synchronized(this) {
            transcriber ?: VoskTranscriber(appContext).also { transcriber = it }
        }
    }
}
