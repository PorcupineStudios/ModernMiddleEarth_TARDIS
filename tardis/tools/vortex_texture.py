"""Makes the time vortex textures (Aaron 2026-11-09: a background for the time vortex dimension).

Everything is drawn from periodic noise (made in Fourier space, so it tiles both ways with no seam): the tunnel wall
texture wraps round the tunnel (u) and repeats along it (v). The noise is stretched along v, so it reads as streaks
rushing past, and warped sideways so the streaks swirl. Two images:
  textures/environment/time_vortex.png       the wall (1024 x 1024, opaque)
  textures/environment/time_vortex_glow.png  bright wisps drawn over it, added on top (black = nothing)
plus the obstacle block's texture (textures/block/vortex_debris.png, 16 x 16).
Run from the repo root: python3 tardis/tools/vortex_texture.py
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "tardis_rb", "textures")
SIZE = 1024
rng = np.random.default_rng(1963)


def periodic_noise(size, beta, stretch_v=1.0, stretch_u=1.0, low=1):
    """Gaussian noise with a 1/f^beta spectrum; tiles both ways. stretch_v > 1 makes features longer along v (streaks
    rushing past), < 1 longer round the tunnel (rings)."""
    fu = np.fft.fftfreq(size)[None, :] * size * stretch_u
    fv = np.fft.fftfreq(size)[:, None] * size * stretch_v
    f = np.sqrt(fu ** 2 + fv ** 2)
    f[0, 0] = 1
    amp = 1 / f ** beta
    amp[f < low] = 0
    phase = rng.uniform(0, 2 * np.pi, (size, size))
    spec = amp * np.exp(1j * phase)
    n = np.real(np.fft.ifft2(spec))
    n = (n - n.mean()) / (n.std() + 1e-9)
    return n


def warp(field, du, dv):
    """Samples field at (u + du, v + dv) pixels, wrapping (bilinear)."""
    s = field.shape[0]
    v, u = np.mgrid[0:s, 0:s].astype(np.float64)
    x = (u + du) % s
    y = (v + dv) % s
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    fx = x - x0
    fy = y - y0
    x1 = (x0 + 1) % s
    y1 = (y0 + 1) % s
    return (field[y0, x0] * (1 - fx) * (1 - fy) + field[y0, x1] * fx * (1 - fy)
            + field[y1, x0] * (1 - fx) * fy + field[y1, x1] * fx * fy)


def ramp(t, stops):
    """Colour ramp: t in 0..1, stops = [(t, (r, g, b)), ...]."""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = (t >= t0) & (t <= t1)
        k = ((t - t0) / max(t1 - t0, 1e-6))[m][:, None]
        out[m] = np.array(c0) * (1 - k) + np.array(c1) * k
    return out


def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def wall():
    # rings of cloud round the tunnel, swirled sideways by a slow field
    swirl = periodic_noise(SIZE, 2.4, stretch_v=0.5) * 70
    twist = periodic_noise(SIZE, 2.6) * 25
    clouds = periodic_noise(SIZE, 1.9, stretch_v=1 / 6)
    fine = periodic_noise(SIZE, 1.3, stretch_v=1 / 10)
    clouds = warp(clouds, swirl, twist)
    fine = warp(fine, swirl * 1.3, twist)
    base = 0.5 + 0.22 * clouds + 0.08 * fine
    hue = periodic_noise(SIZE, 2.8, stretch_v=1 / 3)
    hue = warp(hue, swirl, 0)
    hue = 0.5 + 0.25 * hue
    cool = ramp(base, [(0.0, (6, 4, 26)), (0.35, (30, 18, 92)), (0.55, (70, 60, 190)), (0.75, (120, 170, 255)),
                       (1.0, (235, 245, 255))])
    warm = ramp(base, [(0.0, (14, 2, 10)), (0.35, (90, 14, 40)), (0.55, (200, 70, 30)), (0.75, (255, 160, 60)),
                       (1.0, (255, 240, 200))])
    k = smooth(hue, 0.35, 0.7)[..., None]
    rgb = cool * (1 - k) + warm * k
    return np.clip(rgb, 0, 255).astype(np.uint8)


def glow():
    # thin bright wisps streaking along the tunnel (they scroll faster than the wall, for speed)
    swirl = periodic_noise(SIZE, 2.4, stretch_v=0.5) * 90
    streak = periodic_noise(SIZE, 1.6, stretch_v=3.0, low=2)
    streak = warp(streak, swirl, 0)
    w = smooth(streak, 1.5, 2.8)
    tint = ramp(smooth(warp(periodic_noise(SIZE, 2.8), swirl, 0), -0.6, 0.6),
                [(0.0, (120, 170, 255)), (0.5, (240, 220, 255)), (1.0, (255, 190, 110))])
    rgb = tint * w[..., None]
    return np.clip(rgb, 0, 255).astype(np.uint8)


def debris():
    s = 16
    n = periodic_noise(s, 1.2)
    t = 0.5 + 0.25 * n
    rgb = ramp(t, [(0.0, (30, 16, 50)), (0.5, (90, 50, 140)), (0.8, (200, 120, 255)), (1.0, (255, 230, 255))])
    return np.clip(rgb, 0, 255).astype(np.uint8)


def main():
    env = os.path.join(OUT, "environment")
    blk = os.path.join(OUT, "block")
    os.makedirs(env, exist_ok=True)
    os.makedirs(blk, exist_ok=True)
    Image.fromarray(wall(), "RGB").save(os.path.join(env, "time_vortex.png"), optimize=True)
    Image.fromarray(glow(), "RGB").save(os.path.join(env, "time_vortex_glow.png"), optimize=True)
    Image.fromarray(debris(), "RGB").save(os.path.join(blk, "vortex_debris.png"))
    print("vortex textures written")


if __name__ == "__main__":
    main()
