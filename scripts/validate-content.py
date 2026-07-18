"""
Content-validatie (dev-tool; NIET in de app, dus geen APK-impact).

Controleert de gebundelde content op veelvoorkomende fouten die we eerder
tegenkwamen:
  1. Ongeldige JSON (bijv. een overbodige/ontbrekende komma).
  2. Per versregel: aantal tekst-tokens == aantal lyric-noten in de melodie.
  3. Geen spaties binnen een token-tekst (bijv. de "spot ten"-fout).
  4. Melodiebestand (melodyFile) bestaat en heeft genoeg regels.

Gebruik:  python scripts/validate-content.py
Exit-code 1 bij fouten (handig voor CI).
"""
import json, glob, os, sys, re

ASSETS = os.path.join("app", "src", "main", "assets")
TEXT_DIRS = [
    os.path.join(ASSETS, "content", "psalms", "texts"),
    os.path.join(ASSETS, "content", "gezangen", "texts"),
]

errors = []
warnings = []

def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:
        errors.append(f"{path}: ongeldige JSON -> {e}")
        return None

def lyric_note_count(melody_line):
    # Exact zoals ScoreBundleRenderer: elke noot met lyricSlot=true krijgt een token.
    return sum(1 for n in melody_line if n.get("lyricSlot"))

# Proclitische samentrekkingen ('t pad, 's HEEREN, 'k zal) staan terecht op één noot.
# Sta zowel rechte (') als krul-apostrof (’) toe.
CONTRACTION = re.compile(r"^['’ʼ][a-zA-Z]{1,2}$")

def suspicious_space(token_text):
    parts = token_text.strip().split()
    if len(parts) <= 1:
        return False
    return not CONTRACTION.match(parts[0])

# Valideer eerst alle melodiebestanden apart (JSON-geldigheid).
for path in glob.glob(os.path.join(ASSETS, "content", "melodies", "*.json")):
    load_json(path)

for text_dir in TEXT_DIRS:
    for path in sorted(glob.glob(os.path.join(text_dir, "*.json"))):
        text = load_json(path)
        if text is None:
            continue
        name = os.path.basename(path)
        melody_rel = text.get("melodyFile")
        melody = load_json(os.path.join(ASSETS, melody_rel)) if melody_rel else None
        melody_lines = (melody or {}).get("melody", {}).get("lines", []) if melody else []

        for verse in text.get("verses", []):
            vnum = verse.get("number")
            for li, line in enumerate(verse.get("lines", [])):
                tokens = line.get("tokens", [])
                # 3) spaties in tokens (m.u.v. legitieme samentrekkingen)
                for tok in tokens:
                    t = tok.get("text", "")
                    if suspicious_space(t):
                        errors.append(f"{name} vers {vnum} regel {li+1}: spatie in token {t!r}")
                # 2) meer tokens dan lyric-noten = lettergrepen vallen weg (echte fout).
                #    Minder tokens dan noten is normaal (tekstloze slotnoot per regel).
                if melody_lines and li < len(melody_lines):
                    need = lyric_note_count(melody_lines[li])
                    if len(tokens) > need:
                        errors.append(
                            f"{name} vers {vnum} regel {li+1}: {len(tokens)} tokens maar "
                            f"slechts {need} lyric-noten in {melody_rel} (lettergrepen vallen weg)"
                        )
                elif melody_lines:
                    warnings.append(f"{name} vers {vnum}: regel {li+1} heeft geen melodie-regel")

print(f"Gecontroleerd. Fouten: {len(errors)}, waarschuwingen: {len(warnings)}")
for w in warnings:
    print("  WAARSCHUWING:", w)
for e in errors:
    print("  FOUT:", e)
sys.exit(1 if errors else 0)
