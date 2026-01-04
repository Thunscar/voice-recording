package com.stitchcodes.recording.service

import android.Manifest.permission.RECORD_AUDIO
import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.*
import android.util.Log
import com.stitchcodes.recording.ContextHolder
import com.stitchcodes.recording.utils.FoldPickerLauncher
import com.stitchcodes.recording.utils.PermsUtils
import com.stitchcodes.recording.vad.SlieroVadDetector
import org.greenrobot.eventbus.EventBus
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.LinkedBlockingQueue

@SuppressLint("MissingPermission")
class VoiceRecordHandler {

    companion object {
        private const val TAG = "VoiceRecordHandler"

        //音频采样率
        private const val SAMPLE_RATE = 16000

        //一秒钟有多少帧音频
        private const val FRAME_COUNT_SECOND = 20

        //一秒钟有多少帧音频有声音算是这一秒有声音
        private const val CONFIDENCE_SECOND = 5

        //超过多少秒没有声音结算保存音频
        private const val NO_VOICE_TIMEOUT = 10

        //文件的最大秒数
        private const val FILE_SECONDS = 60 * 5

        //有多少秒有声音才进行保存
        private const val DURATION_SECONDS = 5
    }

    private lateinit var audioRecord: AudioRecord
    private lateinit var voiceRecordHandler: HandlerThread
    private lateinit var voiceSaveHandler: HandlerThread
    private val voiceQueue = LinkedBlockingQueue<VoiceFrame>()
    private var isRecording = false
    private lateinit var voiceDetector: SlieroVadDetector

    fun init(context: Context): Boolean {
        //检查权限
        val hasPerms = PermsUtils.hasPerms(context, RECORD_AUDIO)
        if (hasPerms) {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, channelConfig, audioFormat, minBufferSize)
            voiceRecordHandler = HandlerThread("VoiceRecordHandler")
            voiceSaveHandler = HandlerThread("VoiceSaveHandler")
            voiceDetector = SlieroVadDetector(0.7f, 0.5f, SAMPLE_RATE, 100, 100)
            Log.d(TAG, "voice record handler init success")
            return true
        } else {
            Log.e(TAG, "voice record handler init error, missing permission")
            return false
        }
    }

    fun start() {
        if (!::audioRecord.isInitialized || !::voiceRecordHandler.isInitialized || !::voiceSaveHandler.isInitialized) {
            Log.e(TAG, "Voice Handler has not init, please init first")
            return
        }
        voiceRecordHandler.start()
        voiceSaveHandler.start()
        val recordHandler = Handler(voiceRecordHandler.looper)
        val saveHandler = Handler(voiceSaveHandler.looper)
        isRecording = true
        recordHandler.post(recordRunnable)
        saveHandler.post(saveRunnable)
    }

    fun stop() {
        isRecording = false
        if (::audioRecord.isInitialized) {
            audioRecord.stop()
            audioRecord.release()
        }
        voiceQueue.clear()
    }

    private val recordRunnable = object : Runnable {
        private var isSpeed = false
        override fun run() {
            audioRecord.startRecording()
            while (isRecording) {
                val voiceBytes = ByteArray(SAMPLE_RATE * 2 / FRAME_COUNT_SECOND)
                val read = audioRecord.read(voiceBytes, 0, voiceBytes.size)
                if (read > 0 && read == voiceBytes.size) {
                    val result = voiceDetector.apply(voiceBytes, true)
                    if (result.isNotEmpty()) {
                        if (result.containsKey("start")) {
                            isSpeed = true
                        }
                        if (result.containsKey("end")) {
                            isSpeed = false
                        }
                    }
                    voiceQueue.put(VoiceFrame(voiceBytes, isSpeed))
                }
            }
            Log.d(TAG, "Voice recording end")
        }
    }

    private val saveRunnable = object : Runnable {

        //音频帧计数
        private var voiceIndex = 0

        //一秒内有声音的数量
        private var voiceInSecond = 0

        //连续没有声音的秒数
        private var countNoVoice = 0

        //每秒的数据缓存后统一写入文件
        private val dataInSecond = ByteArrayOutputStream()

        //保存的秒数
        private var saveSeconds = 0

        //临时保存的数据
        private val pcmData = ByteArrayOutputStream()

        //保存文件名称
        private var saveFileName: String? = null

        //整个保存时长里有多少秒有声音
        private var voiceInDuration = 0

        override fun run() {
            Log.d(TAG, "record saver start")
            while (isRecording) {
                val voice = voiceQueue.poll()
                if (voice == null) {
                    Thread.sleep(20)
                    continue
                }
                voiceIndex = (voiceIndex + 1) % FRAME_COUNT_SECOND
                dataInSecond.write(voice.bytes)
                //若有声音 计数+1
                if (voice.hasVoice) voiceInSecond++
                if (voiceIndex == 0) {
                    //处理了1s的数据 开始结算
                    //有声音的数量大于每秒置信度个数 则认为有那一秒有声音
                    if (voiceInSecond > CONFIDENCE_SECOND) {
                        Log.d(TAG, "has voice in one second")
                        //有声音
                        countNoVoice = 0
                        if (saveFileName == null) {
                            saveFileName = genSaveFileName()
                        }
                        //整个保存文件中的声音秒数+1
                        voiceInDuration++
                        //若超出了保存时间上限 则将临时数据保存到文件
                        if (saveSeconds >= FILE_SECONDS) {
                            saveAsWav(pcmData, saveFileName!!)
                            resetSaveState()
                            Log.d(TAG, "save to file while over limit")
                        }
                    } else {
                        Log.d(TAG, "has not voice in one second")
                        //无声音
                        countNoVoice++
                        if (countNoVoice >= NO_VOICE_TIMEOUT && saveFileName != null) {
                            if (voiceInDuration >= DURATION_SECONDS) {
                                //截掉最后没有声音的音频
                                trimLastSeconds(pcmData)
                                //保存文件
                                saveAsWav(pcmData, saveFileName!!)
                                resetSaveState()
                                Log.d(TAG, "save to file while loss voice")
                            } else {
                                resetSaveState()
                                Log.d(TAG, "abandon voice file while voice duration not enough")
                            }
                        }
                    }
                    if (saveFileName != null) {
                        pcmData.write(dataInSecond.toByteArray())
                        saveSeconds++
                    }
                    dataInSecond.reset()
                    voiceInSecond = 0
                }
            }
            if (saveFileName != null && voiceInDuration >= DURATION_SECONDS && saveSeconds >= NO_VOICE_TIMEOUT) {
                //保存文件
                saveAsWav(pcmData, saveFileName!!)
                resetSaveState()
                Log.d(TAG, "save to file while stop recording")
            }
        }

        //重置文件保存状态
        private fun resetSaveState() {
            pcmData.reset()
            saveFileName = null
            voiceInDuration = 0
            saveSeconds = 0
        }

        private fun genSaveFileName(): String {
            val pattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            return pattern.format(LocalDateTime.now()) + ".wav"
        }

        fun trimLastSeconds(
            baos: ByteArrayOutputStream,
            sampleRate: Int = SAMPLE_RATE,
            channels: Int = 1,
            bitsPerSample: Int = 16,
            secondsToTrim: Int = NO_VOICE_TIMEOUT - 1
        ) {
            val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
            val bytesToTrim = bytesPerSecond * secondsToTrim

            val data = baos.toByteArray()
            val newLength = (data.size - bytesToTrim).coerceAtLeast(0)

            // 清空原 ByteArrayOutputStream 并写入新数据
            baos.reset()
            baos.write(data, 0, newLength)
        }

        fun saveAsWav(
            baos: ByteArrayOutputStream, fileName: String, sampleRate: Int = SAMPLE_RATE, channels: Int = 1, bitsPerSample: Int = 16
        ) {
            try {
                val outputTarget = FoldPickerLauncher.getCurrentOutputTarget() ?: return

                val pcmData = baos.toByteArray()
                val byteRate = sampleRate * channels * bitsPerSample / 8
                val blockAlign = (channels * bitsPerSample / 8).toShort()
                val dataSize = pcmData.size

                val header = ByteArray(44)
                // WAV 文件头
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                writeInt(header, 4, dataSize + 36)         // ChunkSize
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                writeInt(header, 16, 16)                        // Subchunk1Size
                writeShort(header, 20, 1.toShort())             // AudioFormat PCM = 1
                writeShort(header, 22, channels.toShort())      // NumChannels
                writeInt(header, 24, sampleRate)                // SampleRate
                writeInt(header, 28, byteRate)                  // ByteRate
                writeShort(header, 32, blockAlign) // BlockAlign
                writeShort(header, 34, 16.toShort())            // BitsPerSample
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                writeInt(header, 40, dataSize)     // Subchunk2Size

                outputTarget.createOutputStream(ContextHolder.appContext(), fileName, "audio/wav").use { dos ->
                    dos.write(header)
                    dos.write(pcmData)
                }

                EventBus.getDefault().post(SaveFileEvent())
            } catch (e: Exception) {
                Log.e(TAG, "save as wav file error", e)
            }
        }

        // 辅助函数
        private fun writeInt(header: ByteArray, offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = ((value shr 8) and 0xff).toByte()
            header[offset + 2] = ((value shr 16) and 0xff).toByte()
            header[offset + 3] = ((value shr 24) and 0xff).toByte()
        }

        private fun writeShort(header: ByteArray, offset: Int, value: Short) {
            header[offset] = (value.toInt() and 0xff).toByte()
            header[offset + 1] = ((value.toInt() shr 8) and 0xff).toByte()
        }
    }

    class SaveFileEvent()
}