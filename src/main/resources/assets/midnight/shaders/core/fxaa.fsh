#version 150

uniform sampler2D Sampler0;
uniform vec2 OutSize;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / OutSize;
    vec3 rgbNW = texture(Sampler0, texCoord0 + vec2(-1.0, -1.0) * texelSize).rgb;
    vec3 rgbNE = texture(Sampler0, texCoord0 + vec2(1.0, -1.0) * texelSize).rgb;
    vec3 rgbSW = texture(Sampler0, texCoord0 + vec2(-1.0, 1.0) * texelSize).rgb;
    vec3 rgbSE = texture(Sampler0, texCoord0 + vec2(1.0, 1.0) * texelSize).rgb;
    vec3 rgbM = texture(Sampler0, texCoord0).rgb;

    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM = dot(rgbM, luma);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y = ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * 0.03125, 0.0078125);
    float rcpDirMin = 1.0/(min(abs(dir.x), abs(dir.y)) + dirReduce);

    dir = min(vec2(8.0,8.0), max(vec2(-8.0,-8.0), dir * rcpDirMin)) * texelSize;

    vec4 color1 = texture(Sampler0, texCoord0 + dir * (1.0/3.0 - 0.5));
    vec4 color2 = texture(Sampler0, texCoord0 + dir * (2.0/3.0 - 0.5));
    vec4 colorA = (color1 + color2) * 0.5;

    vec4 color3 = texture(Sampler0, texCoord0 + dir * -0.5);
    vec4 color4 = texture(Sampler0, texCoord0 + dir * 0.5);
    vec4 colorB = colorA * 0.5 + (color3 + color4) * 0.25;

    float lumaB = dot(colorB.rgb, luma);
    fragColor = lumaB < lumaMin || lumaB > lumaMax ? colorA : colorB;
}