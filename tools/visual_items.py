import json
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex'
ITEMS = ASSETS / 'textures/item'
INKS = {
    'ember': '#ff6a2a', 'earth': '#8c6239', 'gale': '#6fd3e8', 'vine': '#4caf50',
    'blight': '#7e8c2e', 'order': '#b9c6d8', 'chaos': '#c64bc6', 'abyss': '#1b2740',
    'radiance': '#ffe07a', 'gloom': '#5a2070', 'astral': '#7a6fe8',
}
SOURCES = {'san': '#9e9e9e', 'life': '#3fbf6f', 'dark': '#333333', 'white': '#f2f2f2'}
MATERIALS = {
    'wood': '#8a6e43', 'bone': '#c6b78f', 'iron': '#9b9d94', 'gold': '#c0a45d',
    'diamond': '#6cbab0', 'netherite': '#625757', 'amethyst': '#9a78ae',
    'copper': '#b88059', 'nether_star': '#c9d2b2',
}
BRASS = '#b6a16a'
DARK = '#302e2a'


def mix(color, factor):
    values = bytes.fromhex(color.lstrip('#'))
    return tuple(max(0, min(255, round(value * factor))) for value in values) + (255,)


def save(image, name, folder=ITEMS):
    image.save(folder / (name + '.png'))


def symbol(draw, x, y, color, kind):
    shapes = (
        ((0, 3), (2, 0), (4, 3), (0, 3), (2, 5), (4, 3)),
        ((0, 0), (0, 4), (4, 4), (4, 0), (0, 0), (4, 4)),
        ((0, 4), (4, 0), (2, 0), (2, 4)),
        ((0, 2), (4, 2), (2, 0), (2, 4)),
        ((0, 0), (4, 4), (4, 0), (0, 4)),
        ((0, 1), (4, 1), (4, 3), (0, 3), (0, 5), (4, 5)),
    )
    points = [(x + px, y + py) for px, py in shapes[kind % len(shapes)]]
    draw.line(points, fill=color)


def part_color(name):
    for key, color in INKS.items():
        if name.endswith('ink_' + key):
            return color
    for key, color in {**SOURCES, **MATERIALS}.items():
        if name == key or name.startswith(key + '_') or name.endswith('source_' + key):
            return color
    colors = {
        'store': '#8c8c79', 'cycle': '#7c9a83', 'bulwark': '#899993', 'collapse': '#99747a',
        'convert': '#a79167', 'domain': '#7b91a1', 'rate': '#84a5a5', 'tidal': '#588e99',
        'perpetual': '#aa9a67', 'balance': '#b2ab8f', 'close': '#a79270', 'echo': '#7e99ab',
        'far': '#7b92ab', 'pool': '#748ba8', 'quiet': '#8b829d', 'sharp': '#a8acb0',
        'swift': '#8eae9c', 'wide': '#a2997a',
    }
    return colors[name.split('_')[0]]


def wand_parts():
    from visual_wand_parts import generate_icons

    generate_icons()
    parts = ITEMS / 'wand_parts'
    for path in sorted(parts.glob('*.png')):
        short = path.stem.removeprefix('forbidden_hex_wand_')
        kind, name = short.split('_', 1)
        color = part_color(name.removesuffix('_perfect').removesuffix('_rough'))
        rough = name.endswith('_rough')
        perfect = name.endswith('_perfect')
        swatch = Image.new('RGBA', (16, 16), mix(color, 0.72))
        s = ImageDraw.Draw(swatch)
        for x in (1, 5, 9, 13):
            s.line((x, 0, x, 15), fill=mix(color, 1.12))
            s.line((x + 1, 0, x + 1, 15), fill=mix(color, 0.85))
        if kind == 'handle':
            for y in (4, 11):
                s.line((0, y, 15, y), fill=BRASS if perfect else mix(color, 0.55))
            if name in ('wood', 'bone'):
                s.line((7, 5, 8, 7, 7, 9), fill=mix(color, 0.4))
        elif kind == 'core':
            s.polygon(((0, 0), (7, 0), (2, 8), (0, 13)), fill=mix(color, 1.45))
            s.polygon(((15, 2), (15, 15), (7, 15), (11, 8)), fill=mix(color, 0.45))
            s.line((4, 1, 6, 5, 4, 9), fill=mix(color, 1.7))
        else:
            for y in (2, 13):
                s.line((0, y, 15, y), fill=BRASS)
            if name.startswith('ink_'):
                symbol(s, 5, 5, mix(color, 1.8), list(INKS).index(name[4:]))
        if rough:
            s.line((7, 2, 6, 5, 8, 7, 7, 10), fill=mix(color, 0.38))
        if perfect:
            s.line((0, 14, 15, 14), fill='#e4cb8b')
        save(swatch, path.stem, parts)


FUME_BODY = ('.#.###.', '#.#####', '#.#####', '#####.#', '#####.#', '.###.#.')
FUME_MARKS = {
    'earth_breath': ('.......', '..##...', '..#+#..', '.###...', '##..#..', '.##....'),
    'winter_breath': ('...#.#.', '..#+#..', '...+###', '.##+#..', '..#.#..', '.#.....'),
    'dawn_breath': ('.......', '...###.', '..#+++#', '.####..', '#++++.#', '.......'),
    'vitality_breath': ('....##.', '...#+#.', '..#++#.', '.##+#..', '...#...', '..#....'),
    'sun_breath': ('...#...', '..#+##.', '..#++#.', '.##+#.#', '..###..', '.#...#.'),
    'gloom_breath': ('...###.', '..##...', '..#+...', '.#+....', '..###..', '...#...'),
    'swamp_breath': ('...#...', '..###..', '..#+##.', '.##+#..', '#...#..', '.###...'),
    'dream_breath': ('....#..', '..##+#.', '..#++##', '.#++#..', '..##...', '.......'),
    'white_thorn_breath': ('...#...', '..##.#.', '...+##.', '.###...', '#..#...', '...#...'),
    'red_vein_breath': ('...#...', '...##..', '..#+#..', '.#++#..', '.####..', '..##...'),
    'bloodwood_breath': ('....#..', '..####.', '..#+###', '.####..', '...+#..', '..##.#.'),
    'underpass_breath': ('...###.', '..#+++#', '..#...#', '..#.#.#', '..#+#.#', '.###.#.'),
    'water_mirror_breath': ('.......', '..##...', '..#+##.', '##..#..', '..+++.#', '.###...'),
    'shriek_breath': ('.....#.', '...#..#', '..##+.#', '.###..#', '...##.#', '.....#.'),
    'madness_breath': ('....##.', '...##..', '..##.#.', '...+#.#', '..##...', '.##..#.'),
    'charm_breath': ('...#.#.', '..#+++.', '..#+###', '.#+##..', '..##...', '...#...'),
    'hallucination_breath': ('.......', '...##..', '..#+###', '.#+:#.#', '..###..', '.......'),
    'frost_poison_breath': ('...#.#.', '..#+#..', '...+###', '.####..', '...+...', '..##.#.'),
    'heartbeat_breath': ('.......', '..##.##', '..#++##', '.#++#..', '..##...', '...#...'),
    'curse_breath': ('...##..', '..#..#.', '..##+#.', '.#+##..', '#..#...', '.##....'),
    'heat_breath': ('....#..', '...##..', '..##+#.', '.##+#..', '#+++#..', '.###...'),
    'bright_breath': ('...#...', '..#+#..', '..#++#.', '.#++#..', '..##..#', '...#...'),
    'evil_breath': ('.#...#.', '..###..', '..#:+##', '.#++#..', '..#.#..', '..#..#.'),
}
FUME_PATTERN_COLORS = {
    'earth_breath': ('#19366b', '#55f1d3'),
    'winter_breath': ('#24337f', '#ffb237'),
    'dawn_breath': ('#821f4b', '#ffe56a'),
    'vitality_breath': ('#48225c', '#b9fa58'),
    'sun_breath': ('#182f66', '#fff05a'),
    'gloom_breath': ('#ffe46b', '#b05cff'),
    'swamp_breath': ('#571d47', '#5ff4c8'),
    'dream_breath': ('#382567', '#64e8ff'),
    'white_thorn_breath': ('#154b52', '#ff8c71'),
    'red_vein_breath': ('#20254c', '#66f0ea'),
    'bloodwood_breath': ('#b3ef42', '#ffd58a'),
    'underpass_breath': ('#ffdd7a', '#71ffe1'),
    'water_mirror_breath': ('#762755', '#ffe785'),
    'shriek_breath': ('#492476', '#ffbe58'),
    'madness_breath': ('#162d36', '#ecff51'),
    'charm_breath': ('#154b48', '#ffd36a'),
    'hallucination_breath': ('#1d244f', '#ffb648'),
    'frost_poison_breath': ('#4a1e40', '#ff92cf'),
    'heartbeat_breath': ('#152e4b', '#ffe975'),
    'curse_breath': ('#a6f35c', '#ff8eb1'),
    'heat_breath': ('#1c245d', '#69dcff'),
    'bright_breath': ('#31365a', '#bc79ff'),
    'evil_breath': ('#e4fd7b', '#ff7753'),
}


def fume_kinds():
    source = (ROOT / 'src/main/java/com/example/akaishi/forbidden/brewing/BrewingFumes.java').read_text('utf-8')
    constants = dict(re.findall(r'public static final ResourceLocation (\w+) = id\("(\w+)"\)', source))
    order = source.split('NON_EVIL = List.of(', 1)[1].split(');', 1)[0]
    kinds = [constants[name] for name in re.findall(r'\b[A-Z][A-Z_]+\b', order)]
    kinds.append(constants['EVIL_BREATH'])
    assert len(kinds) == len(set(kinds)) == len(FUME_MARKS) == 23
    assert set(kinds) == set(FUME_MARKS) == set(FUME_PATTERN_COLORS)
    return kinds


def fume_colors():
    source = (ROOT / 'src/main/java/com/example/akaishi/forbidden/brewing/BrewingFumes.java').read_text('utf-8')
    colors = [int(value.strip(), 16) for value in source.split('private static final int[] COLORS = {', 1)[1].split('};', 1)[0].split(',')]
    colors.append(0x4C364F)
    kinds = fume_kinds()
    assert len(colors) == len(kinds)
    return dict(zip(kinds, colors))


def fume_mark(kind=None):
    pattern = FUME_MARKS[kind] if kind else ('....##.', '...#++.', '..##+..', '.#+#...', '..##...', '.......')
    assert len(pattern) == len(FUME_BODY) == 6 and all(len(row) == 7 for row in pattern)
    main, accent = FUME_PATTERN_COLORS[kind] if kind else ('#24364f', '#70eedb')
    palette = {'#': mix(main, 1), '+': mix(accent, 1), ':': (20, 23, 34, 255)}
    image = Image.new('RGBA', (16, 16))
    for y, row in enumerate(pattern):
        for x, value in enumerate(row):
            if value in palette:
                assert FUME_BODY[y][x] == '#', f'Pattern covers glass: {kind} {(x, y)}'
                image.putpixel((5 + x, 8 + y), palette[value])
    return image


def fumes():
    kinds = fume_kinds()
    marks, models = ITEMS / 'fume_marks', ASSETS / 'models/item/fumes'
    marks.mkdir(parents=True, exist_ok=True)
    models.mkdir(parents=True, exist_ok=True)
    save(fume_overlay(), 'forbidden_hex_fume')
    save(fume_mark(), 'default', marks)
    textures = {'layer0': 'minecraft:item/glass_bottle',
                'layer1': 'forbidden_hex:item/forbidden_hex_fume',
                'layer2': 'forbidden_hex:item/fume_marks/default'}
    base = {'parent': 'minecraft:item/generated', 'textures': textures, 'overrides': []}
    masks = set()
    for index, kind in enumerate(kinds, 1):
        image = fume_mark(kind)
        mask = image.getchannel('A').point(lambda value: 255 if value else 0).tobytes()
        assert mask not in masks, f'Duplicate fume body pattern: {kind}'
        masks.add(mask)
        save(image, kind, marks)
        variant = {'parent': 'minecraft:item/generated',
                   'textures': {**textures, 'layer2': 'forbidden_hex:item/fume_marks/' + kind}}
        (models / (kind + '.json')).write_text(json.dumps(variant, indent=2) + '\n', 'utf-8')
        base['overrides'].append({'predicate': {'forbidden_hex:fume_kind': index / 32},
                                  'model': 'forbidden_hex:item/fumes/' + kind})
    base['overrides'].append({'predicate': {'forbidden_hex:refined_fume': 1.0},
                              'model': 'forbidden_hex:item/forbidden_hex_refined_fume'})
    (ASSETS / 'models/item/forbidden_hex_fume.json').write_text(json.dumps(base, indent=2) + '\n', 'utf-8')


def fume_previews(glass, previous_marks=None):
    glass = glass.convert('RGBA')
    gas, kinds, colors = fume_overlay(), fume_kinds(), fume_colors()
    names = json.loads((ASSETS / 'lang/zh_cn.json').read_text('utf-8'))
    labels = ['原版玻璃瓶'] + [names['fume.forbidden_hex.' + kind] for kind in kinds]
    images, previous_images = [glass], []
    assert glass.size == gas.size == (16, 16)
    for kind in kinds:
        color, mark = colors[kind], fume_mark(kind)
        assert all(not glass.getpixel((x, y))[3] or not mark.getpixel((x, y))[3]
                   for y in range(16) for x in range(16)), kind
        assert all(not mark.getpixel((x, y))[3] or (5 <= x < 12 and 8 <= y < 14)
                   for y in range(16) for x in range(16)), kind
        tinted = ImageChops.multiply(gas, Image.new('RGBA', (16, 16),
                                   (color >> 16 & 255, color >> 8 & 255, color & 255, 255)))
        image = Image.alpha_composite(Image.alpha_composite(glass, tinted), mark)
        assert all(image.getpixel((x, y)) == glass.getpixel((x, y))
                   for y in range(16) for x in range(16) if glass.getpixel((x, y))[3]), kind
        images.append(image)
        if previous_marks:
            previous_images.append(Image.alpha_composite(Image.alpha_composite(glass, tinted), previous_marks[kind]))
    font_path = Path('C:/Windows/Fonts/msyh.ttc')
    font = ImageFont.truetype(str(font_path), 14) if font_path.exists() else ImageFont.load_default()
    canvas = Image.new('RGB', (960, 684), '#20252e')
    draw = ImageDraw.Draw(canvas)
    draw.text((16, 10), '熏气瓶身 · 撞色图案与不同轮廓 · 灰底物品栏对比', font=font, fill='#edf2fa')
    output = ROOT / 'build/visual-review/fume-glass'
    output.mkdir(parents=True, exist_ok=True)
    for index, image in enumerate(images):
        x, y = index % 6 * 160, 40 + index // 6 * 160
        draw.rectangle((x + 3, y + 3, x + 156, y + 156), outline='#414b5b')
        draw.rectangle((x + 8, y + 8, x + 103, y + 103), fill='#8b8b8b')
        draw.rectangle((x + 108, y + 12, x + 155, y + 59), fill='#8b8b8b')
        draw.rectangle((x + 116, y + 68, x + 147, y + 99), fill='#8b8b8b')
        large = image.resize((96, 96), Image.Resampling.NEAREST)
        canvas.paste(large, (x + 8, y + 8), large)
        medium = image.resize((48, 48), Image.Resampling.NEAREST)
        canvas.paste(medium, (x + 108, y + 12), medium)
        canvas.paste(image, (x + 124, y + 76), image)
        draw.text((x + 12, y + 120), labels[index], font=font, fill='#edf2fa')
        if index:
            image.save(output / (kinds[index - 1] + '.png'))
    canvas.save(output / 'fumes.png')
    inventory = Image.new('RGB', (486, 162), '#c6c6c6')
    slots = ImageDraw.Draw(inventory)
    for index, image in enumerate(images[1:]):
        x, y = index % 9 * 54, index // 9 * 54
        slots.rectangle((x, y, x + 53, y + 53), fill='#8b8b8b', outline='#373737', width=3)
        icon = image.resize((48, 48), Image.Resampling.NEAREST)
        inventory.paste(icon, (x + 3, y + 3), icon)
    inventory.save(output / 'inventory.png')
    if previous_images:
        comparison = Image.new('RGB', (960, 460), '#20252e')
        compare_draw = ImageDraw.Draw(comparison)
        compare_draw.text((16, 10), '同一瓶底与气雾：左为旧版，右为新版（3倍物品栏尺寸）', font=font, fill='#edf2fa')
        for index, (old, new) in enumerate(zip(previous_images, images[1:])):
            x, y = index % 8 * 120, 36 + index // 8 * 140
            for offset, image in ((4, old), (64, new)):
                compare_draw.rectangle((x + offset, y, x + offset + 51, y + 51), fill='#8b8b8b')
                icon = image.resize((48, 48), Image.Resampling.NEAREST)
                comparison.paste(icon, (x + offset + 2, y + 2), icon)
            compare_draw.text((x + 4, y + 62), labels[index + 1], font=font, fill='#edf2fa')
        comparison.save(output / 'comparison.png')
    print('Verified 23 unique bottle-body patterns, no corner badges, and all original glass bottle pixels unchanged; preview:', output / 'fumes.png')


def fume_overlay():
    image = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(image)
    draw.line((8, 5, 8, 7, 9, 8, 9, 9), fill=(188, 188, 188, 136))
    draw.line((8, 9, 10, 9), fill=(208, 208, 208, 200))
    draw.line((7, 10, 11, 10), fill=(192, 192, 192, 176))
    draw.line((5, 11, 9, 11), fill=(216, 216, 216, 224))
    draw.line((6, 12, 9, 12), fill=(184, 184, 184, 160))
    draw.line((6, 13, 8, 13), fill=(160, 160, 160, 112))
    draw.line((9, 9, 8, 10, 7, 11, 7, 12), fill=(250, 250, 250, 240))
    draw.point(((10, 10), (6, 11)), fill=(236, 236, 236, 208))
    return image


def bottles():
    for name, color in {'bottle': '#8e9385'}.items():
        image = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(image)
        d.rectangle((5, 1, 10, 3), fill='#6b563b', outline='#302d27')
        d.line((5, 1, 9, 1), fill='#bda16b')
        d.polygon(((5, 4), (10, 4), (12, 6), (12, 13), (10, 15), (5, 15), (3, 13), (3, 6)), fill='#2e3434')
        d.rectangle((4, 7, 11, 12), fill=mix(color, 0.7))
        d.line((4, 6, 11, 6), fill=color)
        d.line((4, 7, 4, 11), fill=mix(color, 1.45))
        d.rectangle((5, 8, 10, 12), fill='#c8bc96')
        d.line((6, 10, 9, 10), fill='#514b3d')
        save(image, 'forbidden_hex_ink_' + name)
    fumes()
    for name, liquid_y in (('brewed_potion', 9),):
        content = Image.new('RGBA', (16, 16))
        c = ImageDraw.Draw(content)
        c.rectangle((5, liquid_y, 10, 12), fill='#c2c8c1')
        c.line((5, liquid_y, 10, liquid_y), fill='#f0f2df')
        c.line((6, 13, 9, 13), fill='#8d9b96')
        glass = Image.new('RGBA', (16, 16))
        g = ImageDraw.Draw(glass)
        g.line((6, 3, 6, 5, 4, 7, 4, 12, 6, 14, 10, 14, 12, 12, 12, 7, 10, 5, 10, 3), fill='#91a9a5')
        g.line((5, 8, 5, 10), fill='#d5e5dd')
        g.rectangle((6, 1, 10, 3), fill='#806444')
        g.line((6, 1, 9, 1), fill='#b59b6b')
        save(content, 'forbidden_hex_' + name)
        save(glass, 'forbidden_hex_' + name + '_glass')


def cores():
    for name, color in {'blank': '#969387', **SOURCES}.items():
        image = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(image)
        d.polygon(((7, 1), (13, 6), (12, 11), (7, 15), (2, 10), (2, 5)), fill='#343a35')
        d.polygon(((7, 2), (11, 6), (10, 10), (7, 13), (4, 9), (4, 6)), fill=mix(color, 0.65))
        d.line((7, 2, 4, 6, 4, 8), fill=mix(color, 1.35))
        d.line((7, 13, 10, 10, 11, 6), fill=BRASS)
        if name != 'blank':
            symbol(d, 5, 5, mix(color, 1.6), list(SOURCES).index(name))
        else:
            d.rectangle((6, 6, 8, 9), outline='#77786b')
        save(image, 'forbidden_hex_magic_core_' + name)
    image = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(image)
    d.line((3, 14, 11, 5), fill='#3c3028', width=3)
    d.line((3, 13, 10, 6), fill='#9e8050')
    d.line((9, 5, 11, 7), fill=BRASS, width=2)
    d.polygon(((11, 6), (10, 3), (12, 2), (14, 1), (14, 4)), fill='#444b43')
    d.line((12, 4, 14, 1), fill='#a1b9a1')
    save(image, 'forbidden_hex_magic_brush')
    image = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(image)
    d.polygon(((5, 3), (10, 2), (13, 6), (11, 12), (6, 14), (2, 10), (3, 6)), fill='#263c39')
    for box in ((4, 4, 7, 6), (8, 6, 11, 8), (4, 9, 8, 11)):
        d.rectangle(box, fill='#387b78')
        d.point((box[0], box[1]), fill='#84c4b5')
    save(image, 'forbidden_hex_sculk_lifeform')


def wand_icon():
    image = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(image)
    d.line((6, 6, 13, 13), fill='#272b32', width=3)
    d.line((6, 7, 12, 13), fill='#725940')
    d.line((7, 6, 13, 12), fill='#a3875b')
    for x, y in ((8, 9), (10, 11)):
        d.line((x, y, x + 1, y - 1), fill='#494047')
    d.polygon(((11, 14), (14, 11), (14, 13), (13, 14)), fill='#353b40')
    d.line((11, 13, 13, 11), fill='#b1a071')
    d.polygon(((1, 3), (3, 1), (5, 1), (7, 3), (7, 5), (5, 7), (3, 7), (1, 5)), fill='#29333f')
    d.polygon(((2, 3), (3, 2), (5, 2), (6, 4), (4, 6), (2, 5)), fill='#586f80')
    d.line((2, 3, 3, 2, 4, 2), fill='#a1b8ba')
    d.line((3, 4, 4, 5, 5, 4), fill='#354d65')
    d.line((4, 7, 7, 4), fill='#b6a16a')
    d.line((5, 8, 8, 5), fill='#665b46')
    save(image, 'forbidden_hex_wand')


def relics():
    for name in ('cub_heart', 'fertility_ring', 'life_touch', 'mother_seal'):
        animation = Image.new('RGBA', (16, 256))
        for frame in range(16):
            image = Image.new('RGBA', (16, 16))
            d = ImageDraw.Draw(image)
            factor = 1 + math.sin(frame * math.tau / 16) * 0.15
            glow = mix('#b89a69', factor)
            if name == 'cub_heart':
                d.polygon(((7, 4), (9, 2), (12, 3), (14, 6), (12, 10), (8, 14), (3, 10), (1, 6), (3, 3), (5, 2)), fill='#392e30')
                d.polygon(((3, 5), (5, 4), (8, 6), (11, 4), (12, 6), (10, 10), (8, 12), (4, 9)), fill=mix('#90554f', factor))
                d.line((4, 5, 6, 6, 5, 8), fill='#c68e74')
                d.line((8, 7, 10, 6, 9, 9), fill=glow)
            elif name == 'fertility_ring':
                d.ellipse((2, 3, 13, 14), fill='#514a32')
                d.ellipse((3, 3, 12, 13), fill=glow)
                d.ellipse((5, 5, 10, 11), fill=(0, 0, 0, 0))
                d.polygon(((6, 1), (10, 2), (11, 5), (8, 7), (5, 4)), fill='#6f9465')
                d.line((6, 2, 8, 3), fill='#c2cd8b')
            elif name == 'life_touch':
                d.polygon(((4, 14), (3, 11), (4, 6), (6, 8), (6, 3), (8, 2), (8, 8), (10, 4), (12, 5), (10, 10), (9, 14)), fill='#6d8760')
                d.line((5, 12, 8, 10, 8, 6), fill=glow)
                d.point((3, 4), fill=glow)
                d.point((12, 2), fill=glow)
            else:
                d.ellipse((2, 2, 13, 13), fill='#433931', outline=glow)
                d.line((7, 4, 4, 9, 11, 9, 7, 4, 7, 12), fill='#b8a37a')
                d.rectangle((6, 6, 8, 8), fill=mix('#a66c56', factor))
                d.line((4, 13, 3, 15), fill='#9a7651')
                d.line((11, 13, 12, 15), fill='#9a7651')
            animation.paste(image, (0, frame * 16))
        save(animation, 'forbidden_hex_' + name)


if __name__ == '__main__':
    wand_parts()
    bottles()
    cores()
    wand_icon()
    relics()
    print('Authored paired wand icons/materials, diagonal wand, empty ink bottle, layered fumes, magic cores and relic animations; Aseprite-authored ink and refined-fume animations retained.')
