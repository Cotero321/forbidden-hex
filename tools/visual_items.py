import math
from pathlib import Path

from PIL import Image, ImageDraw

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
    parts = ITEMS / 'wand_parts'
    for path in sorted(parts.glob('*.png')):
        short = path.stem.removeprefix('forbidden_hex_wand_')
        kind, name = short.split('_', 1)
        color = part_color(name.removesuffix('_perfect').removesuffix('_rough'))
        rough = name.endswith('_rough')
        perfect = name.endswith('_perfect')
        image = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(image)
        if kind == 'handle':
            d.polygon(((7, 1), (9, 2), (9, 13), (8, 15), (6, 14), (6, 3)), fill=DARK)
            d.line((7, 2, 7, 13), fill=mix(color, 1.22))
            d.line((8, 3, 8, 13), fill=color)
            d.line((6, 5, 8, 6), fill=mix(color, 0.65))
            d.line((6, 9, 8, 10), fill=mix(color, 0.65))
            for y in (3, 12):
                d.line((5, y, 10, y), fill=BRASS if name not in MATERIALS else mix(color, 0.75))
            if name in ('wood', 'bone'):
                d.point((8, 7), fill=mix(color, 0.5))
            elif name.startswith(('life', 'dark', 'san', 'white')):
                d.line((7, 5, 8, 6, 7, 8, 8, 9), fill=mix(color, 1.6))
        elif kind == 'core':
            d.rectangle((4, 11, 11, 12), fill=BRASS)
            d.rectangle((5, 13, 10, 14), fill='#665c43')
            d.polygon(((7, 1), (11, 5), (10, 10), (7, 12), (3, 8), (4, 4)), fill=mix(color, 0.55))
            d.polygon(((7, 2), (9, 5), (8, 9), (5, 8), (4, 5)), fill=color)
            d.line((7, 2, 5, 5, 5, 7), fill=mix(color, 1.65))
            d.line((7, 3, 8, 6, 7, 9), fill=mix(color, 0.8))
            if name.startswith('bulwark'):
                d.line((3, 4, 2, 6, 3, 10, 7, 13, 11, 10, 12, 6, 11, 4), fill=BRASS)
            elif name.startswith(('domain', 'tidal')):
                d.line((2, 7, 5, 10, 10, 10, 13, 7), fill=BRASS)
            elif name.startswith('collapse'):
                d.line((7, 2, 6, 5, 8, 7, 6, 10), fill='#3b3038')
            elif name.startswith('rate'):
                d.line((2, 5, 4, 3, 4, 10, 2, 8), fill=BRASS)
        else:
            d.polygon(((4, 4), (11, 4), (12, 7), (10, 11), (5, 11), (3, 7)), fill=mix(color, 0.6))
            d.line((4, 4, 11, 4, 11, 6), fill=mix(color, 1.45))
            d.line((4, 7, 5, 10, 10, 10, 11, 7), fill=color)
            d.rectangle((6, 11, 9, 13), fill=BRASS)
            if name in ('sharp', 'far', 'swift'):
                d.line((7, 1, 7, 6), fill=mix(color, 1.35))
                d.point((8, 2), fill=color)
            elif name.startswith(('ink_', 'source_')):
                d.rectangle((6, 6, 9, 8), fill=color)
                d.point((6, 6), fill=mix(color, 1.5))
            elif name == 'wide':
                d.line((2, 6, 2, 9, 12, 9, 13, 6), fill=color)
        if perfect:
            d.point((11, 2), fill='#ede2bd')
            d.point((12, 1), fill='#c7b37d')
            d.line((5, 13, 10, 13), fill='#e4cb8b')
        if rough:
            d.line((7, 5, 8, 6, 7, 7), fill='#39352f')
            d.point((6, 10), fill='#827357')
        save(image, path.stem)
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


def bottles():
    for index, (name, color) in enumerate({'bottle': '#8e9385', **INKS}.items()):
        image = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(image)
        d.rectangle((5, 1, 10, 3), fill='#6b563b', outline='#302d27')
        d.line((5, 1, 9, 1), fill='#bda16b')
        d.polygon(((5, 4), (10, 4), (12, 6), (12, 13), (10, 15), (5, 15), (3, 13), (3, 6)), fill='#2e3434')
        d.rectangle((4, 7, 11, 12), fill=mix(color, 0.7))
        d.line((4, 6, 11, 6), fill=color)
        d.line((4, 7, 4, 11), fill=mix(color, 1.45))
        d.rectangle((5, 8, 10, 12), fill='#c8bc96')
        if index:
            d.point((6 + index % 4, 9), fill=color)
            d.line((6, 11, 9, 11), fill=mix(color, 0.7))
        else:
            d.line((6, 10, 9, 10), fill='#514b3d')
        save(image, 'forbidden_hex_ink_' + name)
    for name, liquid_y in (('fume', 6), ('brewed_potion', 9)):
        content = Image.new('RGBA', (16, 16))
        c = ImageDraw.Draw(content)
        if name == 'fume':
            c.line((5, 11, 8, 12, 10, 10, 6, 8, 10, 7), fill='#cbd1c8', width=2)
        else:
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
    print('Authored 68 paired wand icons/materials; diagonal wand, inks, layered bottles, magic cores and four 16x16-frame relic animations.')
