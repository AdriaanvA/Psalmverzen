"""
Genereert app/src/main/assets/content/melodies/Gezang011.json uit een compacte
notatie van de melodie (afgelezen van MelodieGezang11.jpeg). D majeur (2 kruisen),
cut time. Pas de TRANSCRIPTION hieronder aan tijdens de render-vergelijk-lus.

Notatie per noot: "<letter>[#]<octaaf><duur>" met duur h=half, q=kwart.
Bijv. F#4h = F#4 halve noot, A4q = A4 kwartnoot, G#4q = G#4 kwartnoot.
"""
import json, re, os

# 9,8,9,8,8,8,8,8 lettergrepen per regel (moet exact matchen met de tekst-tokens).
TRANSCRIPTION = [
    # P1: O HEER', wij danken U van harte,  (danken = B, niet A)
    "D4h F#4h A4q B4h B4q C#5q A4q B4h A4h",
    # P2: Voor nooddruft en voor overvloed;  (Voor = A, niet B)
    "A4h A4q B4q A4q B4h G#4q A4q F#4h",
    # P3: Waar menig mens eet brood der smarte,  (mens = F#, niet G)
    "A4h B4q A4q F#4q F#4h G4q G#4q A4h A4h",
    # P4: Hebt Gij ons mild en wel gevoed;
    "B4h A4q C#5q D5q C#5h A4q A4q F#4h",
    # P5: Doch geef, dat onze ziele niet
    "A4h B4q A4q G4q F#4h G4q A4q B4h",
    # P6: Aan dit vergank'lijk leven kleev',
    "D5h C#5q B4q A4q B4q C#5h B4q A4h",
    # P7: Maar alles doe, wat Gij gebiedt,
    "A4h G4q A4q B4q A4q G4h F#4q E4h",
    # P8: En eind'lijk eeuwig bij U leev'.
    "F#4h A4q G4q F#4q E4q D4h E4q D4h",
]

STEP_RE = re.compile(r"^([A-G])(#?)(\d)([hq])$")

def note_obj(token):
    m = STEP_RE.match(token)
    if not m:
        raise ValueError(f"Ongeldige noot: {token}")
    step, sharp, octave, dur = m.group(1), m.group(2), int(m.group(3)), m.group(4)
    # D majeur: F en C zijn kruis (volgt voortekening, geen los kruis).
    is_sharp = bool(sharp) or step in ("F", "C")
    return {
        "duration": 2 if dur == "h" else 1,
        "type": "half" if dur == "h" else "quarter",
        "rest": False,
        "lyricSlot": True,
        "step": step,
        "alter": 1 if is_sharp else None,
        "octave": octave,
    }

lines = [[note_obj(t) for t in line.split()] for line in TRANSCRIPTION]

path = os.path.join("app", "src", "main", "assets", "content", "melodies", "Gezang011.json")
data = {
    "book": "gezangen",
    "number": 11,
    "composer": "Franc 1543 / Bourgois 1551",
    "divisions": 2,
    "fifths": 2,
    "melody": {"lines": lines},
}
with open(path, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"Geschreven: {path}")
for i, line in enumerate(lines):
    print(f"L{i+1} [{len(line)}]")
