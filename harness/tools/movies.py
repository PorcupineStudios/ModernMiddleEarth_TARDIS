"""Builds GIFs (and 60 fps MP4s) from the dev movie recorder's frames (run from harness/:
python3 tools/movies.py [gif fps] [gif width]).

Reads run/movie/<run>/times.txt (frame, game ticks) and f*.png, picks for each output frame the recorded frame nearest
to evenly spaced game times (the client renders at an uneven, much higher rate than the game ticks while the server's
tick rate is slowed), from 1 s into the run (the horse starts at 1.5 s) to 1.5 s before its end (it stands still the
last 3 s), and writes run/movies/<run>.gif with ffmpeg (96-colour palette per GIF, no dithering: dither noise on a
moving picture doubled the size) and run/movies/<run>.mp4 (H.264, 60 fps, full size). Prints how many output frames
had no recorded frame within half a step (they repeat the nearest one)."""
import bisect
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
FPS = int(sys.argv[1]) if len(sys.argv) > 1 else 40
WIDTH = int(sys.argv[2]) if len(sys.argv) > 2 else 480
MP4_FPS = 60
SRC, OUT = HERE / "run/movie", HERE / "run/movies"
OUT.mkdir(parents=True, exist_ok=True)



def pick(frames, times, fps):
    """Recorded frame numbers for evenly spaced game times at fps, and how many had none within half a step."""
    start, end = times[0] + 20, times[-1] - 30
    step = 20.0 / fps  # game ticks per output frame
    picked, missing, t = [], 0, start
    while t <= end:
        k = bisect.bisect_left(times, t)
        best = min((j for j in (k - 1, k) if 0 <= j < len(times)), key=lambda j: abs(times[j] - t))
        if abs(times[best] - t) > step / 2:
            missing += 1
        picked.append(frames[best][1])
        t += step
    return picked, missing


def encode(run, picked, args):
    with tempfile.TemporaryDirectory() as tmp:
        for n, i in enumerate(picked):
            shutil.copy(run / f"f{i:05d}.png", Path(tmp) / f"{n:05d}.png")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error"] + args(tmp), check=True)


for run in sorted(p for p in SRC.iterdir() if (p / "times.txt").exists()):
    frames = sorted((float(t), int(i)) for i, t in (l.split() for l in (run / "times.txt").read_text().splitlines()))
    times = [t for t, _ in frames]
    gif, mp4 = OUT / f"{run.name}.gif", OUT / f"{run.name}.mp4"
    picked, missing = pick(frames, times, FPS)
    encode(run, picked, lambda tmp: [
        "-framerate", str(FPS), "-i", f"{tmp}/%05d.png",
        "-vf", f"scale={WIDTH}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];"
               "[b][p]paletteuse=dither=none:diff_mode=rectangle", "-loop", "0", str(gif)])
    picked60, missing60 = pick(frames, times, MP4_FPS)
    encode(run, picked60, lambda tmp: [
        "-framerate", str(MP4_FPS), "-i", f"{tmp}/%05d.png", "-c:v", "libx264", "-crf", "18", "-preset", "slow",
        "-pix_fmt", "yuv420p", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", str(mp4)])
    print(f"{run.name}: gif {len(picked)} frames at {FPS} fps ({len(picked) / FPS:.1f} s, {missing} filled in) "
          f"{gif.stat().st_size / 1e6:.1f} MB; mp4 at {MP4_FPS} fps ({missing60} filled in) "
          f"{mp4.stat().st_size / 1e6:.1f} MB")
