#!/usr/bin/env python3
"""Preview renders of held items, using the same transform chain Minecraft uses (dev tool, needs gen.py).

Third person: player model -> arm pivot and rotation -> ItemInHandLayer offsets -> model display transform -> sprite.
First person: camera space -> ItemInHandRenderer arm offset (+ use-animation transforms) -> display -> sprite.
Also solves for display transforms that point an item where we want it (used to design the Lance charge pose).

Usage: python3 tools/pose_preview.py <out_dir>
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen  # noqa: E402

ITEM_TEX = os.path.join(gen.TEX, 'item')
MODELS = os.path.join(gen.ASSETS, 'models', 'item')
DEG = math.pi / 180


# ------------------------------------------------------------------------------------------- matrices

def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y=None, z=None):
    m = np.eye(4)
    m[0, 0], m[1, 1], m[2, 2] = x, (x if y is None else y), (x if z is None else z)
    return m


def R3(r):
    m = np.eye(4)
    m[:3, :3] = r
    return m


def RX(a): return R3(gen.rot_x(a))
def RY(a): return R3(gen.rot_y(a))
def RZ(a): return R3(gen.rot_z(a))


def display(d, left=False):
    """ItemTransform.apply (json rotation in degrees, translation in pixels, already divided by 16 here)."""
    if not d:
        return np.eye(4)
    rx, ry, rz = d.get('rotation', [0, 0, 0])
    tx, ty, tz = [v / 16 for v in d.get('translation', [0, 0, 0])]
    sx, sy, sz = d.get('scale', [1, 1, 1])
    if left:
        ry, rz, tx = -ry, -rz, -tx
    return T(tx, ty, tz) @ RX(rx * DEG) @ RY(ry * DEG) @ RZ(rz * DEG) @ S(sx, sy, sz)


def load_display(model_name, ctx):
    """Follows the parent chain like the game does, returning the first display entry found for ctx."""
    name = model_name
    while name:
        ns, path = (name.split(':') + [None])[:2] if ':' in name else ('minecraft', name)
        if ns == 'kakugane':
            f = os.path.join(MODELS, path.split('/')[-1] + '.json')
        else:
            f = os.path.join(VANILLA, 'assets', 'minecraft', 'models', path + '.json')
        if not os.path.exists(f):
            return None
        m = json.load(open(f))
        if 'display' in m and ctx in m['display']:
            return m['display'][ctx]
        name = m.get('parent')
    return None


VANILLA = os.environ.get('MC_ASSETS', '')

# ------------------------------------------------------------------------------------------- geometry

def sprite_quads(tex_img):
    """A generated item: the sprite as a front and back face in the item's 0..1 cube."""
    zf, zb = 8.5 / 16, 7.5 / 16
    front = ([(0, 1, zf), (1, 1, zf), (1, 0, zf), (0, 0, zf)], [(0, 0), (16, 0), (16, 16), (0, 16)])
    back = ([(1, 1, zb), (0, 1, zb), (0, 0, zb), (1, 0, zb)], [(16, 0), (0, 0), (0, 16), (16, 16)])
    return [(front, tex_img), (back, tex_img)]


def item_tris(tex_img, m):
    out = []
    for (verts, uvs), tex in sprite_quads(tex_img):
        p = [(m @ np.array([*v, 1.0]))[:3] for v in verts]
        out.append((p, uvs, tex))
    return out


def cube_tris(origin, size, uv, m, tex, inflate=0.0, mirror=False):
    out = []
    for verts, uvs in gen.cube_polys(origin, size, uv, inflate, mirror):
        p = [(m @ np.array([*v, 1.0]))[:3] for v in verts]
        out.append((p, uvs, tex))
    return out


def player_skin():
    """A plain original skin (64x64): skin-tone head, grey-blue shirt, dark trousers, darker shoes."""
    img = np.zeros((64, 64, 4), dtype=np.uint8)
    skin, shirt, pants, hair = (214, 170, 132), (88, 104, 128), (52, 56, 72), (50, 36, 28)

    def fill(u, v, w, h, d, col_fn):
        for face, (x0, y0, fw, fh) in gen.box_faces(u, v, w, h, d).items():
            for yy in range(fh):
                for xx in range(fw):
                    c = col_fn(face, xx, yy, fw, fh)
                    n = (gen.h(x0 + xx, y0 + yy, 7) - 0.5) * 18
                    img[y0 + yy, x0 + xx] = (*[int(max(0, min(255, k + n))) for k in c], 255)

    def head(face, x, y, w, h):
        if face == 'top' or (y < 2 and face != 'bottom') or face == 'back' and y < 6:
            return hair
        if face == 'front' and y == 4 and x in (1, 2, 5, 6):
            return (255, 255, 255) if x in (1, 6) else (40, 60, 120)
        return skin

    fill(0, 0, 8, 8, 8, head)
    fill(16, 16, 8, 12, 4, lambda f, x, y, w, h: shirt)
    for (u, v) in ((40, 16), (32, 48)):
        fill(u, v, 4, 12, 4, lambda f, x, y, w, h: skin if (y >= h - 4 and f != 'top') or f == 'bottom' else shirt)
    for (u, v) in ((0, 16), (16, 48)):
        fill(u, v, 4, 12, 4, lambda f, x, y, w, h: (30, 30, 34) if y >= h - 2 or f == 'bottom' else pants)
    return img


def player_tris(skin, right_arm, left_arm):
    """Player model in model space (pixels, y down, facing -z). Arm angles are (xRot, yRot, zRot)."""
    tris = []
    I = np.eye(4)
    tris += cube_tris((-4, -8, -4), (8, 8, 8), (0, 0), I, skin)
    tris += cube_tris((-4, 0, -2), (8, 12, 4), (16, 16), I, skin)
    tris += cube_tris((-3, -2, -2), (4, 12, 4), (40, 16), arm_matrix(-5, right_arm, pixels=True), skin)
    tris += cube_tris((-1, -2, -2), (4, 12, 4), (32, 48), arm_matrix(5, left_arm, pixels=True), skin)
    tris += cube_tris((-2, 0, -2), (4, 12, 4), (0, 16), T(-1.9, 12, 0), skin)
    tris += cube_tris((-2, 0, -2), (4, 12, 4), (16, 48), T(1.9, 12, 0), skin)
    return tris


def arm_matrix(px, rot, pixels=False):
    """ModelPart.translateAndRotate for an arm at pivot (px, 2, 0): rotation order Z * Y * X."""
    xr, yr, zr = rot
    r = RZ(zr) @ RY(yr) @ RX(xr)
    return T(px, 2, 0) @ r if pixels else T(px / 16, 2 / 16, 0) @ r


def third_person_item(right_arm, disp):
    """ItemInHandLayer for the right hand, in block units, then scaled to pixels."""
    m = arm_matrix(-5, right_arm) @ RX(-90 * DEG) @ RY(180 * DEG) @ T(1 / 16, 0.125, -0.625) @ disp @ T(-0.5, -0.5, -0.5)
    return S(16) @ m

# ------------------------------------------------------------------------------------------- raster

def raster(tris, project, W, H, ss=3, bg=(58, 62, 72, 255)):
    Wp, Hp = W * ss, H * ss
    img = np.zeros((Hp, Wp, 4), dtype=np.float32)
    img[:, :] = bg
    zbuf = np.full((Hp, Wp), np.inf)
    light = np.array([0.3, 0.8, 0.5])
    light /= np.linalg.norm(light)
    for pts, uvs, tex in tris:
        P = [project(p) for p in pts]
        if any(p is None for p in P):
            continue
        a, b, c = (np.array(pts[i]) for i in range(3))
        nrm = np.cross(b - a, c - a)
        if np.linalg.norm(nrm) < 1e-12:
            continue
        shade = 0.6 + 0.4 * abs(float(nrm / np.linalg.norm(nrm) @ light))
        th, tw = tex.shape[:2]
        sc_u, sc_v = tw / 16 if tw == 16 else 1, th / 16 if th == 16 else 1
        for tri in ((0, 1, 2), (0, 2, 3)):
            q = [P[i] for i in tri]
            U = [uvs[i] for i in tri]
            xs, ys = [p[0] * ss for p in q], [p[1] * ss for p in q]
            x0, x1 = int(max(0, min(xs))), int(min(Wp - 1, max(xs)))
            y0, y1 = int(max(0, min(ys))), int(min(Hp - 1, max(ys)))
            if x1 < x0 or y1 < y0:
                continue
            (ax, ay), (bx, by), (cx, cy) = (xs[0], ys[0]), (xs[1], ys[1]), (xs[2], ys[2])
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
            w1 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            zz = w0 * q[0][2] + w1 * q[1][2] + w2 * q[2][2]
            uu = (w0 * U[0][0] + w1 * U[1][0] + w2 * U[2][0]) * sc_u
            vv = (w0 * U[0][1] + w1 * U[1][1] + w2 * U[2][1]) * sc_v
            tx = np.clip(np.floor(uu).astype(int), 0, tw - 1)
            ty = np.clip(np.floor(vv).astype(int), 0, th - 1)
            px = tex[ty, tx].astype(np.float32)
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (px[:, :, 3] > 10) & (zz < sub)
            sub[ok] = zz[ok]
            region = img[y0:y1 + 1, x0:x1 + 1]
            col = px.copy()
            col[:, :, :3] *= shade
            col[:, :, 3] = 255
            region[ok] = col[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').resize((W, H), Image.LANCZOS)


def third_person_view(tris, W=300, H=300, yaw=-60, pitch=12, scale=7.0):
    view = gen.rot_x(pitch * DEG) @ gen.rot_y(yaw * DEG)

    def project(p):
        q = view @ np.array([-p[0], -p[1], p[2]])  # entity renderer flips x and y
        return (W / 2 + q[0] * scale, H * 0.42 - q[1] * scale, q[2])
    return raster(tris, project, W, H)


def first_person_view(tris, W=480, H=270, fov=70):
    f = (H / 2) / math.tan(fov * DEG / 2)

    def project(p):
        if p[2] > -0.05:
            return None
        return (W / 2 + f * p[0] / -p[2], H / 2 - f * p[1] / -p[2], -p[2])
    img = raster(tris, project, W, H)
    # crosshair
    a = np.array(img)
    a[H // 2, W // 2 - 6:W // 2 + 7] = (255, 255, 255, 255)
    a[H // 2 - 6:H // 2 + 7, W // 2] = (255, 255, 255, 255)
    return Image.fromarray(a)


def composite_tex(name, rgb):
    base = np.array(Image.open(os.path.join(ITEM_TEX, name + '.png')).convert('RGBA'))
    acc_p = os.path.join(ITEM_TEX, name + '_accent.png')
    if os.path.exists(acc_p):
        acc = np.array(Image.open(acc_p).convert('RGBA'))
        return np.array(gen.composite(base, acc, rgb))
    return base


def label(img, text):
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width, 16], fill=(30, 32, 38, 255))
    d.text((6, 3), text, fill=(235, 235, 240, 255))
    return img


def sheet(images, cols):
    w, h = images[0].width, images[0].height
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGBA', (w * cols, h * rows), (40, 42, 48, 255))
    for i, im in enumerate(images):
        out.alpha_composite(im, ((i % cols) * w, (i // cols) * h))
    return out


# ------------------------------------------------------------------------------------------- pose solving

def norm(v):
    v = np.array(v, float)
    return v / np.linalg.norm(v)


def frame(d, n):
    d, n = norm(d), norm(n)
    n = norm(n - d * (n @ d))
    return np.stack([d, n, np.cross(d, n)], 1)


def euler_xyz(m):
    """Angles (degrees) a, b, c with m = Rx(a) Ry(b) Rz(c), the order ItemTransform uses."""
    b = math.asin(max(-1, min(1, m[0, 2])))
    a = math.atan2(-m[1, 2], m[2, 2])
    c = math.atan2(-m[0, 1], m[0, 0])
    return [round(math.degrees(x), 2) for x in (a, b, c)]


# The Lance sprite: where the hand grips it, which way the blade points, and the sprite's face normal.
LANCE_GRIP = np.array([4.5 / 16, 1 - 11.5 / 16, 0.5])
LANCE_DIR, SPRITE_NORMAL = norm([1, 1, 0]), np.array([0, 0, 1.0])

# Lance charge design (right hand). Third person: arm a little forward, lance couched at the hip, level.
CHARGE_ARM = (-0.42, 0.12, 0.0)
CHARGE_TP = dict(direction=[0.0, -0.05, -1], normal=[-1, 0, 0], scale=1.25)
# First person (camera space: x right, y up, -z forward): grip lower right, blade toward just past the crosshair.
CHARGE_FP = dict(grip=[0.40, -0.44, -0.80], direction=[-0.30, 0.20, -1], normal=[0.15, 1.0, 0.5], scale=0.85, pullback=0.2)


def solve_third(arm, direction, normal, scale, hand_local=(-1, 9.5, 0)):
    """Display entry that puts the grip in the hand and the blade along `direction` (model space)."""
    B = arm_matrix(-5, arm) @ RX(-90 * DEG) @ RY(180 * DEG) @ T(1 / 16, 0.125, -0.625)
    want = frame(direction, normal) @ np.linalg.inv(frame(LANCE_DIR, SPRITE_NORMAL))
    rot = euler_xyz(np.linalg.inv(B[:3, :3]) @ want)
    rq = (RX(rot[0] * DEG) @ RY(rot[1] * DEG) @ RZ(rot[2] * DEG))[:3, :3]
    hand = (arm_matrix(-5, arm, pixels=True) @ np.array([*hand_local, 1.0]))[:3] / 16
    td = (np.linalg.inv(B) @ np.array([*hand, 1.0]))[:3] - rq @ (scale * (LANCE_GRIP - 0.5))
    return {'rotation': rot, 'translation': [round(float(v) * 16, 3) for v in td], 'scale': [scale] * 3}


def charge_placement(grip, direction, normal, scale):
    """Where the lance sprite should end up in camera space (right hand)."""
    want = np.eye(4)
    want[:3, :3] = frame(direction, normal) @ np.linalg.inv(frame(LANCE_DIR, SPRITE_NORMAL)) * scale
    return T(*grip) @ want @ T(*(-LANCE_GRIP))


def solve_first(placement, fp_display, left=False):
    """Camera-space matrix H with H @ display @ T(-.5) == placement (the game applies the display after H)."""
    return placement @ np.linalg.inv(display(fp_display, left) @ T(-0.5, -0.5, -0.5))


def java_matrix(h):
    """JOML Matrix4f constructor arguments (column-major)."""
    return ', '.join(f'{h[r, c]:.6f}f' for c in range(4) for r in range(4))


def mirror_x(h):
    s = np.diag([-1.0, 1, 1, 1])
    return s @ h @ s


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(gen.ROOT, 'build', 'previews')
    os.makedirs(out, exist_ok=True)
    print('third person charge display:', json.dumps(solve_third(CHARGE_ARM, **CHARGE_TP)))
    place = charge_placement(CHARGE_FP['grip'], CHARGE_FP['direction'], CHARGE_FP['normal'], CHARGE_FP['scale'])
    h_right = solve_first(place, load_display('kakugane:item/form_lance', 'firstperson_righthand'))
    h_left = solve_first(mirror_x(place), load_display('kakugane:item/form_lance', 'firstperson_lefthand'), left=True)
    print('first person RIGHT:', java_matrix(h_right))
    print('first person LEFT :', java_matrix(h_left))
    print('pullback direction:', [round(float(v), 6) for v in -norm(CHARGE_FP['direction']) * CHARGE_FP['pullback']])
