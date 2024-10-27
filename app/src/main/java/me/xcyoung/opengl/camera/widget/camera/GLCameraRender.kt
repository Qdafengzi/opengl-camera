package me.xcyoung.opengl.camera.widget.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Size
import android.view.Surface
import androidx.annotation.WorkerThread
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * @author ChorYeung
 * @since 2021/11/24
 */
class GLCameraRender(private val context: Context, private val callback: Callback) : GLSurfaceView.Renderer,
    Preview.SurfaceProvider, SurfaceTexture.OnFrameAvailableListener {
    private var textures: IntArray = IntArray(1)
    private var surfaceTexture: SurfaceTexture? = null
    private var textureMatrix: FloatArray = FloatArray(16)
    private val executor = Executors.newSingleThreadExecutor()
//    private var filter: Filter? = null
//    var type: String = "Normal"

    private var framebuffer: IntArray = IntArray(1)
    private var framebufferTexture: IntArray = IntArray(1)

    var filter1: Filter? = null
    var filter2: Filter? = null

    private fun setupFramebuffer(width: Int, height: Int) {
        GLES20.glGenFramebuffers(1, framebuffer, 0)
        GLES20.glGenTextures(1, framebufferTexture, 0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebufferTexture[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, framebufferTexture[0], 0
        )

        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer is not complete: $status")
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }


    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//        gl?.let {
//            it.glGenTextures(textures.size, textures, 0)
//            surfaceTexture = SurfaceTexture(textures[0])
//            filter = when(type) {
//                "WhiteBalance" -> WhiteBalanceFilter(context)
//                else -> ScreenFilter(context)
//            }
//        }

        gl?.let {
            it.glGenTextures(textures.size, textures, 0)
            surfaceTexture = SurfaceTexture(textures[0])

            filter1 = LevelFilter(context) // 或其他的第一个滤镜
            filter2 = WhiteBalanceFilter(context) // 或其他的第二个滤镜
        }

    }

    var mWidth = 0
    var mHeight = 0

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        callback.onSurfaceChanged()
//        filter?.onReady(width, height)

        mWidth = width
        mHeight = height
        filter1?.onReady(width, height)
        filter2?.onReady(width, height)
        setupFramebuffer(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
//        val surfaceTexture = this.surfaceTexture
//        if (gl == null || surfaceTexture == null) return
//        gl.glClearColor(0f, 0f, 0f, 0f)
//        gl.glClear(GLES20.GL_COLOR_BUFFER_BIT)
//        surfaceTexture.updateTexImage()
//        surfaceTexture.getTransformMatrix(textureMatrix)
//        filter?.setTransformMatrix(textureMatrix)
//        filter?.onDrawFrame(textures[0])
        val surfaceTexture = this.surfaceTexture ?: return
        gl?.let {
            it.glClearColor(0f, 0f, 0f, 0f)
            it.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(textureMatrix)

            // 渲染到帧缓冲区
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0])
            GLES20.glViewport(0, 0, mWidth, mHeight)
            filter1?.setTransformMatrix(textureMatrix)
            filter1?.onDrawFrame(textures[0])

            // 使用第二个滤镜将帧缓冲区纹理渲染到屏幕
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, mWidth, mHeight)
            filter2?.setTransformMatrix(textureMatrix)
            filter2?.onDrawFrame(framebufferTexture[0])
        }
    }

    override fun onSurfaceRequested(request: SurfaceRequest) {
        val resetTexture = resetPreviewTexture(request.resolution) ?: return
        val surface = Surface(resetTexture)
        request.provideSurface(surface, executor) {
            surface.release()
            surfaceTexture?.release()
        }
    }

    @WorkerThread
    private fun resetPreviewTexture(size: Size): SurfaceTexture? {
        return this.surfaceTexture?.let { surfaceTexture ->
            surfaceTexture.setOnFrameAvailableListener(this)
            surfaceTexture.setDefaultBufferSize(size.width, size.height)
            surfaceTexture
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        callback.onFrameAvailable()
    }

    fun setProgress(progress: Float) {
//        if (filter is WhiteBalanceFilter) {
//            (filter as WhiteBalanceFilter).temperature = progress
//        }

        (filter2 as WhiteBalanceFilter).temperature = progress
    }

    fun setTint(progress: Float) {
//        if (filter is WhiteBalanceFilter) {
//            (filter as WhiteBalanceFilter).tint = progress
//        }

        (filter2 as WhiteBalanceFilter).tint = progress
    }

    fun setLevelMin(progress: Float){
        (filter1 as LevelFilter).setMin(progress,1f,1f)
    }

    interface Callback {
        fun onSurfaceChanged()
        fun onFrameAvailable()
    }
}