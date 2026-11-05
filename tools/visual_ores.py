from __future__ import annotations

import argparse
import io
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/forbidden_hex/textures'
REVIEW = ROOT / 'build/visual-review/ores'
PALETTES = {
    'cinnabar': ('#542c32', '#8f303b', '#ba3d42', '#e65b50', '#ff987b'),
    'sulfur': ('#736134', '#a88c38', '#d3bb45', '#f2dc6b', '#fff3a3'),
    'silver': ('#505d72', '#8297ad', '#b4c6d5', '#dce9ee', '#ffffff'),
    'magic_stone': ('#123b51', '#146d89', '#20b3c6', '#75e9eb', '#d1ffff'),
}
CLUSTERS = {
    'cinnabar': (((2, 1), (3, 1), (4, 2), (4, 3), (3, 3), (2, 2)),
                 ((11, 2), (12, 2), (13, 3), (12, 4), (11, 4)),
                 ((6, 6), (7, 6), (8, 7), (8, 8), (7, 9), (6, 8), (5, 7)),
                 ((1, 10), (2, 10), (3, 11), (3, 12), (2, 12), (1, 11)),
                 ((11, 11), (12, 10), (13, 11), (13, 12), (12, 13), (11, 12))),
    'sulfur': (((2, 2), (3, 2), (4, 2), (4, 3), (3, 4), (2, 3)),
               ((10, 1), (11, 1), (12, 2), (11, 3), (10, 3)),
               ((7, 6), (8, 6), (9, 7), (8, 8), (7, 8), (6, 7)),
               ((1, 9), (2, 9), (3, 10), (4, 10), (3, 11), (2, 11)),
               ((10, 12), (11, 11), (12, 11), (13, 12), (12, 13), (11, 13))),
    'silver': (((2, 1), (3, 2), (3, 3), (4, 4)),
               ((11, 2), (12, 2), (12, 3), (13, 4)),
               ((6, 5), (7, 6), (7, 7), (8, 8), (9, 8)),
               ((1, 9), (2, 10), (3, 10), (3, 11)),
               ((10, 11), (11, 12), (12, 12), (12, 13))),
    'magic_stone': (((3, 1), (2, 2), (3, 2), (4, 2), (3, 3), (3, 4)),
                    ((11, 2), (10, 3), (11, 3), (12, 3), (11, 4), (11, 5)),
                    ((7, 6), (6, 7), (7, 7), (8, 7), (7, 8), (7, 9)),
                    ((2, 10), (1, 11), (2, 11), (3, 11), (2, 12), (2, 13)),
                    ((12, 10), (11, 11), (12, 11), (13, 11), (12, 12), (12, 13))),
}


def vanilla(archive: zipfile.ZipFile, name: str) -> Image.Image:
    with archive.open('assets/minecraft/textures/' + name + '.png') as source:
        image = Image.open(io.BytesIO(source.read())).convert('RGBA')
    assert image.size == (16, 16), name
    return image


def recolor(image: Image.Image, low: tuple[int, ...], high: tuple[int, ...]) -> Image.Image:
    image = image.copy()
    visible = [image.getpixel((x, y)) for y in range(16) for x in range(16)
               if image.getpixel((x, y))[3]]
    levels = [round(0.299 * r + 0.587 * g + 0.114 * b) for r, g, b, _ in visible]
    minimum, maximum = min(levels), max(levels)
    for y in range(16):
        for x in range(16):
            r, g, b, a = image.getpixel((x, y))
            if not a:
                continue
            level = round(0.299 * r + 0.587 * g + 0.114 * b)
            fraction = (level - minimum) / max(1, maximum - minimum)
            image.putpixel((x, y), tuple(round(lo + (hi - lo) * fraction)
                                       for lo, hi in zip(low, high)) + (a,))
    return image


def ore(base: Image.Image, kind: str) -> Image.Image:
    image = base.copy()
    colors = PALETTES[kind]
    drawing = ImageDraw.Draw(image)
    mask = {point for cluster in CLUSTERS[kind] for point in cluster}
    for x, y in mask:
        for dx, dy in ((0, 1), (1, 0)):
            if (x + dx, y + dy) not in mask:
                drawing.point((x + dx, y + dy), colors[0])
    for cluster in CLUSTERS[kind]:
        top = min(y for _, y in cluster)
        for x, y in cluster:
            index = 3 if y == top else 1 if (x + y) % 4 == 0 else 2
            drawing.point((x, y), colors[index])
        drawing.point(cluster[0], colors[4])
    assert sum(image.getpixel((x, y)) == base.getpixel((x, y))
               for y in range(16) for x in range(16)) >= 140
    return image


def fragment(kind: str) -> Image.Image:
    image = Image.new('RGBA', (16, 16))
    drawing = ImageDraw.Draw(image)
    dark, shadow, mid, light, glint = PALETTES[kind]
    if kind == 'cinnabar':
        drawing.polygon(((2, 7), (4, 4), (7, 5), (9, 3), (12, 5), (14, 9),
                         (12, 12), (8, 13), (5, 11), (2, 11)), fill=dark)
        drawing.polygon(((3, 7), (5, 5), (7, 6), (9, 4), (12, 6), (13, 9),
                         (11, 11), (7, 12), (5, 10), (3, 10)), fill=mid)
        drawing.line(((4, 7), (6, 6), (7, 8), (9, 5), (11, 6)), fill=light)
        drawing.line(((6, 10), (8, 9), (10, 10), (12, 8)), fill=shadow)
        drawing.point((5, 6), glint)
        drawing.polygon(((1, 3), (2, 2), (4, 3), (3, 4), (1, 4)), fill=dark)
        drawing.line((2, 3, 3, 3), fill=light)
        drawing.line((12, 13, 13, 13), fill=light)
    else:
        drawing.polygon(((2, 8), (4, 6), (7, 6), (9, 3), (12, 4), (14, 8),
                         (12, 12), (8, 13), (3, 12), (1, 10)), fill=dark)
        drawing.polygon(((3, 8), (5, 7), (8, 7), (10, 4), (12, 5), (13, 8),
                         (11, 11), (7, 12), (3, 11), (2, 10)), fill=mid)
        drawing.line(((3, 9), (5, 8), (8, 8), (10, 5), (11, 5)), fill=light)
        drawing.line(((5, 11), (7, 10), (9, 11), (11, 9)), fill=shadow)
        drawing.point((10, 5), glint)
        drawing.polygon(((2, 3), (3, 2), (5, 3), (4, 5), (2, 4)), fill=dark)
        drawing.line((3, 3, 4, 3), fill=light)
        drawing.point((13, 13), light)
    return image


def magic_crystal() -> Image.Image:
    image = Image.new('RGBA', (16, 16))
    drawing = ImageDraw.Draw(image)
    dark, shadow, mid, light, glint = PALETTES['magic_stone']
    drawing.polygon(((7, 1), (11, 4), (11, 9), (8, 14), (4, 12), (3, 6)), fill=dark)
    drawing.polygon(((7, 2), (10, 4), (7, 6), (4, 6)), fill=light)
    drawing.polygon(((4, 7), (7, 7), (7, 12), (5, 11)), fill=mid)
    drawing.polygon(((8, 7), (10, 5), (10, 9), (8, 12)), fill=shadow)
    drawing.line(((7, 3), (7, 6), (6, 9)), fill=glint)
    drawing.polygon(((12, 8), (14, 9), (13, 12), (10, 14), (10, 11)), fill=dark)
    drawing.polygon(((12, 9), (13, 9), (12, 12), (11, 12)), fill=mid)
    drawing.point((12, 9), light)
    return image


def magic_block() -> Image.Image:
    image = Image.new('RGBA', (16, 16), PALETTES['magic_stone'][0])
    drawing = ImageDraw.Draw(image)
    dark, shadow, mid, light, glint = PALETTES['magic_stone']
    for y in (0, 8):
        for x in (0, 8):
            drawing.polygon(((x, y + 3), (x + 3, y), (x + 7, y + 2), (x + 7, y + 6),
                             (x + 4, y + 7), (x, y + 5)), fill=shadow)
            drawing.polygon(((x + 1, y + 3), (x + 3, y + 1), (x + 6, y + 2),
                             (x + 4, y + 4), (x + 1, y + 4)), fill=mid)
            drawing.line(((x + 1, y + 3), (x + 3, y + 1), (x + 5, y + 2)), fill=light)
            drawing.line(((x + 5, y + 4), (x + 6, y + 3), (x + 6, y + 5)), fill=mid)
            drawing.point((x + 3, y + 1), glint)
            drawing.line(((x + 2, y + 6), (x + 4, y + 6)), fill=dark)
    return image


def generate_magic(client_jar: Path) -> None:
    with zipfile.ZipFile(client_jar) as archive:
        mineral = ore(vanilla(archive, 'block/deepslate'), 'magic_stone')
    images = [('深层魔石矿石', mineral), ('魔石', magic_crystal()), ('魔石块', magic_block())]
    for (label, image), folder, name in zip(images, ('block', 'item', 'block'),
                                          ('deepslate_magic_stone_ore', 'magic_stone', 'magic_stone_block')):
        assert image.size == (16, 16), label
        target = TEXTURES / folder / ('forbidden_hex_' + name + '.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        image.save(target)
    preview(images, '魔石 / 16×16 蓝青晶体', 'magic_stone.png')
    print('Generated 3 magic stone textures; all 16x16. Preview:', REVIEW / 'magic_stone.png')


def preview(images: list[tuple[str, Image.Image]],
            title: str = '朱砂 · 硫磺 · 银  /  16×16 原版像素风', filename: str = 'ores.png') -> None:
    font_path = Path('C:/Windows/Fonts/msyh.ttc')
    font = ImageFont.truetype(str(font_path), 14) if font_path.exists() else ImageFont.load_default()
    canvas = Image.new('RGB', (864, 568), '#222830')
    drawing = ImageDraw.Draw(canvas)
    drawing.text((14, 10), title, font=font, fill='#ecf1f7')
    for index, (name, image) in enumerate(images):
        x, y = index % 5 * 172, 46 + index // 5 * 172
        drawing.rectangle((x + 4, y + 2, x + 166, y + 164), outline='#485363')
        large = image.resize((96, 96), Image.Resampling.NEAREST)
        canvas.paste(large, (x + 32, y + 12), large)
        canvas.paste(image, (x + 140, y + 104), image)
        drawing.text((x + 12, y + 130), name, font=font, fill='#ecf1f7')
    REVIEW.mkdir(parents=True, exist_ok=True)
    canvas.save(REVIEW / filename)


def generate(client_jar: Path) -> None:
    block = TEXTURES / 'block'
    item = TEXTURES / 'item'
    block.mkdir(parents=True, exist_ok=True)
    item.mkdir(parents=True, exist_ok=True)
    images = []
    with zipfile.ZipFile(client_jar) as archive:
        for kind, label in (('cinnabar', '朱砂矿石'), ('sulfur', '硫磺矿石'), ('silver', '银矿石')):
            for prefix, base in (('', 'stone'), ('deepslate_', 'deepslate')):
                image = ore(vanilla(archive, 'block/' + base), kind)
                image.save(block / ('forbidden_hex_' + prefix + kind + '_ore.png'))
                images.append((('深层' if prefix else '') + label, image))
        for name, source, low, high, label in (
                ('silver_block', 'block/gold_block', (102, 121, 143), (241, 250, 255), '银块'),
                ('raw_silver_block', 'block/raw_gold_block', (59, 67, 81), (223, 237, 243), '粗银块'),
                ('raw_silver', 'item/raw_gold', (65, 73, 88), (229, 242, 250), '粗银'),
                ('silver_ingot', 'item/gold_ingot', (76, 91, 112), (246, 253, 255), '银锭'),
                ('silver_nugget', 'item/gold_nugget', (68, 82, 102), (242, 251, 255), '银粒')):
            image = recolor(vanilla(archive, source), low, high)
            folder = block if source.startswith('block/') else item
            image.save(folder / ('forbidden_hex_' + name + '.png'))
            images.append((label, image))
    for kind, label in (('cinnabar', '朱砂碎矿'), ('sulfur', '硫磺碎矿')):
        image = fragment(kind)
        image.save(item / ('forbidden_hex_' + kind + '_fragment.png'))
        images.append((label, image))
    assert len(images) == 13 and all(image.size == (16, 16) for _, image in images)
    preview(images)
    print('Generated 13 ore/material textures; all 16x16. Preview:', REVIEW / 'ores.png')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--client-jar', type=Path, required=True)
    parser.add_argument('--magic-only', action='store_true')
    args = parser.parse_args()
    if args.magic_only:
        generate_magic(args.client_jar)
    else:
        generate(args.client_jar)
