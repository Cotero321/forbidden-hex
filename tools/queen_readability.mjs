import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const asset = path.join(root, 'src/main/resources/assets/forbidden_hex/animations/entity/agaitolos_queen.animation.json');
const out = path.join(root, 'build/queen-readability');
const fkFile = process.argv.find(arg => arg.startsWith('--fk='))?.slice(5);
if (!fkFile) throw new Error('Provide --fk=<existing fk_queen.mjs absolute path>');
const F = await import(pathToFileURL(path.resolve(fkFile)).href);
const factor = Number(process.argv.find(arg => arg.startsWith('--factor='))?.slice(9) ?? 1);
if (!Number.isFinite(factor) || factor <= 0 || factor > 1) throw new Error('Invalid candidate factor');
fs.mkdirSync(out, { recursive: true });
const baseline = path.join(out, 'baseline.animation.json');
if (!fs.existsSync(baseline)) fs.copyFileSync(asset, baseline);
const source = JSON.parse(fs.readFileSync(baseline, 'utf8'));
const result = structuredClone(source);
const geo = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/assets/forbidden_hex/geo/entity/agaitolos_queen.geo.json'), 'utf8'))['minecraft:geometry'][0];
if (JSON.stringify(geo.bones) !== JSON.stringify([...F.bones.values()])) throw new Error('FK geometry differs from runtime geometry');
const edits = {
  boss_slash: {
    AllBody: [[0, [0, 0, 0]], [7, [-8, 0, 0]], [10, [-8, 0, 0]], [12, [14, 0, 0]], [14, [14, 0, 0]], [20, [0, 0, 0]]]
  },
  boss_execution_cleave: {
    AllBody: [[0, [0, 0, 0]], [7, [-12, 0, 0]], [9, [-12, 0, 0]], [12, [22, 0, 0]], [16, [22, 0, 0]], [22, [8, 0, 0]], [28, [0, 0, 0]]]
  },
  boss_summon_throw: {
    AllBody: [[0, [0, 0, 0]], [12, [-10, 0, 0]], [18, [-10, 0, 0]], [20, [0, 0, 0]], [23, [6, 0, 0]], [30, [0, 0, 0]]]
  }
};
function entries(data) {
  if (Array.isArray(data)) return [[0, data]];
  return Object.entries(data).map(([t, v]) => {
    if (!Array.isArray(v)) throw new Error('Only linear vector channels are supported');
    return [Number(t) * 20, v];
  }).sort((a, b) => a[0] - b[0]);
}
function smooth(keys, t) {
  if (t <= keys[0][0]) return keys[0][1];
  for (let i = 1; i < keys.length; i++) if (t <= keys[i][0]) {
    let u = (t - keys[i - 1][0]) / (keys[i][0] - keys[i - 1][0]);
    u = u * u * (3 - 2 * u);
    return keys[i - 1][1].map((v, k) => v + (keys[i][1][k] - v) * u);
  }
  return keys.at(-1)[1];
}
function pose(clip, tick) {
  const p = { r: {}, p: {} };
  for (const [bone, channels] of Object.entries(clip.bones)) for (const [ch, data] of Object.entries(channels)) {
    if (ch === 'rotation') p.r[bone] = F.sample(entries(data), tick);
    if (ch === 'position') p.p[bone] = F.sample(entries(data), tick);
    if (ch === 'scale' && entries(data).some(([, v]) => v.some(x => x !== 1))) throw new Error('Scaled geometry requires a different FK');
  }
  return p;
}
for (const [name, changes] of Object.entries(edits)) {
  const clip = result.animations[name];
  for (const [bone, offsets] of Object.entries(changes)) {
    const original = entries(clip.bones[bone]?.rotation ?? [0, 0, 0]);
    const ticks = new Set(original.map(([t]) => t));
    for (let t = 0; t <= clip.animation_length * 20 + 1e-8; t += 0.0625) ticks.add(t);
    const baked = {};
    for (const t of [...ticks].sort((a, b) => a - b)) {
      const old = F.sample(original, t), delta = smooth(offsets, t);
      baked[(t / 20).toFixed(10).replace(/0+$/, '').replace(/\.$/, '') || '0'] = old.map((v, i) => Number((v + factor * delta[i]).toFixed(6)));
    }
    clip.bones[bone] ??= {};
    clip.bones[bone].rotation = baked;
  }
}
for (const name of Object.keys(edits)) {
  const clip = result.animations[name];
  const original = entries(source.animations[name].bones.Root?.position ?? [0, 0, 0]);
  const ticks = new Set(original.map(([t]) => t));
  for (let t = 0; t <= clip.animation_length * 20 + 1e-8; t += 0.0625) ticks.add(t);
  const lifted = {};
  for (const t of [...ticks].sort((a, b) => a - b)) {
    const old = F.sample(original, t);
    const before = F.solve(pose(source.animations[name], t));
    const after = F.solve(pose(clip, t));
    const floor = solved => Math.min(...solved.obbs.flatMap(o => o.corners.map(c => c[1])));
    lifted[(t / 20).toFixed(10).replace(/0+$/, '').replace(/\.$/, '') || '0'] = [old[0], Number((old[1] + Math.max(0, floor(before) - floor(after))).toFixed(6)), old[2]];
  }
  clip.bones.Root ??= {};
  clip.bones.Root.position = lifted;
}
function serialize(value, indent = 0) {
  if (Array.isArray(value)) return '[' + value.map(x => serialize(x, indent)).join(', ') + ']';
  if (value && typeof value === 'object') {
    let rows = Object.entries(value);
    if (rows.length && rows.every(([k]) => /^\d+(\.\d+)?$/.test(k))) rows.sort((a, b) => Number(a[0]) - Number(b[0]));
    return '{\n' + rows.map(([k, v]) => ' '.repeat(indent + 2) + JSON.stringify(k) + ': ' + serialize(v, indent + 2)).join(',\n') + '\n' + ' '.repeat(indent) + '}';
  }
  return JSON.stringify(value);
}
const reports = [];
for (const name of Object.keys(edits)) {
  const before = source.animations[name], after = result.animations[name];
  const report = { name, factor, stepTicks: 0.03125, failures: [], contacts: [], maxGripIncrease: 0, minGroundChange: 0, projections: {} };
  const points = { before: [], after: [] };
  const rest = F.solve(pose(before, 0)).obbs;
  const affected = bone => {
    for (let b = F.bones.get(bone); b; b = F.bones.get(b.parent)) if (edits[name][b.name]) return b.name;
    return null;
  };
  const pairs = [];
  for (let i = 0; i < rest.length; i++) for (let j = i + 1; j < rest.length; j++) {
    if (affected(rest[i].bone) !== affected(rest[j].bone)) pairs.push([i, j]);
  }
  const contact = () => false;
  const findings = new Map();
  for (let t = 0; t <= before.animation_length * 20 + 1e-8; t += report.stepTicks) {
    const bp = pose(before, t), ap = pose(after, t);
    const b = F.solve(bp), a = F.solve(ap);
    for (const [i, j] of pairs) {
      const depth = F.depth(a.obbs[i], a.obbs[j]);
      if (depth <= 0.05) continue;
      const old = Math.max(0, F.depth(b.obbs[i], b.obbs[j]));
      if (depth <= old + 0.05) continue;
      const label = `${a.obbs[i].bone}/${a.obbs[j].bone}`;
      const id = `${i}:${j}`;
      if (!findings.has(id) || findings.get(id).increase < depth - old) findings.set(id, { label, tick: t, depth, old, increase: depth - old, contact: contact(a.obbs[i].bone, a.obbs[j].bone) });
    }
    const minBefore = Math.min(...b.obbs.flatMap(o => o.corners.map(c => c[1])));
    const minAfter = Math.min(...a.obbs.flatMap(o => o.corners.map(c => c[1])));
    report.minGroundChange = Math.min(report.minGroundChange, minAfter - minBefore);
    if (Math.abs(t * 2 - Math.round(t * 2)) < 1e-8) {
      const bm = F.measure(bp), am = F.measure(ap);
      const delta = am.dR - bm.dR;
      report.maxGripIncrease = Math.max(report.maxGripIncrease, delta, name === 'boss_execution_cleave' ? am.dL - bm.dL : 0);
    }
    const tip = transform => F.apply(transform.W.get('FH_RoyalScythe'), F.tipLocal);
    const point = transform => name === 'boss_summon_throw' ? F.apply(transform.W.get('LeftHandLocator'), F.sp(F.bones.get('LeftHandLocator').pivot)) : tip(transform);
    if (t >= (name === 'boss_slash' ? 7 : name === 'boss_execution_cleave' ? 7 : 12) && t <= (name === 'boss_slash' ? 14 : name === 'boss_execution_cleave' ? 16 : 20)) {
      points.before.push(point(b)); points.after.push(point(a));
    }
  }
  const range = (pts, axis) => Math.max(...pts.map(p => p[axis])) - Math.min(...pts.map(p => p[axis]));
  for (const side of ['before', 'after']) report.projections[side] = { vertical: range(points[side], 1), lateral: range(points[side], 0), depth: range(points[side], 2) };
  report.contacts = [...findings.values()].filter(f => f.contact);
  report.failures = [...findings.values()].filter(f => !f.contact);
  report.verticalGain = report.projections.after.vertical / report.projections.before.vertical;
  report.releaseAnchorDelta = 0;
  if (name === 'boss_summon_throw') {
    const anchor = clip => {
      const solved = F.solve(pose(clip, 20));
      return F.apply(solved.W.get('LeftHandLocator'), F.sp(F.bones.get('LeftHandLocator').pivot));
    };
    report.releaseAnchorDelta = Math.hypot(...F.sub(anchor(after), anchor(before)));
  }
  report.passed = !report.failures.length && report.maxGripIncrease < 0.001 && report.minGroundChange >= -0.05
    && report.verticalGain >= (name === 'boss_execution_cleave' ? 1.5 : 1.25) && report.releaseAnchorDelta < 0.00001;
  reports.push(report);
  console.log(name, JSON.stringify({ passed: report.passed, collisions: report.failures.length, ground: report.minGroundChange, grip: report.maxGripIncrease, projections: report.projections }));
}
function objectEnd(text, begin) {
  let depth = 0, quoted = false, escaped = false;
  for (let i = begin; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (escaped) escaped = false;
      else if (c === '\\') escaped = true;
      else if (c === '"') quoted = false;
    } else if (c === '"') quoted = true;
    else if (c === '{') depth++;
    else if (c === '}' && --depth === 0) return i + 1;
  }
  throw new Error('Unterminated animation object');
}
let text = fs.readFileSync(baseline, 'utf8');
for (const name of Object.keys(edits)) {
  const marker = '    ' + JSON.stringify(name) + ': ';
  const start = text.indexOf(marker);
  if (start < 0 || text.indexOf(marker, start + 1) >= 0) throw new Error('Ambiguous clip: ' + name);
  const begin = text.indexOf('{', start + marker.length), end = objectEnd(text, begin);
  let clipText = text.slice(begin, end);
  for (const bone of [...Object.keys(edits[name]), 'Root']) {
    const boneMarker = '        ' + JSON.stringify(bone) + ': ';
    const boneStart = clipText.indexOf(boneMarker);
    const replacement = serialize(result.animations[name].bones[bone], 8);
    if (boneStart >= 0) {
      if (clipText.indexOf(boneMarker, boneStart + 1) >= 0) throw new Error('Ambiguous bone');
      const from = clipText.indexOf('{', boneStart + boneMarker.length);
      clipText = clipText.slice(0, from) + replacement + clipText.slice(objectEnd(clipText, from));
    } else {
      const bonesMarker = '      "bones": {\n';
      const from = clipText.indexOf(bonesMarker);
      if (from < 0) throw new Error('Missing bones object');
      const at = from + bonesMarker.length;
      clipText = clipText.slice(0, at) + boneMarker + replacement + ',\n' + clipText.slice(at);
    }
  }
  text = text.slice(0, begin) + clipText + text.slice(end);
}
const parsed = JSON.parse(text);
for (const [name, clip] of Object.entries(source.animations)) {
  if (!edits[name] && JSON.stringify(parsed.animations[name]) !== JSON.stringify(clip)) throw new Error('Unrelated clip changed: ' + name);
  if (clip.animation_length !== parsed.animations[name].animation_length || clip.loop !== parsed.animations[name].loop) throw new Error('Timing or loop changed: ' + name);
}
fs.writeFileSync(path.join(out, 'candidate.animation.json'), text);
const passed = reports.every(r => r.passed);
fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify({ passed, baselineSha256: createHash('sha256').update(fs.readFileSync(baseline)).digest('hex'), candidateSha256: createHash('sha256').update(text).digest('hex'), coverage: 'Same-age differential OBB sampling; original contact overlaps retained, not continuous collision or visual approval', reports }, null, 2));
if (!passed) process.exitCode = 1;
else if (process.argv.includes('--apply')) {
  const current = fs.readFileSync(asset, 'utf8');
  if (current !== fs.readFileSync(baseline, 'utf8') && !isDeepStrictEqual(JSON.parse(current), parsed)) throw new Error('Runtime resource changed since baseline; refusing overwrite');
  fs.writeFileSync(asset, text);
  console.log('Applied three representative clips only; other animations and all event timings retained.');
}
