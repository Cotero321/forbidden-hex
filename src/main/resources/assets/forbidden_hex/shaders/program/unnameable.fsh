#version 150

// Local post chain only; shader-pack mode retains the camera and HUD fallback.
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float AkaishiTime;
uniform float AkaishiIntensity;

in vec2 texCoord;
out vec4 fragColor;

float forbidden_hex_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float forbidden_hex_tone(float phase, float seed) {
    float stepIndex = floor(phase);
    float blend = smoothstep(0.0, 1.0, fract(phase));
    return mix(forbidden_hex_hash(vec2(stepIndex, seed)),
            forbidden_hex_hash(vec2(stepIndex + 1.0, seed)), blend);
}

vec3 forbidden_hex_hue(vec3 color, float angle) {
    const vec3 axis = vec3(0.57735027);
    float c = cos(angle);
    float s = sin(angle);
    return color * c + cross(axis, color) * s + axis * dot(axis, color) * (1.0 - c);
}

void main() {
    vec4 col = texture(DiffuseSampler, texCoord);
    float intensity = clamp(AkaishiIntensity, 0.0, 1.0);
    float phase = AkaishiTime / 3.0;
    float exposure = 0.98 + forbidden_hex_tone(phase, 9.87) * 0.12;
    float saturation = 0.65 + forbidden_hex_tone(phase, 4.53) * 0.55;
    float hue = (forbidden_hex_tone(phase, 6.11) - 0.5) * 0.38;
    vec3 rgb = col.rgb * exposure;
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    // A luma-relative curve preserves dark coverage instead of subtracting a black pedestal.
    rgb = mix(vec3(luma), rgb, saturation);
    rgb = mix(rgb, forbidden_hex_hue(rgb, hue), smoothstep(0.02, 0.25, luma));

    vec2 borderDistance = min(texCoord, 1.0 - texCoord);
    float edge = 1.0 - smoothstep(0.025, 0.32, min(borderDistance.x, borderDistance.y));
    float breath = 0.5 + 0.5 * sin(AkaishiTime * 1.3);
    vec3 tint = mix(vec3(0.70, 0.93, 1.04), vec3(1.04, 0.76, 0.94), breath);
    rgb *= mix(vec3(1.0), tint, edge * 0.38);

    float frame = floor(AkaishiTime * 12.0);
    vec2 cell = floor(gl_FragCoord.xy / 2.0) + vec2(frame, -frame);
    float grain = forbidden_hex_hash(cell) - 0.5;
    // Modulate existing light: noise must not turn dark particle fringes into opaque black flecks.
    rgb *= 1.0 + grain * (0.035 + 0.09 * edge);
    float scanPhase = texCoord.y * 8.0 - AkaishiTime * 0.35;
    float scanDistance = abs(fract(scanPhase) - 0.5);
    float scan = 1.0 - smoothstep(0.008, 0.035, scanDistance);
    rgb += vec3(0.025, 0.045, 0.052) * scan * edge * (0.3 + 0.7 * luma);
    float spark = step(0.998, forbidden_hex_hash(cell + 31.7));
    rgb += spark * edge * vec3(0.035, 0.06, 0.075);

    fragColor = vec4(mix(col.rgb, clamp(rgb, 0.0, 1.0), intensity), col.a);
}
