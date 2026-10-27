"""Wave 2 art: Ram, Crawler, Lab Leader (human and hybrid), embryo vat. Imported by gen.py (same helpers)."""
import json
import math
import os

import numpy as np
from PIL import Image

import gen
from gen import new, put, save, ramp, h, box_faces, HIDE, FLESH, BONE, DARK, STEEL, cube_polys, render, mat

LIGHT = {'top': 1.12, 'bottom': 0.65, 'front': 1.0, 'back': 0.85, 'left': 0.9, 'right': 0.9}
CHITIN = [(14, 10, 18), (30, 20, 38), (50, 32, 62), (78, 48, 96), (110, 70, 130)]
COAT = [(120, 122, 128), (170, 172, 176), (206, 208, 210), (226, 228, 230), (244, 245, 246)]
SKIN = [(120, 98, 86), (168, 140, 124), (204, 178, 160), (224, 202, 186), (240, 224, 212)]
GREYSKIN = [(52, 50, 58), (84, 80, 90), (116, 110, 120), (146, 138, 148), (176, 168, 176)]
TROUSER = [(18, 18, 24), (32, 32, 40), (46, 46, 56), (62, 62, 74), (80, 80, 94)]
HAIR = [(14, 12, 12), (28, 22, 20), (44, 34, 30), (60, 48, 42), (78, 64, 56)]


def paint(img, cubes, colour):
    """cubes: list of (name, u, v, w, h, d). colour(name, face, xx, yy, fw, fh, x, y, n) -> rgb or None."""
    for name, u, v, w, hh, d in cubes:
        for face, (x0, y0, fw, fh) in box_faces(u, v, w, hh, d).items():
            for yy in range(fh):
                for xx in range(fw):
                    x, y = x0 + xx, y0 + yy
                    n = (h(x, y, 41) - 0.5) * 0.14 + (h(x // 2, y // 2, 42) - 0.5) * 0.08
                    c = colour(name, face, xx, yy, fw, fh, x, y, n)
                    if c is None:
                        continue
                    if yy == fh - 1 and face not in ('top', 'bottom'):
                        c = tuple(int(k * 0.82) for k in c)
                    put(img, x, y, c)

# ---------------------------------------------------------------------------------------------- ram

RAM_CUBES = [('body', 1, 1, 9, 11, 16), ('body2', 0, 28, 11, 14, 11), ('plate', 64, 32, 9, 8, 2),
             ('leg', 36, 29, 3, 6, 3), ('leg', 49, 29, 3, 6, 3), ('leg', 49, 2, 3, 10, 3), ('leg', 35, 2, 3, 10, 3),
             ('ear', 2, 61, 3, 2, 1), ('nose', 34, 46, 5, 7, 10), ('horn', 64, 0, 3, 3, 6), ('horn2', 64, 10, 3, 7, 3),
             ('brow', 64, 24, 7, 4, 2)]


def ram_textures():
    base, glow = new(128, 64), new(128, 64)

    def col(name, face, xx, yy, fw, fh, x, y, n):
        L = LIGHT[face]
        if name in ('horn', 'horn2'):
            ridge = 0.12 if (yy + xx) % 3 == 0 else 0
            return ramp(BONE, 0.5 * L + n + ridge)
        if name in ('plate', 'brow'):
            rivet = (xx % 4 == 1 and yy % 4 == 1)
            return ramp(STEEL, (0.62 if rivet else 0.36) * L + n * 0.6)
        if name == 'leg' and yy >= fh - 2:
            return ramp(BONE, 0.3 + n)
        if name == 'nose':
            return ramp(HIDE, 0.5 * L + n)
        # Shaggy wool with raw seams along the flanks.
        # Shaggy wool, with darker tufts along the flanks.
        tuft = name in ('body', 'body2') and face in ('left', 'right') and yy % 4 == 2 and h(x, y, 44) > 0.4
        return ramp(HIDE, (0.3 if tuft else 0.58) * L + n)
    paint(base, RAM_CUBES, col)
    # Eyes on both sides of the face (the nose box's side faces, near its top back).
    for (x0, y0, fw, fh) in (box_faces(34, 46, 5, 7, 10)['right'], box_faces(34, 46, 5, 7, 10)['left']):
        for ex in (fw - 3,):
            put(base, x0 + ex, y0 + 1, (90, 0, 0))
            put(glow, x0 + ex, y0 + 1, (255, 50, 40))
    return base, glow

# ---------------------------------------------------------------------------------------------- crawler

CRAWLER_CUBES = [('head', 32, 4, 8, 8, 8), ('neck', 0, 0, 6, 6, 6), ('abdomen', 0, 12, 10, 8, 12), ('leg', 18, 0, 16, 2, 2)]


def crawler_texture():
    img = new(64, 32)

    def col(name, face, xx, yy, fw, fh, x, y, n):
        L = LIGHT[face]
        if name == 'abdomen' and face == 'top':
            # A red hourglass-like mark split down the middle, and ribbing.
            cxm = abs(xx - (fw - 1) / 2)
            if cxm < 1.5 and 3 <= yy <= fh - 4:
                return ramp(FLESH, 0.55 + n)
            if yy % 3 == 0:
                return ramp(CHITIN, 0.25 + n)
        if name == 'leg':
            joint = xx % 8 in (0, 7)
            return ramp(CHITIN, (0.65 if joint else 0.45) * L + n)
        if name == 'head' and face == 'front' and yy >= 6:
            return ramp(BONE, 0.45 + n)  # fangs row
        return ramp(CHITIN, 0.5 * L + n)
    paint(img, CRAWLER_CUBES, col)
    return img

# ---------------------------------------------------------------------------------------------- lab leader

LEADER_BASE = [('head', 0, 0, 8, 8, 8), ('body', 16, 16, 8, 12, 4), ('arm', 40, 16, 4, 12, 4), ('leg', 0, 16, 4, 12, 4)]
LEADER_PARTS = [('snout', 0, 32, 4, 3, 4), ('ear', 16, 32, 2, 3, 1), ('claw', 24, 32, 1, 1, 3), ('tail', 32, 32, 2, 2, 7),
                ('horn', 0, 40, 2, 2, 6), ('horn2', 0, 48, 2, 5, 2), ('rplate', 16, 40, 5, 2, 6), ('cleg', 40, 48, 2, 2, 10),
                ('cleg', 8, 49, 3, 3, 12)]


def leader_textures():
    human, hybrid, glow = new(64, 64), new(64, 64), new(64, 64)

    def make(hy):
        skin = GREYSKIN if hy else SKIN

        def col(name, face, xx, yy, fw, fh, x, y, n):
            L = LIGHT[face]
            if name == 'head':
                if face == 'top' or (face != 'bottom' and yy < 2) or (face == 'back' and yy < 6):
                    return ramp(HAIR, 0.5 * L + n)
                if face == 'front':
                    if yy == 4 and xx in (1, 2, 5, 6):
                        return (150, 10, 10) if hy else ((240, 240, 240) if xx in (1, 6) else (40, 50, 60))
                    if yy == 6 and 2 <= xx <= 5:
                        return (60, 20, 24) if hy else ramp(skin, 0.3)
                    if hy and (xx + yy * 3) % 7 == 0 and yy > 4:
                        return (120, 20, 30)  # veins
                return ramp(skin, 0.6 * L + n)
            if name == 'body':
                if face == 'front':
                    if 3 <= xx <= 4 and yy < 9:
                        return (140, 20, 30) if yy < 7 else ramp(TROUSER, 0.4)  # tie / shirt gap
                    if xx in (2, 5) and yy < 9:
                        return ramp(TROUSER, 0.55 + n)  # shirt under the open coat
                coat = ramp(COAT, 0.62 * L + n)
                if hy and h(x, y, 47) > 0.72:
                    coat = ramp(FLESH, 0.35 + n)  # torn and stained
                return coat
            if name == 'arm':
                if yy >= fh - 3 and face != 'top':
                    return ramp(skin, 0.55 * L + n)
                return ramp(COAT, 0.6 * L + n) if not (hy and h(x, y, 48) > 0.78) else ramp(FLESH, 0.4 + n)
            if name == 'leg':
                if yy >= fh - 2 or face == 'bottom':
                    return ramp(DARK, 0.25 + n)
                if yy < 3 and face != 'top':
                    return ramp(COAT, 0.55 * L + n)  # coat hem
                return ramp(TROUSER, 0.5 * L + n)
            # Motif parts (only visible on the hybrid).
            if name in ('snout', 'ear', 'tail'):
                return ramp(HIDE, 0.55 * L + n) if not (name == 'snout' and face == 'front' and yy == 2) else (40, 8, 12)
            if name == 'claw':
                return ramp(BONE, 0.6 + n)
            if name in ('horn', 'horn2'):
                return ramp(BONE, 0.5 * L + n + (0.12 if (xx + yy) % 3 == 0 else 0))
            if name == 'rplate':
                return ramp(STEEL, (0.6 if (xx % 3 == 1 and yy == 1) else 0.38) * L + n * 0.6)
            if name == 'cleg':
                return ramp(CHITIN, (0.62 if (xx + yy) % 5 == 0 else 0.42) * L + n)
            return None
        return col

    paint(human, LEADER_BASE + LEADER_PARTS, make(False))
    paint(hybrid, LEADER_BASE + LEADER_PARTS, make(True))
    fx, fy, _, _ = box_faces(0, 0, 8, 8, 8)['front']
    for ex in (1, 2, 5, 6):
        put(glow, fx + ex, fy + 4, (255, 40, 40))
    sx, sy, _, _ = box_faces(0, 32, 4, 3, 4)['front']
    for ex in (0, 3):
        put(glow, sx + ex, sy, (255, 60, 50))
    return human, hybrid, glow

# ---------------------------------------------------------------------------------------------- vat

def vat_textures():
    frame, glass, fluid, emb = new(16, 16), new(16, 16), new(16, 16), new(16, 16)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            put(frame, x, y, ramp(DARK, (0.75 if edge else 0.5) + (h(x, y, 51) - 0.5) * 0.1))
            if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                put(frame, x, y, ramp(STEEL, 0.8))
            # Glass: clear with bright edges and a diagonal glint.
            if edge:
                put(glass, x, y, (200, 230, 240), 150)
            elif x == y + 3 or x == y + 4:
                put(glass, x, y, (230, 250, 255), 90)
            else:
                put(glass, x, y, (180, 220, 230), 28)
            put(fluid, x, y, (int(150 + 40 * h(x, y, 52)), int(190 + 30 * h(x, y, 53)), 70), 120)
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7:
                put(emb, x, y, ramp(FLESH, 0.75 - d * 0.06 + (h(x, y, 54) - 0.5) * 0.1))
    put(emb, 6, 6, (240, 40, 40))
    return frame, glass, fluid, emb


def vat_models():
    m = {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'ambientocclusion': False,
        'textures': {'particle': 'kakugane:block/embryo_vat_glass', 'frame': 'kakugane:block/embryo_vat_frame',
                     'glass': 'kakugane:block/embryo_vat_glass', 'fluid': 'kakugane:block/embryo_vat_fluid',
                     'embryo': 'kakugane:block/embryo_vat_embryo'},
        'elements': [
            cuboid([1, 0, 1], [15, 2, 15], 'frame'), cuboid([1, 14, 1], [15, 16, 15], 'frame'),
            cuboid([6, 7, 6], [10, 11, 10], 'embryo'), cuboid([3, 2, 3], [13, 13, 13], 'fluid'),
            cuboid([2, 2, 2], [14, 14, 14], 'glass'),
        ],
    }
    d = os.path.join(gen.ASSETS, 'models', 'block')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, 'embryo_vat.json'), 'w') as f:
        json.dump(m, f, indent=2)
        f.write('\n')
    with open(os.path.join(gen.ASSETS, 'models', 'item', 'embryo_vat.json'), 'w') as f:
        json.dump({'parent': 'kakugane:block/embryo_vat'}, f, indent=2)
        f.write('\n')
    bs = os.path.join(gen.ASSETS, 'blockstates')
    os.makedirs(bs, exist_ok=True)
    with open(os.path.join(bs, 'embryo_vat.json'), 'w') as f:
        json.dump({'variants': {'': {'model': 'kakugane:block/embryo_vat'}}}, f, indent=2)
        f.write('\n')
    for egg in ('ram', 'crawler', 'lab_leader'):
        with open(os.path.join(gen.ASSETS, 'models', 'item', egg + '_spawn_egg.json'), 'w') as f:
            json.dump({'parent': 'minecraft:item/template_spawn_egg'}, f, indent=2)
            f.write('\n')


def cuboid(fr, to, tex):
    faces = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        faces[f] = {'texture': '#' + tex, 'uv': uv_for(f, fr, to)}
    return {'from': fr, 'to': to, 'faces': faces}


def uv_for(face, fr, to):
    if face in ('north', 'south'):
        return [fr[0], 16 - to[1], to[0], 16 - fr[1]]
    if face in ('east', 'west'):
        return [fr[2], 16 - to[1], to[2], 16 - fr[1]]
    return [fr[0], fr[2], to[0], to[2]]

# ---------------------------------------------------------------------------------------------- previews

def ram_parts():
    hd = mat(1, 14, 0)
    nose = mat(0, -8, -8, rx=0.9599, parent=hd)
    body = mat(0, 24, 0)
    return [
        (cube_polys((-6, -11, -10), (3, 2, 1), (2, 61)), hd), (cube_polys((2, -11, -10), (3, 2, 1), (2, 61), mirror=True), hd),
        (cube_polys((2.5, -15, -9), (3, 3, 6), (64, 0)), hd), (cube_polys((3.5, -13, -4), (3, 7, 3), (64, 10)), hd),
        (cube_polys((-6.5, -15, -9), (3, 3, 6), (64, 0), mirror=True), hd), (cube_polys((-7.5, -13, -4), (3, 7, 3), (64, 10), mirror=True), hd),
        (cube_polys((-3, -4, -8), (5, 7, 10), (34, 46)), nose), (cube_polys((-4, -14, -11), (7, 4, 2), (64, 24)), hd),
        (cube_polys((-4, -17, -7), (9, 11, 16), (1, 1)), body), (cube_polys((-5, -18, -8), (11, 14, 11), (0, 28)), body),
        (cube_polys((-4, -13, -9.5), (9, 8, 2), (64, 32)), body),
        (cube_polys((0, 4, 0), (3, 6, 3), (36, 29)), mat(1, 14, 4)), (cube_polys((0, 4, 0), (3, 6, 3), (49, 29)), mat(-3, 14, 4)),
        (cube_polys((0, 0, 0), (3, 10, 3), (49, 2)), mat(1, 14, -6)), (cube_polys((0, 0, 0), (3, 10, 3), (35, 2)), mat(-3, 14, -6)),
    ]


def crawler_parts():
    parts = [(cube_polys((-4, -4, -8), (8, 8, 8), (32, 4)), mat(0, 15, -3)),
             (cube_polys((-3, -3, -3), (6, 6, 6), (0, 0)), mat(0, 15, 0)),
             (cube_polys((-5, -4, -6), (10, 8, 12), (0, 12)), mat(0, 15, 9))]
    q = math.pi / 4
    for z, yr, zr in ((2, -q, -q), (1, -q / 2, -0.58), (0, q / 2, -0.58), (-1, q, -q)):
        parts.append((cube_polys((-15, -1, -1), (16, 2, 2), (18, 0)), mat(-4, 15, z, ry=-yr, rz=zr)))
        parts.append((cube_polys((-1, -1, -1), (16, 2, 2), (18, 0), mirror=True), mat(4, 15, z, ry=yr, rz=-zr)))
    return parts


def leader_parts(motif=None):
    I = np.eye(4)
    ra, la = mat(-5, 2, 0), mat(5, 2, 0)
    if motif == 'hound':
        ra, la = mat(-5, 2, 0, rx=-1.45), mat(5, 2, 0, rx=-1.45)
    body = mat(0, 0, 0)
    parts = [(cube_polys((-4, -8, -4), (8, 8, 8), (0, 0)), I), (cube_polys((-4, 0, -2), (8, 12, 4), (16, 16)), body),
             (cube_polys((-3, -2, -2), (4, 12, 4), (40, 16)), ra), (cube_polys((-1, -2, -2), (4, 12, 4), (40, 16), mirror=True), la),
             (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16)), mat(-1.9, 12, 0)), (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), mirror=True), mat(1.9, 12, 0))]
    if motif == 'hound':
        parts += [(cube_polys((-2, -4, -7.5), (4, 3, 4), (0, 32)), I), (cube_polys((-4, -10, -1), (2, 3, 1), (16, 32)), I),
                  (cube_polys((2, -10, -1), (2, 3, 1), (16, 32), mirror=True), I)]
        for dx in (-3.5, -2, -0.5):
            parts.append((cube_polys((dx, 9, -4), (1, 1, 3), (24, 32)), ra))
        for dx in (-0.5, 1, 2.5):
            parts.append((cube_polys((dx, 9, -4), (1, 1, 3), (24, 32), mirror=True), la))
        parts.append((cube_polys((-1, 0, 0), (2, 2, 7), (32, 32)), mat(0, 10, 2, rx=-0.6)))
    elif motif == 'ram':
        parts += [(cube_polys((3.5, -9, -3), (2, 2, 6), (0, 40)), I), (cube_polys((4.5, -8, 2), (2, 5, 2), (0, 48)), I),
                  (cube_polys((-5.5, -9, -3), (2, 2, 6), (0, 40), mirror=True), I), (cube_polys((-6.5, -8, 2), (2, 5, 2), (0, 48), mirror=True), I),
                  (cube_polys((-4, -3, -3), (5, 2, 6), (16, 40)), ra), (cube_polys((-1, -3, -3), (5, 2, 6), (16, 40), mirror=True), la)]
    elif motif == 'crawler':
        for lx, ly, ry in ((-2.5, 2, -1.5), (2.5, 2, 1.5)):
            parts.append((cube_polys((-1, -1, 0), (2, 2, 10), (40, 48)), mat(lx, ly, 2, rx=0.7, ry=ry)))
        for lx, ly, ry in ((-2.5, 6, -1.6), (2.5, 6, 1.6)):
            parts.append((cube_polys((-1.5, -1.5, 0), (3, 3, 12), (8, 49)), mat(lx, ly, 2, rx=0.45, ry=ry)))
    return parts


def vat_preview(tex):
    frame, glass, fluid, emb = tex
    parts = []
    for fr, to, t in (([1, 0, 1], [15, 2, 15], frame), ([1, 14, 1], [15, 16, 15], frame), ([6, 7, 6], [10, 11, 10], emb),
                      ([3, 2, 3], [13, 13, 13], fluid)):
        size = [to[i] - fr[i] for i in range(3)]
        # Block-model faces sample the texture by position; approximate with a cube tiled from the 16x16 sprite.
        parts.append((cube_polys(tuple(fr), tuple(size), (0, 0)), np.eye(4), np.tile(t, (8, 8, 1))))
    return parts


def previews(out, ram, crawler, leader, vat):
    os.makedirs(out, exist_ok=True)
    def combo(b, g):
        c = Image.fromarray(b, 'RGBA'); c.alpha_composite(Image.fromarray(g, 'RGBA')); return np.array(c)
    tiles = [(render(ram_parts(), combo(*ram), 256, -140, 14), 'Ram'),
             (render(crawler_parts(), crawler, 256, -150, 26), 'Crawler'),
             (render(leader_parts(), leader[0], 256, -160, 8), 'Lab Leader (human form)')]
    hyb = combo(leader[1], leader[2])
    for m in ('hound', 'ram', 'crawler'):
        tiles.append((render(leader_parts(m), hyb, 256, -150 if m != 'crawler' else -20, 8), f'Leader, {m} hybrid'))
    sheet = Image.new('RGBA', (256 * 3, 280 * 2), (54, 58, 66, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(sheet)
    for i, (im, label) in enumerate(tiles):
        x, y = (i % 3) * 256, (i // 3) * 280
        sheet.alpha_composite(im, (x, y + 20))
        d.text((x + 8, y + 4), label, fill=(235, 235, 240, 255))
    sheet.save(os.path.join(out, 'homunculi.png'))


def run(preview_dir=None):
    ram = ram_textures()
    save(ram[0], 'entity', 'ram.png')
    save(ram[1], 'entity', 'ram_glow.png')
    crawler = crawler_texture()
    save(crawler, 'entity', 'crawler.png')
    leader = leader_textures()
    save(leader[0], 'entity', 'lab_leader.png')
    save(leader[1], 'entity', 'lab_leader_hybrid.png')
    save(leader[2], 'entity', 'lab_leader_glow.png')
    vat = vat_textures()
    for name, img in zip(('frame', 'glass', 'fluid', 'embryo'), vat):
        save(img, 'block', f'embryo_vat_{name}.png')
    vat_models()
    if preview_dir:
        previews(preview_dir, ram, crawler, leader, vat)
