package com.example.myapplication.vosk

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    data class Buffered(val jobId: String) : TranscriptionStartResult()
    data class Busy(val message: String) : TranscriptionStartResult()
}


object VoskTranscriptionManager {
    private const val TAG = "VoskTranscriptionMgr"
    private const val ERROR_BUFFER_FULL = "Jest już jedno nagranie w kolejce"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    private val updates = MutableSharedFlow<TranscriptionState>(extraBufferCapacity = 16)
    private val lock = Any()
    private var activeJob: PendingJob? = null
    private var bufferedJob: PendingJob? = null
    private var transcriber: VoskTranscriber? = null

    val transcriptionState: StateFlow<TranscriptionState> = state.asStateFlow()
    val transcriptionUpdates: SharedFlow<TranscriptionState> = updates.asSharedFlow()

    private data class PendingJob(
        val jobId: String,
        val audioFile: File
    )

    fun startTranscription(context: Context, audioFile: File): TranscriptionStartResult {
        val pendingJob = PendingJob(
            jobId = UUID.randomUUID().toString(),
            audioFile = audioFile
        )
        var shouldStartNow = false

        val startResult = synchronized(lock) {
            when {
                activeJob == null -> {
                    activeJob = pendingJob
                    shouldStartNow = true
                    TranscriptionStartResult.Started(pendingJob.jobId)
                }

                bufferedJob == null -> {
                    bufferedJob = pendingJob
                    TranscriptionStartResult.Buffered(pendingJob.jobId)
                }

                else -> {
                    Log.w(TAG, "BUSY: buffer full")
                    deleteAudioFile(audioFile)
                    TranscriptionStartResult.Busy(ERROR_BUFFER_FULL)
                }
            }
        }

        if (shouldStartNow) {
            launchTranscription(context, pendingJob)
        }

        return startResult
    }

    private fun launchTranscription(context: Context, pendingJob: PendingJob) {
        val jobId = pendingJob.jobId
        val audioFile = pendingJob.audioFile
        val audioPath = audioFile.absolutePath

        emitTranscriptionUpdate(TranscriptionState.Running(jobId = jobId, audioPath = audioPath))

        scope.launch {
            try {
                val result = getTranscriber(context).transcribe(audioFile)
                val trimmed = result.getOrNull()?.trim().orEmpty()
                if (result.isSuccess && trimmed.isNotEmpty()) {
                    Log.i(
                        TAG,
                        "APP: transcription result emitted jobId=$jobId filename=${audioFile.name} textLength=${trimmed.length}"
                    )
                    emitTranscriptionUpdate(TranscriptionState.Success(
                        jobId = jobId,
                        audioPath = audioPath,
                        text = trimmed
                    ))
                } else {
                    val message = result.exceptionOrNull()?.message ?: "Transcription failed."
                    emitTranscriptionUpdate(TranscriptionState.Error(
                        jobId = jobId,
                        audioPath = audioPath,
                        message = message
                    ))
                }
            } catch (e: CancellationException) {
                Log.w(TAG, "Transcription cancelled (lifecycle) jobId=$jobId")
                emitTranscriptionUpdate(TranscriptionState.Cancelled(jobId = jobId, audioPath = audioPath))
            } finally {
                scheduleNext(context, completedJobId = jobId)
            }
        }
    }

    private fun scheduleNext(context: Context, completedJobId: String) {
        val nextJob = synchronized(lock) {
            if (activeJob?.jobId == completedJobId) {
                activeJob = null
            }

            val promoted = if (activeJob == null) {
                bufferedJob.also { bufferedJob = null }
            } else {
                null
            }

            if (promoted != null) {
                activeJob = promoted
            }

            promoted
        }

        if (nextJob != null) {
            launchTranscription(context, nextJob)
        } else {
            emitTranscriptionUpdate(TranscriptionState.Idle)
        }
    }

    private fun emitTranscriptionUpdate(nextState: TranscriptionState) {
        state.value = nextState
        updates.tryEmit(nextState)
    }


    private fun deleteAudioFile(audioFile: File) {
        if (audioFile.exists() && audioFile.delete()) {
            Log.w(TAG, "BUSY -> deleted temp audio ${audioFile.absolutePath}")
        } else {
            Log.w(TAG, "BUSY -> failed to delete temp audio ${audioFile.absolutePath}")
        }
    }

    private fun getTranscriber(context: Context): VoskTranscriber {
        val appContext = context.applicationContext
        return transcriber ?: synchronized(this) {
            transcriber ?: VoskTranscriber(appContext).also { transcriber = it }
        }
    }
}
