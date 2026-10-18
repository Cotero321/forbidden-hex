from pathlib import Path
import math

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/forbidden_hex/textures'

PALETTES = {
    'mandrake': ('#263c27', '#506f39', '#8d9a50', '#b5ae6b', '#775135'),
    'henbane': ('#353a29', '#697844', '#a0a36b', '#d9c786', '#694d70'),
    'belladonna': ('#263829', '#4c6741', '#7d9154', '#917495', '#322740'),
    'datura': ('#2d3e30', '#617852', '#96a076', '#e1dbb4', '#b3a081'),
    'aconite': ('#2c403b', '#49775e', '#78a37b', '#6a608c', '#aaa0c0'),
    'foxglove': ('#303f31', '#60794c', '#8a9b61', '#a56888', '#deb4b5'),
    'hellroot': ('#372c30', '#654135', '#a7643e', '#cb8e52', '#7b3033'),
    'colchicum': ('#324435', '#637e4b', '#a0a36b', '#ae87a1', '#e0bcc5'),
    'yew': ('#23382e', '#3c5e42', '#698152', '#a94d43', '#d58a59'),
    'ergot': ('#4b4834', '#8b8752', '#c2b877', '#484053', '#756080'),
    'parasitic_vine': ('#293e30', '#456c48', '#74945b', '#985455', '#be8883'),
    'ember_moss': ('#3c3e2b', '#727649', '#a09a5c', '#b86d3b', '#dfaa61'),
    'glint_weed': ('#304541', '#547a68', '#88aa8b', '#aab9a2', '#dde0ba'),
}

PATTERNS = {
    'mandrake': ['.......F........','......LFL.......','...L..LFL..L....','...LHL.F.LHL....','....LHLFLHL.....','..L..LHFHL..L...','..LHL.LF.LHL....','...LHLLFLLH.....','.....LHFHL......','...LLL.F.LLL....','....LL.F.LL.....','.......F........','......RRR.......','.....RR.RR......','....RR...RR.....','....R.....R.....'],
    'henbane': ['.......F........','......HFF.......','...LL..F..LL....','....LL.F.LL.....','.....LLFLL......','.......F.HFF....','..LHL..F..F.....','...LLL.F.LL.....','.....LLFLL......','...HFF.F........','.....F.F..LHL...','.....LLFLLL.....','......LFLL......','.......F........','......FFF.......','................'],
    'belladonna': ['................','.......F........','....LL.F.LL.....','...LHLLFLLHL....','....LL.F.LL.....','.......F........','..LLL..F..LLL...','..LHL.LF.LHL....','...LLLFFFLL.....','.....B.F.B......','....B..F..B.....','.......F........','......LFL.......','.....LLFLL......','.......F........','................'],
    'datura': ['................','..........FFF...','.......F..FHF...','...FFF.F...F....','...FHF.F..LF....','....F..F.LL.....','....LFLFLL......','..L.LLFFF.LL....','..LHL..F.LHL....','...LLL.FLLL.....','.....LLF........','......LFL.......','.....LLFLL......','.......F........','.......F........','................'],
    'aconite': ['.......F........','......FHF.......','......FF........','........F.......','......FFHF......','......FFF.......','........F.......','.....FHF........','.....FFF........','.......F........','...L.LLFL.L.....','..LLHL.F.LHLL...','...LLL.F.LLL....','.....LLFLL......','.......F........','................'],
    'foxglove': ['.......F........','......HFF.......','......BFF.......','.......FFHF.....','.......FFBF.....','.....HFFF.......','.....BFFF.......','.......FFHF.....','.......FFBF.....','.....HFFF.......','.....BFFF.......','.......F........','...LHL.F.LHL....','....LLLFLLL.....','......FFF.......','................'],
    'hellroot': ['.......B........','....B..B..B.....','.....B.B.B......','......BFB.......','...B...F...B....','....BBBFB BB....'.replace(' ',''),'.......F........','.....L.F.L......','......LFL.......','.......F........','......RRR.......','.....RRHRR......','....RRRRRRR.....','...RRR.R.RRR....','...RR..R..RR....','..RR...R...RR...'],
    'colchicum': ['................','................','......F.F.......','.....FHFHF......','......FFF.......','.......F........','...F.F.F........','..FHFHFF........','...FFF.F........','....F..F........','....F..F.L......','....F.LFLL......','....F.LFL.......','.....LFFL.......','......FF........','................'],
    'yew': ['................','......H.........','.....LFL........','....LLFLL.......','.....LFL........','...LLLBFLLL.....','....LLFLL.......','..LLLBFLLL......','....LLFLL.......','...LLLFLLL......','.....LFB........','....LLFLL.......','......F.........','......F.........','.....FF.........','................'],
    'ergot': ['......H.........','.....HLH........','......HH........','.....HLH........','......HB........','.....HLB........','......HB........','.....HLH........','......HF........','.......F........','.....L.F........','......LF........','.......FL.......','.......F........','......LF........','................'],
    'parasitic_vine': ['.........LL.....','........LHL.....','........FL......','......LFF.......','.....LHLF.......','......L.F.......','........FFF.....','........F.LHL...','......B.F..LL...','.....BLFF.......','.....LHLF.......','......L.F.......','......FFF.......','.....FF.........','....FL..........','...LL...........'],
    'ember_moss': ['................','................','................','................','................','................','................','................','.....H.....H....','..H..F...H.F....','..F..F...F.F....','..FLHLHLLFLHL...','..LHLHLHLHLHL...','.LLHLLLHLHLLHL..','.LLLLLHLHLLLLL..','................'],
    'glint_weed': ['................','.......H........','......HFH.......','.......H........','.......F........','...H...F...H....','..HFH..F..HFH...','...H...F...H....','...F...F...F....','...F.L.F.L.F....','....FL.F.LF.....','.....FLFLF......','......FFF.......','.....LLFLL......','.......F........','................'],
}


def save(image, folder, name):
    destination = ASSETS / folder / f'forbidden_hex_{name}.png'
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination)


def sprite(pattern, colors):
    image = Image.new('RGBA', (16, 16))
    palette = dict(zip('FLHBR', colors))
    for y, row in enumerate(pattern):
        for x, token in enumerate(row[:16]):
            if token in palette:
                image.putpixel((x, y), tuple(bytes.fromhex(palette[token][1:])) + (255,))
    return image


def highlight(image):
    overlay = Image.new('RGBA', image.size)
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a and (x + 2 * y) % 7 == 0 and r + g + b > 220:
                overlay.putpixel((x, y), (min(255, r + 17), min(255, g + 17), min(255, b + 12), 255))
    return overlay


def crops():
    for name, pattern in PATTERNS.items():
        mature = sprite(pattern, PALETTES[name])
        for stage, height in enumerate((5, 8, 12, 16)):
            if stage == 3:
                image = mature.copy()
            else:
                width = (7, 10, 13)[stage]
                scaled = mature.resize((width, height), Image.Resampling.NEAREST)
                image = Image.new('RGBA', (16, 16))
                image.paste(scaled, ((16 - width) // 2, 16 - height))
                draw = ImageDraw.Draw(image)
                if name not in ('ember_moss', 'ergot', 'parasitic_vine'):
                    draw.line((7, 15 - height // 2, 7, 15), fill=PALETTES[name][0])
                if stage == 0:
                    for y in range(16):
                        for x in range(16):
                            r, g, b, a = image.getpixel((x, y))
                            if a and r > g * 1.12:
                                image.putpixel((x, y), tuple(bytes.fromhex(PALETTES[name][1][1:])) + (255,))
            save(image, 'block', f'{name}_crop_{stage}')
            save(highlight(image), 'block', f'{name}_crop_{stage}_uv')
        item_name = 'mandrake_root' if name == 'mandrake' else name
        if name == 'mandrake':
            item = sprite(['.......L........','....L.LFL.L.....','.....LHFHL......','......LF........','......RRR.......','.....RHRRR......','.....RRRRR......','....RRRRRRR.....','...RR.RRR.RR....','...R..RRR..R....','......R.R.......','.....RR.RR......','.....R...R......','....RR...RR.....','....R.....R.....','................'], PALETTES[name])
        elif name == 'ergot':
            item = sprite(['................','................','....B...........','...BHB....B.....','...BBB...BHB....','....BB...BBB....','....B.....BB....','..........B.....','......B.........','.....BHB........','.....BBB........','......BB........','......B.........','................','................','................'], PALETTES[name])
        else:
            item = mature.copy()
            ImageDraw.Draw(item).line((6, 13, 9, 13), fill='#b69a68')
        save(item, 'item', item_name)
        seeds = Image.new('RGBA', (16, 16))
        draw = ImageDraw.Draw(seeds)
        seed_color = ('#ab8151', '#8d6a44', '#aa986c', '#c1aa78', '#6c6650')[list(PATTERNS).index(name) % 5]
        for index, (x, y) in enumerate(((4, 4), (10, 6), (6, 10))):
            long = name in ('ergot', 'foxglove', 'glint_weed', 'parasitic_vine')
            draw.ellipse((x, y, x + (1 if long else 2), y + (3 if long else 2)), fill=seed_color, outline='#493e30')
            draw.point((x + 1, y + 1), fill=PALETTES[name][2])
        if name == 'datura':
            draw.line((9, 4, 12, 9), fill='#79674b')
        save(seeds, 'item', name + '_seeds')


TREE = {
    'hawthorn': ('#4e443a', '#796752', '#a48a66', '#c6b18b', '#536848', '#82935d'),
    'rowan': ('#655c4e', '#968978', '#b1a08a', '#d2bd98', '#526e46', '#8e9c5d'),
    'alder': ('#403a31', '#665747', '#92816a', '#c1a075', '#3e6045', '#70865a'),
    'elder': ('#655e4b', '#8d846a', '#aea085', '#cfbd94', '#596a42', '#94a16a'),
    'willow': ('#494836', '#6c6b4c', '#96946e', '#c2b586', '#596d43', '#91a15e'),
}


def trees():
    for index, (name, colors) in enumerate(TREE.items()):
        dark, bark, light, wood, leaf, high = colors
        sapling = Image.new('RGBA', (16, 16))
        draw = ImageDraw.Draw(sapling)
        if name == 'willow':
            draw.line((8, 15, 8, 5, 6, 3), fill=bark, width=2)
            for x, y, length in ((3, 4, 7), (5, 2, 10), (10, 3, 10), (12, 5, 6)):
                draw.line((8, 4, x, y + 1, x, y + length), fill=leaf)
                draw.line((x + 1, y + 1, x + 1, y + length - 2), fill=high)
        else:
            draw.line((7, 15, 7, 5), fill=bark, width=2)
            branches = ((3, 5), (10, 3), (12, 8), (4, 10)) if name != 'elder' else ((3, 4), (7, 2), (12, 4), (4, 9), (11, 10))
            for bx, by in branches:
                draw.line((7, min(12, by + 4), bx, by), fill=dark)
                if name == 'rowan':
                    for offset in (-2, 0, 2):
                        draw.line((bx + offset, by + 3, bx + offset + 1, by), fill=leaf)
                        draw.point((bx + offset + 1, by), fill=high)
                elif name == 'alder':
                    draw.ellipse((bx - 2, by - 1, bx + 1, by + 3), fill=leaf)
                    draw.line((bx - 1, by, bx - 1, by + 2), fill=high)
                else:
                    draw.polygon(((bx - 2, by + 1), (bx, by - 1), (bx + 2, by + 1), (bx + 1, by + 3), (bx - 1, by + 2)), fill=leaf)
                    draw.point((bx, by), fill=high)
                if name in ('rowan', 'hawthorn'):
                    draw.point((bx, by + 2), fill='#b47755' if name == 'hawthorn' else '#b35e43')
        draw.line((6, 15, 10, 15), fill=dark)
        save(sapling, 'block', name + '_sapling')
        save(highlight(sapling), 'block', name + '_sapling_uv')
        log = Image.new('RGBA', (16, 16), bark)
        ld = ImageDraw.Draw(log)
        for x in range(16):
            stripe = (x + index) % (3 if name == 'willow' else 5)
            if stripe == 0:
                ld.line((x, 0, x, 15), fill=dark)
            elif stripe == 1:
                ld.line((x, 0, x, 15), fill=light)
        for y in (2 + index % 3, 8, 13):
            x = (y * 3 + index * 2) % 13
            if name in ('rowan', 'elder'):
                ld.line((x, y, min(15, x + 3), y), fill=dark)
                ld.point((min(15, x + 1), y + 1), fill=light)
            else:
                ld.line((x, y - 1, x + 1, y, x + 1, y + 2), fill=dark)
        save(log, 'block', name + '_log')
        overlay = Image.new('RGBA', (16, 16))
        od = ImageDraw.Draw(overlay)
        for y in (3, 11):
            x = (index * 3 + y) % 12
            od.line((x, y, x + 2, y), fill=light)
        save(overlay, 'block', name + '_log_uv')
        end = Image.new('RGBA', (16, 16), dark)
        ed = ImageDraw.Draw(end)
        ed.rectangle((1, 1, 14, 14), fill=wood)
        for inset in (3, 5, 7):
            ed.rectangle((inset, inset, 15 - inset, 15 - inset), outline=bark)
        ed.line((12, 1, 11, 3, 11, 4), fill=dark)
        ed.line((1, 10, 3, 10), fill=light)
        save(end, 'block', name + '_log_top')
        leaves = Image.new('RGBA', (16, 16))
        ld = ImageDraw.Draw(leaves)
        for x, y in ((0, 0), (5, 1), (11, 0), (-1, 6), (5, 7), (12, 6), (1, 12), (8, 13), (14, 12)):
            if name == 'willow':
                ld.line((x + 1, y, x + 2, y + 5), fill=leaf, width=2)
                ld.line((x + 2, y, x + 3, y + 3), fill=high)
            elif name == 'rowan':
                ld.line((x, y + 1, x + 4, y + 4), fill=dark)
                for shift in (0, 2, 4):
                    ld.line((x + shift, y + 1, x + shift + 1, y + 3), fill=leaf, width=2)
                    ld.point((x + shift, y + 1), fill=high)
            elif name == 'alder':
                ld.ellipse((x, y, x + 5, y + 4), fill=leaf)
                ld.line((x + 1, y + 1, x + 3, y + 3), fill=high)
            else:
                ld.polygon(((x + 2, y), (x + 5, y + 2), (x + 4, y + 5), (x + 2, y + 4), (x, y + 3)), fill=leaf)
                ld.line((x + 2, y + 1, x + 2, y + 3), fill=high)
                if name == 'hawthorn':
                    ld.point((x + 4, y + 3), fill=dark)
        save(leaves, 'block', name + '_leaves')
        save(highlight(leaves), 'block', name + '_leaves_uv')


def materials():
    for name, base, accent in (('wood_ash', '#78736b', '#b9afa0'), ('grass_ash', '#737862', '#a7af85'), ('transmutation_dust', '#978551', '#d5c38b')):
        image = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(image)
        d.polygon(((2, 13), (4, 10), (7, 8), (10, 10), (13, 12), (13, 14), (3, 14)), fill=base)
        d.line((4, 10, 7, 9, 9, 10), fill=accent)
        for point in ((3, 13), (6, 12), (10, 13), (8, 11), (11, 10)):
            d.point(point, fill=accent)
        if name == 'wood_ash':
            d.line((4, 12, 8, 13), fill='#45443f')
            d.line((8, 10, 11, 11), fill='#45443f')
        if name == 'transmutation_dust':
            d.line((4, 4, 4, 6), fill=accent)
            d.line((3, 5, 5, 5), fill=accent)
            d.point((11, 7), fill=accent)
        save(image, 'item', name)
    paste = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(paste)
    d.polygon(((3, 10), (5, 6), (8, 5), (12, 8), (13, 12), (10, 14), (4, 13)), fill='#3d4f31')
    d.polygon(((4, 10), (6, 7), (9, 7), (11, 9), (10, 12), (6, 12)), fill='#6d7c42')
    d.line((6, 9, 8, 8, 10, 9), fill='#a1a36a')
    d.line((5, 13, 9, 13, 11, 12), fill='#253d2a')
    save(paste, 'item', 'refined_herb_paste')
    for charged in (False, True):
        stone = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(stone)
        d.polygon(((5, 2), (11, 3), (13, 7), (12, 13), (8, 15), (3, 12), (2, 7)), fill='#474b47')
        d.polygon(((5, 3), (10, 4), (11, 7), (10, 12), (5, 11), (3, 7)), fill='#747b6e')
        d.line((5, 3, 9, 4, 10, 5), fill='#a1a697')
        d.line((6, 6, 6, 10, 9, 8, 6, 8, 9, 6, 9, 11), fill='#b9dcce' if charged else '#333e36')
        if charged:
            d.point((2, 3), fill='#bbdccc')
            d.point((13, 12), fill='#bbdccc')
        save(stone, 'item', 'charged_rune_stone' if charged else 'rune_stone')


if __name__ == '__main__':
    crops()
    trees()
    materials()
    print('Authored 13 crops x 4 stages, seeds/harvests, 5 tree sets and 6 materials.')
