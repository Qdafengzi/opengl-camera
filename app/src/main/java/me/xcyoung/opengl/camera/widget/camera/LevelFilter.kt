package me.xcyoung.opengl.camera.widget.camera

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import me.xcyoung.opengl.camera.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class LevelFilter(context: Context): Filter {
    private val vPosition: Int
    private val vCoord: Int
    private val vTexture: Int
    private val vMatrix: Int
    private val levelMinimumLocation: Int
    private val levelMiddleLocation: Int
    private val levelMaximumLocation: Int
    private val minOutputLocation: Int
    private val maxOutputLocation: Int
    private var mtx: FloatArray = FloatArray(16)
    private var mWidth: Int = 0
    private var mHeight: Int = 0

    private val textureBuffer: FloatBuffer
    private val vertexBuffer: FloatBuffer

    private var levelMinimum: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)
    private var levelMiddle: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)
    private var levelMaximum: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)
    private var minOutput: FloatArray = floatArrayOf(0.0f, 0.0f, 0.0f)
    private var maxOutput: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)

    private val program: Int

    init {
        vertexBuffer = ByteBuffer.allocateDirect(4 * 4 * 2)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertexBuffer.put(VERTEX).position(0)

        textureBuffer = ByteBuffer.allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        textureBuffer.put(TEXTURE).position(0)

        val vertexShader = OpenGLUtils.readRawTextFile(context, R.raw.camera_vert)
        val textureShader = OpenGLUtils.readRawTextFile(context, R.raw.level_frag)

        program = OpenGLUtils.loadProgram(vertexShader, textureShader)

        vPosition = GLES20.glGetAttribLocation(program, "vPosition")
        vCoord = GLES20.glGetAttribLocation(program, "vCoord")
        vTexture = GLES20.glGetUniformLocation(program, "vTexture")
        vMatrix = GLES20.glGetUniformLocation(program, "vMatrix")

        levelMinimumLocation = GLES20.glGetUniformLocation(program, "levelMinimum")
        levelMiddleLocation = GLES20.glGetUniformLocation(program, "levelMiddle")
        levelMaximumLocation = GLES20.glGetUniformLocation(program, "levelMaximum")
        minOutputLocation = GLES20.glGetUniformLocation(program, "minOutput")
        maxOutputLocation = GLES20.glGetUniformLocation(program, "maxOutput")
    }

    override fun onDrawFrame(textureId: Int): Int {
        GLES20.glViewport(0, 0, mWidth, mHeight)
        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(vPosition)

        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glEnableVertexAttribArray(vCoord)

        GLES20.glUniformMatrix4fv(vMatrix, 1, false, mtx, 0)

        GLES20.glUniform3fv(levelMinimumLocation, 1, levelMinimum, 0)
        GLES20.glUniform3fv(levelMiddleLocation, 1, levelMiddle, 0)
        GLES20.glUniform3fv(levelMaximumLocation, 1, levelMaximum, 0)
        GLES20.glUniform3fv(minOutputLocation, 1, minOutput, 0)
        GLES20.glUniform3fv(maxOutputLocation, 1, maxOutput, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(vTexture, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textureId
    }

    override fun setTransformMatrix(mtx: FloatArray) {
        this.mtx = mtx
    }

    override fun onReady(width: Int, height: Int) {
        mWidth = width
        mHeight = height
    }

    fun setMin(min: Float, mid: Float, max: Float, minOut: Float, maxOut: Float) {
        setRedMin(min, mid, max, minOut, maxOut)
        setGreenMin(min, mid, max, minOut, maxOut)
        setBlueMin(min, mid, max, minOut, maxOut)
    }

    fun setMin(min: Float, mid: Float, max: Float) {
        setMin(min, mid, max, 0.0f, 1.0f)
    }

    fun setRedMin(min: Float, mid: Float, max: Float, minOut: Float, maxOut: Float) {
        levelMinimum[0] = min
        levelMiddle[0] = mid
        levelMaximum[0] = max
        minOutput[0] = minOut
        maxOutput[0] = maxOut
        updateUniforms()
    }

    fun setRedMin(min: Float, mid: Float, max: Float) {
        setRedMin(min, mid, max, 0.0f, 1.0f)
    }

    fun setGreenMin(min: Float, mid: Float, max: Float, minOut: Float, maxOut: Float) {
        levelMinimum[1] = min
        levelMiddle[1] = mid
        levelMaximum[1] = max
        minOutput[1] = minOut
        maxOutput[1] = maxOut
        updateUniforms()
    }

    fun setGreenMin(min: Float, mid: Float, max: Float) {
        setGreenMin(min, mid, max, 0.0f, 1.0f)
    }

    fun setBlueMin(min: Float, mid: Float, max: Float, minOut: Float, maxOut: Float) {
        levelMinimum[2] = min
        levelMiddle[2] = mid
        levelMaximum[2] = max
        minOutput[2] = minOut
        maxOutput[2] = maxOut
        updateUniforms()
    }

    fun setBlueMin(min: Float, mid: Float, max: Float) {
        setBlueMin(min, mid, max, 0.0f, 1.0f)
    }

    private fun updateUniforms() {
        GLES20.glUseProgram(program)
        GLES20.glUniform3fv(levelMinimumLocation, 1, levelMinimum, 0)
        GLES20.glUniform3fv(levelMiddleLocation, 1, levelMiddle, 0)
        GLES20.glUniform3fv(levelMaximumLocation, 1, levelMaximum, 0)
        GLES20.glUniform3fv(minOutputLocation, 1, minOutput, 0)
        GLES20.glUniform3fv(maxOutputLocation, 1, maxOutput, 0)
    }

    fun release() {
        GLES20.glDeleteProgram(program)
    }

    companion object {
        private val VERTEX = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )

        private val TEXTURE = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }
}