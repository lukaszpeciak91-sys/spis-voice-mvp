package com.example.myapplication

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        val dir = context.externalCacheDir ?: context.cacheDir
        val file = File(dir, "spis_${System.currentTimeMillis()}.m4a")
        outputFile = file

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        r.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = r
        return file
    }

    fun stop(): File? {
        val r = recorder ?: return null
        try {
            r.stop()
        } catch (_: Exception) {
        } finally {
            r.release()
            recorder = null
        }
        return outputFile
    }

    fun isRecording(): Boolean = recorder != null
}
