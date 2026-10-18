from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
import re
import shutil
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ROOTS = (ROOT / 'src/main/resources', ROOT / 'src/generated/resources')
REVIEW = ROOT / 'build/visual-review'
INVENTORY = REVIEW / 'inventory.json'
BASELINE = REVIEW / 'baseline.json'
NS = 'forbidden_hex'


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def excluded(path: str) -> str | None:
    if re.search(r'(?:mother_altar|altar_stone|altar_seal|altar_carpet|MotherAltar|AltarSeal|AltarStone)', path):
        return 'excluded_altar'
    if re.search(r'/(?:geo|animations|textures)/entity/', path):
        return 'excluded_entity'
    return None


def protected(path: str) -> bool:
    return excluded(path) is not None


def files():
    for resource_root in ROOTS:
        if resource_root.exists():
            for path in sorted(resource_root.rglob('*')):
                if path.is_file():
                    yield resource_root, path


def dump(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def snapshot():
    if BASELINE.exists():
        raise SystemExit('Baseline exists; refusing to overwrite the original comparison.')
    records = {}
    for resource_root, path in files():
        relative = path.relative_to(resource_root).as_posix()
        key = path.relative_to(ROOT).as_posix()
        records[key] = {'sha256': digest(path), 'resource_path': relative, 'protected': protected(relative)}
        if relative.startswith(f'assets/{NS}/') and path.suffix in ('.png', '.json', '.mcmeta'):
            target = REVIEW / 'before' / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if target.exists() and digest(target) != digest(path):
                raise SystemExit(f'Conflicting baseline resource: {relative}')
            shutil.copy2(path, target)
    dump(BASELINE, records)
    print(f'Captured {len(records)} resource files without changing source resources.')


def model_id(relative: str):
    match = re.fullmatch(r'assets/([^/]+)/models/(.+)\.json', relative)
    return f'{match[1]}:{match[2]}' if match else None


def split_id(identifier: str):
    return identifier.split(':', 1) if ':' in identifier else ('minecraft', identifier)


def texture_path(identifier: str):
    namespace, name = split_id(identifier)
    return f'assets/{namespace}/textures/{name}.png'


def font(size=11):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/consola.ttf', size)
    except OSError:
        return ImageFont.load_default()


def sheet(entries, output: Path, columns=6, per_page=48):
    output.mkdir(parents=True, exist_ok=True)
    page_count = math.ceil(len(entries) / per_page)
    for path in output.glob('page-*.png'):
        match = re.fullmatch(r'page-(\d+)\.png', path.name)
        if match and int(match[1]) > page_count:
            path.unlink()
    label_font = font(10)
    for page in range(page_count):
        subset = entries[page * per_page:(page + 1) * per_page]
        tile_w, tile_h = 188, 158
        canvas = Image.new('RGB', (columns * tile_w, math.ceil(len(subset) / columns) * tile_h), '#20252b')
        drawing = ImageDraw.Draw(canvas)
        for index, (label, source) in enumerate(subset):
            x, y = index % columns * tile_w, index // columns * tile_h
            try:
                if isinstance(source, bytes):
                    image = Image.open(io.BytesIO(source)).convert('RGBA')
                else:
                    image = Image.open(source).convert('RGBA')
                if image.height > image.width * 2:
                    image = image.crop((0, 0, image.width, image.width))
                size = min(112 / image.width, 112 / image.height)
                scaled = image.resize((max(1, int(image.width * size)), max(1, int(image.height * size))), Image.Resampling.NEAREST)
                for cx in range(0, 176, 8):
                    for cy in range(0, 116, 8):
                        color = '#454d55' if (cx // 8 + cy // 8) % 2 else '#626a71'
                        drawing.rectangle((x + 6 + cx, y + 4 + cy, x + 13 + cx, y + 11 + cy), fill=color)
                canvas.paste(scaled, (x + (tile_w - scaled.width) // 2, y + 5), scaled)
                canvas.paste(image.crop((0, 0, min(32, image.width), min(32, image.height))), (x + 8, y + 83), image.crop((0, 0, min(32, image.width), min(32, image.height))))
                clean = label.replace('forbidden_hex_', '')
                for line in range(2):
                    drawing.text((x + 6, y + 122 + 12 * line), clean[line * 28:(line + 1) * 28], font=label_font, fill='#e5dfcf')
            except (OSError, ValueError) as error:
                drawing.text((x + 6, y + 10), str(error)[:27], font=label_font, fill='#ff8c84')
        canvas.save(output / f'page-{page + 1:02}.png')


def audit(previews=False):
    baseline = json.loads(BASELINE.read_text('utf-8')) if BASELINE.exists() else {}
    original = {entry['resource_path']: entry['sha256'] for entry in baseline.values()}
    resources, records, errors, warnings = {}, [], [], []
    for resource_root, path in files():
        relative = path.relative_to(resource_root).as_posix()
        if relative in resources:
            errors.append(f'Duplicate resource: {relative}')
        resources[relative] = path
        checksum = digest(path)
        record = {'path': path.relative_to(ROOT).as_posix(), 'resource_path': relative, 'sha256': checksum,
                  'ownership': 'authored' if resource_root == ROOTS[0] else 'generated',
                  'decision': excluded(relative) or ('retained' if original.get(relative) == checksum else 'updated' if relative in original else 'added'),
                  'references_from': [], 'checks': []}
        if path.suffix == '.png':
            try:
                with Image.open(path) as image:
                    image.load()
                    record['size'] = list(image.size)
                    if image.mode not in ('RGBA', 'RGB', 'P', 'L', 'LA'):
                        warnings.append(f'Unusual PNG mode {image.mode}: {relative}')
                    meta = path.with_name(path.name + '.mcmeta')
                    if meta.exists():
                        animation = json.loads(meta.read_text('utf-8-sig')).get('animation', {})
                        width = animation.get('width', image.width)
                        height = animation.get('height', width)
                        record['frame_size'] = [width, height]
                        if width <= 0 or height <= 0 or image.width % width or image.height % height:
                            errors.append(f'Invalid frame grid: {relative}')
                        else:
                            frame_count = image.width // width * (image.height // height)
                            for frame in animation.get('frames', []):
                                number = frame.get('index', -1) if isinstance(frame, dict) else frame
                                if not isinstance(number, int) or not 0 <= number < frame_count:
                                    errors.append(f'Invalid animation frame: {relative}: {frame}')
                    record['checks'].append('png_decoded')
            except (OSError, ValueError, TypeError) as error:
                errors.append(f'Invalid PNG/animation {relative}: {error}')
        if protected(relative) and relative in original and original[relative] != checksum:
            errors.append(f'Excluded resource changed: {relative}')
        records.append(record)
    by_relative = {record['resource_path']: record for record in records}
    models, documents = {}, {}
    for relative, path in resources.items():
        if path.suffix == '.json':
            try:
                value = json.loads(path.read_text('utf-8-sig'))
                documents[relative] = value
                identifier = model_id(relative)
                if identifier:
                    models[identifier] = value
            except (ValueError, OSError) as error:
                errors.append(f'Invalid JSON {relative}: {error}')

    def reference(identifier, kind, source):
        namespace, name = split_id(identifier)
        if namespace != NS:
            return
        target = texture_path(identifier) if kind == 'texture' else f'assets/{namespace}/models/{name}.json'
        if target not in resources:
            errors.append(f'Missing {kind}: {identifier} referenced by {source}')
        else:
            by_relative[target]['references_from'].append(source)

    def inheritance(identifier):
        textures, visited, current, generated = {}, set(), identifier, False
        while current in models:
            if current in visited:
                errors.append(f'Model parent cycle: {identifier}')
                break
            visited.add(current)
            value = models[current]
            textures = {**value.get('textures', {}), **textures}
            current = value.get('parent', '')
            if current in ('minecraft:item/generated', 'minecraft:item/handheld', 'item/generated', 'item/handheld'):
                generated = True
        return textures, generated

    for relative, value in documents.items():
        identifier = model_id(relative)
        if identifier:
            if 'parent' in value:
                reference(value['parent'], 'model', relative)
            textures, generated = inheritance(identifier)
            for key, texture in textures.items():
                visited = {key}
                while texture.startswith('#') and texture[1:] in textures and texture[1:] not in visited:
                    visited.add(texture[1:])
                    texture = textures[texture[1:]]
                if not texture.startswith('#'):
                    reference(texture, 'texture', relative)
                    if generated and key.startswith('layer'):
                        target = texture_path(texture)
                        record = by_relative.get(target)
                        if record and '/textures/item/' in target:
                            size = record.get('frame_size', record.get('size'))
                            if size and size != [16, 16]:
                                errors.append(f'Item icon must have 16x16 frames: {target} ({size})')
                else:
                    warnings.append(f'Unresolved texture variable {texture}: {relative}')
            for element in value.get('elements', []):
                for face in element.get('faces', {}).values():
                    uv = face.get('uv', [])
                    if uv and (len(uv) != 4 or min(uv) < 0 or max(uv) > 16):
                        warnings.append(f'UV outside 0..16 (inspect intentional tiling): {relative}: {uv}')
        elif '/blockstates/' in relative:
            def walk(node):
                if isinstance(node, dict):
                    if 'model' in node:
                        reference(node['model'], 'model', relative)
                    for child in node.values():
                        walk(child)
                elif isinstance(node, list):
                    for child in node:
                        walk(child)
            walk(value)
        elif '/particles/' in relative:
            for texture in value.get('textures', []):
                namespace, name = split_id(texture)
                reference(f'{namespace}:particle/{name}', 'texture', relative)
        elif '/geo/' in relative and not excluded(relative):
            for geometry in value.get('minecraft:geometry', []):
                description = geometry.get('description', {})
                tw, th = description.get('texture_width', 0), description.get('texture_height', 0)
                for bone in geometry.get('bones', []):
                    for cube in bone.get('cubes', []):
                        if isinstance(cube.get('uv'), dict):
                            for face in cube['uv'].values():
                                u, v = face.get('uv', (0, 0))
                                w, h = face.get('uv_size', (0, 0))
                                if min(u, u + w) < 0 or max(u, u + w) > tw or min(v, v + h) < 0 or max(v, v + h) > th:
                                    errors.append(f'Geo UV out of atlas: {relative}, bone {bone["name"]}')
                name = Path(relative).name.replace('.geo.json', '')
                for suffix in ('', '_glow'):
                    target = f'assets/{NS}/textures/entity/{name}{suffix}.png'
                    if target in by_relative and by_relative[target].get('size') != [tw, th]:
                        errors.append(f'Geo/texture size mismatch: {target}')
    for entry in baseline.values():
        if (entry.get('protected', False) or protected(entry['resource_path'])) and entry['resource_path'] not in resources:
            errors.append(f'Excluded resource removed: {entry["resource_path"]}')
    for record in records:
        record['references_from'] = sorted(set(record['references_from']))
    result = {'schema': 1, 'scope': 'all visual resources excluding entity and altar changes', 'records': records,
              'errors': sorted(set(errors)), 'warnings': sorted(set(warnings)),
              'runtime_verification': 'not implied by offline checks'}
    dump(INVENTORY, result)
    dump(REVIEW / 'audit.json', {key: value for key, value in result.items() if key != 'records'})
    if previews:
        for group in ('block', 'item', 'gui', 'magic', 'particle', 'mob_effect', 'font', 'slot'):
            entries = [(str(Path(relative).relative_to(f'assets/{NS}/textures/{group}').with_suffix('')), path)
                       for relative, path in sorted(resources.items())
                       if relative.startswith(f'assets/{NS}/textures/{group}/')
                       and path.suffix == '.png' and not excluded(relative)]
            sheet(entries, REVIEW / 'after' / group)
            before = REVIEW / f'before/assets/{NS}/textures/{group}'
            sheet([(path.relative_to(before).with_suffix('').as_posix(), path)
                   for path in sorted(before.rglob('*.png'))
                   if not excluded(path.relative_to(REVIEW / 'before').as_posix())],
                  REVIEW / 'before-sheets' / group)
    print(f'{len(records)} files; {len(set(errors))} errors; {len(set(warnings))} inspection warnings.')
    for error in sorted(set(errors))[:25]:
        print('ERROR:', error)
    return 1 if errors else 0


def reference_jar(path: Path):
    entries, records = [], []
    with zipfile.ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith('assets/witchery/textures/') or not name.endswith('.png'):
                continue
            if not any(word in name.lower() for word in ('belladonna', 'mandrake', 'sapling_', 'leaves_', 'log_', 'witchesoven', 'distillery', 'kettle', 'cauldron', 'wolfsbane', 'waterartichoke', 'seeds')):
                continue
            content = archive.read(name)
            with Image.open(io.BytesIO(content)) as image:
                records.append({'entry': name, 'size': list(image.size), 'sha256': hashlib.sha256(content).hexdigest(), 'use': 'reference_only_not_distributed'})
            entries.append((Path(name).stem, content))
    sheet(entries, REVIEW / 'witchery-reference')
    dump(REVIEW / 'witchery-reference.json', {'source_jar': str(path), 'sha256': digest(path), 'textures': records,
                                          'uv': 'Check ModelWitchesOven/ModelKettle/ModelDistillery bytecode; image dimensions alone do not establish UV.'})
    print(f'Rendered {len(records)} reference textures; source JAR unchanged.')


def particle_edges(fix=False):
    names = ('spell_mote_an_beam.png', 'spell_mote_an_field.png')
    texture_root = ROOTS[0] / f'assets/{NS}/textures/particle'
    backup_root = REVIEW / 'particle-edge-backup'
    records = []
    for name in names:
        path = texture_root / name
        with Image.open(path) as source:
            image = source.convert('RGBA')
        before = image.copy()
        pixels = image.load()
        original = before.load()
        changes = []
        error = 0.0
        for y in range(image.height):
            for x in range(image.width):
                r, g, b, alpha = original[x, y]
                brightness = max(r, g, b)
                if alpha == 0 or brightness >= 96:
                    continue
                exterior = any(
                    not (0 <= x + dx < image.width and 0 <= y + dy < image.height)
                    or original[x + dx, y + dy][3] < 32
                    for dx in range(-2, 3) for dy in range(-2, 3))
                if not exterior:
                    continue
                opacity = round(alpha * brightness / 96)
                rgb = tuple(round(channel * 96 / brightness) if brightness else 96 for channel in (r, g, b))
                pixels[x, y] = (*rgb, opacity)
                error = max(error, *(abs(channel * alpha / 255 - new * opacity / 255)
                                     for channel, new in zip((r, g, b), rgb)))
                changes.append((x, y))
        if fix and changes:
            backup_root.mkdir(parents=True, exist_ok=True)
            backup = backup_root / name
            if not backup.exists():
                shutil.copy2(path, backup)
            for y in range(image.height):
                for x in range(image.width):
                    if original[x, y][3] == 0:
                        pixels[x, y] = (96, 96, 96, 0)
            if error > 0.5:
                raise ValueError(f'Particle emission changed unexpectedly: {name}: {error}')
            image.save(path)
        records.append({'texture': name, 'exterior_pixels': len(changes),
                        'max_emission_error_255': error, 'written': fix and bool(changes),
                        'sha256_before': hashlib.sha256(before.tobytes()).hexdigest(),
                        'scope': 'dim exterior only; interior and runtime tint preserved'})
    dump(REVIEW / ('particle-edge-fix.json' if fix else 'particle-edge-audit.json'), records)
    print(json.dumps(records, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=('snapshot', 'audit', 'reference', 'particle-edges'))
    parser.add_argument('--previews', action='store_true')
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--fix', action='store_true')
    args = parser.parse_args()
    if args.command == 'snapshot':
        snapshot()
    elif args.command == 'particle-edges':
        particle_edges(args.fix)
    elif args.command == 'reference':
        if args.jar is None:
            parser.error('reference requires --jar')
        reference_jar(args.jar)
    else:
        raise SystemExit(audit(args.previews))


if __name__ == '__main__':
    main()
