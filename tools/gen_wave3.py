"""Wave 3 art: the worn forms (Aerial Operator, Alice in Wonderland, Gauntlets, Greaves, Mantle, Blade Belt).

Imported by gen.py, using its helpers. For each form: the model texture and its grey accent layer (tinted with the
Kakugane's palette in game), a 16x16 item icon with its accent, the item models, and a preview of the form worn
on a mannequin. The cube layouts here mirror the Java models in client/worn exactly.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

import gen
from gen import new, put, save, ramp, h, box_faces, STEEL, GREY, cube_polys, render, mat, outline, hexagon

LIGHT = {'top': 1.12, 'bottom': 0.65, 'front': 1.0, 'back': 0.85, 'left': 0.9, 'right': 0.9}
GUNMETAL = [(22, 24, 30), (40, 44, 52), (62, 68, 78), (90, 98, 110), (124, 134, 148)]
GLASS = [(10, 14, 20), (20, 30, 42), (34, 52, 70), (60, 90, 116), (150, 190, 214)]
CLOTH = [(16, 16, 20), (28, 28, 34), (40, 40, 48), (56, 56, 66), (74, 74, 86)]
FLAKE = [(150, 160, 172), (190, 198, 208), (220, 226, 234), (238, 242, 248), (255, 255, 255)]


def paint(img, cubes, colour):
    """cubes: (name, u, v, w, h, d). colour(name, face, xx, yy, fw, fh, n) -> rgb, (rgb, alpha) or None."""
    for name, u, v, w, hh, d in cubes:
        for face, (x0, y0, fw, fh) in box_faces(u, v, w, hh, d).items():
            for yy in range(fh):
                for xx in range(fw):
                    x, y = x0 + xx, y0 + yy
                    n = (h(x, y, 61) - 0.5) * 0.12
                    c = colour(name, face, xx, yy, fw, fh, n)
                    if c is None:
                        continue
                    if isinstance(c[0], tuple):
                        put(img, x, y, c[0], c[1])
                    else:
                        put(img, x, y, c)


def edge(xx, yy, fw, fh):
    return xx == 0 or yy == 0 or xx == fw - 1 or yy == fh - 1

# ---------------------------------------------------------------------------------------------- Aerial Operator

MASK_CUBES = [('face', 0, 0, 10, 8, 1), ('lens', 0, 10, 3, 3, 1), ('snout', 24, 0, 5, 4, 2), ('filter', 24, 8, 6, 5, 3),
              ('can', 0, 16, 3, 4, 4), ('strap', 0, 26, 8, 1, 8)]


def mask_textures():
    base, acc = new(64, 64), new(64, 64)

    def col(name, face, xx, yy, fw, fh, n):
        L = LIGHT[face]
        if name == 'lens':
            if face == 'front':
                return ramp(GLASS, 0.85 if (xx, yy) == (0, 0) else 0.3 + n)
            return ramp(GUNMETAL, 0.5 * L + n)
        if name == 'filter' and face == 'front':
            return ramp(GUNMETAL, 0.12 if (xx + yy) % 2 == 0 and not edge(xx, yy, fw, fh) else 0.55)
        if name == 'strap':
            return ramp(CLOTH, 0.45 * L + n)
        hexed = gen.hex_metal(xx * 2 + yy, yy * 2, L)
        return tuple(int(k * 0.7) for k in hexed) if name == 'face' else ramp(GUNMETAL, 0.62 * L + n)

    def acc_col(name, face, xx, yy, fw, fh, n):
        if name == 'lens' and face == 'front' and edge(xx, yy, fw, fh) and (xx, yy) != (0, 0):
            return ramp(GREY, 0.9)
        if name == 'filter' and face == 'front' and edge(xx, yy, fw, fh):
            return ramp(GREY, 0.8)
        if name == 'can' and face != 'top' and face != 'bottom' and yy == 1:
            return ramp(GREY, 0.85)
        if name == 'strap' and face == 'back' and 3 <= xx <= 4:
            return ramp(GREY, 0.9)  # buckle
        if name == 'face' and face == 'front' and yy == fh - 1 and xx % 2 == 0:
            return ramp(GREY, 0.7)
        return None

    paint(base, MASK_CUBES, col)
    paint(acc, MASK_CUBES, acc_col)
    return base, acc


def mask_parts(tex):
    I = np.eye(4)
    return [(cube_polys((-5, -7, -5.5), (10, 8, 1), (0, 0)), I, tex),
            (cube_polys((-4, -6.5, -6.5), (3, 3, 1), (0, 10)), I, tex),
            (cube_polys((1, -6.5, -6.5), (3, 3, 1), (0, 10), mirror=True), I, tex),
            (cube_polys((-2.5, -3.5, -7.5), (5, 4, 2), (24, 0)), I, tex),
            (cube_polys((-3, -4, -10.5), (6, 5, 3), (24, 8)), I, tex),
            (cube_polys((-7.5, -4.5, -4), (3, 4, 4), (0, 16)), I, tex),
            (cube_polys((4.5, -4.5, -4), (3, 4, 4), (0, 16), mirror=True), I, tex),
            (cube_polys((-4, -5.5, -4), (8, 1, 8), (0, 26), 0.35), I, tex)]

# ---------------------------------------------------------------------------------------------- Alice in Wonderland

def wings_textures():
    base, acc = new(16, 16), new(16, 16)
    for (u, v, bright) in ((0, 0, 0.95), (4, 2, 0.75), (4, 4, 0.6)):
        for face, (x0, y0, fw, fh) in box_faces(u, v, 1, 1, 1).items():
            for yy in range(fh):
                for xx in range(fw):
                    put(base, x0 + xx, y0 + yy, ramp(FLAKE, bright * LIGHT[face]), 215)
                    if v == 0:
                        put(acc, x0 + xx, y0 + yy, ramp(GREY, 0.85), 200)
    return base, acc


def wing_cubes(right):
    """The flake positions of one wing, exactly as WingsRenderer.wing builds them."""
    out = []
    for row, length in enumerate((15, 12, 9)):
        for i in range(length):
            t = i / 14
            x = i + 0.5
            y = row * 2.4 - 4.5 * math.sin(math.pi * min(1.0, t * 1.15)) + t * t * 5
            z = i * 0.35 + row * 0.9 + ((i * 7 + row * 3) % 3) * 0.25
            if right:
                x = -x - 1
            out.append(((x, y, z), (0, 0) if row == 0 else (4, 2 * row)))
    return out


def wings_parts(tex, fold=0.25, beat=0.2):
    parts = []
    for right in (False, True):
        m = mat(-1.5 if right else 1.5, 2.5, 2.5, ry=fold if right else -fold, rz=beat if right else -beat)
        for origin, uv in wing_cubes(right):
            parts.append((cube_polys(origin, (1, 1, 1), uv), m, tex))
    return parts

# ---------------------------------------------------------------------------------------------- Gauntlets

GAUNT_CUBES = [('cuff', 0, 0, 5, 5, 5), ('hand', 0, 10, 5, 3, 5), ('knuckles', 0, 18, 5, 2, 1)]


def gauntlet_textures():
    base, acc = new(64, 32), new(64, 32)

    def col(name, face, xx, yy, fw, fh, n):
        L = LIGHT[face]
        if name == 'knuckles':
            return ramp(STEEL, (0.75 if xx % 2 == 0 else 0.45) * L + n)
        if name == 'cuff':
            return gen.hex_metal(xx + 3, yy + 1, L)
        return ramp(STEEL, (0.58 if not edge(xx, yy, fw, fh) else 0.4) * L + n)

    def acc_col(name, face, xx, yy, fw, fh, n):
        if name == 'cuff' and face not in ('top', 'bottom') and yy in (0, fh - 1):
            return ramp(GREY, 0.85)
        if name == 'hand' and face == 'right' and fw >= 5 and 1 <= xx <= 3 and yy == 1:
            return ramp(GREY, 0.95)  # hex badge on the back of the hand
        if name == 'knuckles' and face == 'front' and yy == 0:
            return ramp(GREY, 0.8)
        return None

    paint(base, GAUNT_CUBES, col)
    paint(acc, GAUNT_CUBES, acc_col)
    return base, acc


def gauntlet_parts(tex, ra, la):
    return [(cube_polys((-3.5, 3, -2.5), (5, 5, 5), (0, 0)), ra, tex),
            (cube_polys((-3.5, 8, -2.5), (5, 3, 5), (0, 10)), ra, tex),
            (cube_polys((-3.5, 8.5, -3.25), (5, 2, 1), (0, 18)), ra, tex),
            (cube_polys((-1.5, 3, -2.5), (5, 5, 5), (0, 0), mirror=True), la, tex),
            (cube_polys((-1.5, 8, -2.5), (5, 3, 5), (0, 10), mirror=True), la, tex),
            (cube_polys((-1.5, 8.5, -3.25), (5, 2, 1), (0, 18), mirror=True), la, tex)]

# ---------------------------------------------------------------------------------------------- Mantle

MANTLE_CUBES = [('collar', 0, 0, 9, 2, 5), ('cloak', 0, 8, 10, 18, 1), ('hood', 0, 28, 8, 3, 2)]


def mantle_textures():
    base, acc = new(64, 64), new(64, 64)

    def col(name, face, xx, yy, fw, fh, n):
        L = LIGHT[face]
        if name == 'cloak':
            fold = 0.08 if xx % 3 == 1 else 0
            weave = (h(xx, yy, 71) - 0.5) * 0.1
            return ramp(CLOTH, (0.5 + fold + weave) * L)
        if name == 'collar':
            return ramp(GUNMETAL, (0.6 if yy == 0 else 0.45) * L + n)
        return ramp(CLOTH, 0.42 * L + n)

    def acc_col(name, face, xx, yy, fw, fh, n):
        if name == 'cloak' and face in ('front', 'back'):
            if yy >= fh - 2 and (xx + yy) % 2 == 0:
                return ramp(GREY, 0.75)  # hem
            if face == 'back' and hexagon(4.5, 5.5, 2.6)(xx, yy) and not hexagon(4.5, 5.5, 1.4)(xx, yy):
                return ramp(GREY, 0.9)  # hexagon emblem
        if name == 'collar' and yy == 1:
            return ramp(GREY, 0.8)
        return None

    paint(base, MANTLE_CUBES, col)
    paint(acc, MANTLE_CUBES, acc_col)
    return base, acc


def mantle_parts(tex, lift=0.08):
    body = mat(0, 0, 0)
    return [(cube_polys((-4.5, -1.5, -2.5), (9, 2, 5), (0, 0)), body, tex),
            (cube_polys((-4, -2, 1.5), (8, 3, 2), (0, 28)), body, tex),
            (cube_polys((-5, 0, 0), (10, 18, 1), (0, 8)), mat(0, 0, 2.6, rx=lift), tex)]

# ---------------------------------------------------------------------------------------------- Blade Belt

BELT_CUBES = [('cuff', 0, 0, 5, 2, 5), ('seg', 0, 8, 1, 1, 8), ('joint', 0, 17, 2, 2, 2), ('blade', 0, 22, 1, 4, 16)]


def belt_textures():
    base, acc = new(64, 64), new(64, 64)

    def col(name, face, xx, yy, fw, fh, n):
        L = LIGHT[face]
        if name == 'blade':
            if face in ('right', 'left'):
                # Flat of the blade: bright steel, a darker spine along the top edge.
                return ramp(STEEL, (0.4 if yy == 0 else 0.82 - 0.06 * (yy == fh - 1)) * L + n * 0.5)
            return ramp(STEEL, 0.55 * L + n)
        if name == 'joint':
            return ramp(GUNMETAL, 0.75 * L + n)
        if name == 'seg':
            return ramp(GUNMETAL, (0.6 if (xx + yy) % 2 else 0.45) * L + n)
        return gen.hex_metal(xx + 2, yy * 3, L)

    def acc_col(name, face, xx, yy, fw, fh, n):
        if name == 'blade' and face in ('right', 'left') and yy == fh - 2 and xx % 4 == 1:
            return ramp(GREY, 0.95)  # hexagon studs along the edge
        if name == 'cuff' and face not in ('top', 'bottom') and yy == 0:
            return ramp(GREY, 0.85)
        if name == 'joint' and face not in ('top', 'bottom') and (xx + yy) % 2 == 0:
            return ramp(GREY, 0.7)
        return None

    paint(base, BELT_CUBES, col)
    paint(acc, BELT_CUBES, acc_col)
    return base, acc


SPLAY, TILT, BEND1, BEND2 = 0.5, -0.12, -0.6, 0.62
REST_YAW = [-math.pi / 2 - SPLAY, -math.pi / 2 + SPLAY, math.pi / 2 + SPLAY, math.pi / 2 - SPLAY]


def belt_parts(tex, strike=None):
    """strike: (arm index, yaw toward the target, 0..1) to pose one arm mid-strike."""
    parts = [(cube_polys((-2.5, 1, -2.5), (5, 2, 5), (0, 0)), mat(-1.9, 12, 0), tex),
             (cube_polys((-2.5, 1, -2.5), (5, 2, 5), (0, 0), mirror=True), mat(1.9, 12, 0), tex)]
    for i, (x, z) in enumerate(((-4.4, -1.2), (-4.4, 1.2), (4.4, -1.2), (4.4, 1.2))):
        yaw, tilt, b1, b2 = REST_YAW[i], TILT, BEND1, BEND2
        if strike and strike[0] == i:
            e = math.sin(math.pi * strike[2])
            yaw += (math.atan2(math.sin(strike[1] - yaw), math.cos(strike[1] - yaw))) * e
            tilt, b1, b2 = tilt + (0.05 - tilt) * e, b1 * (1 - e), b2 * (1 - e)
        seg1 = mat(x, 14, z, rx=tilt, ry=yaw)
        j1 = mat(0, 0, 7, rx=b1, parent=seg1)
        j2 = mat(0, 0, 8, rx=b2, parent=j1)
        parts += [(cube_polys((-0.5, -0.5, 0), (1, 1, 7), (0, 8)), seg1, tex),
                  (cube_polys((-1, -1, -1), (2, 2, 2), (0, 17)), j1, tex),
                  (cube_polys((-0.5, -0.5, 0), (1, 1, 8), (0, 8)), j1, tex),
                  (cube_polys((-1, -1, -1), (2, 2, 2), (0, 17)), j2, tex),
                  (cube_polys((-0.5, -2, 0), (1, 4, 16), (0, 22)), j2, tex)]
    return parts

# ---------------------------------------------------------------------------------------------- Greaves (armor layer)

def greaves_layer():
    base, acc = new(64, 32), new(64, 32)
    for face, (x0, y0, fw, fh) in box_faces(0, 16, 4, 12, 4).items():
        L = LIGHT[face]
        for yy in range(fh):
            for xx in range(fw):
                x, y = x0 + xx, y0 + yy
                if face in ('top',):
                    continue
                if face == 'bottom':
                    put(base, x, y, ramp(GUNMETAL, 0.4))
                    continue
                if yy < 3:
                    continue  # thigh left bare
                knee = 3 <= yy <= 5
                c = ramp(STEEL, 0.74 * L + (h(x, y, 81) - 0.5) * 0.08) if knee else gen.hex_metal(xx * 2 + 1, yy * 2, L)
                if yy in (5, fh - 1):
                    c = tuple(int(k * 0.75) for k in c)
                put(base, x, y, c)
                if face == 'front' and (yy == 3 or (knee and xx in (0, fw - 1))):
                    put(acc, x, y, ramp(GREY, 0.9))
                if face == 'front' and yy >= 7 and xx in (1, 2) and yy % 2 == 1:
                    put(acc, x, y, ramp(GREY, 0.7))  # shin ridge
    return base, acc


def greaves_parts(tex):
    return [(cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 0.5), mat(-1.9, 12, 0), tex),
            (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 0.5, mirror=True), mat(1.9, 12, 0), tex)]

# ---------------------------------------------------------------------------------------------- item icons

ICONS = {
    'aerial_operator': ['..######..', '.#OO##OO#.', '.#OO##OO#.', '.########.', '#..####..#',
                        '#..#AA#..#', '...#AA#...', '...####...'],
    'alice_wings': ['A........A', '#A......A#', '##A....A##', '###....###', '.##....##.', '..#....#..'],
    'gauntlets': ['.####.', '######', '######', '#AAAA#', '######', 'A#A#A#'],
    'greaves': ['AA..AA', '##..##', '##..##', '##..##', '##..##', '##..##', 'AA..AA', '##..##', '###.###'],
    'mantle': ['.AAAAAA.', '########', '#######.', '########', '########', '########', '########', 'A#A#A#A#'],
    'blade_belt': ['A..........A', '#A........A#', '.##......##.', '..#AAAAAA#..', '..#.####.#..', '.#........#.',
                   '#..........#'],
}


def icon(name):
    base, acc = new(16, 16), new(16, 16)
    rows = ICONS[name]
    w = max(len(r) for r in rows)
    ox, oy = (16 - w) // 2, (16 - len(rows)) // 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            x, y = ox + i, oy + j
            if ch == '#':
                put(base, x, y, ramp(STEEL, 0.72 - j / len(rows) * 0.3 + ((x + y) % 3 == 0) * 0.08))
            elif ch == 'O':
                put(base, x, y, ramp(GLASS, 0.4))
            elif ch == 'A':
                put(base, x, y, ramp(STEEL, 0.6))
                put(acc, x, y, ramp(GREY, 0.9))
    return outline(base), acc

# ---------------------------------------------------------------------------------------------- models

FORMS = [('aerial_operator', 'aerial_operator', 5), ('alice', 'alice_wings', 6), ('gauntlets', 'gauntlets', 7),
         ('greaves', 'greaves', 8), ('mantle', 'mantle', 9), ('blade_belt', 'blade_belt', 10)]


def write_models():
    for _, item, _ in FORMS:
        gen.item_model(item, [item, item + '_accent'])
    # The Kakugane item, while one of these forms is active, shows the form's icon.
    path = os.path.join(gen.ASSETS, 'models', 'item', 'kakugane.json')
    with open(path) as f:
        m = json.load(f)
    ov = [o for o in m['overrides'] if o['predicate'].get('kakugane:form', 0) < 5]
    for _, item, idx in FORMS:
        ov.append({'predicate': {'kakugane:form': idx}, 'model': f'kakugane:item/{item}'})
    m['overrides'] = ov
    with open(path, 'w') as f:
        json.dump(m, f, indent=2)
        f.write('\n')

# ---------------------------------------------------------------------------------------------- previews

SKIN = [(120, 98, 86), (168, 140, 124), (204, 178, 160), (224, 202, 186), (240, 224, 212)]
SHIRT = [(20, 32, 48), (32, 50, 72), (46, 70, 98), (64, 92, 124), (88, 118, 150)]
JEANS = [(14, 18, 30), (24, 30, 48), (36, 44, 66), (50, 60, 86), (66, 78, 106)]


def mannequin():
    img = new(64, 64)
    cubes = [('head', 0, 0, 8, 8, 8), ('body', 16, 16, 8, 12, 4), ('arm', 40, 16, 4, 12, 4), ('leg', 0, 16, 4, 12, 4)]

    def col(name, face, xx, yy, fw, fh, n):
        L = LIGHT[face]
        if name == 'head':
            if face == 'top' or yy < 2 or face == 'back':
                return ramp(gen.DARK, 0.4 * L + n)
            if face == 'front' and yy == 4 and xx in (2, 5):
                return (40, 40, 60)
            return ramp(SKIN, 0.6 * L + n)
        if name == 'arm' and yy >= fh - 4:
            return ramp(SKIN, 0.55 * L + n)
        if name == 'leg':
            return ramp(JEANS, 0.5 * L + n) if yy < fh - 2 else ramp(gen.DARK, 0.3)
        return ramp(SHIRT, 0.55 * L + n)

    paint(img, cubes, col)
    return img


def body_parts(tex, ra=None, la=None):
    I = np.eye(4)
    ra = ra if ra is not None else mat(-5, 2, 0)
    la = la if la is not None else mat(5, 2, 0)
    return [(cube_polys((-4, -8, -4), (8, 8, 8), (0, 0)), I, tex), (cube_polys((-4, 0, -2), (8, 12, 4), (16, 16)), I, tex),
            (cube_polys((-3, -2, -2), (4, 12, 4), (40, 16)), ra, tex), (cube_polys((-1, -2, -2), (4, 12, 4), (40, 16), mirror=True), la, tex),
            (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16)), mat(-1.9, 12, 0), tex),
            (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), mirror=True), mat(1.9, 12, 0), tex)]


def previews(out, tex, icons):
    os.makedirs(out, exist_ok=True)
    man = mannequin()
    pal = gen.PALETTES

    def t(name, p):
        b, a = tex[name]
        return np.array(gen.composite(b, a, pal[p]))

    ra, la = mat(-5, 2, 0, rx=-0.35, rz=0.12), mat(5, 2, 0, rx=0.25, rz=-0.12)
    rows = [
        ('Aerial Operator', lambda p: body_parts(man) + mask_parts(t('aerial_operator', p)), [(-35, 8), (-150, 8)]),
        ('Alice in Wonderland', lambda p: body_parts(man) + wings_parts(t('alice_wings', p)), [(-150, 12), (160, 25)]),
        ('Gauntlets', lambda p: body_parts(man, ra, la) + gauntlet_parts(t('gauntlets', p), ra, la), [(-35, 8), (-150, 8)]),
        ('Greaves', lambda p: body_parts(man) + greaves_parts(t('greaves', p)), [(-35, 8), (-150, 8)]),
        ('Mantle', lambda p: body_parts(man) + mantle_parts(t('mantle', p), 0.25), [(-145, 10), (-35, 8)]),
        ('Blade Belt', lambda p: body_parts(man) + belt_parts(t('blade_belt', p)), [(-30, 14), (-150, 14)]),
    ]
    W, Hh = 256, 276
    sheet = Image.new('RGBA', (W * 4, Hh * len(rows)), (54, 58, 66, 255))
    d = ImageDraw.Draw(sheet)
    for r, (label, parts, views) in enumerate(rows):
        for c, (p, (yaw, pitch)) in enumerate([('steel', views[0]), ('steel', views[1]), ('crimson', views[0]), ('azure', views[1])]):
            sheet.alpha_composite(render(parts(p), None, W, yaw, pitch), (c * W, r * Hh + 20))
        d.text((8, r * Hh + 4), f'{label}  (steel front and back, then crimson and azure palettes)', fill=(235, 235, 240, 255))
    sheet.save(os.path.join(out, 'worn_forms.png'))

    # A Blade Belt arm mid-strike, toward a target on the wearer's right front.
    strike = Image.new('RGBA', (W * 2, Hh), (54, 58, 66, 255))
    strike.alpha_composite(render(body_parts(man) + belt_parts(t('blade_belt', 'steel')), None, W, -30, 14), (0, 20))
    strike.alpha_composite(render(body_parts(man) + belt_parts(t('blade_belt', 'steel'), (0, math.pi + 0.6, 0.45)), None, W, -30, 14), (W, 20))
    ImageDraw.Draw(strike).text((8, 4), 'Blade Belt at rest, and arm 1 mid-strike', fill=(235, 235, 240, 255))
    strike.save(os.path.join(out, 'blade_belt_strike.png'))

    cell = 96
    isheet = Image.new('RGBA', (cell * len(icons), cell * 2), (54, 58, 66, 255))
    for i, (name, (b, a)) in enumerate(icons.items()):
        for r, p in enumerate(('steel', 'crimson')):
            isheet.alpha_composite(gen.composite(b, a, pal[p]).resize((cell - 16, cell - 16), Image.NEAREST), (i * cell + 8, r * cell + 8))
    isheet.save(os.path.join(out, 'worn_icons.png'))


def run(preview_dir=None):
    tex = {'aerial_operator': mask_textures(), 'alice_wings': wings_textures(), 'gauntlets': gauntlet_textures(),
           'mantle': mantle_textures(), 'blade_belt': belt_textures()}
    for name, (b, a) in tex.items():
        save(b, 'entity', 'worn', name + '.png')
        save(a, 'entity', 'worn', name + '_accent.png')
    gb, ga = greaves_layer()
    save(gb, 'models', 'armor', 'greaves_layer_2.png')
    save(ga, 'models', 'armor', 'greaves_layer_2_accent.png')
    # Vanilla asks for layer 1 too for leggings in some paths; an empty one keeps the log clean.
    save(new(64, 32), 'models', 'armor', 'greaves_layer_1.png')
    save(new(64, 32), 'models', 'armor', 'greaves_layer_1_accent.png')
    tex['greaves'] = (gb, ga)
    icons = {}
    for _, item, _ in FORMS:
        icons[item] = icon(item)
        save(icons[item][0], 'item', item + '.png')
        save(icons[item][1], 'item', item + '_accent.png')
    write_models()
    if preview_dir:
        previews(preview_dir, tex, icons)
