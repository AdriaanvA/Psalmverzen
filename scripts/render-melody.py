#!/usr/bin/env python3
"""
render-melody.py - Render een melodie-JSON als notenbalk-afbeelding (PNG).

Eén notenbalk per regel (zoals bladmuziek), noten links->rechts, met nootnaam.
Verboden noten (bijv. C/Cis) worden ROOD gemarkeerd, zodat fouten er visueel
uitspringen. Handig om naast de bladmuziek te leggen.

Voorbeeld:
  python scripts/render-melody.py \
      --melody app/src/main/assets/content/melodies/Gezang006b.json \
      --out melody-gz6v2.png --forbid C
"""
import argparse, json, subprocess, sys

def ensure_matplotlib():
    try:
        import matplotlib  # noqa
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", "matplotlib"])

ensure_matplotlib()
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Ellipse

STEP_IDX = {"C": 0, "D": 1, "E": 2, "F": 3, "G": 4, "A": 5, "B": 6}
ACC = {-2: "bb", -1: "b", 0: "", 1: "#", 2: "##"}
STAFF = [30, 32, 34, 36, 38]  # E4 G4 B4 D5 F5 (diatonische posities)


def diat(n):
    return n["octave"] * 7 + STEP_IDX[n["step"]]


def name(n):
    return f'{n["step"]}{ACC.get(n.get("alter") or 0, "?")}{n["octave"]}'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--melody", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--forbid", default="")
    args = ap.parse_args()
    forbid = {s.strip().upper() for s in args.forbid.split(",") if s.strip()}

    mel = json.load(open(args.melody, encoding="utf-8"))
    lines = mel["melody"]["lines"]
    nlines = len(lines)

    fig, axes = plt.subplots(nlines, 1, figsize=(15, 1.7 * nlines))
    if nlines == 1:
        axes = [axes]

    for i, ax in enumerate(axes):
        notes = [nn for nn in lines[i] if not nn.get("hidden")]
        audible = [nn for nn in notes if not nn.get("rest")]
        xmax = max(len(notes), 1)
        for y in STAFF:
            ax.plot([-0.6, xmax - 0.4], [y, y], color="#333", lw=0.9, zorder=1)
        x = 0
        for nn in notes:
            if nn.get("rest"):
                ax.text(x, 34, "𝄽", ha="center", va="center", fontsize=13, color="#888")
                x += 1
                continue
            y = diat(nn)
            col = "red" if nn["step"].upper() in forbid else "#111"
            filled = nn.get("type") in ("quarter", "eighth")
            # ledger lines buiten de balk
            yy = 40
            while yy <= y:
                ax.plot([x - 0.35, x + 0.35], [yy, yy], color="#333", lw=0.9, zorder=2)
                yy += 2
            yy = 28
            while yy >= y:
                ax.plot([x - 0.35, x + 0.35], [yy, yy], color="#333", lw=0.9, zorder=2)
                yy -= 2
            ax.add_patch(Ellipse((x, y), 0.62, 1.05,
                                 facecolor=(col if filled else "white"),
                                 edgecolor=col, lw=1.4, zorder=3))
            if nn.get("alter"):
                ax.text(x - 0.5, y, ACC.get(nn["alter"], "?"), ha="right", va="center",
                        fontsize=9, color=col)
            slot = "\u25cf" if nn.get("lyricSlot") else ""  # bolletje = nieuwe lettergreep
            ax.text(x, 24.3, name(nn), rotation=90, ha="center", va="top", fontsize=6.5, color=col)
            if nn.get("lyricSlot"):
                ax.plot([x], [23.2], marker="^", markersize=3, color="#3a7", zorder=3)
            x += 1
        start = name(audible[0]) if audible else "-"
        end = name(audible[-1]) if audible else "-"
        ax.text(-1.6, 34, f"regel {i}\n{start}\u2192{end}\n{len(audible)} kl.",
                ha="right", va="center", fontsize=8, color="#333")
        ax.set_xlim(-2.2, xmax)
        ax.set_ylim(20, 42)
        ax.axis("off")

    plt.tight_layout()
    plt.savefig(args.out, dpi=130, bbox_inches="tight", facecolor="white")
    print(f"Geschreven: {args.out}  ({nlines} regels)")


if __name__ == "__main__":
    main()
