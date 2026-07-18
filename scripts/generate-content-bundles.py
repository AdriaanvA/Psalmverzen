import argparse
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTENT_DIR = ROOT / "content"
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
MANIFEST_PATH = ASSETS_DIR / "bundled-content-manifest.json"
PSALM_BUNDLE_DIR = ASSETS_DIR / "content" / "psalms"
GEZANG_BUNDLE_DIR = ASSETS_DIR / "content" / "gezangen"
MELODY_ASSET_DIR = ASSETS_DIR / "content" / "melodies"
PSALM_TEXT_ASSET_DIR = PSALM_BUNDLE_DIR / "texts"
GEZANG_TEXT_ASSET_DIR = GEZANG_BUNDLE_DIR / "texts"

PSALM_DIVISIONS = 2
HYMN_DIVISIONS = 24
PITCH_CLASSES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
FIFTHS_BY_MAJOR_PITCH_CLASS = [0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5]
MODE_OFFSETS = {"ionian": 0, "dorian": 2, "phrygian": 4, "lydian": 5, "mixo-lydian": 7, "aeolian": 9}
SHARP_ORDER = ["F", "C", "G", "D", "A", "E", "B"]
FLAT_ORDER = ["B", "E", "A", "D", "G", "C", "F"]
MELODY_ALIASES = {
    "Gezang001": "Psalm140.json",
    "Gezang009": "Psalm100.json",
}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def clear_directory(path: Path) -> int:
    if not path.exists():
        return 0
    removed = 0
    for child in sorted(path.rglob("*"), reverse=True):
        if child.is_file():
            child.unlink()
            removed += 1
    return removed


def key_fifths(key: str) -> int:
    parts = [part for part in key.split() if part]
    if len(parts) < 2:
        raise ValueError(f"Unsupported key: {key}")
    tonic = parts[0][0].upper()
    mode = parts[1].lower()
    major_pitch_class = (PITCH_CLASSES[tonic] - MODE_OFFSETS[mode]) % 12
    return FIFTHS_BY_MAJOR_PITCH_CLASS[major_pitch_class]


def key_alters(fifths: int) -> dict[str, int]:
    alters = {}
    if fifths > 0:
        for step in SHARP_ORDER[:fifths]:
            alters[step] = 1
    elif fifths < 0:
        for step in FLAT_ORDER[:-fifths]:
            alters[step] = -1
    return alters


def parse_abc_token(token: str, alters: dict[str, int]) -> dict:
    match = re.match(r"^([\^_=]?)([A-Ga-gz])(\d*)$", token)
    if not match:
        raise ValueError(f"Unsupported ABC token: {token}")
    accidental, raw_note, raw_length = match.groups()
    length = int(raw_length) if raw_length else 1
    if raw_note == "z":
        return {"rest": True, "length": length}

    step = raw_note.upper()
    alter = None
    if accidental == "^":
        alter = 1
    elif accidental == "_":
        alter = -1
    elif accidental == "=":
        alter = 0
    elif step in alters:
        alter = alters[step]

    return {
        "rest": False,
        "length": length,
        "step": step,
        "alter": alter,
        "octave": 5 if raw_note.islower() else 4,
    }


def expanded_psalm_notes(note: dict, base_length: int, lyric_slot: bool) -> list[dict]:
    quarter_units = round(note["length"] / base_length)
    if quarter_units == 4:
        return [renderable_note(note, 8, "whole", False, None, lyric_slot)]

    result = []
    remaining = quarter_units
    first = True
    while remaining > 0:
        unit = 2 if remaining >= 2 else 1
        tie = None
        if not note["rest"] and quarter_units > 2:
            if first:
                tie = "start"
            elif remaining == unit:
                tie = "stop"
            else:
                tie = "continue"
        result.append(renderable_note(note, unit * 2, "half" if unit == 2 else "quarter", False, tie, lyric_slot and first))
        remaining -= unit
        first = False
    return result


def renderable_note(note: dict, duration: int, note_type: str, dot: bool, tie: str | None, lyric_slot: bool) -> dict:
    result = {
        "duration": duration,
        "type": note_type,
        "rest": bool(note.get("rest")),
        "lyricSlot": lyric_slot,
    }
    if dot:
        result["dot"] = True
    if tie:
        result["tie"] = tie
    if note.get("hidden"):
        result["hidden"] = True
    if not result["rest"]:
        result["step"] = note["step"]
        result["alter"] = note.get("alter")
        result["octave"] = note["octave"]
    return result


def psalm_melody_lines(melody: dict) -> tuple[int, list[list[dict]]]:
    fifths = key_fifths(melody["key"])
    alters = key_alters(fifths)
    parsed_lines = [
        [parse_abc_token(token, alters) for token in line.split() if token]
        for line in melody["lines"]
    ]
    base_length = min(note["length"] for line in parsed_lines for note in line)
    bundle_lines = []
    for line in parsed_lines:
        last_vocal_index = max((index for index, note in enumerate(line) if not note["rest"]), default=-1)
        expanded_line = []
        for index, note in enumerate(line):
            lyric_slot = (not note["rest"]) or (index > last_vocal_index)
            expanded_line.extend(expanded_psalm_notes(note, base_length, lyric_slot))
        bundle_lines.append(expanded_line)
    return fifths, bundle_lines


def hymn_duration_type(duration: int) -> tuple[str, bool]:
    candidates = [
        (96, "whole", False),
        (72, "half", True),
        (48, "half", False),
        (36, "quarter", True),
        (24, "quarter", False),
        (18, "eighth", True),
        (12, "eighth", False),
        (6, "16th", False),
    ]
    return min(candidates, key=lambda candidate: abs(candidate[0] - duration))[1:]


def pad_short_hymn_lines(lines: list[list[dict]]) -> list[list[dict]]:
    target_duration = max((sum(note["duration"] for note in line) for line in lines), default=0)
    padded_lines = []
    for line in lines:
        padded_line = [dict(note) for note in line]
        remaining = target_duration - sum(note["duration"] for note in padded_line)
        while remaining > 0:
            duration = min(96, remaining)
            padded_line.append({"duration": duration, "rest": True, "hidden": True})
            remaining -= duration
        padded_lines.append(padded_line)
    return padded_lines


def hymn_melody_lines(melody: dict) -> list[list[dict]]:
    bundle_lines = []
    for line in pad_short_hymn_lines(melody["lines"]):
        bundle_line = []
        for note in line:
            note_type, dot = hymn_duration_type(note["duration"])
            bundle_line.append(renderable_note(note, note["duration"], note_type, dot, None, not note.get("rest", False)))
        bundle_lines.append(bundle_line)
    return bundle_lines


def build_psalm_assets(number: int) -> tuple[dict, dict]:
    melody = read_json(CONTENT_DIR / "psalmen" / "melodies" / f"Psalm{number:03d}.json")
    lyrics = read_json(CONTENT_DIR / "psalmen" / "lyrics" / f"Psalm{number:03d}.json")
    fifths, melody_lines = psalm_melody_lines(melody)
    melody_asset = {
        "book": "psalms",
        "number": number,
        "composer": melody.get("composer", ""),
        "divisions": PSALM_DIVISIONS,
        "fifths": fifths,
        "melody": {"lines": melody_lines},
    }
    text_asset = {
        "book": "psalms",
        "number": number,
        "title": lyrics["title"],
        "verses": lyrics["verses"],
    }
    return melody_asset, text_asset


def build_hymn_assets(number: int) -> tuple[dict, dict]:
    melody = read_json(CONTENT_DIR / "gezangen" / "melodies" / f"Gezang{number:03d}.json")
    lyrics = read_json(CONTENT_DIR / "gezangen" / "lyrics" / f"Gezang{number:03d}.json")
    melody_asset = {
        "book": "gezangen",
        "number": number,
        "composer": melody.get("composer", ""),
        "divisions": HYMN_DIVISIONS,
        "fifths": melody["fifths"],
        "melody": {"lines": hymn_melody_lines(melody)},
    }
    text_asset = {
        "book": "gezangen",
        "number": number,
        "title": lyrics["title"],
        "verses": lyrics["verses"],
    }
    return melody_asset, text_asset


def melody_key(melody_asset: dict) -> str:
    canonical = {
        "divisions": melody_asset["divisions"],
        "fifths": melody_asset["fifths"],
        "melody": melody_asset["melody"],
    }
    return json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def owner_label(text_asset: dict) -> str:
    prefix = "Gezang" if text_asset["book"] == "gezangen" else "Psalm"
    return f"{prefix}{text_asset['number']:03d}"


def register_melody(melody_assets: dict, melody_lookup: dict, melody_asset: dict, text_asset: dict) -> str:
    label = owner_label(text_asset)
    if label in MELODY_ALIASES:
        file_name = MELODY_ALIASES[label]
        if file_name not in melody_assets:
            raise ValueError(f"Melody alias for {label} points to missing {file_name}")
    else:
        key = melody_key(melody_asset)
        if key not in melody_lookup:
            file_name = f"{label}.json"
            melody_lookup[key] = file_name
            melody_assets[file_name] = dict(melody_asset)
            melody_assets[file_name]["sourceItems"] = []
            melody_assets[file_name]["composers"] = []
        file_name = melody_lookup[key]

    melody_assets[file_name]["sourceItems"].append({
        "book": text_asset["book"],
        "number": text_asset["number"],
        "title": text_asset["title"],
    })
    composer = melody_asset.get("composer", "")
    if composer and composer not in melody_assets[file_name]["composers"]:
        melody_assets[file_name]["composers"].append(composer)
    return f"content/melodies/{file_name}"


def with_melody_file(text_asset: dict, melody_file: str) -> dict:
    return {
        "book": text_asset["book"],
        "number": text_asset["number"],
        "title": text_asset["title"],
        "melodyFile": melody_file,
        "verses": text_asset["verses"],
    }


def manifest_items_for(text_asset: dict) -> list[dict]:
    prefix = "Gezang" if text_asset["book"] == "gezangen" else "Psalm"
    items = []
    for verse in text_asset["verses"]:
        first_line = text_asset["title"]
        lines = verse.get("lines", [])
        if lines:
            first_line = lines[0].get("display") or lines[0].get("raw") or first_line

        item = {
            "book": text_asset["book"],
            "number": text_asset["number"],
            "verse": verse["number"],
            "fileName": f"{prefix}{text_asset['number']}_v{verse['number']}.xml",
            "firstLine": first_line,
        }
        if text_asset["book"] == "gezangen":
            item["title"] = text_asset["title"]
        items.append(item)
    return items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--psalms", default="1..150")
    parser.add_argument("--hymns", default="1..12")
    parser.add_argument("--gezangen", help="Alias for --hymns, for the Enige gezangen range.")
    parser.add_argument("--keep-legacy-xml", action="store_true")
    args = parser.parse_args()

    psalm_start, psalm_end = [int(value) for value in args.psalms.split("..")]
    gezang_range = args.gezangen or args.hymns
    hymn_start, hymn_end = [int(value) for value in gezang_range.split("..")]
    melody_assets = {}
    melody_lookup = {}
    manifest_items = []

    for number in range(psalm_start, psalm_end + 1):
        melody_asset, text_asset = build_psalm_assets(number)
        text_asset = with_melody_file(text_asset, register_melody(melody_assets, melody_lookup, melody_asset, text_asset))
        write_json(PSALM_TEXT_ASSET_DIR / f"Psalm{number:03d}.json", text_asset)
        manifest_items.extend(manifest_items_for(text_asset))
    for number in range(hymn_start, hymn_end + 1):
        melody_asset, text_asset = build_hymn_assets(number)
        text_asset = with_melody_file(text_asset, register_melody(melody_assets, melody_lookup, melody_asset, text_asset))
        write_json(GEZANG_TEXT_ASSET_DIR / f"Gezang{number:03d}.json", text_asset)
        manifest_items.extend(manifest_items_for(text_asset))

    clear_directory(MELODY_ASSET_DIR)
    for file_name, melody_asset in melody_assets.items():
        write_json(MELODY_ASSET_DIR / file_name, melody_asset)
    write_json(MANIFEST_PATH, {"items": manifest_items})

    removed_legacy_count = 0
    if not args.keep_legacy_xml:
        for pattern in (
            "Psalm*_v*.xml",
            "Gezang*_v*.xml",
            "content/psalms/Psalm*.json",
            "content/hymns/Gezang*.json",
            "content/psalms/melodies/Psalm*.json",
            "content/hymns/melodies/Gezang*.json",
            "content/hymns/texts/Gezang*.json",
        ):
            for path in ASSETS_DIR.glob(pattern):
                path.unlink()
                removed_legacy_count += 1
        removed_legacy_count += clear_directory(ASSETS_DIR / "content" / "hymns")

    print(f"Wrote {len(melody_assets)} unique melodies, {psalm_end - psalm_start + 1} psalm texts, {hymn_end - hymn_start + 1} gezang texts, and {len(manifest_items)} manifest items.")
    if removed_legacy_count:
        print(f"Removed {removed_legacy_count} legacy combined or per-verse assets.")


if __name__ == "__main__":
    main()