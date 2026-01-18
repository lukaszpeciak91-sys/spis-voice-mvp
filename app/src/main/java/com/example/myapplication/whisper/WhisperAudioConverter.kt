package com.example.myapplication.whisper

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object WhisperAudioConverter {
    private const val TARGET_SAMPLE_RATE = 16000
    private const val TAG = "WhisperAudioConverter"

    fun convertToWav(context: Context, inputFile: File): ConversionResult {
        val conversionStart = SystemClock.elapsedRealtime()
        val decoded = decodeToPcm(inputFile)
        val monoSamples = downmixToMono(decoded.samples, decoded.channels)
        val resampled = resample(monoSamples, decoded.sampleRate, TARGET_SAMPLE_RATE)
        val outputFile = File(context.cacheDir, "whisper_${inputFile.nameWithoutExtension}.wav")
        writeWav(outputFile, resampled, TARGET_SAMPLE_RATE)
        val conversionMs = SystemClock.elapsedRealtime() - conversionStart
        Log.i(
            TAG,
            "Output format: ${TARGET_SAMPLE_RATE}Hz mono, samples=${resampled.size} (conversion ${conversionMs}ms)"
        )
        return ConversionResult(
            outputFile = outputFile,
            outputSamples = resampled.size,
            outputSampleRate = TARGET_SAMPLE_RATE,
            outputChannels = 1,
            conversionMs = conversionMs
        )
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
        var outputSampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outputChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmEncoding = trackFormat.getInteger(
            MediaFormat.KEY_PCM_ENCODING,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
            trackFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            -1L
        }
        Log.i(
            TAG,
            "Input format: sampleRate=$outputSampleRate channels=$outputChannels durationUs=$durationUs"
        )

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
                    val newFormat = codec.outputFormat
                    outputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outputChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmEncoding = newFormat.getInteger(
                        MediaFormat.KEY_PCM_ENCODING,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val pcmBytes = outputStream.toByteArray()
        val samples = when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shortCount = pcmBytes.size / 2
                val buffer = ShortArray(shortCount)
                ByteBuffer.wrap(pcmBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .get(buffer)
                buffer
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = pcmBytes.size / 4
                val floatBuffer = FloatArray(floatCount)
                ByteBuffer.wrap(pcmBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .get(floatBuffer)
                val shortBuffer = ShortArray(floatCount)
                for (i in floatBuffer.indices) {
                    val clamped = (floatBuffer[i] * Short.MAX_VALUE).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    shortBuffer[i] = clamped.toShort()
                }
                shortBuffer
            }
            else -> {
                throw IllegalArgumentException("Unsupported PCM encoding: $pcmEncoding")
            }
        }

        return DecodedAudio(samples, outputSampleRate, outputChannels, durationUs)
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

    private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray())
        header.putInt(dataSize)

        FileOutputStream(file).use { output ->
            output.write(header.array())
            val dataBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { sample -> dataBuffer.putShort(sample) }
            output.write(dataBuffer.array())
        }
    }

    data class ConversionResult(
        val outputFile: File,
        val outputSamples: Int,
        val outputSampleRate: Int,
        val outputChannels: Int,
        val conversionMs: Long
    )

    private data class DecodedAudio(
        val samples: ShortArray,
        val sampleRate: Int,
        val channels: Int,
        val durationUs: Long
    )
}
