#version 150

// 「不可名状」后处理：按 3 秒台阶随机偏移画面色调（色相 / 曝光 / 对比度 / 饱和度），
// 并叠加电视机花白（雪花噪点 / 滚动亮线 / 彩色火花）。
// 由 forbidden_hex:shaders/post/unnameable.json 挂到原版后处理管线，随效果生灭整体加载/卸载。

uniform sampler2D DiffuseSampler;

uniform vec2 OutSize;
uniform float Time;        // 原版后处理时间：0~1 每秒回绕，仅供高频花白 / 滚线使用
uniform float AkaishiTime; // 客户端真实时间（秒），由 PostPassMixin 每帧写入，支撑数秒级台阶

in vec2 texCoord;
out vec4 fragColor;

// 色调台阶周期（秒）：四色调参数每 3 秒同步跳变一次
const float TONE_PERIOD = 3.0;
// 曝光度基准与抖动幅度：整体亮度缩放，随机忽明忽暗
const float EXPOSURE_BASE = 1.05;
const float EXPOSURE_JITTER = 0.9;
// 对比度基准与抖动幅度：以 0.5 中灰为轴拉伸，暗部更暗、亮部更亮
const float CONTRAST_BASE = 1.35;
const float CONTRAST_JITTER = 1.35;
// 饱和度基准与抖动幅度：与自身亮度灰阶混合，随机失色 / 浓艳
const float SATURATION_BASE = 1.15;
const float SATURATION_JITTER = 1.7;
// 色相偏转幅度（弧度）：绕灰轴旋转，随机把整幅画面压向某一色系
const float HUE_SWING = 0.5;
// 花白强度基准：逐格噪点振幅，数值越大画面越"糊白"
const float STATIC_BASE = 0.16;

// 坐标哈希伪随机：无需额外噪点贴图，避免多引一份资源
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// 绕灰轴旋转色相（Rodrigues 公式）：两个向量运算即可，亮度基本不变
vec3 hueShift(vec3 color, float angle) {
    const vec3 k = vec3(0.57735027);
    float c = cos(angle);
    float s = sin(angle);
    return color * c + cross(k, color) * s + k * dot(k, color) * (1.0 - c);
}

void main() {
    vec4 col = texture(DiffuseSampler, texCoord);

    // 四色调参数共用同一台阶：每 TONE_PERIOD 秒同步跳变一次，
    // 模拟显像管增益忽高忽低、整块画面"变调"的信号不稳感
    float toneStep = floor(AkaishiTime / TONE_PERIOD);
    float exposure = EXPOSURE_BASE + (hash(vec2(toneStep, 9.87)) - 0.5) * EXPOSURE_JITTER;
    float contrast = CONTRAST_BASE + (hash(vec2(toneStep, 7.31)) - 0.5) * CONTRAST_JITTER;
    // 下限截零：负饱和度会外推成反相色，非预期
    float saturation = max(0.0, SATURATION_BASE + (hash(vec2(toneStep, 4.53)) - 0.5) * SATURATION_JITTER);
    float hue = (hash(vec2(toneStep, 6.11)) - 0.5) * HUE_SWING;
    // 花白强度保持约每秒 6 次的高频跳变，与色调的慢台阶区分开
    float staticAmt = STATIC_BASE * (0.55 + 0.9 * hash(vec2(floor(Time * 120.0), 2.17)));

    // 1) 曝光度（随机浮动）
    vec3 rgb = col.rgb * exposure;

    // 2) 对比度（随机浮动）
    rgb = (rgb - 0.5) * contrast + 0.5;

    // 3) 饱和度（随机浮动）：0 = 全灰，1 = 原色，大于 1 = 加浓
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(luma), rgb, saturation);

    // 4) 色相（随机浮动）：与上面三项同台阶，形成整幅画面的色系偏移
    rgb = hueShift(rgb, hue);

    // 5) 电视机花白：乘 480 折算为约每秒 24 次刷新；
    //    坐标再按 1.5 像素分格，得到显像管颗粒感而非逐像素细沙
    float frame = floor(Time * 480.0);
    vec2 cell = floor(gl_FragCoord.xy / 1.5) + frame;
    rgb += (hash(cell) - 0.5) * staticAmt;

    // 6) 缓慢下滚的横向亮线：折算约每秒 1.5 条滚过整屏，模拟显像管信号跳变
    float scan = mod(gl_FragCoord.y / max(1.0, OutSize.y) * 30.0 + Time * 30.0, 30.0);
    if (scan < 0.6) {
        rgb += 0.18;
    }

    // 7) 彩色火花：极少量像素随机偏色，制造信号干扰的颗粒感
    float spark = step(0.9965, hash(cell + 31.7));
    rgb += spark * vec3(hash(cell + 1.3), hash(cell + 2.7), hash(cell + 5.1)) * 0.5;

    fragColor = vec4(clamp(rgb, 0.0, 1.0), col.a);
}
