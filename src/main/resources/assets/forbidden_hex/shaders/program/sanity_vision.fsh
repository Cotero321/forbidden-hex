#version 150

// 低理智的视野表现（post shader）：冷青褪色 → 紫红暗角 → 边缘血丝。
// 三个强度 uniform 全部由驱动层（ClientSanityVision）按服务端权威快照档位给出，
// 着色器本身不含任何时间台阶/随机阈值，只做"按强度插值"，因此表现可精确对齐数值档位。
// 由 ClientPostEffects 管理 forbidden_hex:shaders/post/sanity_vision.json 的加载、优先级与释放。

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
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

    float gray = clamp(AkaishiGray, 0.0, 1.0);
    float blood = clamp(AkaishiBlood, 0.0, 1.0);
    float mono = clamp(AkaishiMono, 0.0, 1.0);

    // 1) 灰化：先压向冷青灰，再随血丝强度转入紫红，和 SAN HUD 的状态色保持一致。
    vec3 coolGray = vec3(luma) * vec3(0.84, 0.96, 1.08);
    vec3 dangerGray = vec3(luma) * vec3(1.05, 0.70, 0.86);
    vec3 grayTarget = mix(coolGray, dangerGray, blood * 0.72);
    rgb = mix(rgb, grayTarget, gray);

    // 2) 黑白：彻底抽掉色彩，但保留少量冷暖偏色，避免画面变成突兀的纯灰。
    vec3 monoTarget = mix(vec3(luma), vec3(luma) * vec3(0.90, 0.82, 0.94), blood * 0.45);
    rgb = mix(rgb, monoTarget, mono);

    // Keep the clear central aperture consistent on wide and tall windows.
    vec2 forbidden_hex_border = min(texCoord, 1.0 - texCoord);
    float edge = 1.0 - smoothstep(0.035, 0.30,
            min(forbidden_hex_border.x, forbidden_hex_border.y));
    float forbidden_hex_breath = 0.5 + 0.5 * sin(AkaishiTime * 1.15);
    vec3 vignetteTint = mix(vec3(0.70, 0.90, 0.95), vec3(0.92, 0.60, 0.77), blood);
    rgb *= mix(vec3(1.0), vignetteTint, (0.25 + 0.07 * forbidden_hex_breath) * gray * edge);

    if (blood > 0.001) {
        vec2 p = texCoord * vec2(OutSize.x / max(1.0, OutSize.y), 1.0) * 9.0;
        float drift = AkaishiTime * 0.025;
        float forbidden_hex_warp = fbm(p * 0.65 + vec2(drift, -drift));
        float forbidden_hex_ridge = abs(fbm(p + forbidden_hex_warp * 2.0) - 0.43);
        float forbidden_hex_width = max(0.009, fwidth(forbidden_hex_ridge) * 1.2);
        float forbidden_hex_vein = (1.0 - smoothstep(0.012,
                0.012 + forbidden_hex_width, forbidden_hex_ridge)) * edge * edge;
        vec3 veinColor = mix(vec3(0.31, 0.13, 0.30), vec3(0.53, 0.15, 0.26), blood);
        rgb = mix(rgb, veinColor, forbidden_hex_vein * blood * 0.42);
        rgb += vec3(0.045, 0.012, 0.028) * forbidden_hex_vein * blood * forbidden_hex_breath;
    }

    // 保持 alpha 不变，避免后处理链影响 GUI/粒子合成边界。
    fragColor = vec4(clamp(rgb, 0.0, 1.0), col.a);
}
