from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex/textures'
PAPER = '#e1d3ac'
INK = '#494638'
BRASS = '#ac9061'


def emblems():
    for name in ('quiet_thought', 'breath_of_dark', 'the_watchers_name', 'cold_sympathy', 'blood_offering'):
        image = Image.new('RGBA', (32, 32))
        d = ImageDraw.Draw(image)
        d.polygon(((6, 3), (25, 3), (28, 6), (28, 25), (25, 28), (6, 28), (3, 25), (3, 6)), fill=PAPER)
        d.rectangle((7, 5, 24, 26), outline='#c1ae83')
        for x, y in ((5, 8), (25, 23), (8, 26)):
            d.point((x, y), fill='#ac956f')
        if name == 'quiet_thought':
            d.arc((9, 7, 22, 20), 125, 405, fill=INK, width=2)
            d.line((13, 18, 13, 23, 19, 23, 19, 18), fill=INK, width=2)
            d.line((7, 13, 10, 13), fill='#7c9a9c')
            d.line((22, 13, 25, 13), fill='#7c9a9c')
            d.line((14, 11, 17, 11), fill='#7c9a9c')
            d.point((15, 8), fill='#596b68')
        elif name == 'breath_of_dark':
            d.polygon(((17, 6), (11, 10), (10, 17), (14, 23), (20, 24), (23, 20), (17, 20), (14, 15), (15, 11), (20, 8)), fill='#5c535f')
            d.line((9, 23, 13, 21), fill='#899c92')
            d.line((20, 12, 24, 15, 21, 17), fill='#8a7993')
        elif name == 'the_watchers_name':
            d.polygon(((6, 15), (11, 11), (20, 11), (25, 15), (20, 19), (11, 19)), outline=INK)
            d.ellipse((12, 11, 19, 19), fill='#7d6890')
            d.line((15, 12, 15, 18), fill='#322f36', width=2)
            d.point((14, 12), fill='#c5bcaa')
            for x in (9, 15, 22):
                d.line((x, 6, x, 8), fill=BRASS)
                d.line((x, 22, x, 25), fill=INK)
        elif name == 'cold_sympathy':
            d.line((15, 7, 15, 24), fill='#728e91', width=2)
            d.line((7, 11, 23, 21), fill='#728e91', width=2)
            d.line((7, 21, 23, 11), fill='#728e91', width=2)
            for x, y in ((15, 7), (7, 11), (23, 11), (7, 21), (23, 21), (15, 24)):
                d.line((x - 2, y, x, y + 2, x + 2, y), fill=INK)
            d.rectangle((14, 14, 17, 17), fill='#bf6f69')
        else:
            d.polygon(((16, 6), (11, 13), (10, 18), (13, 22), (19, 22), (22, 18), (21, 14)), fill='#984d4e')
            d.line((15, 10, 13, 15, 13, 17), fill='#cb8b71')
            d.arc((6, 16, 25, 26), 5, 175, fill=INK)
            d.line((9, 25, 23, 25), fill=BRASS)
        image.save(ASSETS / 'gui/codex/emblem' / (name + '.png'))


def effects():
    for name in ('doom', 'sanity_restore', 'unnameable'):
        image = Image.new('RGBA', (18, 18))
        d = ImageDraw.Draw(image)
        if name == 'doom':
            d.polygon(((5, 3), (12, 3), (14, 6), (13, 10), (11, 12), (11, 15), (6, 15), (6, 12), (3, 9), (3, 6)), fill='#b4b09e')
            d.rectangle((5, 7, 7, 9), fill='#383637')
            d.rectangle((10, 7, 12, 9), fill='#383637')
            d.line((8, 10, 8, 12), fill='#716354')
            d.line((7, 14, 10, 14), fill='#5b4748')
            d.point((4, 3), fill='#9c5456')
        elif name == 'sanity_restore':
            d.arc((3, 3, 14, 14), 35, 325, fill='#87a294', width=2)
            d.polygon(((12, 1), (16, 5), (11, 6)), fill='#c7c19d')
            d.line((7, 13, 7, 7, 10, 4), fill='#c7c19d')
            d.polygon(((7, 7), (4, 4), (6, 4), (8, 6)), fill='#83aa83')
            d.polygon(((8, 10), (11, 6), (12, 7), (10, 10)), fill='#a7c19d')
        else:
            d.polygon(((1, 8), (5, 4), (12, 4), (16, 8), (12, 12), (5, 12)), outline='#9c799e')
            d.rectangle((7, 5, 10, 10), fill='#635878')
            d.line((8, 5, 8, 10), fill='#d8c7ba')
            d.line((2, 14, 6, 14, 8, 16, 12, 14, 16, 14), fill='#78658b')
            d.point((3, 2), fill='#a18f9c')
            d.point((14, 2), fill='#a18f9c')
        image.save(ASSETS / 'mob_effect' / (name + '.png'))


if __name__ == '__main__':
    emblems()
    effects()
    print('Authored five 32x32 knowledge emblems and three 18x18 effect icons; book slices, font and boss atlases preserved.')
