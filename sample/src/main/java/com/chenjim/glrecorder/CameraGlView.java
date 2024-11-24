package com.chenjim.glrecorder;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.chenjim.glrecorder.CameraRenderer;

public class CameraGlView extends GLSurfaceView {


    private CameraRenderer cameraRenderer;

    public CameraGlView(Context context) {
        this(context, null);
    }

    public CameraGlView(Context context, AttributeSet attrs) {
        super(context, attrs);
        /**
         * 配置GLSurfaceView
         */
        //设置EGL版本
        setEGLContextClientVersion(2);
        cameraRenderer = new CameraRenderer(this);
        setRenderer(cameraRenderer);
        //设置按需渲染 当我们调用 requestRender 请求GLThread 回调一次 onDrawFrame
        // 连续渲染 就是自动的回调onDrawFrame
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        cameraRenderer.onSurfaceDestroyed();
    }


    public void startRecord() {
        float speed = 1.f;
        cameraRenderer.startRecord(speed);
    }

    public void stopRecord() {
        cameraRenderer.stopRecord();
    }

}
