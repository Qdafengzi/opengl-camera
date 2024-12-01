#extension GL_OES_EGL_image_external : require
//SurfaceTexture比较特殊
//float数据是什么精度的
precision mediump float;

varying highp vec2 aCoord;

//采样器
uniform samplerExternalOES vTexture;

varying highp vec2 textureCoordinate;

uniform sampler2D inputImageTexture;
uniform lowp float gamma;

void main()
{
    lowp vec4 textureColor = texture2D(inputImageTexture, textureCoordinate);

    gl_FragColor = vec4(pow(textureColor.rgb, vec3(gamma)), textureColor.w);
}