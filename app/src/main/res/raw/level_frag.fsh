#extension GL_OES_EGL_image_external : require
//SurfaceTexture比较特殊
//float数据是什么精度的
precision mediump float;
varying highp vec2 aCoord;

//采样器
uniform samplerExternalOES vTexture;
uniform mediump vec3 levelMinimum;
uniform mediump vec3 levelMiddle;
uniform mediump vec3 levelMaximum;
uniform mediump vec3 minOutput;
uniform mediump vec3 maxOutput;

void main(){
    mediump vec4 textureColor = texture2D(vTexture, aCoord);
    gl_FragColor = vec4(mix(minOutput, maxOutput, pow(min(max(textureColor.rgb - levelMinimum, vec3(0.0)) / (levelMaximum - levelMinimum), vec3(1.0)), 1.0 / levelMiddle)), textureColor.a);
}