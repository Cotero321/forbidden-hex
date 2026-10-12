#version 150

// 低理智的视野表现（post shader）：灰化 → 黑白 → 边缘血丝。
// 三个强度 uniform 全部由驱动层（ClientSanityVision）按服务端权威快照档位给出，
// 着色器本身不含任何时间台阶/随机阈值，只做"按强度插值"，因此表现可精确对齐数值档位。
// 由 forbidden_hex:shaders/post/sanity_vision.json 挂到原版后处理管线，按需加载/卸载（见 AkaishiSanityVisionPostHandler）。

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform float Time;         // 原版后处理时间（1s 锯齿），仅用于血丝的轻微蠕动
uniform float AkaishiTime;  // 客户端真实时间（秒），由 PostPassMixin 每帧写入
uniform float AkaishiGray;  // 灰化程度 0~1
uniform float AkaishiBlood; // 血丝强度 0~1
uniform float AkaishiMono;  // 黑白程度 0~1

in vec2 texCoord;
out vec4 fragColor;

// 坐标哈希伪随机：不额外引入噪点贴图
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// 双线性插值的值噪声（在哈希之上做平滑，避免血丝呈纯颗粒感）
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// 三倍频 FBM：血管的粗细层次
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += amplitude * noise(p);
        p *= 2.02;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec4 col = texture(DiffuseSampler, texCoord);
    vec3 rgb = col.rgb;
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    // 1) 灰化：去饱和并压向偏冷灰（低理智 = 世界褪色）
    vec3 grayTarget = vec3(luma) * vec3(0.92, 0.96, 1.06);
    rgb = mix(rgb, grayTarget, clamp(AkaishiGray, 0.0, 1.0));

    // 2) 黑白：彻底抽掉色彩（20% 档及以下）
    rgb = mix(rgb, vec3(luma), clamp(AkaishiMono, 0.0, 1.0));

    // 3) 视野收暗：以屏幕中心为原点，越靠边越暗；随灰化程度加深
    vec2 centered = (texCoord - 0.5) * vec2(OutSize.x / max(1.0, OutSize.y), 1.0);
    float edge = smoothstep(0.30, 0.72, length(centered));
    rgb *= 1.0 - 0.35 * clamp(AkaishiGray, 0.0, 1.0) * edge;

    // 4) 血丝：只在边缘、沿噪声脊线出现的暗红细血管，缓慢蠕动
    if (AkaishiBlood > 0.001) {
        vec2 p = texCoord * vec2(OutSize.x / max(1.0, OutSize.y), 1.0) * 6.0;
        float drift = AkaishiTime * 0.05 + Time * 0.02;
        float vein = fbm(p + vec2(drift, -drift * 0.7));
        vein = smoothstep(0.60, 0.95, vein) * edge;
        rgb = mix(rgb, vec3(0.42, 0.03, 0.05), min(1.0, vein * clamp(AkaishiBlood, 0.0, 1.0) * 1.6));
    }

    fragColor = vec4(clamp(rgb, 0.0, 1.0), col.a);
}
