# Forbidden Hex 视觉改造验收记录

历史视觉改造记录日期：2026-10-16。后续法阵、音效与低理智补充验证见文末 2026-10-18 记录；完整发布验收仍有下述未验证项。

## 范围与交付

遵循用户后续要求，跳过实体和祭坛的美术改造。资源审计按当前排除规则检查它们相对既有 baseline 未被改动，不将它们列入本轮视觉验收范围。未改变施法消耗、伤害、冷却、网络字段及酿造玩法。

- 历史候选 JAR 路径：`build/libs/forbidden-hex-1.0.0.jar`；该文件随重新构建更新，不以本段旧包尺寸或哈希标识当前成品。
- 本段对应 2026-10-16 的未提交开发记录，后续提交状态以 Git 历史为准，未执行外部发布。
- 可再生清单：`build/visual-review/inventory.json`；离线摘要：`build/visual-review/audit.json`，均不纳入版本控制。
- 参考记录：`build/visual-review/witchery-reference.json`，原始参考仅供分析，不进入发行资源。
- 前后平面图集：`build/visual-review/before-sheets/`、`build/visual-review/after/`。包含 block、item、gui、magic、particle、mob_effect、font、slot，按当前规则过滤实体和祭坛。
- GUI 原创徽记尺寸与哈希记录：`build/visual-review/gui-status-checks.json`。内部彩色图案必须实际比较，透明轮廓相同不能视为内容相同。

## 本轮实现与复核

共享配额按世界对象和游戏 tick 管理，法术、酿造保留独立限额。同 tick 的多个入口不重复补充配额；旧世界、暂停、非法距离及超距离申请被拒绝。环形粒子按实际生成坐标申请一次额度。

蓄力、载体、束流、连锁、命中与余韵使用距离/粒子档位降级；保留主轮廓与既有源色、墨色。资源重载使秘典贴图存在性缓存失效；切换世界和断线清空自有表现队列。

限定范围代码探索复核确认四项问题，均已修复：环形粒子距离检查使用圆心、阶段间余韵污染零事件基线、旧截图掩盖保存失败、场景模式与实际光影状态缺少断言。

三路专项只读报告随后补齐。特效复核确认破瓶余韵只在入队时检查距离，已改为绘制前按当前相机与配置重新剔除，保留寿命内返回范围后的恢复。构建复核确认 smoke 的统一成功日志范围过宽及 `shaderCompatTest` 单开关不启动检查，已改为明确区分自定义/原版回退分支，并使单开关自动检查退出；默认自定义 smoke 也会拒绝关闭能量着色器、强制回退或未确认的光影状态。资源复核未发现确定资源阻断，提出同一秘典实例跨字体/语言重载的布局缓存风险；已加入重载版本标记，在客户端下一次绘制时清正文行数缓存、强制同族包围盒重算并保留平移。该条件性风险尚未做字体包交互复现，不宣称已证明原行为必然裁切。

三路专项没有运行构建或游戏；静态结论与主智能体实际运行记录分开。补充修复后的编译、打包、23 项专用端测试和两条客户端 smoke 均已重新通过。

开发场景开关为 `-PvisualSceneTest`，沿用 `build/shader-compat-run` 下的隔离测试世界，通过纯客户端视觉事件入口注入样例，不执行服务端施法。默认不开启。每档先排空 60 世界 tick，再预热 120 帧并采样 180 帧；每轮使用独立目录，并要求截图回调成功。测试暂时修改粒子档、限帧和垂直同步，完成后恢复。

`-PshaderCompatTest` 现在单独即可启用回退 smoke、重载检查和自动退出；与 `-PvisualSceneTest` 组合时继续运行场景。此入口仍依赖本地预先准备的 `saves/shader_compat_world/level.dat`、`shaderpacks/ComplementaryReimagined_r5.9.3.zip` 和启用该包的 `config/oculus.properties`。Gradle 不下载光影包、不创建世界；clean 或干净检出后必须重新准备，不能仅凭属性声称测试环境完整。

## 实际验证

本轮使用 Minecraft 1.20.1、Forge 47.4.10、Java 17.0.20、Architectury 9.2.14、GeckoLib 4.4.9、官方 mappings；Gradle 命令均使用现有 Zulu 17 和 `--offline --no-daemon --console=plain`。

| 实际命令或检查 | 结果 | 证据 |
| --- | --- | --- |
| `gradlew.bat compileJava build runGameTestServer --offline --no-daemon --console=plain` | 补充修复后 BUILD SUCCESSFUL，退出码 0；23 项 required GameTest 全部通过；重混淆 JAR 已更新 | `build/visual-review/review-followup-build.log` |
| `gradlew.bat runClient -PvisualSmokeTest --offline --no-daemon --console=plain` | 补充修复后 ABSENT；成功日志明确自定义核心着色器、材质、强制回退、后处理及重载通过；退出码 0 | `build/visual-review/review-followup-custom-smoke.log` |
| `gradlew.bat runClient -PshaderCompatTest --offline --no-daemon --console=plain` | 单开关检查并退出，退出码 0；Complementary Reimagined r5.9.3 ACTIVE；日志明确回退材质/绑定断言和重载通过，自定义程序跳过 | `build/visual-review/review-followup-shader-smoke.log` |
| `gradlew.bat runClient -PvisualSceneTest --offline --no-daemon --console=plain` | 补充修复前 ABSENT；8 组场景完成、4 张截图写出、退出码 0；补充修复后未重新采样 | `build/visual-review/scene-vanilla-final.log` |
| `gradlew.bat runClient -PvisualSceneTest -PshaderCompatTest --offline --no-daemon --console=plain` | 补充修复前 ACTIVE；8 组场景完成、4 张截图写出、退出码 0；补充修复后未重新采样 | `build/visual-review/scene-shader-final.log`、`scene-shader-final.exit` |
| 项目局部 Python 执行 `tools/visual_resources.py audit --previews` | 966 文件、0 错误、0 检查警告；PNG 解码、引用、帧网格、重复路径和排除资源保护检查通过 | `inventory.json`、`build/visual-review/audit.json` |
| 直接核对最终 JAR | 1705 条目、483 张 PNG、0 重复条目；元数据、许可、粒子、混入、预算与场景类存在 | 本轮 ZIP 检查输出 |

旧版 `shader-smoke-current.log` 及 `scene-shader-final.log` 的统一成功日志曾包含 core shader/post programs；其 ACTIVE 分支实际只检查回退，不能据旧措辞认定自定义程序通过。以上新的 followup 日志已按实际分支区分。

清单分类：更新 358，新增 4，保留 560，排除实体 14，排除祭坛 30；源码资源 752，生成资源 214。这些是资源文件分类，不等于全部画面已经人工验收。

历史 `build/visual-review/runData-first.log` 记录数据生成 BUILD SUCCESSFUL，written 0。本轮未修改生成器，也未重复运行数据生成；`datagen-final.log` 只有启动信息，不能据此宣称该次成功。

构建仍有既有弃用/注解警告。光影环境日志含 Oculus 修改 Embeddium 内部类的告警；本轮 smoke 与场景正常结束，不将其静默视为不存在。

## 世界截图与帧时间

- 无光影本轮目录：`build/visual-review/world-scenes/vanilla/run-2838845282430442082/`。
- 光影本轮目录：`build/visual-review/world-scenes/shader/run-12030365959665788634/`。
- 两个目录均有 `frame-times.json` 和 `screenshots/{near-all-1,near-all-32,far-minimal-1,far-minimal-32}.png`。这些来自补充审查修复前的合成法术场景，不覆盖此次破瓶裁剪和秘典布局变更，也未作为最新候选包重新采样的证据。
- 早期 `world-scenes/vanilla/` 根目录的图片和报告来自排空/防误报修复前的诊断运行，已被上述独立运行替代，不作本轮验收证据。

硬件：NVIDIA GeForce RTX 4070 SUPER；窗口 854×480；渲染距离 12；测试限帧 120，垂直同步关闭。近处为 8 格、ALL 粒子；远处为 52 格、MINIMAL 粒子。合成事件每 6 tick 注入一批，载体活动寿命 6 tick；24 tick 的命中余韵和较长寿命粒子允许按原实现重叠，因此表中 1/8/32 是活动载体事件数，不代表全部视觉对象总数。

P95 帧间隔（毫秒）：

| 本轮环境 | 0 事件 | 1 事件 | 8 事件 | 32 事件 |
| --- | ---: | ---: | ---: | ---: |
| 无光影，近处 ALL | 10.3800 | 10.4338 | 10.1661 | 10.1321 |
| 无光影，远处 MINIMAL | 9.9413 | 10.1080 | 9.9967 | 10.1082 |
| Complementary ACTIVE，近处 ALL | 9.9925 | 10.0703 | 9.9930 | 9.9226 |
| Complementary ACTIVE，远处 MINIMAL | 9.6897 | 9.9225 | 9.8563 | 10.0509 |

这些是 RenderTick END 之间的帧间隔，包含限帧等待及环境开销，不是 GPU 耗时。每组只有 180 个样本；当前零效果基线不等于改造前基线，也没有固定昼夜和所有世界变量。不得据此宣称达到“改造前 P95 增加不超过 15%”或推断开启光影更快。

## 未验证与验收边界

1. 截图实际保存成功，但当前图片读取通道未返回可供人工视觉判读的画面；几何可见性、深度、透明度、遮挡、配色和美术质量仍需打开截图及实际游戏确认。
2. 未完成同相机、同昼夜、同输入的改造前后性能比较，以及常用 1080p/1440p 分辨率的持续并发测试。
3. 未穷举全部源/墨/载体、蓄力、投射物飞行尾迹和酿造云/破瓶/液位的实际玩法画面；合成场景覆盖的是客户端载体、装饰粒子、命中和余韵。破瓶余韵距离复检已实现并通过编译，但尚未交互验证范围边缘移动、传送、运行时降低距离及寿命内移回的显隐行为。
4. 已运行无加载器和真实 ACTIVE 光影环境、手动原版回退及资源重载。安装加载器但关闭、真实运行时开关光影、接口探测失败的真实环境、维度切换和退出重连未做完整交互验收；策略 GameTest 与静态审查不能替代它们。
5. GUI 多档缩放、字体可读性、物品手持与组装、设备全方向/工作态/UV 接缝仍需真实画面逐项确认。秘典布局重载失效已实现并通过编译，但尚未以同一 Screen 实例加载改变字宽/翻译的资源包确认书高、包围盒及平移保持。平面图集和 JSON 检查不能代替三维验收。
6. 专用端开发启动与 GameTest 已通过；未另外执行最终发行 JAR 在干净客户端/专用端整合环境中的安装回归。

以上限制随候选 JAR 一并交付，完整发布验收保持未完成状态。

## 2026-10-18 法阵、音效与低理智补充

- 音效由 `tools/audio_synth.py` 与 `assets/forbidden_hex/audio/spell_synth.json` 确定性生成，54 事件、96 个 OGG；少量无台词抽象低语仅用于心智效果。蓄力固定样本音高，释放/结束/打断保留 6 tick 退场包络；退场声部纳入总预算。
- 法阵保留固定四层、真实八槽词序、空槽和逐槽墨色，加粗主线，补充四源印记与 20 个效果的中心轮廓差异。
- 低理智与不可名状使用连续色调、抗锯齿分支纹及羽化边缘，保留原 alpha 和统一光影回退。仅修正两张黯系粒子纹理的高覆盖暗外缘；原发光贡献误差小于 0.2/255，不透明内核未改。

| 检查 | 已实际运行的结果 | 本地证据 |
| --- | --- | --- |
| `compileJava build runGameTestServer` | 构建成功，33 项 required GameTest 通过 | `build/submission-audit-build.log` |
| `runClient -PaudioSceneTest` | 实际 OpenAL、施法管理器阶段、交叉淡出归零、12 声部预算、退场中资源重载与清理通过 | `build/experience-polish-final.log` |
| `runClient -PcircleSceneTest` 与 `-PshaderCompatTest` 组合 | 无加载器 ABSENT 与 Oculus/Complementary ACTIVE 各保存 40 张截图，含同源同墨 20 效果对照 | `build/circle-polish-{vanilla,shader}-verified.log` |
| `runClient -PsanitySceneTest` 与 `-PshaderCompatTest` 组合 | 两环境理智档位、不可名状、暗粒子、强制回退、重载与恢复通过，各保存 9 张截图 | `build/sanity-particle-{vanilla,shader}-verified.log` |
| `tools/audio_synth.py --verify` | 54 事件、96 文件的原始格式、解码和峰值通过 | `build/submission-audio-verify.log` |
| `tools/visual_resources.py audit` | 1066 项资源，0 错误、0 检查警告 | `build/visual-review/inventory.json` |

补充环境为 Java 17.0.10，Oculus 1.8.0、Embeddium 0.3.31、Complementary Reimagined r5.9.3，其他项目基线不变。工具依赖见 `tools/requirements.txt`；生成的报告、图集、缓存与本机配置均不提交。

上述运行记录确认资源、GPU/音频入口与生命周期路径，不替代截图美术判读或主观试听。多人、其他光影包、真实运行时开关及最终发行包在干净整合环境中的安装回归仍未验收。具体粒子毛边不能以离线像素检查或自动截图保存结果宣称全部消失。
