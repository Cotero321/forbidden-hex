/**
 * 魔法侧执行层的<b>四阶段管道契约</b>（设计记忆 §19.2 / §20.9 / §36）。
 *
 * <h3>四阶段与四段词序的对齐（§19.2）</h3>
 * <table border="1">
 *   <caption>管道阶段</caption>
 *   <tr><th>阶段</th><th>驱动段</th><th>接口</th><th>决定</th></tr>
 *   <tr><td>① 目标解析</td><td>作用点段 B</td><td>{@link com.example.akaishi.forbidden.api.magic.exec.ITargetResolver}</td>
 *       <td>目标集合 + 形状 G</td></tr>
 *   <tr><td>② 投射</td><td>性质段 D</td><td>{@link com.example.akaishi.forbidden.api.magic.exec.IDeliverer}</td>
 *       <td>交付方式 X + 前摇 / 可打断性</td></tr>
 *   <tr><td>③ 效果执行</td><td>动作段 A</td><td>{@link com.example.akaishi.forbidden.api.magic.exec.IPrimitiveExecutor}</td>
 *       <td>效果语义 E</td></tr>
 *   <tr><td>④ 持续与二次负载</td><td>结构段 C</td><td>{@link com.example.akaishi.forbidden.api.magic.exec.IPersistenceHandler}</td>
 *       <td>脉冲 / 衰减 / 衍生 / 场地残留</td></tr>
 * </table>
 *
 * <h3>为什么这四件事必须拆开</h3>
 * <p>原先它们是焊死在同一条 {@code execute()} 里的，结果<b>同一 E 配不同 X 的手感完全趋同</b> ——
 * 而"直射 vs 引导"的差异是数值调不出来的体验差（§19.3）。拆开后每阶段只关心自己的输入输出，
 * 新增一种 X 或一种 E 都不必改其余三阶段（OCP）。
 *
 * <h3>纪律</h3>
 * <ul>
 *   <li>本包与父包 {@code api.magic} 同级纪律：<b>只暴露接口与不可变值</b>，实现一律在
 *       {@code forbidden.magic.exec.*}；签名发布后<b>零改动</b>（只能加方法）。</li>
 *   <li>值类型里允许出现 Minecraft 的<b>共享类型</b>（{@code Entity} / {@code Vec3}）——
 *       它们对附属与本体是同一份，不属于"实现类型"；真正禁止的是反向依赖 {@code forbidden.magic.*}。</li>
 *   <li><b>静态表不下发</b>：本包只说"某次施法的上下文与目标集"，不下发词表 / E 表 / X 表内容。</li>
 * </ul>
 */
package com.example.akaishi.forbidden.api.magic.exec;
