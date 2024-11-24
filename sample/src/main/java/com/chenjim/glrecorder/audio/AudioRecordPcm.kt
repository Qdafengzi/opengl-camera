package com.chenjim.glrecorder.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.chenjim.glrecorder.util.Logger

/**
 * @author chenjim me@h89.cn
 * @description [AudioRecordPcm]
 * @date 2024/02/08 11:56
 */
class AudioRecordPcm {
    companion object {
        private const val READ_BUF_LEN = 1024 * 2
    }

    private var mAudioRecord: AudioRecord? = null
    private val mAudioBuffer: ByteArray by lazy { ByteArray(READ_BUF_LEN) }

    @Volatile
    private var isRecord = false

    // 编码制式
    private var audioFormat = AudioFormat.ENCODING_PCM_16BIT

    // stereo 立体声，
    private var channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private var callback: Callback? = null

    private constructor()
    constructor(callback: Callback?) {
        this.callback = callback
    }

    constructor(audioFormat: Int, channelConfig: Int, callback: Callback?) {
        this.audioFormat = audioFormat
        this.channelConfig = channelConfig
        this.callback = callback
    }

    /**
     * 初始化AudioRecord
     */
    @SuppressLint("MissingPermission")
    private fun initAudioRecord(): AudioRecord? {
        val sampleRates = intArrayOf(44100, 22050, 16000, 11025)
        for (sampleRate in sampleRates) {

            // buffer 缓存要比最小值大，越大越耗内存
            val audioMaxBufSize =
                2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val mAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, channelConfig,
                audioFormat, audioMaxBufSize
            )
            // 并不是所有设备都支持所有 sampleRates
            if (mAudioRecord.state == AudioRecord.STATE_INITIALIZED) {
                val mAudioChanelCount = if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
                val mSampleRateType = ADTSUtils.getSampleRateType(sampleRate)
                if (callback != null) {
                    callback!!.start(
                        sampleRate,
                        mSampleRateType,
                        mAudioChanelCount,
                        audioMaxBufSize
                    )
                }
                Logger.w("$sampleRate,$mSampleRateType,$mSampleRateType,$mAudioChanelCount,$audioMaxBufSize")
                return mAudioRecord
            }
        }
        return null
    }

    /**
     * start record pcm
     */
    // @Permissions(Manifest.permission.RECORD_AUDIO)
    fun start() {
        mAudioRecord = initAudioRecord()
        if (mAudioRecord == null) {
            throw NullPointerException("init audio record fail")
        }
        mAudioRecord!!.startRecording()
        isRecord = true
        // 开启录音
        val mRecordThread = Thread { fetchPcmFromDevice() }
        mRecordThread.start()
    }

    /**
     * stop record pcm
     */
    fun stop() {
        isRecord = false
    }

    /**
     * 采集音频数据
     */
    private fun fetchPcmFromDevice() {
        Logger.w("")
        while (isRecord && mAudioRecord != null && !Thread.interrupted()) {
            val size = mAudioRecord!!.read(mAudioBuffer, 0, mAudioBuffer.size)
            if (size < 0) {
                Logger.w("audio ignore ,no data to read")
                break
            }
            val audio = ByteArray(size)
            System.arraycopy(mAudioBuffer, 0, audio, 0, size)
//            Logger.v("get pcm data len:" + audio.size)
            callback?.offerPcm(audio)
            SystemClock.sleep(10)
        }
        release()
    }

    /**
     * 释放资源
     */
    private fun release() {
        Logger.w("release")
        mAudioRecord?.stop()
        mAudioRecord?.release()
        callback?.finished()
    }

    /**
     *  record callback
     */
    interface Callback {
        /**
         * start  record
         */
        fun start(sampleRate: Int, sampleRateType: Int, channelCount: Int, maxBufSize: Int)

        /**
         * out pcm
         */
        fun offerPcm(data: ByteArray)

        /**
         * record is finished
         */
        fun finished()
    }
}