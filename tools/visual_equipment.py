import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex'
TEXTURES = ASSETS / 'textures/block'
MODELS = ASSETS / 'models/block'


def texture(name, image):
    image.save(TEXTURES / f'forbidden_hex_{name}.png')


def material(base, kind):
    image = Image.new('RGBA', (32, 32), base)
    d = ImageDraw.Draw(image)
    if kind == 'iron':
        for y in (3, 13, 23):
            d.line((2, y, 29, y), fill='#53524d')
            d.line((4, y + 1, 27, y + 1), fill='#363a39')
        for y in (2, 27):
            for x in (3, 28):
                d.rectangle((x, y, x + 1, y + 1), fill='#a1926d')
                d.point((x, y), fill='#c4b58b')
        for x, y in ((8, 8), (23, 17), (13, 25)):
            d.line((x, y, x + 3, y - 1), fill='#62635a')
    elif kind == 'wood':
        for x in (0, 8, 17, 25, 31):
            d.line((x, 0, x, 31), fill='#3c322b')
            if x < 31:
                d.line((x + 1, 1, x + 1, 30), fill='#8b7350')
        for x, y in ((4, 8), (13, 21), (21, 13), (28, 27)):
            d.line((x, y - 3, x + 1, y, x, y + 3), fill='#44382d')
            d.point((x + 2, y), fill='#ac9165')
    return image


def face_uv(a, b, face):
    x, y, z = a
    xx, yy, zz = b
    return {
        'north': [16 - xx, 16 - yy, 16 - x, 16 - y],
        'south': [x, 16 - yy, xx, 16 - y],
        'west': [z, 16 - yy, zz, 16 - y],
        'east': [16 - zz, 16 - yy, 16 - z, 16 - y],
        'up': [x, z, xx, zz],
        'down': [x, 16 - zz, xx, 16 - z],
    }[face]


def cuboid(a, b, side='side', top=None, front=None, bottom=None):
    faces = {}
    for name in ('north', 'south', 'west', 'east', 'up', 'down'):
        key = (top or side) if name == 'up' else (bottom or side) if name == 'down' else (front or side) if name == 'north' else side
        faces[name] = {'texture': '#' + key, 'uv': face_uv(a, b, name)}
    return {'from': list(a), 'to': list(b), 'faces': faces}


def model(name, textures, elements, **extra):
    value = {'parent': 'minecraft:block/block', 'textures': {key: f'forbidden_hex:block/forbidden_hex_{texture_name}' for key, texture_name in textures.items()}, 'elements': elements, **extra}
    (MODELS / f'forbidden_hex_{name}.json').write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def oven():
    side = material('#434543', 'iron')
    d = ImageDraw.Draw(side)
    for y in (7, 19):
        d.rectangle((6, y, 25, y + 7), outline='#292f2e')
        d.line((7, y, 24, y), fill='#787663')
    texture('oven_side', side)
    top = material('#494b45', 'iron')
    d = ImageDraw.Draw(top)
    for inset in (7, 10):
        d.rectangle((inset, inset, 31 - inset, 31 - inset), outline='#2e3431')
    d.line((9, 9, 22, 9, 22, 22), fill='#817c60')
    texture('oven_top', top)
    for lit in (False, True):
        front = material('#434543', 'iron')
        d = ImageDraw.Draw(front)
        d.rectangle((5, 7, 26, 26), fill='#262c2b', outline='#8d8060', width=2)
        d.line((7, 8, 24, 8), fill='#c2ab7c')
        d.rectangle((8, 11, 23, 23), fill='#161e1f')
        if lit:
            d.polygon(((9, 23), (9, 19), (12, 20), (13, 15), (16, 19), (18, 14), (20, 19), (22, 17), (22, 23)), fill='#bb6135')
            d.polygon(((11, 23), (13, 20), (15, 22), (18, 18), (20, 23)), fill='#e8b363')
        for x in (10, 14, 18, 22):
            d.line((x, 12, x, 23), fill='#55584c')
            d.line((x - 1, 12, x - 1, 22), fill='#242d2b')
        d.rectangle((23, 15, 26, 17), fill='#b6a374')
        d.line((8, 5, 23, 5), fill='#292e2c')
        texture('oven_front_on' if lit else 'oven_front', front)
    trim = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(trim)
    for x in (1, 30):
        d.line((x, 1, x, 30), fill='#827453')
    for y in (1, 30):
        d.line((1, y, 30, y), fill='#827453')
    for x, y in ((3, 3), (27, 3), (3, 27), (27, 27)):
        d.rectangle((x, y, x + 1, y + 1), fill='#c2ac7d')
    texture('oven_uv', trim)
    base = cuboid((0, 0, 0), (16, 16, 16), top='top', front='front')
    overlay = cuboid((-0.015, -0.015, -0.015), (16.015, 16.015, 16.015), side='trim')
    for face in overlay['faces'].values():
        face['uv'] = [0, 0, 16, 16]
    model('witch_oven_textured', {'side': 'oven_side', 'top': 'oven_top', 'front': 'oven_front', 'trim': 'oven_uv', 'particle': 'oven_side'}, [base, overlay], render_type='minecraft:cutout')


def crucible():
    body = material('#414a43', 'iron')
    d = ImageDraw.Draw(body)
    for y in (5, 26):
        d.line((0, y, 31, y), fill='#8d8b65')
        d.line((0, y + 1, 31, y + 1), fill='#292f2b')
    d.rectangle((13, 11, 18, 18), outline='#666e55')
    texture('crucible_body', body)
    inner = material('#303c35', 'iron')
    d = ImageDraw.Draw(inner)
    d.rectangle((4, 4, 27, 27), outline='#6b7657')
    texture('crucible_inner', inner)
    rim = Image.new('RGBA', (32, 32), '#777956')
    d = ImageDraw.Draw(rim)
    d.rectangle((0, 0, 31, 31), outline='#a7a27a', width=1)
    d.rectangle((4, 4, 27, 27), outline='#353f32', width=1)
    for x, y in ((6, 1), (20, 1), (1, 9), (29, 22), (10, 29)):
        d.line((x, y, x + 1, y), fill='#c6b88d')
    texture('crucible_uv', rim)
    liquid = Image.new('RGBA', (32, 32), '#c8cec3')
    d = ImageDraw.Draw(liquid)
    d.arc((6, 7, 25, 24), 20, 160, fill='#e3e7dd', width=1)
    d.arc((9, 10, 22, 22), 190, 300, fill='#a8b5ab', width=1)
    for box in ((9, 7, 11, 9), (22, 17, 24, 19)):
        d.ellipse(box, fill='#e1e5db')
    texture('crucible_liquid', liquid)
    walls = [cuboid((0, 0, 0), (16, 4, 16), top='inner'),
             cuboid((0, 4, 0), (2, 16, 16), top='rim'),
             cuboid((14, 4, 0), (16, 16, 16), top='rim'),
             cuboid((2, 4, 0), (14, 16, 2), top='rim'),
             cuboid((2, 4, 14), (14, 16, 16), top='rim')]
    walls[1]['faces']['east']['texture'] = '#inner'
    walls[2]['faces']['west']['texture'] = '#inner'
    walls[3]['faces']['south']['texture'] = '#inner'
    walls[4]['faces']['north']['texture'] = '#inner'
    textures = {'side': 'crucible_body', 'rim': 'crucible_uv', 'inner': 'crucible_inner', 'liquid': 'crucible_liquid', 'particle': 'crucible_body'}
    for level in range(4):
        elements = list(walls)
        if level:
            height = 4 + level * 3
            elements.append({'from': [2, height, 2], 'to': [14, height, 14], 'faces': {'up': {'uv': [2, 2, 14, 14], 'texture': '#liquid', 'tintindex': 0}}})
        model('crucible_textured_' + str(level), textures, elements, ambientocclusion=False)


def inscription():
    top = material('#66583e', 'wood')
    d = ImageDraw.Draw(top)
    d.rectangle((2, 2, 29, 29), outline='#2e332c', width=2)
    d.rectangle((4, 4, 27, 27), outline='#b5a071')
    d.ellipse((8, 8, 23, 23), outline='#b8a97f')
    d.line((15, 7, 15, 24), fill='#b8a97f')
    d.line((9, 16, 22, 16), fill='#b8a97f')
    d.polygon(((15, 11), (20, 19), (11, 19)), outline='#d0ba86')
    d.rectangle((24, 5, 26, 8), fill='#363f35')
    texture('inscription_table_top', top)
    texture('inscription_table_side', material('#645239', 'wood'))
    texture('inscription_table_bottom', material('#4b4435', 'wood'))
    trim = Image.new('RGBA', (32, 32), '#4c5147')
    d = ImageDraw.Draw(trim)
    d.line((0, 2, 31, 2), fill='#aca17b')
    for x in (3, 13, 23):
        d.rectangle((x, 4, x + 1, 5), fill='#c2af80')
    texture('inscription_table_uv', trim)
    elements = [cuboid((0, 13, 0), (16, 16, 16), top='top', bottom='bottom'),
                cuboid((1, 11, 1), (15, 13, 15), side='trim'),
                cuboid((3, 2, 3), (13, 11, 13)),
                cuboid((1, 0, 1), (15, 2, 15), side='bottom')]
    model('inscription_table', {'side': 'inscription_table_side', 'top': 'inscription_table_top', 'bottom': 'inscription_table_bottom', 'trim': 'inscription_table_uv', 'particle': 'inscription_table_side'}, elements)


if __name__ == '__main__':
    oven()
    crucible()
    inscription()
    print('Authored 13 device textures at 32x32; metric UVs for oven, four liquid levels and inscription table.')
