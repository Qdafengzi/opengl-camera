package com.chenjim.glrecorder.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.SystemClock
import com.chenjim.glrecorder.util.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * @author chenjim me@h89.cn
 * @description [PcmEncodeAacCtrl]
 *
 * @date 2024/02/08 14:35
 */
class PcmEncodeAacCtrl {
    companion object {
        private const val QUEUE_MAX = 5

        /**
         * ADTS(Audio Data Transport Stream)头部的大小
         */
        private const val AAC_HEAD_LEN = 7
    }

    private var mAudioEncoder: MediaCodec? = null
    private var aacBos: BufferedOutputStream? = null

    private val queue: LinkedBlockingQueue<ByteArray> by lazy {
        LinkedBlockingQueue<ByteArray>(QUEUE_MAX)
    }

    @Volatile
    private var isEncoding = false
    private lateinit var encodeInputBuffers: Array<ByteBuffer>
    private lateinit var encodeOutputBuffers: Array<ByteBuffer>
    private val mAudioEncodeBufferInfo: MediaCodec.BufferInfo by lazy {
        MediaCodec.BufferInfo()
    }
    private var timeUs = System.nanoTime() / 1000

    /**
     * 参考 [ADTSUtils.getSampleRateType]
     */
    private var sampleRateType = 4

    /**
     * [MediaCodec.getOutputFormat].getInteger([MediaFormat.KEY_CHANNEL_COUNT])
     */
    private var channelCount = 2

    /**
     *  [MediaCodec.getOutputFormat].getInteger([MediaFormat.KEY_PROFILE])
     * [MediaCodecInfo.CodecProfileLevel.AACObjectLC]
     */
    private var outProfile = 2

    private var callback: Callback? = null
    private var mediaFormat: MediaFormat? = null

    private fun initAudioEncoder(
        sampleRate: Int,
        channelCount: Int,
        maxBuff: Int
    ): MediaCodec? {
        return try {
            mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount
            )
            mediaFormat!!.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBuff)
            // 动态设置 比特率
            mediaFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, sampleRate * channelCount)

            sampleRateType = ADTSUtils.getSampleRateType(sampleRate)
            this.channelCount = channelCount

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            outProfile = try {
                encoder.outputFormat.getInteger(MediaFormat.KEY_PROFILE)
            } catch (e: Exception) {
                e.printStackTrace()
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            }
            encoder
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * return audio encoder config
     */
    fun getMediaFormat(): MediaFormat? {
        return mediaFormat
    }

    /**
     * 开始编码
     * @param outAacPath 当 File 为 null，不保存编码后 aac 流
     */
    fun start(
        sampleRate: Int,
        channelCount: Int,
        maxBuff: Int,
        outAacPath: File?,
        callback: Callback?
    ): Boolean {
        this.callback = callback
        mAudioEncoder = initAudioEncoder(sampleRate, channelCount, maxBuff)
        if (mAudioEncoder == null) {
            return false
        }
        if (outAacPath != null) {
            try {
                aacBos = BufferedOutputStream(FileOutputStream(outAacPath))
                Logger.d("out aac to file:$outAacPath")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isEncoding = true
        timeUs = System.nanoTime() / 1000L
        mAudioEncoder!!.start()
        encodeInputBuffers = mAudioEncoder!!.inputBuffers
        encodeOutputBuffers = mAudioEncoder!!.outputBuffers
        val mEncodeThread = Thread {
            Logger.w("start mEncodeThread")
            while (isEncoding) {
                encodePCM()
            }
            release()
        }
        mEncodeThread.start()

        return true
    }

    /**
     * offer pcm data
     */
    fun offerData(pcm: ByteArray) {
        if (!isEncoding) {
            return
        }
        if (queue.size >= QUEUE_MAX) {
            queue.poll()
            Logger.w("queue length is max size,then poll")
        }
        queue.offer(pcm)
    }


    /**
     * stop encode pcm
     */
    fun stop() {
        isEncoding = false
    }

    /**
     * 在Container中队列取出PCM数据
     *
     * @return PCM数据块
     */
    private fun getPCMData(): ByteArray? {
        return queue.poll()
    }

    /**
     * 编码PCM数据 得到MediaFormat.MIMETYPE_AUDIO_AAC格式的音频文件，并保存到
     */
    private fun encodePCM() {
        val inputBuffer: ByteBuffer
        val outputBuffer: ByteBuffer
        var chunkAudio: ByteArray
        var outPacketSize: Int

        val pcmData = getPCMData()
        if (pcmData == null) {
            SystemClock.sleep(2)
            return
        }
        val inputIndex: Int = mAudioEncoder!!.dequeueInputBuffer(-1)
        if (inputIndex >= 0) {
            inputBuffer = encodeInputBuffers[inputIndex]
            inputBuffer.clear()
            inputBuffer.limit(pcmData.size)
            // PCM数据填充给inputBuffer
            inputBuffer.put(pcmData)
//            val pts = System.nanoTime() / 1000 - timeUs
//            Logger.d("encoding .... ")
            // 通知编码器 编码
            mAudioEncoder!!.queueInputBuffer(
                inputIndex,
                0,
                pcmData.size,
                (System.nanoTime() + 500) / 1000,
                0
            )
        }
        val outputIndex: Int = mAudioEncoder!!.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000)

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Logger.w("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:${mAudioEncoder!!.outputFormat}")
            callback?.initSuccess(mAudioEncoder!!.outputFormat)
        } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && queue.isEmpty()) {
            // Logger.w("MediaCodec.INFO_TRY_AGAIN_LATER")
            SystemClock.sleep(2)
        } else if (outputIndex >= 0) {

            val info = mAudioEncodeBufferInfo
            // 拿到输出Buffer
            outputBuffer = encodeOutputBuffers[outputIndex]
            outputBuffer.position(info.offset)
            outputBuffer.limit(info.offset + info.size)

            aacBos?.let {
                outPacketSize = info.size + AAC_HEAD_LEN
                chunkAudio = ByteArray(outPacketSize)
                ADTSUtils.addADTStoPacket(
                    sampleRateType,
                    outProfile,
                    channelCount,
                    chunkAudio,
                    outPacketSize
                )
                outputBuffer.get(chunkAudio, AAC_HEAD_LEN + info.offset, info.size)
                outputBuffer.position(info.offset)

//                Logger.d("write aac data len: " + chunkAudio.size)
                try {
                    it.write(chunkAudio, 0, chunkAudio.size)
                } catch (e: IOException) {
                    Logger.e(e)
                }
            }

            callback?.let {
                it.offerAac(outputBuffer, mAudioEncodeBufferInfo)
            }

            mAudioEncoder!!.releaseOutputBuffer(outputIndex, false)

//            outputIndex = mAudioEncoder!!.dequeueOutputBuffer(mAudioEncodeBufferInfo, 10000)
        }
    }

    /**
     * 释放资源
     */
    private fun release() {
        try {
            aacBos?.flush()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            try {
                aacBos?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                aacBos = null
            }
        }

        mAudioEncoder?.stop()
        mAudioEncoder?.release()
        callback?.finished()
        mAudioEncoder = null
        callback = null
        Logger.w("release")
    }

    /**
     * encode pcm to aac callback
     */
    interface Callback {
        /**
         * encode init success
         */
        fun initSuccess(format: MediaFormat)

        /**
         * out  aac stream
         */
        fun offerAac(data: ByteBuffer, info: MediaCodec.BufferInfo)

        /**
         * encode is finished
         */
        fun finished()
    }

}