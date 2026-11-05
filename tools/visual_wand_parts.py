from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from visual_items import INKS, mix, part_color

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex'
ITEMS = ASSETS / 'textures/item'
TEMPLATES = Path(__file__).resolve().parent / 'wand_templates'
REVIEW = ROOT / 'build/visual-review/wand-parts'
PREFIX = 'forbidden_hex_wand_'
INK_STAGE = {
    'ember': 1, 'earth': 1, 'gale': 1, 'vine': 2, 'blight': 2,
    'order': 3, 'chaos': 3, 'abyss': 4, 'radiance': 5, 'gloom': 5, 'astral': 6,
}
GLYPHS = {
    'rate': (((0, 2), (3, 0), (2, 3), (5, 2), (2, 6), (3, 3)),),
    'convert': (((0, 2), (2, 0), (5, 0), (6, 2)), ((6, 4), (4, 6), (1, 6), (0, 4))),
    'domain': (((3, 0), (6, 3), (3, 6), (0, 3), (3, 0)), ((3, 2), (3, 4))),
    'san': (((0, 1), (2, 1), (2, 5), (0, 5)), ((6, 1), (4, 1), (4, 5), (6, 5)), ((3, 2), (3, 4))),
    'life': (((3, 6), (3, 2), (5, 0), (6, 0), (6, 2), (3, 4)), ((3, 3), (0, 2), (0, 4), (3, 5))),
    'dark': (((0, 3), (2, 1), (4, 1), (6, 3), (4, 5), (2, 5), (0, 3)), ((3, 2), (3, 4))),
    'white': (((0, 1), (6, 1), (6, 3), (5, 5), (3, 6), (1, 5), (0, 3), (0, 1)), ((3, 2), (3, 4))),
    'perpetual': (((0, 3), (1, 1), (2, 1), (4, 5), (5, 5), (6, 3), (5, 1), (4, 1), (2, 5), (1, 5), (0, 3)),),
    'tidal': (((0, 2), (1, 1), (3, 1), (4, 3), (6, 3)), ((0, 5), (2, 5), (3, 4), (5, 4), (6, 5))),
    'collapse': (((3, 0), (2, 2), (4, 3), (1, 5), (2, 6)), ((4, 3), (6, 2))),
    'bulwark': (((0, 0), (6, 0), (6, 4), (3, 6), (0, 4), (0, 0)), ((1, 3), (5, 3)), ((3, 1), (3, 5))),
    'ember': (((2, 6), (0, 4), (1, 2), (3, 0), (3, 3), (5, 2), (6, 4), (4, 6)),),
    'earth': (((0, 5), (0, 3), (2, 3), (2, 1), (4, 1), (4, 3), (6, 3), (6, 5), (0, 5)),),
    'gale': (((0, 1), (4, 1), (5, 2), (4, 3), (0, 3)), ((1, 5), (5, 5), (6, 4))),
    'vine': (((2, 6), (2, 3), (4, 1), (6, 1), (5, 3), (2, 3), (0, 1)),),
    'blight': (((0, 0), (2, 2), (1, 4), (3, 6)), ((6, 1), (4, 3), (6, 5)), ((3, 0), (3, 2))),
    'order': (((0, 0), (6, 0), (6, 6), (0, 6), (0, 0)), ((3, 0), (3, 6)), ((0, 3), (6, 3))),
    'chaos': (((0, 0), (5, 5), (1, 5), (5, 1), (5, 6)), ((0, 4), (2, 4))),
    'abyss': (((5, 0), (1, 0), (0, 1), (0, 5), (1, 6), (5, 6), (6, 5), (6, 2), (3, 2), (3, 4), (4, 4)),),
    'radiance': (((3, 0), (3, 6)), ((0, 3), (6, 3)), ((1, 1), (5, 5)), ((1, 5), (5, 1))),
    'gloom': (((4, 0), (1, 1), (0, 3), (1, 5), (4, 6), (6, 4), (3, 4), (2, 3), (3, 1), (4, 0)),),
    'astral': (((0, 2), (6, 4)), ((0, 4), (6, 2)), ((3, 0), (3, 6)), ((1, 1), (5, 5))),
    'balance': (((0, 2), (6, 2)), ((3, 0), (3, 6)), ((0, 3), (1, 4), (2, 3)), ((4, 3), (5, 4), (6, 3))),
    'swift': (((0, 0), (3, 3), (0, 6)), ((3, 0), (6, 3), (3, 6))),
    'pool': (((0, 2), (0, 5), (1, 6), (5, 6), (6, 5), (6, 2)), ((2, 3), (4, 3))),
    'quiet': (((0, 1), (2, 1)), ((1, 3), (5, 3)), ((4, 5), (6, 5))),
    'far': (((0, 6), (6, 0), (3, 0)), ((6, 0), (6, 3))),
    'close': (((0, 0), (2, 3), (0, 6)), ((6, 0), (4, 3), (6, 6))),
    'wide': (((2, 0), (0, 0), (0, 6), (2, 6)), ((4, 0), (6, 0), (6, 6), (4, 6))),
    'sharp': (((3, 0), (5, 5), (3, 4), (1, 5), (3, 0)), ((3, 4), (3, 6))),
}


def blend(first, second, amount):
    a, b = mix(first, 1)[:3], mix(second, 1)[:3]
    return tuple(round(x + (y - x) * amount) for x, y in zip(a, b)) + (255,)


def load_templates():
    result = {}
    for kind in ('core', 'handle', 'tip'):
        with Image.open(TEMPLATES / f'{kind}.png') as source:
            image = source.convert('RGBA')
        if image.size != (16, 16) or image.getextrema()[3] != (0, 255):
            raise ValueError(f'Expected transparent 16x16 user template: {kind}')
        if any(r != g or g != b for r, g, b, alpha in
               (image.getpixel((x, y)) for y in range(16) for x in range(16)) if alpha):
            raise ValueError(f'Expected grayscale user template: {kind}')
        result[kind] = image
    return result


def registered_parts():
    table_root = ROOT / 'src/main/java/com/example/akaishi/forbidden/wand'
    result = []
    for kind in ('core', 'handle', 'tip'):
        text = (table_root / f'Wand{kind.title()}Table.java').read_text('utf-8')
        for identifier in re.findall(r'"(forbidden_hex_wand_\w+)"', text):
            if identifier.endswith('_'):
                result.extend(PREFIX + 'tip_ink_' + ink for ink in INKS)
            elif identifier not in result:
                result.append(identifier)
    models = {path.stem for kind in ('core', 'handle', 'tip')
              for path in (ASSETS / 'models/item').glob(f'{PREFIX}{kind}_*.json')}
    icons = {path.stem for kind in ('core', 'handle', 'tip') for path in ITEMS.glob(f'{PREFIX}{kind}_*.png')}
    if len(result) != len(set(result)) or set(result) != models or set(result) != icons:
        raise ValueError('Registered wand parts, item models and icons must match exactly')
    return result


def quality(name):
    return 'rough' if name.endswith('_rough') else 'perfect' if name.endswith('_perfect') else 'standard'


def tint(template, color):
    image = template.copy()
    low, high = blend(color, '#18151d', .76), blend(color, '#f3f6ff', .62)
    middle = mix(color, 1)
    for y in range(16):
        for x in range(16):
            light, _, _, alpha = template.getpixel((x, y))
            left, right, amount = (low, middle, light / 150) if light <= 150 else (middle, high, (light - 150) / 105)
            image.putpixel((x, y), tuple(round(a + (b - a) * amount) for a, b in zip(left[:3], right[:3])) + (alpha,))
    return image


def glyph(draw, name, color, origin=(4, 4)):
    for stroke in GLYPHS[name]:
        draw.line([(x + origin[0], y + origin[1]) for x, y in stroke], fill=color)


def gradient_edge(image, color, shape, coordinates, **options):
    mask = Image.new('L', image.size)
    getattr(ImageDraw.Draw(mask), shape)(coordinates, fill=255, **options)
    gradient = Image.new('RGBA', image.size)
    low, middle, high = mix(color, .42), mix(color, 1), blend(color, '#f3f6ff', .72)
    for y in range(image.height):
        for x in range(image.width):
            position = (.25 * x + .75 * y) / 15
            first, second, amount = (high, middle, position * 2) if position <= .5 else (middle, low, (position - .5) * 2)
            gradient.putpixel((x, y), tuple(round(a + (b - a) * amount) for a, b in zip(first, second)))
    image.paste(gradient, (0, 0), mask)


def core_detail(draw, name, grade, color, image):
    shade, light = mix(color, .37), blend(color, '#f3f6ff', .65)
    late = name in ('perpetual', 'tidal', 'collapse', 'bulwark')
    if grade == 'rough':
        draw.line((3, 9, 4, 11, 6, 12, 9, 12), fill=shade)
        draw.line((10, 3, 9, 5, 10, 6), fill=shade)
    elif grade == 'perfect' or late:
        rim = ((5, 2), (10, 2), (13, 5), (13, 10), (10, 13), (5, 13), (2, 10), (2, 5), (5, 2))
        gradient_edge(image, color, 'line', rim)
        draw.line((4, 4, 4, 10, 6, 12, 10, 12), fill=shade)
        gradient_edge(image, color, 'point', ((5, 3), (11, 5), (10, 12), (3, 10)))
    else:
        gradient_edge(image, color, 'arc', (3, 3, 12, 12), start=35, end=300)
    draw.ellipse((4, 4, 10, 10), fill=mix(color, .48 if grade == 'rough' else .62))
    glyph(draw, name, light if grade != 'rough' else mix(color, 1.10))
    if name == 'collapse':
        draw.line((7, 2, 6, 4, 8, 7, 6, 11, 7, 13), fill='#ffbe8b')
    elif name == 'tidal':
        draw.arc((3, 3, 12, 11), 10, 155, fill='#94d7d7')
    elif name == 'bulwark':
        gradient_edge(image, color, 'line', (3, 4, 3, 9, 7, 12, 11, 9, 11, 4))


def handle_band(draw, position, color):
    draw.line((position, 14 - position, position + 2, 16 - position), fill=color)


def handle_detail(draw, name, grade, color, image):
    shade, light = mix(color, .42), blend(color, '#f3f6ff', .65)
    if name == 'wood':
        draw.line((2, 13, 5, 10, 6, 10, 9, 7, 9, 6, 12, 3), fill=shade)
        draw.point(((4, 12), (10, 5)), fill='#c29660')
    elif name == 'bone':
        for at in (3, 7, 11):
            handle_band(draw, at, '#9b8a65')
        draw.point(((5, 10), (9, 6)), fill='#554c3c')
    elif name in ('iron', 'gold', 'diamond', 'netherite'):
        for at in (2, 12):
            gradient_edge(image, color, 'line', (at, 14 - at, at + 2, 16 - at))
        if name == 'iron':
            draw.point(((4, 11), (11, 4)), fill=light)
        elif name == 'gold':
            for at in (5, 8):
                handle_band(draw, at, light)
        elif name == 'diamond':
            draw.line((4, 11, 7, 8, 7, 7, 10, 4), fill='#d4fff5')
            draw.point(((6, 10), (9, 7)), fill=shade)
        else:
            draw.line((4, 12, 6, 10, 5, 9, 8, 6, 10, 6), fill='#d19382')
    else:
        bands = (3, 11) if grade != 'perfect' else (2, 4, 10, 12)
        for at in bands:
            if grade == 'rough':
                handle_band(draw, at, shade)
            else:
                gradient_edge(image, color, 'line', (at, 14 - at, at + 2, 16 - at))
        if name == 'store':
            for at in (5, 8):
                handle_band(draw, at, '#b6dac3')
            draw.line((6, 9, 8, 7), fill='#e3efcb')
        elif name == 'cycle':
            draw.line((4, 11, 7, 10, 7, 7, 10, 6, 10, 3), fill='#b6efcf')
        elif name == 'san':
            draw.point(((6, 9), (8, 7), (10, 5)), fill=light)
            draw.line((6, 10, 9, 7), fill=shade)
        elif name == 'life':
            draw.line((4, 12, 5, 10, 7, 10, 7, 7, 10, 7, 10, 4), fill=light)
        elif name == 'dark':
            draw.line((5, 11, 7, 7, 9, 7, 10, 4), fill='#a18cbd')
            draw.point((8, 7), fill='#e6bbd9')
        elif name == 'white':
            gradient_edge(image, color, 'line', (5, 11, 5, 9, 8, 8, 8, 5, 11, 4))
        if grade == 'rough':
            draw.line((6, 9, 8, 9, 7, 7), fill=shade)
        elif grade == 'perfect':
            draw.line((3, 12, 11, 4), fill=light)
            gradient_edge(image, color, 'point', ((3, 13), (12, 4)))


def tip_detail(draw, name, color, image):
    shade, light = mix(color, .40), blend(color, '#f3f6ff', .68)
    if name.startswith(('ink_', 'source_')):
        family = name.split('_', 1)[1]
        stage = INK_STAGE[family] if name.startswith('ink_') else 3
        if stage < 3:
            draw.arc((2, 2, 12, 12), 0, 180, fill=shade)
        else:
            gradient_edge(image, color, 'arc', (2, 2, 12, 12), start=0, end=180)
            gradient_edge(image, color, 'line', (4, 4, 4, 10, 7, 12, 10, 10))
        if stage >= 5:
            gradient_edge(image, color, 'point', ((5, 2), (11, 5), (11, 9), (6, 12)))
        draw.ellipse((4, 4, 10, 10), fill=mix(color, .50))
        glyph(draw, family, light)
    elif name in GLYPHS:
        gradient_edge(image, color, 'arc', (2, 2, 12, 12), start=15, end=170)
        draw.ellipse((4, 4, 10, 10), fill=shade)
        glyph(draw, name, light)
    elif name in ('diamond', 'amethyst'):
        draw.polygon(((6, 3), (10, 5), (10, 9), (7, 11), (3, 8), (4, 5)), outline=light)
        draw.line((6, 3, 6, 7, 10, 5), fill=light)
        draw.line((3, 8, 6, 7, 7, 11), fill=shade)
        draw.line((6, 7, 10, 9), fill=mix(color, .7))
    elif name == 'echo':
        draw.line((4, 4, 7, 4, 9, 6, 9, 9, 7, 11), fill='#91e0d3')
        draw.line((3, 6, 6, 6, 7, 7, 7, 9, 5, 10), fill='#4f9da7')
        draw.point((5, 8), fill='#d0f8df')
    elif name == 'nether_star':
        draw.polygon(((7, 2), (8, 5), (12, 7), (8, 8), (7, 12), (6, 8), (2, 7), (6, 6)), fill='#e5dbac')
        draw.line((7, 3, 7, 10), fill='#fff7df')
        draw.line((4, 7, 10, 7), fill='#fff7df')
    elif name == 'netherite':
        gradient_edge(image, color, 'line', (3, 5, 5, 3, 9, 3, 11, 5))
        gradient_edge(image, color, 'line', (4, 9, 6, 11, 10, 10, 11, 8))
        draw.line((7, 4, 6, 6, 8, 8, 7, 10), fill='#7e565c')
    elif name == 'gold':
        gradient_edge(image, color, 'arc', (3, 3, 11, 11), start=20, end=285)
        draw.line((5, 5, 8, 5, 8, 8), fill=shade)
    elif name == 'iron':
        draw.line((3, 6, 11, 6), fill=shade)
        draw.point(((4, 5), (10, 5), (6, 10)), fill=light)
    elif name == 'copper':
        draw.line((3, 5, 6, 3, 9, 4), fill='#edbc87')
        draw.line((4, 10, 8, 11, 11, 8), fill='#705237')
        draw.point(((4, 7), (9, 6)), fill='#8d7050')
    else:
        raise ValueError(f'Unspecified tip design: {name}')


def render_icon(identifier, templates):
    kind, name = identifier.removeprefix(PREFIX).split('_', 1)
    grade = quality(name)
    family = name.removesuffix('_rough').removesuffix('_perfect')
    color = part_color(family)
    template = templates[kind]
    image = tint(template, color)
    if grade == 'rough':
        image = Image.blend(image, tint(template, '#958574'), .28)
    detail = ImageDraw.Draw(image)
    if kind == 'core':
        core_detail(detail, family, grade, color, image)
    elif kind == 'handle':
        handle_detail(detail, family, grade, color, image)
    else:
        tip_detail(detail, family, color, image)
    mask = template.getchannel('A')
    image.putalpha(mask)
    return image


def previews(identifiers):
    REVIEW.mkdir(parents=True, exist_ok=True)
    names = json.loads((ASSETS / 'lang/zh_cn.json').read_text('utf-8'))
    font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 13)
    for kind in ('core', 'handle', 'tip'):
        entries = [identifier for identifier in identifiers if identifier.startswith(PREFIX + kind + '_')]
        canvas = Image.new('RGB', (900, ((len(entries) + 5) // 6) * 160 + 35), '#20252e')
        draw = ImageDraw.Draw(canvas)
        draw.text((12, 8), '原图 / 新图（最近邻放大） · ' + {'core': '杖核', 'handle': '杖柄', 'tip': '杖端'}[kind], font=font, fill='#eee7d7')
        for index, identifier in enumerate(entries):
            x, y = (index % 6) * 150, (index // 6) * 160 + 35
            draw.rectangle((x + 3, y + 2, x + 146, y + 154), outline='#444b59')
            old = ROOT / 'build/visual-review/wand-parts-before' / (identifier + '.png')
            if old.exists():
                with Image.open(old) as source:
                    before = source.convert('RGBA').resize((32, 32), Image.Resampling.NEAREST)
                canvas.paste(before, (x + 7, y + 70), before)
            with Image.open(ITEMS / (identifier + '.png')) as source:
                image = source.convert('RGBA')
            enlarged = image.resize((96, 96), Image.Resampling.NEAREST)
            canvas.paste(enlarged, (x + 46, y + 8), enlarged)
            canvas.paste(image, (x + 125, y + 110), image)
            label = names['item.forbidden_hex.' + identifier]
            draw.text((x + 7, y + 130), label, font=font, fill='#eee7d7')
        canvas.save(REVIEW / f'{kind}.png')


def generate_icons(check=False, preview=False):
    identifiers, templates = registered_parts(), load_templates()
    hashes = set()
    for identifier in identifiers:
        image = render_icon(identifier, templates)
        checksum = hashlib.sha256(image.tobytes()).hexdigest()
        if checksum in hashes:
            raise ValueError(f'Duplicate wand icon: {identifier}')
        hashes.add(checksum)
        path = ITEMS / (identifier + '.png')
        model = json.loads((ASSETS / 'models/item' / (identifier + '.json')).read_text('utf-8'))
        if model.get('parent') != 'minecraft:item/generated' or model.get('textures', {}).get('layer0') != 'forbidden_hex:item/' + identifier:
            raise ValueError(f'Unexpected part model: {identifier}')
        with Image.open(path) as source:
            actual = source.convert('RGBA')
        if check:
            if actual.size != (16, 16) or actual.tobytes() != image.tobytes():
                raise ValueError(f'Wand icon differs from authored design: {identifier}')
        else:
            image.save(path)
    if preview:
        previews(identifiers)
    print(f'{"Verified" if check else "Authored"} {len(identifiers)} unique 16x16 wand icons; user template alpha and item IDs retained; assembled wand materials untouched.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--previews', action='store_true')
    arguments = parser.parse_args()
    generate_icons(arguments.check, arguments.previews)
