#!/usr/bin/env python3
"""
check-melody.py - Controleer een melodie (per vers) tegen een verwachte opgave.

Maakt fout-zoeken makkelijk: toont per regel de beginnoot, eindnoot en het
aantal klanken (audible noten), vergelijkt met een opgegeven specificatie, en
scant op 'verboden' noten (bijv. een Cis die er niet in hoort).

Voorbeeld:
  python scripts/check-melody.py \
      --melody app/src/main/assets/content/melodies/Gezang006b.json \
      --text   app/src/main/assets/content/gezangen/texts/Gezang006.json \
      --verse 2 --forbid C --expect scripts/specs/gz6v2.json

Het --expect bestand is een JSON-lijst per regel: [beginnoot, eindnoot, klanken],
bijv. [["D","D",11],["D","F#",11], ...]. Beginnoot/eindnoot als letter + optioneel
kruis/mol (bijv. "F#", "Bb"), ZONDER octaaf.
"""
import argparse, json, os, sys

ACC = {-2: "bb", -1: "b", 0: "", 1: "#", 2: "##"}


def note_name(n):
    return f"{n.get('step')}{ACC.get(n.get('alter') or 0, '?')}{n.get('octave')}"


def bare(name):
    return name.rstrip("0123456789")


def audible(notes):
    return [n for n in notes if not n.get("hidden") and not n.get("rest")]


def main():
    ap = argparse.ArgumentParser(description="Controleer melodie tegen opgave.")
    ap.add_argument("--melody", required=True)
    ap.add_argument("--text", required=True)
    ap.add_argument("--verse", type=int, required=True)
    ap.add_argument("--forbid", default="", help="Komma-lijst van stap-letters die niet mogen voorkomen, bv. 'C'.")
    ap.add_argument("--expect", default="", help="Optioneel JSON-bestand: lijst van [begin, eind, klanken] per regel.")
    args = ap.parse_args()

    mel = json.load(open(args.melody, encoding="utf-8"))
    text = json.load(open(args.text, encoding="utf-8"))
    mlines = mel["melody"]["lines"]
    verse = next((v for v in text["verses"] if v.get("number") == args.verse), None)
    if verse is None:
        sys.exit(f"Vers {args.verse} niet gevonden in {args.text}")
    tlines = verse["lines"]

    expected = json.load(open(args.expect, encoding="utf-8")) if args.expect else None
    forbid = {s.strip().upper() for s in args.forbid.split(",") if s.strip()}

    print(f"fifths={mel.get('fifths')}  melodieregels={len(mlines)}  tekstregels(vers {args.verse})={len(tlines)}\n")
    problems = 0
    for i, notes in enumerate(mlines):
        aud = audible(notes)
        start = note_name(aud[0]) if aud else "-"
        end = note_name(aud[-1]) if aud else "-"
        klank = len(aud)
        lyric = sum(1 for n in notes if n.get("lyricSlot") and not n.get("hidden"))
        ntok = len(tlines[i]["tokens"]) if i < len(tlines) else -1
        flags = []
        if expected and i < len(expected):
            es, ee, ek = expected[i]
            if bare(start) != es: flags.append(f"BEGIN {start}!={es}")
            if bare(end) != ee: flags.append(f"EIND {end}!={ee}")
            if klank != ek: flags.append(f"KLANKEN {klank}!={ek}")
        if ntok != -1 and ntok != lyric:
            flags.append(f"tokens {ntok} != lyricSlot {lyric}")
        problems += len(flags)
        status = "  <<< " + "; ".join(flags) if flags else "  OK"
        seq = " ".join((note_name(n) if not n.get("rest") else "R") + ("*" if n.get("lyricSlot") else "")
                       for n in notes if not n.get("hidden"))
        print(f"regel {i}: begin={start:5s} eind={end:5s} klanken={klank:2d} lyricSlot={lyric:2d} tokens={ntok:2d}{status}")
        print(f"     {seq}")

    if forbid:
        print(f"\nSCAN op verboden noten {sorted(forbid)}:")
        any_found = False
        for i, notes in enumerate(mlines):
            for j, n in enumerate(notes):
                if (n.get("step") or "").upper() in forbid:
                    print(f"  !! regel {i} noot {j}: {note_name(n)}")
                    any_found = True
                    problems += 1
        if not any_found:
            print("  (geen gevonden)")

    print("\nLegenda: * = lyricSlot (nieuwe lettergreep); R = rust; #/b = kruis/mol.")
    print(f"\n{'ALLES OK' if problems == 0 else f'{problems} punt(en) om te controleren'}")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
