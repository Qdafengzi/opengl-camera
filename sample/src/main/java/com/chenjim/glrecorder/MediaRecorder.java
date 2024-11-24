package com.chenjim.glrecorder;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.chenjim.glrecorder.audio.AudioRecordPcm;
import com.chenjim.glrecorder.audio.PcmEncodeAacCtrl;
import com.chenjim.glrecorder.util.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 录制类
 */
public class MediaRecorder {

    private final Context mContext;
    private final String mPath;
    private final int mWidth;
    private final int mHeight;
    private final EGLContext mEglContext;
    private MediaCodec mMediaCodec;
    private Surface mInputSurface;
    private MediaMuxer mMediaMuxer;
    private volatile boolean isMediaMuxerStart = false;
    private volatile long startTimeUs = 0;
    private volatile long pauseRecordTimeUs = 0;
    private Handler mHandler;
    private EGLBase mEglBase;
    private volatile boolean isStart;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private float mSpeed;

    private AudioRecordPcm audioRecord;
    private PcmEncodeAacCtrl pcmEncode;
    private boolean isNeedKeyFrame = true;
    private boolean isRecordPause = false;
    // finish write first key frame
    private volatile boolean isWriteFirstKeyFrame = false;

    /**
     * @param context 上下文
     * @param path    保存视频的地址
     * @param width   视频宽
     * @param height  视频高
     *                还可以让人家传递帧率 fps、码率等参数
     */
    public MediaRecorder(Context context, String path, int width, int height, EGLContext eglContext) {
        mContext = context.getApplicationContext();
        mPath = path;
        mWidth = width;
        mHeight = height;
        mEglContext = eglContext;
    }


    /**
     * 开始录制视频
     *
     * @param speed
     */
    public void start(float speed) throws IOException {
        mSpeed = speed;
        /**
         * 配置MediaCodec 编码器
         */
        //视频格式
        // 类型（avc高级编码 h264） 编码出的宽、高
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mWidth, mHeight);
        //参数配置
        // 1500kbs码率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) (mWidth * mHeight * 30 * 0.2));
        //帧率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        //关键帧间隔
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        //颜色格式（RGB\YUV）
        //从surface当中回去
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        //编码器
        mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        //将参数配置给编码器
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        //交给虚拟屏幕 通过opengl 将预览的纹理 绘制到这一个虚拟屏幕中
        //这样MediaCodec 就会自动编码 inputSurface 中的图像
        mInputSurface = mMediaCodec.createInputSurface();

        //  H.264
        // 播放：
        //  MP4 -> 解复用 (解封装) -> 解码 -> 绘制
        //封装器 复用器
        // 一个 mp4 的封装器 将h.264 通过它写出到文件就可以了
        mMediaMuxer = new MediaMuxer(mPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);


        /**
         * 配置EGL环境
         */
        //Handler ： 线程通信
        // Handler: 子线程通知主线程
//        Looper.loop();
        HandlerThread handlerThread = new HandlerThread("VideoCodec");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        // 用于其他线程 通知子线程
        mHandler = new Handler(looper);
        //子线程： EGL的绑定线程 ，对我们自己创建的EGL环境的opengl操作都在这个线程当中执行
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //创建我们的EGL环境 (虚拟设备、EGL上下文等)
                mEglBase = new EGLBase(mContext, mWidth, mHeight, mInputSurface, mEglContext);
                //启动编码器
                mMediaCodec.start();
                isStart = true;
            }
        });

        pcmEncode = new PcmEncodeAacCtrl();
        audioRecord = new AudioRecordPcm(new AudioRecordPcmCallback());
        audioRecord.start();
    }


    /**
     * 传递 纹理进来
     * 相当于调用一次就有一个新的图像需要编码
     */
    public void encodeFrame(final int textureId, final long timestamp) {
        if (!isStart) {
            return;
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //把图像画到虚拟屏幕
                mEglBase.draw(textureId, timestamp);
                //从编码器的输出缓冲区获取编码后的数据就ok了
                getCodec(false);
            }
        });
    }

    /**
     * 获取编码后 的数据
     *
     * @param endOfStream 标记是否结束录制
     */
    private void getCodec(boolean endOfStream) {
        //不录了， 给mediacodec一个标记
        if (endOfStream) {
            mMediaCodec.signalEndOfInputStream();
        }
        //输出缓冲区
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        // 希望将已经编码完的数据都 获取到 然后写出到mp4文件
        while (true) {
            //等待10 ms
            int status = mMediaCodec.dequeueOutputBuffer(bufferInfo, 10_000);
            //让我们重试  1、需要更多数据  2、可能还没编码为完（需要更多时间）
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 如果是停止 我继续循环
                // 继续循环 就表示不会接收到新的等待编码的图像
                // 相当于保证mediacodec中所有的待编码的数据都编码完成了，不断地重试 取出编码器中的编码好的数据
                // 标记不是停止 ，我们退出 ，下一轮接收到更多数据再来取输出编码后的数据
                if (!endOfStream) {
                    //不写这个 会卡太久了，没有必要 你还是在继续录制的，还能调用这个方法的！
                    break;
                }
                //否则继续
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //开始编码 就会调用一次
                MediaFormat outputFormat = mMediaCodec.getOutputFormat();
                //配置封装器
                // 增加一路指定格式的媒体流 视频
                videoTrack = mMediaMuxer.addTrack(outputFormat);
                if (audioTrack >= 0 && !isMediaMuxerStart) {
                    mMediaMuxer.start();
                    isMediaMuxerStart = true;
                }
            } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //忽略
            } else if (status > 0) {
                //成功 取出一个有效的输出
                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(status);
                //如果获取的ByteBuffer 是配置信息 ,不需要写出到mp4
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size != 0) {

                    //写到mp4
                    //根据偏移定位
                    outputBuffer.position(bufferInfo.offset);
                    //ByteBuffer 可读写总长度
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                    //写出
                    if (isMediaMuxerStart && !isRecordPause) {
                        if (isNeedKeyFrame && bufferInfo.flags != MediaCodec.BUFFER_FLAG_KEY_FRAME) {
                            Logger.d("need key frame,but not, so drop it");
                        } else {
                            isNeedKeyFrame = false;
                            if (!isWriteFirstKeyFrame) {
                                isWriteFirstKeyFrame = true;
                                startTimeUs = SystemClock.elapsedRealtimeNanos() / 1000;
                            }
                            bufferInfo.presentationTimeUs = pauseRecordTimeUs + (SystemClock.elapsedRealtimeNanos() / 1000 - startTimeUs);
                            bufferInfo.presentationTimeUs = (long) (bufferInfo.presentationTimeUs / mSpeed);
                            mMediaMuxer.writeSampleData(videoTrack, outputBuffer, bufferInfo);
                        }
                    }
                }
                //输出缓冲区 我们就使用完了，可以回收了，让mediacodec继续使用
                mMediaCodec.releaseOutputBuffer(status, false);
                //结束
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break;
                }
            }
        }
    }


    public void stop() {
        Toast.makeText(mContext, mPath, Toast.LENGTH_LONG).show();
        isStart = false;
        isMediaMuxerStart = false;
        isWriteFirstKeyFrame = false;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                getCodec(true);
                audioRecord.stop();
                pcmEncode.stop();
                audioTrack = -1;
                videoTrack = -1;
                mMediaCodec.stop();
                mMediaCodec.release();
                mMediaCodec = null;
                try {
                    mMediaMuxer.stop();
                    mMediaMuxer.release();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
                mMediaMuxer = null;
                mEglBase.release();
                mEglBase = null;
                mInputSurface = null;
                pauseRecordTimeUs = 0;
                mHandler.getLooper().quitSafely();
                mHandler = null;
            }
        });
    }


    /**
     * Audio Record Pcm Callback
     */
    class AudioRecordPcmCallback implements AudioRecordPcm.Callback {

        @Override
        public void start(int sampleRate, int sampleRateType, int channelCount, int maxBufSize) {
            pcmEncode.start(sampleRate, channelCount, maxBufSize, null,
                    new PcmEncodeAacCtrlCallback());
        }

        @Override
        public void offerPcm(@NonNull byte[] data) {
            pcmEncode.offerData(data);
        }

        @Override
        public void finished() {

        }
    }

    /**
     * Pcm Encode to Aac then Callback
     */
    class PcmEncodeAacCtrlCallback implements PcmEncodeAacCtrl.Callback {

        @Override
        public void initSuccess(@NonNull MediaFormat format) {
            audioTrack = mMediaMuxer.addTrack(format);
            if (videoTrack >= 0 && !isMediaMuxerStart) {
                mMediaMuxer.start();
//                startTimeUs = SystemClock.elapsedRealtimeNanos() / 1000;
                isMediaMuxerStart = true;
            }
        }

        @Override
        public void offerAac(@NonNull ByteBuffer data, @NonNull MediaCodec.BufferInfo info) {
            if (isMediaMuxerStart && !isRecordPause && !isNeedKeyFrame) {
                info.presentationTimeUs = pauseRecordTimeUs + (SystemClock.elapsedRealtimeNanos() / 1000 - startTimeUs);
                Logger.d(info.presentationTimeUs);
                mMediaMuxer.writeSampleData(audioTrack, data, info);
                //Logger.d("aac presentationTimeUs:" + info.presentationTimeUs);
            }
        }

        @Override
        public void finished() {

        }
    }
}
