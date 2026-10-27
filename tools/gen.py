#!/usr/bin/env python3
"""Generates Kakugane's textures and item models from scratch (no third-party art), plus preview renders.

Run from the project root:  python3 tools/gen.py [preview_dir]
Writes into src/main/resources/assets/kakugane/. Previews go to build/previews unless a folder is given.
All randomness is a fixed hash, so the output is identical every run.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'kakugane')
TEX = os.path.join(ASSETS, 'textures')
PREVIEW = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'previews')

# ---------------------------------------------------------------------------------------------- helpers

def h(x, y, seed=0):
    """Deterministic 0..1 noise."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def ramp(colors, t):
    t = max(0.0, min(1.0, t))
    i = t * (len(colors) - 1)
    a, b = int(math.floor(i)), min(len(colors) - 1, int(math.floor(i)) + 1)
    f = i - a
    return tuple(int(colors[a][k] + (colors[b][k] - colors[a][k]) * f) for k in range(3))


STEEL = [(38, 42, 50), (78, 86, 98), (128, 138, 150), (182, 190, 200), (232, 236, 242)]
DARK = [(14, 14, 18), (30, 30, 36), (48, 46, 54), (70, 66, 74), (98, 92, 100)]
GREY = [(40, 40, 40), (90, 90, 90), (150, 150, 150), (205, 205, 205), (250, 250, 250)]
WOOD = [(40, 26, 18), (70, 46, 30), (102, 70, 44), (132, 96, 62), (160, 120, 80)]


def new(w, h_):
    return np.zeros((h_, w, 4), dtype=np.uint8)


def put(img, x, y, rgb, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*rgb, a)


def save(img, *path):
    full = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    Image.fromarray(img, 'RGBA').save(full)


def outline(img, rgb=(18, 18, 24)):
    """1px dark outline around opaque pixels (items)."""
    a = img[:, :, 3] > 0
    out = img.copy()
    hgt, wid = a.shape
    for y in range(hgt):
        for x in range(wid):
            if a[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < wid and 0 <= ny < hgt and a[ny, nx]:
                    out[y, x] = (*rgb, 255)
                    break
    return out


def line(img, x0, y0, x1, y1, color_fn):
    """Bresenham line; color_fn(i, n) gives the colour of point i of n."""
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    for i, (x, y) in enumerate(pts):
        c = color_fn(i, len(pts))
        if c:
            put(img, x, y, c)
    return pts


def hexagon(cx, cy, r):
    """Pointy-top hexagon test."""
    def inside(x, y):
        dx, dy = abs(x - cx), abs(y - cy)
        return dy <= r and dx <= r * 0.866 and (dy + dx * 0.577) <= r
    return inside

# ---------------------------------------------------------------------------------------------- items

def plate(active=False):
    base, acc = new(16, 16), new(16, 16)
    inside = hexagon(7.5, 7.5, 7.2)
    inner = hexagon(7.5, 7.5, 5.2)
    for y in range(16):
        for x in range(16):
            if not inside(x, y):
                continue
            t = 0.75 - (y / 15) * 0.35 - (x / 15) * 0.1 + (h(x, y, 3) - 0.5) * 0.08
            if not inner(x, y):
                t += 0.12  # raised rim
            put(base, x, y, ramp(STEEL, t))
            if inside(x, y) and not inner(x, y) and inner(x + 1, y) is False and (x + y) % 2 == 0:
                pass
    # Engraved numeral (a stylised "C" for "Centuria" plus bar) in the accent layer: tinted by the palette.
    mark = [(6, 5), (7, 5), (8, 5), (5, 6), (5, 7), (5, 8), (6, 9), (7, 9), (8, 9), (10, 5), (10, 6), (10, 7), (10, 8), (10, 9)]
    for x, y in mark:
        put(acc, x, y, ramp(GREY, 0.85 if active else 0.6))
    # Inner ring line in the accent colour.
    for y in range(16):
        for x in range(16):
            if inner(x, y) and not hexagon(7.5, 7.5, 4.4)(x, y) and h(x, y, 9) > (0.0 if active else 0.45):
                put(acc, x, y, ramp(GREY, 0.95 if active else 0.7))
    return outline(base), acc


def lance():
    base, acc = new(16, 16), new(16, 16)
    # Shaft from bottom-left to the guard.
    line(base, 1, 14, 8, 7, lambda i, n: ramp(WOOD, 0.35 + 0.4 * (i / n)))
    line(base, 2, 14, 8, 8, lambda i, n: ramp(WOOD, 0.2 + 0.3 * (i / n)))
    # Guard ring.
    for x, y in [(7, 6), (8, 6), (9, 7), (9, 8), (7, 9), (6, 8)]:
        put(base, x, y, ramp(STEEL, 0.55))
    # Blade: long cone to the top-right corner.
    for i in range(7):
        w = 2 if i < 4 else 1
        cx, cy = 9 + i, 6 - i
        for k in range(-w + 1, w):
            put(base, cx + k, cy + k, ramp(STEEL, 0.55 + 0.06 * i + (0.1 if k > 0 else 0)))
            put(base, cx + k + 1, cy + k, ramp(STEEL, 0.4 + 0.06 * i))
    put(base, 15, 0, ramp(STEEL, 1.0))
    # Ribbon (accent) trailing from the guard: Sunlight Heart's ribbon.
    for x, y in [(6, 7), (5, 7), (4, 8), (3, 8), (2, 9), (2, 10), (1, 10), (4, 9), (3, 10)]:
        put(acc, x, y, ramp(GREY, 0.8 if (x + y) % 2 else 0.65))
    # Edge line on the blade.
    for i in range(6):
        put(acc, 10 + i, 5 - i, ramp(GREY, 0.95))
    return outline(base), acc


def bow(pull=-1):
    base, acc = new(16, 16), new(16, 16)
    # Limb: arc from top-left to bottom-right (like the vanilla bow's diagonal).
    pts = []
    for i in range(23):
        t = i / 22
        x = 2 + 11 * t + 3.2 * math.sin(math.pi * t)
        y = 2 + 11 * t - 3.2 * math.sin(math.pi * t)
        pts.append((int(round(x)), int(round(y))))
    for k, (x, y) in enumerate(pts):
        put(base, x, y, ramp(STEEL, 0.45 + 0.4 * math.sin(math.pi * k / 22)))
    # Wing ornaments at both tips (accent).
    for x, y in [(1, 1), (2, 0), (1, 3), (0, 2), (14, 15), (15, 14), (13, 15), (15, 13)]:
        put(acc, x, y, ramp(GREY, 0.85))
    for x, y in [(3, 1), (1, 4), (12, 14), (14, 12)]:
        put(acc, x, y, ramp(GREY, 0.65))
    # String, pulled back toward the bottom-left as the draw increases.
    back = [0, 1, 2, 3][pull + 1] if pull >= 0 else 0
    sx, sy = 2 - back // 2, 2 + back
    ex, ey = 13 + 0, 13
    mx, my = 7 - back, 7 + back
    line(base, sx + 1, sy + 1, mx, my, lambda i, n: (235, 235, 245))
    line(base, mx, my, ex - 1 + (0 if back == 0 else 0), ey - 1 if back == 0 else ey - 1, lambda i, n: (235, 235, 245))
    if pull >= 0:
        # Conjured arrow of light (accent, so it takes the palette colour).
        L = 4 + pull * 2
        for i in range(L):
            put(acc, mx + i, my - i, ramp(GREY, 0.75 + 0.25 * (i / max(1, L - 1))))
    return outline(base), acc


def mirror(layers):
    return tuple(np.ascontiguousarray(img[:, ::-1]) for img in layers)


def breaker():
    base, acc = new(16, 16), new(16, 16)
    line(base, 1, 14, 9, 6, lambda i, n: ramp(WOOD, 0.3 + 0.4 * (i / n)))
    line(base, 2, 14, 9, 7, lambda i, n: ramp(WOOD, 0.15 + 0.3 * (i / n)))
    # Hammer head: a heavy block rotated 45 degrees at the top-right.
    for y in range(0, 11):
        for x in range(5, 16):
            u, v = (x - y), (x + y)
            if -2 <= u - 8 <= 2 and 10 <= v <= 20 and (x, y) != (15, 0):
                t = 0.5 + (u - 8) * 0.08 - (v - 15) * 0.02 + (h(x, y, 5) - 0.5) * 0.06
                put(base, x, y, ramp(STEEL, t))
            elif -3 <= u - 8 <= 3 and 11 <= v <= 19:
                put(base, x, y, ramp(STEEL, 0.25))
    # Hex emblem on the head (accent).
    for x, y in [(10, 5), (11, 4), (12, 5), (12, 6), (11, 7), (10, 6), (11, 5), (11, 6)]:
        put(acc, x, y, ramp(GREY, 0.85 if (x + y) % 2 else 0.7))
    return outline(base), acc


def embryo():
    img = new(16, 16)
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5) / 6.0, (y - 8.0) / 6.5)
            if d <= 1.0:
                t = 0.8 - d * 0.5 + (h(x, y, 11) - 0.5) * 0.1
                put(img, x, y, ramp([(70, 16, 24), (130, 34, 44), (186, 70, 78), (226, 130, 132), (250, 196, 190)], t))
    # Curled embryo inside.
    for x, y in [(6, 6), (7, 6), (8, 6), (9, 7), (9, 8), (9, 9), (8, 10), (7, 10), (6, 9), (6, 8), (7, 8)]:
        put(img, x, y, (52, 8, 16))
    put(img, 7, 7, (240, 60, 60))  # eye
    put(img, 5, 5, (255, 236, 230))  # highlight
    return outline(img)


ARMOR_SHAPES = {
    'helmet': ["....######......", "...########.....", "..##########....", "..###....###....", "..##......##....", "..##......##....", "...#......#....."],
    'chestplate': ["..###....###....", ".#####..#####...", ".############...", ".############...", "..##########....", "..##########....",
                   "..##########....", "..##########....", "..##########....", "...########....."],
    'leggings': ["..##########....", "..##########....", "..####..####....", "..###....###....", "..###....###....", "..###....###....",
                 "..###....###....", "..###....###....", "..###....###...."],
    'boots': ["..###....###....", "..###....###....", ".####....####...", ".####....####..."],
}


def armor_icon(kind):
    base, acc = new(16, 16), new(16, 16)
    rows = ARMOR_SHAPES[kind]
    top = (16 - len(rows)) // 2
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '#':
                x, y = i + 1, top + j
                t = 0.7 - j / len(rows) * 0.3 + ((x + y) % 3 == 0) * 0.08
                put(base, x, y, ramp(STEEL, t))
                # Accent: the edge pixels of the shape (trim).
                edge = (i == 0 or row[i - 1] != '#' or i == len(row) - 1 or row[i + 1] != '#'
                        or j == 0 or rows[j - 1][i] != '#')
                if edge:
                    put(acc, x, y, ramp(GREY, 0.8))
    return outline(base), acc

# ---------------------------------------------------------------------------------------------- armor layers

def hex_metal(x, y, light=1.0):
    """Hexagonal plate pattern: dark seams on a 6x5 offset grid."""
    row = y // 5
    xo = (x + (3 if row % 2 else 0)) % 6
    yo = y % 5
    seam = yo == 0 or xo == 0
    t = (0.62 if not seam else 0.32) * light + (h(x, y, 21) - 0.5) * 0.08
    if yo == 1 and not seam:
        t += 0.1
    return ramp(STEEL, t)


def humanoid_boxes(layer):
    """(u, v, w, h, d) of the vanilla humanoid armor boxes used on each layer."""
    if layer == 1:
        return [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, 4, 12, 4), (0, 16, 4, 12, 4)]
    return [(16, 16, 8, 12, 4), (0, 16, 4, 12, 4)]


def box_faces(u, v, w, hgt, d):
    """Pixel rectangles of each face: name -> (x0, y0, width, height)."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, hgt), 'front': (u + d, v + d, w, hgt),
        'left': (u + d + w, v + d, d, hgt), 'back': (u + 2 * d + w, v + d, w, hgt),
    }


def armor_layers(layer):
    base, acc = new(64, 32), new(64, 32)
    for (u, v, w, hgt, d) in humanoid_boxes(layer):
        for face, (x0, y0, fw, fh) in box_faces(u, v, w, hgt, d).items():
            light = {'top': 1.15, 'bottom': 0.7, 'front': 1.0, 'back': 0.85, 'left': 0.9, 'right': 0.9}[face]
            for yy in range(fh):
                for xx in range(fw):
                    x, y = x0 + xx, y0 + yy
                    c = hex_metal(x, y, light)
                    if yy == 0 and face not in ('top', 'bottom'):
                        c = tuple(min(255, int(k * 1.15)) for k in c)
                    if yy == fh - 1 or xx == 0 or xx == fw - 1:
                        c = tuple(int(k * 0.8) for k in c)
                    put(base, x, y, c)
                    # Accent trim: cuffs and hems (bottom two rows of vertical faces), and a centre line on the chest.
                    if face not in ('top', 'bottom') and yy >= fh - 2 and (xx // 2) % 3 != 2:
                        put(acc, x, y, ramp(GREY, 0.85 if yy == fh - 2 else 0.65))
                    if layer == 1 and (u, v) == (16, 16) and face == 'front' and xx in (3, 4) and yy < fh - 2 and yy % 3 != 2:
                        put(acc, x, y, ramp(GREY, 0.8))
                    if (u, v) == (0, 0) and face == 'front' and yy == 3 and 1 <= xx <= 6 and xx not in (3, 4):
                        put(acc, x, y, ramp(GREY, 0.95))  # visor slits
    return base, acc

# ---------------------------------------------------------------------------------------------- hound

# Cubes of HoundModel: name, (u, v), (x, y, z), (w, h, d), part
HOUND_CUBES = {
    'skull': ((0, 0), (6, 6, 4)), 'ear': ((16, 14), (2, 2, 1)), 'jaw': ((0, 10), (3, 2, 4)),
    'lower_jaw': ((50, 22), (3, 1, 3)), 'body': ((18, 14), (6, 9, 6)), 'mane': ((21, 0), (8, 6, 7)),
    'spine': ((52, 0), (1, 5, 2)), 'leg': ((0, 18), (2, 8, 2)), 'tail': ((9, 18), (2, 8, 2)),
}
HIDE = [(18, 16, 20), (34, 30, 36), (52, 46, 54), (74, 66, 74), (98, 88, 96)]
FLESH = [(60, 10, 18), (104, 20, 30), (150, 34, 44), (190, 64, 70), (220, 110, 110)]
BONE = [(110, 100, 86), (160, 150, 130), (200, 190, 168), (226, 218, 198), (246, 240, 226)]


def hound_textures():
    base, glow = new(64, 32), new(64, 32)
    for name, ((u, v), (w, hgt, d)) in HOUND_CUBES.items():
        for face, (x0, y0, fw, fh) in box_faces(u, v, w, hgt, d).items():
            light = {'top': 1.15, 'bottom': 0.65, 'front': 1.0, 'back': 0.85, 'left': 0.9, 'right': 0.9}[face]
            for yy in range(fh):
                for xx in range(fw):
                    x, y = x0 + xx, y0 + yy
                    n = (h(x, y, 31) - 0.5) * 0.14 + (h(x // 2, y // 2, 32) - 0.5) * 0.08
                    if name in ('spine', 'lower_jaw') or (name == 'jaw' and face == 'bottom'):
                        c = ramp(BONE, 0.55 * light + n)
                    elif name == 'mane' or name == 'body':
                        # Hide with raw red seams running along the ribs.
                        seam = (yy % 4 == 2) and face in ('left', 'right', 'bottom') and h(x, y, 33) > 0.3
                        c = ramp(FLESH, 0.45 + n) if seam else ramp(HIDE, 0.55 * light + n)
                    elif name == 'leg' and yy >= fh - 2:
                        c = ramp(BONE, 0.35 + n)  # claws
                    else:
                        c = ramp(HIDE, 0.55 * light + n)
                    if yy == fh - 1 and face not in ('top', 'bottom'):
                        c = tuple(int(k * 0.8) for k in c)
                    put(base, x, y, c)
    # Eyes on the skull front face (front = (u+d, v+d) = (4, 4), 6x6): two narrow slits.
    for ex in (4 + 1, 4 + 4):
        put(base, ex, 4 + 2, (90, 0, 0))
        put(glow, ex, 4 + 2, (255, 40, 40))
    # Teeth on the upper jaw front face (front = (4, 14), 3x2).
    for tx in range(4, 7):
        put(base, tx, 15, ramp(BONE, 0.85 if tx % 2 else 0.6))
    # Glowing embryo core in the chest: centre of both mane faces that end up facing forward/down.
    for face in ('top', 'bottom'):
        x0, y0, fw, fh = box_faces(21, 0, 8, 6, 7)[face]
        cx, cy = x0 + fw // 2, y0 + fh // 2
        for dx, dy in ((0, 0), (-1, 0), (0, -1), (-1, -1), (1, -1), (-2, -1), (0, 1), (-1, 1)):
            put(base, cx + dx, cy + dy, ramp(FLESH, 0.7))
            put(glow, cx + dx, cy + dy, (255, 90, 70) if (dx, dy) in ((0, 0), (-1, 0), (0, -1), (-1, -1)) else (170, 30, 30))
    return base, glow


def alchemic_arrow():
    """32x32 arrow texture in the vanilla arrow layout (shaft rows 0-4, back cross rows 5-9)."""
    img = new(32, 32)
    core, edge = (250, 250, 255), (170, 210, 255)
    for x in range(16):
        put(img, x, 2, core)
        put(img, x, 1 if x % 3 else 3, edge)
    for x in range(12, 16):
        put(img, x, 1, core)
        put(img, x, 3, core)
    for x in (0, 1, 2):
        put(img, x, 0, edge)
        put(img, x, 4, edge)
    for y in range(5, 10):
        for x in range(5):
            if x == 2 or y == 7:
                put(img, x, y, core if (x == 2 and y == 7) else edge)
    return img

# ---------------------------------------------------------------------------------------------- models

def item_model(name, layers, parent='minecraft:item/generated', overrides=None, display=None):
    m = {'parent': parent, 'textures': {f'layer{i}': f'kakugane:item/{t}' for i, t in enumerate(layers)}}
    if overrides:
        m['overrides'] = overrides
    if display:
        m['display'] = display
    path = os.path.join(ASSETS, 'models', 'item', name + '.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(m, f, indent=2)
        f.write('\n')


BIG_HANDHELD = {
    'thirdperson_righthand': {'rotation': [0, -90, 55], 'translation': [0, 4.0, 0.5], 'scale': [1.15, 1.15, 0.85]},
    'thirdperson_lefthand': {'rotation': [0, 90, -55], 'translation': [0, 4.0, 0.5], 'scale': [1.15, 1.15, 0.85]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.8, 0.8, 0.8]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.8, 0.8, 0.8]},
}


# Third-person display while charging the Lance: couched at the hip, levelled forward. Solved in
# tools/pose_preview.py (solve_third with CHARGE_ARM / CHARGE_TP); the arm pose itself is client/LancePose.java.
_LC_ROT, _LC_T, _LC_S = [-114.06, 83.12, 137.86], [-0.74, 3.728, -1.719], [1.25, 1.25, 1.25]
LANCE_CHARGE_DISPLAY = {
    'thirdperson_righthand': {'rotation': _LC_ROT, 'translation': _LC_T, 'scale': _LC_S},
    # Left hand uses vanilla's convention: the game negates Y/Z rotation and X translation itself.
    'thirdperson_lefthand': {'rotation': [_LC_ROT[0], -_LC_ROT[1], -_LC_ROT[2]], 'translation': _LC_T, 'scale': _LC_S},
}


def write_models():
    f = 'kakugane:form'
    item_model('kakugane', ['kakugane_plate', 'kakugane_plate_accent'], overrides=[
        {'predicate': {f: 1}, 'model': 'kakugane:item/form_lance'},
        # Matched only for a charging lance: overrides are checked last-first and every value is a minimum, so
        # this must sit before the bow entries (a drawing bow, form 2, would also pass form >= 1).
        {'predicate': {f: 1, 'kakugane:pulling': 1}, 'model': 'kakugane:item/form_lance_charging'},
        {'predicate': {f: 2}, 'model': 'kakugane:item/form_bow'},
        {'predicate': {f: 2, 'kakugane:pulling': 1}, 'model': 'kakugane:item/form_bow_pulling_0'},
        {'predicate': {f: 2, 'kakugane:pulling': 1, 'kakugane:pull': 0.65}, 'model': 'kakugane:item/form_bow_pulling_1'},
        {'predicate': {f: 2, 'kakugane:pulling': 1, 'kakugane:pull': 0.9}, 'model': 'kakugane:item/form_bow_pulling_2'},
        {'predicate': {f: 3}, 'model': 'kakugane:item/form_breaker'},
        {'predicate': {f: 4}, 'model': 'kakugane:item/form_silver_skin'},
    ])
    item_model('form_lance', ['form_lance', 'form_lance_accent'], parent='minecraft:item/handheld', display=BIG_HANDHELD)
    item_model('form_lance_charging', ['form_lance', 'form_lance_accent'], parent='kakugane:item/form_lance', display=LANCE_CHARGE_DISPLAY)
    item_model('form_breaker', ['form_breaker', 'form_breaker_accent'], parent='minecraft:item/handheld', display=BIG_HANDHELD)
    item_model('form_bow', ['form_bow', 'form_bow_accent'], parent='minecraft:item/bow')
    for i in range(3):
        item_model(f'form_bow_pulling_{i}', [f'form_bow_pulling_{i}', f'form_bow_pulling_{i}_accent'], parent='minecraft:item/bow')
    item_model('form_silver_skin', ['kakugane_plate_active', 'kakugane_plate_active_accent'])
    item_model('homunculus_embryo', ['homunculus_embryo'])
    for k in ('helmet', 'chestplate', 'leggings', 'boots'):
        item_model(f'silver_skin_{k}', [f'silver_skin_{k}', f'silver_skin_{k}_accent'])
    path = os.path.join(ASSETS, 'models', 'item', 'hound_spawn_egg.json')
    with open(path, 'w') as fh:
        json.dump({'parent': 'minecraft:item/template_spawn_egg'}, fh, indent=2)
        fh.write('\n')

# ---------------------------------------------------------------------------------------------- previews

PALETTES = {'steel': (154, 167, 180), 'gold': (232, 185, 49), 'crimson': (194, 39, 45), 'azure': (46, 139, 234),
            'jade': (47, 182, 122), 'violet': (142, 79, 216), 'ivory': (237, 228, 207), 'copper': (200, 112, 63)}


def tint(img, rgb):
    out = img.astype(np.float32).copy()
    out[:, :, :3] *= np.array(rgb, dtype=np.float32) / 255.0
    return out.astype(np.uint8)


def composite(base, acc, rgb):
    a = Image.fromarray(base, 'RGBA')
    a.alpha_composite(Image.fromarray(tint(acc, rgb), 'RGBA'))
    return a


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def cube_polys(origin, size, uv, inflate=0.0, mirror=False):
    """Polygons of a ModelPart cube exactly as Minecraft builds them: list of (4 vertices, 4 uvs)."""
    (x, y, z), (w, hgt, d), (u, v) = origin, size, uv
    x0, y0, z0 = x - inflate, y - inflate, z - inflate
    x1, y1, z1 = x + w + inflate, y + hgt + inflate, z + d + inflate
    if mirror:
        x0, x1 = x1, x0
    v0, v1, v2, v3 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v4, v5, v6, v7 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    f4, f5, f6, f7 = u, u + d, u + d + w, u + d + w + w
    f8, f9 = u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + hgt
    raw = [([v5, v4, v0, v1], f5, f10, f6, f11), ([v2, v3, v7, v6], f6, f11, f7, f10),
           ([v0, v4, v7, v3], f4, f11, f5, f12), ([v1, v0, v3, v2], f5, f11, f6, f12),
           ([v5, v1, v2, v6], f6, f11, f8, f12), ([v4, v5, v6, v7], f8, f11, f9, f12)]
    out = []
    for verts, ua, va, ub, vb in raw:
        uvs = [(ub, va), (ua, va), (ua, vb), (ub, vb)]
        if mirror:
            verts = verts[::-1]
            uvs = uvs[::-1]
        out.append((verts, uvs))
    return out


def render(parts, tex, size=256, yaw=-35, pitch=18, scale=None):
    """Tiny orthographic rasteriser for model previews. parts: list of (polys, 4x4 transform[, texture])."""
    tris = []
    for part in parts:
        polys, m = part[0], part[1]
        ptex = part[2] if len(part) > 2 else tex
        for verts, uvs in polys:
            pv = [m[:3, :3] @ np.array(p, dtype=float) + m[:3, 3] for p in verts]
            tris.append((pv, uvs, ptex))
    view = rot_x(math.radians(pitch)) @ rot_y(math.radians(yaw))
    allp = []
    proj = []
    for pv, uvs, ptex in tris:
        q = [view @ np.array([-p[0], -p[1], p[2]]) for p in pv]  # flip x and y like the entity renderer
        proj.append((q, uvs, ptex))
        allp += q
    allp = np.array(allp)
    mn, mx = allp.min(0), allp.max(0)
    ss = 3
    S = size * ss
    sc = scale or (0.8 * S / max(mx[0] - mn[0], mx[1] - mn[1]))
    cx, cy = (mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2
    img = np.zeros((S, S, 4), dtype=np.float32)
    zbuf = np.full((S, S), -1e9)
    light_dir = np.array([0.35, 0.85, -0.4])
    light_dir /= np.linalg.norm(light_dir)
    for q, uvs, ptex in proj:
        th, tw = ptex.shape[:2]
        a, b, c = np.array(q[0]), np.array(q[1]), np.array(q[2])
        nrm = np.cross(b - a, c - a)
        if np.linalg.norm(nrm) == 0:
            continue
        nrm /= np.linalg.norm(nrm)
        shade = 0.55 + 0.45 * max(0.0, abs(float(nrm @ light_dir)))
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = [((q[i][0] - cx) * sc + S / 2, (-(q[i][1] - cy)) * sc + S / 2, q[i][2]) for i in tri]
            U = [uvs[i] for i in tri]
            xs, ys = [p[0] for p in P], [p[1] for p in P]
            x0, x1 = int(max(0, min(xs))), int(min(S - 1, max(xs)))
            y0, y1 = int(max(0, min(ys))), int(min(S - 1, max(ys)))
            (ax, ay, az), (bx, by, bz), (qx, qy, qz) = P
            den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((by - qy) * (gx - qx) + (qx - bx) * (gy - qy)) / den
            w1 = ((qy - ay) * (gx - qx) + (ax - qx) * (gy - qy)) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            zz = w0 * az + w1 * bz + w2 * qz
            uu = w0 * U[0][0] + w1 * U[1][0] + w2 * U[2][0]
            vv = w0 * U[0][1] + w1 * U[1][1] + w2 * U[2][1]
            tx = np.clip(np.floor(uu).astype(int), 0, tw - 1)
            ty = np.clip(np.floor(vv).astype(int), 0, th - 1)
            px = ptex[ty, tx].astype(np.float32)
            ok = inside & (px[:, :, 3] > 10)
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            closer = ok & (-zz > sub)  # nearer = larger -z after the view flip
            sub[closer] = -zz[closer]
            region = img[y0:y1 + 1, x0:x1 + 1]
            col = px.copy()
            col[:, :, :3] *= shade
            region[closer] = col[closer]
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA')
    return out.resize((size, size), Image.LANCZOS)


def mat(tx=0.0, ty=0.0, tz=0.0, rx=0.0, ry=0.0, rz=0.0, parent=None):
    m = np.eye(4)
    m[:3, :3] = rot_z(rz) @ rot_y(ry) @ rot_x(rx)
    m[:3, 3] = (tx, ty, tz)
    return m if parent is None else parent @ m


def hound_parts():
    hp = math.pi / 2
    head = mat(-1, 13.5, -7)
    mane = mat(-1, 14, -3, rx=hp)
    tail = mat(-1, 12, 8, rx=0.9)
    return [
        (cube_polys((-2, -3, -2), (6, 6, 4), (0, 0)), head),
        (cube_polys((-2, -5, 0), (2, 2, 1), (16, 14)), head),
        (cube_polys((2, -5, 0), (2, 2, 1), (16, 14)), head),
        (cube_polys((-0.5, -0.5, -5), (3, 2, 4), (0, 10)), head),
        (cube_polys((-0.5, 1.6, -4.6), (3, 1, 3), (50, 22)), head),
        (cube_polys((-3, -2, -3), (6, 9, 6), (18, 14)), mat(0, 14, 2, rx=hp)),
        (cube_polys((-3, -3, -3), (8, 6, 7), (21, 0)), mane),
        (cube_polys((-0.5, -2, -5.5), (1, 5, 2), (52, 0)), mat(0.5, 0, 0, parent=mane)),
        (cube_polys((2.5, -2, -5.5), (1, 5, 2), (52, 0)), mat(0.5, 0, 0, parent=mane)),
        (cube_polys((0, 0, -1), (2, 8, 2), (0, 18)), mat(-2.5, 16, 7)),
        (cube_polys((0, 0, -1), (2, 8, 2), (0, 18)), mat(0.5, 16, 7)),
        (cube_polys((0, 0, -1), (2, 8, 2), (0, 18)), mat(-2.5, 16, -4)),
        (cube_polys((0, 0, -1), (2, 8, 2), (0, 18)), mat(0.5, 16, -4)),
        (cube_polys((0, 0, -1), (2, 8, 2), (9, 18)), tail),
    ]


def armor_parts(layer):
    if layer == 1:
        return [
            (cube_polys((-4, -8, -4), (8, 8, 8), (0, 0), 1.0), mat(0, 0, 0)),
            (cube_polys((-4, 0, -2), (8, 12, 4), (16, 16), 1.0), mat(0, 0, 0)),
            (cube_polys((-3, -2, -2), (4, 12, 4), (40, 16), 1.0), mat(-5, 2, 0)),
            (cube_polys((-1, -2, -2), (4, 12, 4), (40, 16), 1.0, mirror=True), mat(5, 2, 0)),
            (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 1.0), mat(-1.9, 12, 0)),
            (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 1.0, mirror=True), mat(1.9, 12, 0)),
        ]
    return [
        (cube_polys((-4, 0, -2), (8, 12, 4), (16, 16), 0.5), mat(0, 0, 0)),
        (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 0.5), mat(-1.9, 12, 0)),
        (cube_polys((-2, 0, -2), (4, 12, 4), (0, 16), 0.5, mirror=True), mat(1.9, 12, 0)),
    ]


def previews(tex):
    os.makedirs(PREVIEW, exist_ok=True)
    # Hound, front three-quarter and side, with the glow layer composited.
    base, glow = tex['hound']
    combo = Image.fromarray(base, 'RGBA')
    combo.alpha_composite(Image.fromarray(glow, 'RGBA'))
    combo = np.array(combo)
    sheet = Image.new('RGBA', (768, 300), (54, 58, 66, 255))
    for i, (yaw, pitch) in enumerate([(-145, 15), (-90, 8), (150, 20)]):
        sheet.alpha_composite(render(hound_parts(), combo, 256, yaw, pitch), (i * 256, 22))
    sheet.save(os.path.join(PREVIEW, 'hound.png'))

    # Silver Skin on a humanoid in three palettes (layer 1 and layer 2 drawn together).
    l1b, l1a = tex['armor1']
    l2b, l2a = tex['armor2']
    sheet = Image.new('RGBA', (768, 300), (54, 58, 66, 255))
    for i, pal in enumerate(['steel', 'crimson', 'azure']):
        t1 = np.array(composite(l1b, l1a, PALETTES[pal]))
        t2 = np.array(composite(l2b, l2a, PALETTES[pal]))
        parts = [(p, m, t2) for p, m in armor_parts(2)] + [(p, m, t1) for p, m in armor_parts(1)]
        sheet.alpha_composite(render(parts, None, 256, -30, 10), (i * 256, 22))
    sheet.save(os.path.join(PREVIEW, 'silver_skin.png'))

    # Item sheet: every item icon at 8x, accent tinted with three palettes.
    names = ['kakugane_plate', 'form_lance', 'form_bow', 'form_bow_pulling_2', 'form_breaker', 'kakugane_plate_active',
             'silver_skin_helmet', 'silver_skin_chestplate', 'silver_skin_leggings', 'silver_skin_boots']
    pals = ['steel', 'gold', 'crimson', 'jade']
    cell = 128
    sheet = Image.new('RGBA', (cell * len(names), cell * len(pals) + cell), (54, 58, 66, 255))
    for c, n in enumerate(names):
        b, a = tex['items'][n]
        for r, pal in enumerate(pals):
            sheet.alpha_composite(composite(b, a, PALETTES[pal]).resize((cell - 16, cell - 16), Image.NEAREST), (c * cell + 8, r * cell + 8))
    sheet.alpha_composite(Image.fromarray(tex['embryo'], 'RGBA').resize((cell - 16, cell - 16), Image.NEAREST), (8, len(pals) * cell + 8))
    sheet.save(os.path.join(PREVIEW, 'items.png'))

# ---------------------------------------------------------------------------------------------- main

def main():
    items = {}
    items['kakugane_plate'] = plate(False)
    items['kakugane_plate_active'] = plate(True)
    items['form_lance'] = lance()
    # Bows are drawn mirrored to match vanilla's bow.png layout (grip lower-left, arc toward the upper left),
    # which the vanilla bow display transforms expect.
    items['form_bow'] = mirror(bow(-1))
    for i in range(3):
        items[f'form_bow_pulling_{i}'] = mirror(bow(i))
    items['form_breaker'] = breaker()
    for k in ('helmet', 'chestplate', 'leggings', 'boots'):
        items[f'silver_skin_{k}'] = armor_icon(k)
    for name, (b, a) in items.items():
        save(b, 'item', name + '.png')
        save(a, 'item', name + '_accent.png')
    emb = embryo()
    save(emb, 'item', 'homunculus_embryo.png')

    armor1, armor2 = armor_layers(1), armor_layers(2)
    save(armor1[0], 'models', 'armor', 'silver_skin_layer_1.png')
    save(armor1[1], 'models', 'armor', 'silver_skin_layer_1_accent.png')
    save(armor2[0], 'models', 'armor', 'silver_skin_layer_2.png')
    save(armor2[1], 'models', 'armor', 'silver_skin_layer_2_accent.png')

    hound = hound_textures()
    save(hound[0], 'entity', 'hound.png')
    save(hound[1], 'entity', 'hound_glow.png')
    save(alchemic_arrow(), 'entity', 'alchemic_arrow.png')

    write_models()
    if '--no-preview' not in sys.argv:
        previews({'items': items, 'embryo': emb, 'armor1': armor1, 'armor2': armor2, 'hound': hound})
    print('generated textures and models')


if __name__ == '__main__':
    main()
