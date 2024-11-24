package com.chenjim.glrecorder.audio

import android.media.MediaCodecInfo

/**
 * Author : chenjim
 * Email : me@h89.cn
 * Desc :  ADTS (Audio Data Transport Stream)头 相关
 * 参考自 [https://github.com/EricLi22/AndroidMultiMedia] ADTSUtils.java
 * 优化参数传递
 */
object ADTSUtils {
    private val sampleRateMap: MutableMap<Int, Int> = HashMap()

    init {
        sampleRateMap[96000] = 0
        sampleRateMap[88200] = 1
        sampleRateMap[64000] = 2
        sampleRateMap[48000] = 3
        sampleRateMap[44100] = 4
        sampleRateMap[32000] = 5
        sampleRateMap[24000] = 6
        sampleRateMap[22050] = 7
        sampleRateMap[16000] = 8
        sampleRateMap[12000] = 9
        sampleRateMap[11025] = 10
        sampleRateMap[8000] = 11
        sampleRateMap[7350] = 12
    }

    /**
     * return sample rate type , follow [sampleRateMap]
     */
    @JvmStatic
    fun getSampleRateType(sampleRate: Int): Int {
        return sampleRateMap[sampleRate]
            ?: throw NullPointerException("sampleRate $sampleRate error")
    }

    /**
     * 添加 ADTS (Audio Data Transport Stream)头
     *
     * @param profile  [MediaCodecInfo.CodecProfileLevel.AACObjectLC] and so on
     * @param channel  声道数
     * @param packet
     * @param packetLen
     */
    @JvmStatic
    fun addADTStoPacket(
        sampleRateType: Int,
        profile: Int = MediaCodecInfo.CodecProfileLevel.AACObjectLC,
        channel: Int = 2,
        packet: ByteArray,
        packetLen: Int
    ) {
        // fill in ADTS data
        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = ((profile - 1 shl 6) + (sampleRateType shl 2) + (channel shr 2)).toByte()
        packet[3] = ((channel and 3 shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

}