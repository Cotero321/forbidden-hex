#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float VisualTime;
uniform float GlowStrength;
uniform float FlowSpeed;
uniform float PulseStrength;
uniform float PulseSpeed;
uniform float EdgeSoftness;
uniform float AlphaCutoff;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float seconds = VisualTime;
    vec2 uv = texCoord0;
    float pulse = 1.0;
    float edge = 1.0;
    if (abs(FlowSpeed) > 0.0001) {
        uv.x = fract(uv.x - seconds * FlowSpeed);
    }
    if (EdgeSoftness > 0.0) {
        float center = 1.0 - abs(uv.y * 2.0 - 1.0);
        edge = smoothstep(0.0, EdgeSoftness, center);
    }
    if (PulseStrength > 0.0) {
        float wave = sin((texCoord0.x * 2.0 - seconds * PulseSpeed) * 6.2831853);
        pulse = 1.0 - clamp(PulseStrength, 0.0, 1.0) * (0.5 - 0.5 * wave);
    }
    vec4 sampleColor = texture(Sampler0, uv);
    vec4 color = sampleColor * vertexColor * ColorModulator;
    float fog = 1.0 - smoothstep(FogStart, max(FogEnd, FogStart + 0.001), vertexDistance);
    color.a *= edge * pulse * fog;
    if (color.a < AlphaCutoff) discard;
    color.rgb *= GlowStrength;
    fragColor = color;
}
