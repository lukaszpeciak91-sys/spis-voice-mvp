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
        return try {
            val decoded = decodeToPcm(inputFile)
            Log.i(
                LOG_TAG,
                "Input format mime=${decoded.mimeType} sampleRate=${decoded.sampleRate}Hz channels=${decoded.channels}"
            )
            val monoSamples = downmixToMono(decoded.samples, decoded.channels)
            val outputSamples = calculateOutputSampleCount(
                monoSamples.size,
                decoded.sampleRate,
                TARGET_SAMPLE_RATE
            )
            Log.i(
                LOG_TAG,
                "Conversion summary sourceRate=${decoded.sampleRate} targetRate=${TARGET_SAMPLE_RATE} " +
                    "channels=${decoded.channels} inputBytes=${decoded.byteCount} " +
                    "inputSamples=${monoSamples.size} outSamples=${outputSamples}"
            )
            val resampled = resample(monoSamples, decoded.sampleRate, TARGET_SAMPLE_RATE)
            val floatSamples = pcm16ToFloat(resampled)
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            Log.i(
                LOG_TAG,
                "Output format sampleRate=${TARGET_SAMPLE_RATE}Hz channels=1 samples=${floatSamples.size} conversionMs=${elapsedMs}"
            )
            floatSamples
        } catch (e: Exception) {
            val message = e.message ?: "unknown error"
            Log.e(LOG_TAG, "Audio conversion failed: $message", e)
            if (e is IllegalArgumentException && message.startsWith("Audio conversion failed:")) {
                throw e
            }
            throw IllegalArgumentException("Audio conversion failed: $message", e)
        }
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

        require(trackIndex >= 0 && trackFormat != null) {
            "Audio conversion failed: no audio track found in ${inputFile.name}"
        }

        extractor.selectTrack(trackIndex)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME)
            ?: throw IllegalArgumentException("Audio conversion failed: missing MIME type")
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
        if (pcmBytes.isEmpty()) {
            throw IllegalArgumentException("Audio conversion failed: decoded audio is empty")
        }
        if (pcmBytes.size % 2 != 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: decoded PCM byte count is not even (${pcmBytes.size})"
            )
        }
        val shortCount = pcmBytes.size / 2
        val samples = ShortArray(shortCount)
        ByteBuffer.wrap(pcmBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(samples)

        if (sampleRate <= 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: invalid sample rate $sampleRate"
            )
        }
        if (channels <= 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: invalid channel count $channels"
            )
        }

        return DecodedAudio(samples, sampleRate, channels, mime, pcmBytes.size)
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
        val outputLength = calculateOutputSampleCount(samples.size, inputRate, targetRate)
        if (inputRate == targetRate) return samples
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

    private fun calculateOutputSampleCount(
        inputSamples: Int,
        sourceSampleRate: Int,
        targetSampleRate: Int
    ): Int {
        if (sourceSampleRate <= 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: invalid source sample rate $sourceSampleRate"
            )
        }
        if (targetSampleRate <= 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: invalid target sample rate $targetSampleRate"
            )
        }
        if (inputSamples <= 0) {
            throw IllegalArgumentException(
                "Audio conversion failed: invalid input sample count $inputSamples"
            )
        }
        // Round to nearest: (in * target + source/2) / source.
        val outSamplesLong =
            (inputSamples.toLong() * targetSampleRate + sourceSampleRate / 2L) / sourceSampleRate
        if (outSamplesLong <= 0 || outSamplesLong > Int.MAX_VALUE) {
            throw IllegalArgumentException(
                "Audio conversion failed: computed output samples invalid: " +
                    "out=$outSamplesLong in=$inputSamples srcRate=$sourceSampleRate dstRate=$targetSampleRate"
            )
        }
        return outSamplesLong.toInt()
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
        val mimeType: String,
        val byteCount: Int
    )
}
