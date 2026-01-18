package com.example.myapplication.whisper

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object WhisperAudioConverter {
    private const val TARGET_SAMPLE_RATE = 16000
    private const val LOG_TAG = "WhisperAudioConverter"

    fun convertToPcm(inputFile: File): FloatArray {
        val startTime = System.nanoTime()
        val decoded = decodeToPcm(inputFile)
        Log.i(
            LOG_TAG,
            "Input format mime=${decoded.mimeType} sampleRate=${decoded.sampleRate}Hz channels=${decoded.channels}"
        )
        val monoSamples = downmixToMono(decoded.samples, decoded.channels)
        val resampled = resample(monoSamples, decoded.sampleRate, TARGET_SAMPLE_RATE)
        val floatSamples = pcm16ToFloat(resampled)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        Log.i(
            LOG_TAG,
            "Output format sampleRate=${TARGET_SAMPLE_RATE}Hz channels=1 samples=${floatSamples.size} conversionMs=${elapsedMs}"
        )
        return floatSamples
    }

    private fun decodeToPcm(inputFile: File): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        var trackIndex = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                trackIndex = i
                trackFormat = format
                break
            }
        }

        require(trackIndex >= 0 && trackFormat != null) { "No audio track found in ${inputFile.name}" }

        extractor.selectTrack(trackIndex)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: error("Missing MIME type")
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(trackFormat, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val outputStream = ByteArrayOutputStream()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputBufferIndex = codec.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    val sampleSize = if (inputBuffer != null) {
                        extractor.readSampleData(inputBuffer, 0)
                    } else {
                        -1
                    }
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // No-op; we rely on the original track format for sample rate/channels.
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcmBytes = outputStream.toByteArray()
        val shortCount = pcmBytes.size / 2
        val samples = ShortArray(shortCount)
        ByteBuffer.wrap(pcmBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(samples)

        return DecodedAudio(samples, sampleRate, channels, mime)
    }

    private fun downmixToMono(samples: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return samples
        val frameCount = samples.size / channels
        val mono = ShortArray(frameCount)
        var sampleIndex = 0
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) {
                sum += samples[sampleIndex++].toInt()
            }
            mono[i] = (sum / channels).toShort()
        }
        return mono
    }

    private fun resample(samples: ShortArray, inputRate: Int, targetRate: Int): ShortArray {
        if (inputRate == targetRate) return samples
        val outputLength = (samples.size * targetRate / inputRate.toDouble()).roundToInt()
        val output = ShortArray(outputLength)
        val ratio = inputRate.toDouble() / targetRate.toDouble()
        for (i in output.indices) {
            val srcIndex = i * ratio
            val index = srcIndex.toInt()
            val nextIndex = (index + 1).coerceAtMost(samples.lastIndex)
            val frac = srcIndex - index
            val interpolated = samples[index] + (samples[nextIndex] - samples[index]) * frac
            val clamped = interpolated.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output[i] = clamped.toShort()
        }
        return output
    }

    private fun pcm16ToFloat(samples: ShortArray): FloatArray {
        val floats = FloatArray(samples.size)
        for (i in samples.indices) {
            floats[i] = samples[i] / 32768.0f
        }
        return floats
    }

    private data class DecodedAudio(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val mimeType: String
    )
}
