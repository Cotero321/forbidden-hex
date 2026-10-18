"""Deterministic spell sound design; Python standard library and FFmpeg/libvorbis."""

from __future__ import annotations

import argparse
from array import array
import hashlib
import json
import math
from pathlib import Path
import random
import shutil
import subprocess
import tempfile
import wave

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/forbidden_hex"
TAU = math.tau


def noise(rng, size, cutoff, rate):
    smooth = 0.0
    coefficient = 1.0 - math.exp(-TAU * cutoff / rate)
    values = array("f")
    for _ in range(size):
        smooth += coefficient * (rng.uniform(-1.0, 1.0) - smooth)
        values.append(smooth)
    return values


def envelope(t, duration, attack=0.008, decay=5.0):
    return min(1.0, t / attack) * math.exp(-decay * t / duration) * min(1.0, (duration - t) / 0.04)


def resonator(samples, frequency, bandwidth, rate):
    radius = math.exp(-math.pi * bandwidth / rate)
    feedback = 2.0 * radius * math.cos(TAU * frequency / rate)
    last = older = 0.0
    result = array("f")
    for value in samples:
        current = value * (1.0 - radius) + feedback * last - radius * radius * older
        result.append(current)
        older, last = last, current
    peak = max(map(abs, result), default=1.0) or 1.0
    return array("f", (value / peak for value in result))


def reverb(samples, rate):
    result = array("f", samples)
    for delay, gain in ((0.043, 0.17), (0.089, -0.12), (0.151, 0.09), (0.237, 0.06)):
        offset = int(delay * rate)
        for i in range(offset, len(result)):
            result[i] += samples[i - offset] * gain
    return result


def transient(spec, variant, rate, rng):
    duration, frequency, kind = spec["duration"], spec["frequency"], spec["kind"]
    frequency *= (1.0, 1.05946)[variant]
    count = int(duration * rate)
    low = noise(rng, count, 900, rate)
    high = noise(rng, count, 6500, rate)
    whisper = resonator(high, 730, 120, rate) if kind == "whisper" else None
    second_formant = resonator(high, 1420, 180, rate) if kind == "whisper" else None
    result = array("f")
    for i in range(count):
        t = i / rate
        u = t / duration
        env = envelope(t, duration)
        tone = math.sin(TAU * frequency * t)
        glass = (math.sin(TAU * frequency * t) + 0.43 * math.sin(TAU * frequency * 2.756 * t)
                 + 0.24 * math.sin(TAU * frequency * 4.07 * t))
        if kind in ("strike", "fracture"):
            body = math.sin(TAU * (frequency * t + 52.0 * (1.0 - math.exp(-t * 18.0)) / 18.0))
            value = body * 0.65 + low[i] * 0.8 * math.exp(-t * 13) + glass * 0.12
        elif kind in ("chime", "glass", "vessel", "ping"):
            value = glass * (0.65 if kind != "vessel" else 0.4)
            if kind == "chime":
                value += math.sin(TAU * frequency * 1.5 * max(0.0, t - 0.06)) * 0.2
            elif kind == "vessel":
                value += math.sin(TAU * frequency * 0.5 * t) * 0.55
            elif kind == "ping":
                value *= 0.68 + 0.32 * math.cos(TAU * 5.0 * t)
        elif kind in ("cut", "gust"):
            sweep = math.sin(TAU * (frequency * t + 750.0 * t * t))
            value = high[i] * (1.0 - u) * 0.9 + sweep * (0.35 if kind == "cut" else 0.12)
        elif kind == "shatter":
            pulse = math.exp(-((t % 0.065) / 0.013) ** 2)
            value = glass * pulse * 0.65 + high[i] * 0.45 + low[i] * 0.15
        elif kind == "chain":
            pulse = math.exp(-((t % 0.11) / 0.022) ** 2)
            value = glass * pulse * 0.75 + tone * 0.18
        elif kind in ("fold", "drain", "vortex"):
            descending = frequency * (2.2 * t - 0.8 * t * t / duration)
            value = math.sin(TAU * descending + 1.8 * math.sin(TAU * frequency * 0.5 * t)) * 0.48
            value += low[i] * 0.55
            if kind == "vortex":
                value *= 0.55 + 0.45 * math.sin(TAU * (5.0 * t + t * t))
        elif kind == "rise":
            value = sum(math.sin(TAU * frequency * ratio * t) for ratio in (1, 1.25, 1.5)) * 0.28
            env = envelope(t, duration, attack=duration * 0.2, decay=2.2)
        elif kind == "whisper":
            syllables = (0.5 + 0.5 * math.sin(TAU * 2.3 * t)) ** 2
            value = (whisper[i] * 0.38 + second_formant[i] * 0.22) * syllables + glass * 0.14
        elif kind == "veil":
            value = high[i] * 0.65 + math.sin(TAU * frequency * t + math.sin(TAU * 0.8 * t)) * 0.16
            env = envelope(t, duration, attack=0.08, decay=3)
        elif kind == "portal":
            value = math.sin(TAU * frequency * t + 2 * math.sin(TAU * frequency * 1.5 * t)) * 0.4
            value += glass * 0.28 + low[i] * 0.3
        elif kind == "seal":
            value = sum(math.sin(TAU * frequency * ratio * t) for ratio in (1, 1.5, 2)) * 0.24
            value *= 0.75 + 0.25 * math.cos(TAU * 7.0 * t)
        elif kind == "spark":
            value = math.sin(TAU * (frequency * t + 1900 * t * t)) * 0.32 + high[i] * 0.7
            value *= 0.4 + 0.6 * (math.sin(TAU * 36.0 * t) ** 4)
        elif kind == "echo":
            value = glass * math.exp(-((t % 0.18) / 0.035) ** 2) * 0.7
        else:
            raise ValueError(f"Unknown synthesis kind: {kind}")
        result.append(value * env)
    return reverb(result, rate)


def resonance_loop(spec, stage, rate, rng):
    duration = 4.0
    count = int(duration * rate)
    overlap = int(rate * 0.15)
    air = noise(rng, count + overlap, 3600, rate)
    for i in range(overlap):
        mix = math.sin(math.pi * 0.5 * i / overlap) ** 2
        air[i] = air[count + i] * (1.0 - mix) + air[i] * mix
    partials = [(round(spec["frequency"] * ratio * 2.0 * duration) / duration,
                 0.34 / (harmonic + 1) ** 1.2, harmonic * 1.7)
                for harmonic, ratio in enumerate(spec["ratios"])]
    crystal_frequency = round(spec["frequency"] * 5.0 * duration) / duration
    result = array("f")
    for i in range(count):
        t = i / rate
        drift = TAU * t / duration
        value = sum(math.sin(TAU * frequency * t + 0.025 * math.sin(drift + phase))
                    * weight * (0.93 + 0.07 * math.cos(drift + phase))
                    for frequency, weight, phase in partials)
        crystal = math.sin(TAU * crystal_frequency * t + 0.02 * math.sin(drift))
        value += crystal * (0.075 if stage == "charge" else 0.035)
        value += air[i] * spec["air"] * (0.65 if stage == "charge" else 0.85)
        result.append(value)
    return result


def source_sound(spec, stage, rate, rng):
    if stage in ("charge", "sustain"):
        return resonance_loop(spec, stage, rate, rng)
    duration = 0.9
    count = int(duration * rate)
    air = noise(rng, count, 2200, rate)
    result = array("f")
    for i in range(count):
        t = i / rate
        value = 0.0
        for harmonic, ratio in enumerate(spec["ratios"]):
            frequency = round(spec["frequency"] * ratio * duration) / duration
            fm = 0.32 * math.sin(TAU * (harmonic + 1) * t / duration)
            value += math.sin(TAU * frequency * t + fm) / (harmonic + 1) ** 1.35
        shimmer = 0.82 + 0.18 * math.sin(TAU * spec["shimmer"] * 0.5 * t)
        crystal = math.sin(TAU * round(spec["frequency"] * 4.0) * t) * 0.035
        value = (value * 0.45 + crystal) * shimmer + air[i] * spec["air"]
        result.append(value * envelope(t, duration, attack=0.012, decay=5))
    return reverb(result, rate)


def pcm(samples):
    mean = sum(samples) / len(samples)
    peak = max(abs(value - mean) for value in samples) or 1.0
    gain = 0.63 / peak
    return array("h", (round(max(-0.98, min(0.98, (value - mean) * gain)) * 32767) for value in samples))


def encode(samples, target, rate, encoder):
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary_root = ROOT / "build/audio-preview"
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="encoding_", dir=temporary_root) as folder:
        wav = Path(folder) / "source.wav"
        with wave.open(str(wav), "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(rate)
            output.writeframes(samples.tobytes())
        subprocess.run([encoder, "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                        "-map_metadata", "-1", "-c:a", "libvorbis", "-q:a", "4", str(target)], check=True)


def stable_seed(base, key):
    return base ^ int.from_bytes(hashlib.sha256(key.encode()).digest()[:8], "little")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true", help="Check catalog, resources, codec and decoded peaks")
    args = parser.parse_args()
    encoder = shutil.which("ffmpeg")
    if encoder is None:
        raise SystemExit("FFmpeg with libvorbis is required on PATH")
    probe = shutil.which("ffprobe") if args.verify else None
    if args.verify and probe is None:
        raise SystemExit("FFprobe is required for source format verification")
    config = json.loads((ASSETS / "audio/spell_synth.json").read_text(encoding="utf-8"))
    rate, seed = config["sample_rate"], config["seed"]
    jobs = {}
    for source_id, spec in config["sources"].items():
        for stage in ("charge", "sustain", "release"):
            jobs[f"spell.source.{source_id}.{stage}"] = ("source", spec, stage)
    for group in ("effects", "inks", "carriers"):
        singular = {"effects": "effect", "inks": "ink", "carriers": "carrier"}[group]
        for name, spec in config[group].items():
            jobs[f"spell.{singular}.{name}"] = ("transient", spec, None)
    for name, spec in {
        "end": {"kind": "chime", "frequency": 370, "duration": 0.50},
        "interrupt": {"kind": "cut", "frequency": 155, "duration": 0.42},
        "shatter": {"kind": "shatter", "frequency": 880, "duration": 0.55},
        "pulse": {"kind": "seal", "frequency": 270, "duration": 0.52},
    }.items():
        jobs[f"spell.{name}"] = ("transient", spec, None)
    entries = {}
    reports = {}
    generated = {}
    for event, (mode, spec, stage) in jobs.items():
        variants = 1 if mode == "source" else 2
        paths = []
        for variant in range(variants):
            key = event.replace(".", "/") + (f"_{variant + 1}" if variants > 1 else "")
            target = ASSETS / "sounds" / f"{key}.ogg"
            paths.append({"name": f"forbidden_hex:{key}", "attenuation_distance": 24})
            if args.verify:
                if not target.is_file():
                    raise ValueError(f"Missing audio: {target}")
                metadata = subprocess.run([probe, "-v", "error", "-show_entries",
                                           "stream=codec_name,channels,sample_rate", "-of", "json", str(target)],
                                          check=True, capture_output=True, text=True)
                streams = json.loads(metadata.stdout).get("streams", [])
                if (len(streams) != 1 or streams[0].get("codec_name") != "vorbis"
                        or streams[0].get("channels") != 1 or int(streams[0].get("sample_rate", 0)) != rate):
                    raise ValueError(f"Invalid source format: {target}: {streams}")
                decoded = subprocess.run([encoder, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", str(target),
                                          "-f", "f32le", "-ac", "1", "-ar", str(rate), "pipe:1"],
                                         check=True, capture_output=True)
                values = array("f")
                values.frombytes(decoded.stdout)
                if not values or not all(math.isfinite(value) for value in values):
                    raise ValueError(f"Invalid audio: {target}")
                peak = max(map(abs, values))
                if peak >= 0.99 or peak < 0.01:
                    raise ValueError(f"Unsafe/silent decoded peak {peak}: {target}")
                reports[key] = {"seconds": round(len(values) / rate, 3), "peak": round(peak, 5)}
                continue
            rng = random.Random(stable_seed(seed, key))
            samples = source_sound(spec, stage, rate, rng) if mode == "source" else transient(spec, variant, rate, rng)
            encoded = pcm(samples)
            encode(encoded, target, rate, encoder)
            generated[key] = encoded
            reports[key] = {"seconds": round(len(samples) / rate, 3), "bytes": target.stat().st_size}
        entries[event] = {"subtitle": "subtitles.forbidden_hex." + event, "sounds": paths}
    sound_json = ASSETS / "sounds.json"
    if args.verify:
        existing = json.loads(sound_json.read_text(encoding="utf-8"))
        for event, expected in entries.items():
            if existing.get(event) != expected:
                raise ValueError(f"Catalog mismatch: {event}")
    else:
        existing = json.loads(sound_json.read_text(encoding="utf-8")) if sound_json.exists() else {}
        existing.update(entries)
        existing.setdefault("agaitolos_theme", {"sounds": [{"name": "forbidden_hex:agaitolos_theme", "stream": True}]})
        sound_json.write_text(json.dumps(existing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        previews = ROOT / "build/audio-preview"
        previews.mkdir(parents=True, exist_ok=True)
        for source_id in config["sources"]:
            snippets = [generated[f"spell/source/{source_id}/charge"]]
            for effect, ink in (("damage", "chi"), ("ward", "yao"), ("mind", "yuan"), ("gather", "ju")):
                snippets.append(array("h", [0] * int(rate * 0.35)))
                layers = [(generated[f"spell/source/{source_id}/release"], 0.35),
                          (generated[f"spell/effect/{effect}_1"], 0.55),
                          (generated[f"spell/ink/{ink}_1"], 0.22)]
                length = max(len(values) for values, _ in layers)
                snippets.append(array("h", (round(sum(values[i] * gain for values, gain in layers if i < len(values)))
                                            for i in range(length))))
            joined = array("h")
            for snippet in snippets:
                joined.extend(snippet)
            with wave.open(str(previews / f"{source_id}_showcase.wav"), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(rate)
                output.writeframes(joined.tobytes())
    report_path = ROOT / "build/audio-preview" / ("verification.json" if args.verify else "generation.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps({"seed": seed, "sample_rate": rate, "events": len(entries),
                                       "files": reports}, indent=2) + "\n", encoding="utf-8")
    print(f"{'Verified' if args.verify else 'Synthesized'} {len(entries)} events / {len(reports)} OGG files")


if __name__ == "__main__":
    main()
