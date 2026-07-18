import argparse
import html
import json
import math
import re
import struct
import urllib.request
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
MANIFEST_PATH = ASSETS_DIR / "bundled-content-manifest.json"
ABC_PATH = ROOT / "psalmen.abc"
CONTENT_DIR = ROOT / "content" / "gezangen"
TEXT_SOURCE_DIR = CONTENT_DIR / "texts"
MELODY_SOURCE_DIR = CONTENT_DIR / "melodies"
LYRIC_SOURCE_DIR = CONTENT_DIR / "lyrics"
DIVISIONS = 24
VOWELS = set("aeiouyAEIOUYáéíóúÁÉÍÓÚàèìòùÀÈÌÒÙäëïöüÄËÏÖÜâêîôûÂÊÎÔÛ")
PITCH_CLASSES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
FIFTHS_BY_MAJOR_PITCH_CLASS = [0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5]
MODE_OFFSETS = {"ionian": 0, "dorian": 2, "phrygian": 4, "lydian": 5, "mixo-lydian": 7, "aeolian": 9}
SHARP_SPELLINGS = [("C", 0), ("C", 1), ("D", 0), ("D", 1), ("E", 0), ("F", 0), ("F", 1), ("G", 0), ("G", 1), ("A", 0), ("A", 1), ("B", 0)]
FLAT_SPELLINGS = [("C", 0), ("D", -1), ("D", 0), ("E", -1), ("E", 0), ("F", 0), ("G", -1), ("G", 0), ("A", -1), ("A", 0), ("B", -1), ("B", 0)]
SHARP_ORDER = ["F", "C", "G", "D", "A", "E", "B"]
FLAT_ORDER = ["B", "E", "A", "D", "G", "C", "F"]
OFFICIAL_HYMN_TITLES = {
    1: "De Tien Geboden des Heeren",
    2: "De Lofzang van Maria (Magnificat)",
    3: "De Lofzang van Zacharias (Benedictus)",
    4: "De Lofzang van Simeon (Nunc dimittis)",
    5: "Het Gebed des Heeren (Onze Vader)",
    6: "De Geloofsbelijdenis 1 (Apostolische geloofsbelijdenis)",
    7: "De Geloofsbelijdenis 2 (Apostolische geloofsbelijdenis)",
    8: "Bedezang voor de predikatie",
    9: "Morgenzang",
    10: "Bedezang voor het eten",
    11: "Dankzang na het eten",
    12: "Avondzang",
}


@dataclass
class Note:
    duration: int
    step: str | None = None
    alter: int | None = None
    octave: int | None = None
    rest: bool = False
    hidden: bool = False


def source_path(directory: Path, number: int) -> Path:
    return directory / f"Gezang{number:03d}.json"


def relative_source_path(directory_name: str, number: int) -> str:
    return f"content/gezangen/{directory_name}/Gezang{number:03d}.json"


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def note_to_dict(note: Note) -> dict:
    return {
        "duration": note.duration,
        "step": note.step,
        "alter": note.alter,
        "octave": note.octave,
        "rest": note.rest,
    }


def note_from_dict(data: dict) -> Note:
    return Note(
        duration=int(data["duration"]),
        step=data.get("step"),
        alter=data.get("alter"),
        octave=data.get("octave"),
        rest=bool(data.get("rest", False)),
    )


class TextExtractor(HTMLParser):
    block_tags = {"br", "p", "div", "li", "h1", "h2", "h3", "section", "article"}

    def __init__(self):
        super().__init__()
        self.parts: list[str] = []

    def handle_starttag(self, tag, attrs):
        if tag.lower() in self.block_tags:
            self.parts.append("\n")

    def handle_endtag(self, tag):
        if tag.lower() in self.block_tags:
            self.parts.append("\n")

    def handle_data(self, data):
        if data:
            self.parts.append(data)

    def text(self) -> str:
        raw = html.unescape("".join(self.parts)).replace("\xa0", " ")
        lines = [re.sub(r"\s+", " ", line).strip() for line in raw.splitlines()]
        return "\n".join(line for line in lines if line)


def parse_id_range(values: list[str]) -> list[int]:
    ids: list[int] = []
    for value in values:
        if ".." in value:
            start, end = value.split("..", 1)
            ids.extend(range(int(start), int(end) + 1))
        elif "," in value:
            ids.extend(int(part.strip()) for part in value.split(",") if part.strip())
        else:
            ids.append(int(value))
    return ids


def fetch_text(url: str) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "PsalmenApp asset importer"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read().decode("utf-8", errors="replace")


def fetch_binary(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "PsalmenApp asset importer"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def get_online_hymn(number: int):
    extractor = TextExtractor()
    page_html = fetch_text(f"https://www.online-bijbel.nl/12gezang/{number}/")
    page_html = re.sub(r"<script\b[\s\S]*?</script>", "", page_html, flags=re.IGNORECASE)
    page_html = re.sub(r"<style\b[\s\S]*?</style>", "", page_html, flags=re.IGNORECASE)
    extractor.feed(page_html)
    text = extractor.text()
    marker = f"Enig Gezang {number}"
    start = text.find(marker)
    if start < 0:
        raise ValueError(f"Cannot find hymn heading for Enig Gezang {number}")
    section = text[start:]
    end = section.find("Beluister de melodie")
    if end >= 0:
        section = section[:end]

    lines = [line.strip() for line in section.splitlines() if line.strip()]
    title = marker
    for line in lines[1:]:
        if is_visible_title_line(line) and not re.match(r"^Vers\s*\d+", line):
            title = line
            break

    verses: list[dict] = []
    current_number: int | None = None
    current_lines: list[str] = []
    for line in lines:
        match = re.match(r"^Vers\s*(\d+)\s*(.*)$", line)
        if match:
            if current_number is not None:
                verses.append({"number": current_number, "lines": current_lines})
            current_number = int(match.group(1))
            current_lines = []
            rest = match.group(2).strip()
            if rest:
                current_lines.append(rest)
            continue
        if current_number is not None:
            if is_trailing_boilerplate_line(line):
                break
            current_lines.append(line)
    if current_number is not None:
        verses.append({"number": current_number, "lines": current_lines})
    if not verses:
        raise ValueError(f"Cannot find verses for Enig Gezang {number}")

    if verses[0]["lines"]:
        title = verses[0]["lines"][0]

    return {"number": number, "title": OFFICIAL_HYMN_TITLES.get(number, title), "verses": verses}


def load_or_fetch_hymn(number: int, refresh: bool) -> dict:
    path = source_path(TEXT_SOURCE_DIR, number)
    if path.exists() and not refresh:
        return read_json(path)

    hymn = get_online_hymn(number)
    write_json(path, hymn)
    return hymn


def is_visible_title_line(line: str) -> bool:
    if not line or line.startswith("{") or "@context" in line:
        return False
    ignored_prefixes = (
        "Naar hoofdinhoud",
        "Lees de Bijbel",
        "De gehele Bijbel",
        "Welkom bij",
        "Online-Bijbel",
        "Image",
    )
    return not line.startswith(ignored_prefixes)


def is_trailing_boilerplate_line(line: str) -> bool:
    return line in {"Delen", "Kopiëren", "Kopieren", "Bekijk ook", "Over de melodieën", "Over de melodieen"}


def key_fifths(key: str) -> int:
    parts = [part for part in key.split() if part]
    if len(parts) < 2:
        return 0
    tonic = parts[0][0].upper()
    mode = parts[1].lower()
    major_pitch_class = (PITCH_CLASSES[tonic] - MODE_OFFSETS[mode]) % 12
    return FIFTHS_BY_MAJOR_PITCH_CLASS[major_pitch_class]


def key_alters_from_fifths(fifths: int) -> dict[str, int]:
    alters: dict[str, int] = {}
    if fifths > 0:
        for step in SHARP_ORDER[:fifths]:
            alters[step] = 1
    elif fifths < 0:
        for step in FLAT_ORDER[:-fifths]:
            alters[step] = -1
    return alters


def parse_abc_psalm(number: int):
    lines = ABC_PATH.read_text(encoding="utf-8").splitlines()
    start = next((index for index, line in enumerate(lines) if line == f"X: {number}"), -1)
    if start < 0:
        raise ValueError(f"Psalm {number} not found in psalmen.abc")
    end = next((index for index in range(start + 1, len(lines)) if lines[index].startswith("X: ")), len(lines))
    block = lines[start:end]
    key = next((line[3:].strip() for line in block if line.startswith("K: ")), "C ionian")
    composer = next((line[3:].strip() for line in block if line.startswith("C: ")), "")
    fifths = key_fifths(key)
    key_alters = key_alters_from_fifths(fifths)
    melody_lines = [re.sub(r"[|-]", " ", line).strip() for line in block if line and not re.match(r"^[A-Z]: ", line)]
    note_lines = [[parse_abc_token(token, key_alters) for token in line.split()] for line in melody_lines]
    min_length = min(note.duration for line in note_lines for note in line)
    scaled_lines = []
    for line in note_lines:
        scaled_lines.append([
            Note(duration=max(1, round(note.duration / min_length)) * DIVISIONS, step=note.step, alter=note.alter, octave=note.octave, rest=note.rest)
            for note in line
        ])
    return {"fifths": fifths, "composer": composer, "lines": scaled_lines}


def parse_abc_token(token: str, key_alters: dict[str, int]) -> Note:
    match = re.match(r"^([\^_=]?)([A-Ga-gz])(\d*)$", token)
    if not match:
        raise ValueError(f"Unsupported ABC token: {token}")
    accidental, raw_note, length_text = match.groups()
    length = int(length_text or "1")
    if raw_note == "z":
        return Note(duration=length, rest=True)
    step = raw_note.upper()
    alter = None
    if accidental == "^":
        alter = 1
    elif accidental == "_":
        alter = -1
    elif accidental == "=":
        alter = 0
    elif step in key_alters:
        alter = key_alters[step]
    octave = 5 if raw_note.islower() else 4
    return Note(duration=length, step=step, alter=alter, octave=octave)


def read_varlen(data: bytes, index: int) -> tuple[int, int]:
    value = 0
    while True:
        byte = data[index]
        index += 1
        value = (value << 7) | (byte & 0x7F)
        if not byte & 0x80:
            return value, index


def parse_midi(data: bytes):
    if data[:4] != b"MThd":
        raise ValueError("Not a MIDI file")
    header_length = struct.unpack(">I", data[4:8])[0]
    _format_type, track_count, division = struct.unpack(">HHH", data[8:14])
    index = 8 + header_length
    tracks = []
    key_fifths_value = 0
    for _ in range(track_count):
        if data[index:index + 4] != b"MTrk":
            raise ValueError("Missing MIDI track chunk")
        length = struct.unpack(">I", data[index + 4:index + 8])[0]
        track_data = data[index + 8:index + 8 + length]
        index += 8 + length
        tracks.append((track_data, []))

    parsed_tracks = []
    for track_data, _ in tracks:
        time = 0
        cursor = 0
        running_status = None
        active: dict[tuple[int, int], list[tuple[int, int]]] = {}
        events = []
        while cursor < len(track_data):
            delta, cursor = read_varlen(track_data, cursor)
            time += delta
            status = track_data[cursor]
            if status < 0x80:
                if running_status is None:
                    raise ValueError("MIDI running status without previous status")
                status = running_status
            else:
                cursor += 1
                running_status = status

            if status == 0xFF:
                meta_type = track_data[cursor]
                cursor += 1
                length, cursor = read_varlen(track_data, cursor)
                payload = track_data[cursor:cursor + length]
                cursor += length
                if meta_type == 0x59 and payload:
                    key_fifths_value = struct.unpack("b", payload[:1])[0]
                continue
            if status in (0xF0, 0xF7):
                length, cursor = read_varlen(track_data, cursor)
                cursor += length
                continue

            event_type = status & 0xF0
            channel = status & 0x0F
            data_length = 1 if event_type in (0xC0, 0xD0) else 2
            payload = track_data[cursor:cursor + data_length]
            cursor += data_length
            if channel == 9 or event_type not in (0x80, 0x90):
                continue
            note = payload[0]
            velocity = payload[1] if len(payload) > 1 else 0
            key = (channel, note)
            if event_type == 0x90 and velocity > 0:
                active.setdefault(key, []).append((time, velocity))
            else:
                starts = active.get(key)
                if starts:
                    start, start_velocity = starts.pop(0)
                    if time > start:
                        events.append({"start": start, "end": time, "pitch": note, "velocity": start_velocity})
        parsed_tracks.append(events)

    track_events = max(parsed_tracks, key=len)
    by_start: dict[int, dict] = {}
    for event in track_events:
        existing = by_start.get(event["start"])
        if existing is None or event["pitch"] > existing["pitch"]:
            by_start[event["start"]] = event
    notes = sorted(by_start.values(), key=lambda item: (item["start"], item["pitch"]))
    return notes, division, key_fifths_value


def midi_pitch_to_note(pitch: int, prefer_flats: bool) -> tuple[str, int | None, int]:
    pitch_class = pitch % 12
    if pitch_class == 6:
        step, alter = "F", 1
    elif pitch_class == 10:
        step, alter = "B", -1
    else:
        step, alter = (FLAT_SPELLINGS if prefer_flats else SHARP_SPELLINGS)[pitch_class]
    octave = pitch // 12 - 1
    return step, (alter if alter else None), octave


def midi_melody_lines(number: int, line_count: int, token_counts: list[int]):
    midi = fetch_binary(f"https://www.online-bijbel.nl/midi/12gezang_{number:03d}.mid")
    events, ticks_per_quarter, fifths = parse_midi(midi)
    if not events:
        raise ValueError(f"No MIDI notes found for Enig Gezang {number}")
    groups = split_events_into_lines(events, line_count, token_counts)
    prefer_flats = fifths < 0
    lines = []
    for group in groups:
        note_line = []
        for event in group:
            duration = max(6, round((event["end"] - event["start"]) * DIVISIONS / ticks_per_quarter / 6) * 6)
            step, alter, octave = midi_pitch_to_note(event["pitch"], prefer_flats)
            note_line.append(Note(duration=duration, step=step, alter=alter, octave=octave))
        lines.append(note_line)
    return {"fifths": fifths, "composer": "Online-Bijbel MIDI", "lines": lines}


def melody_to_json(number: int, source: str, melody: dict) -> dict:
    return {
        "number": number,
        "source": source,
        "fifths": melody["fifths"],
        "composer": melody.get("composer", ""),
        "lines": [[note_to_dict(note) for note in line] for line in melody["lines"]],
    }


def melody_from_json(data: dict) -> dict:
    return {
        "fifths": int(data.get("fifths", 0)),
        "composer": data.get("composer", ""),
        "lines": [[note_from_dict(note) for note in line] for line in data.get("lines", [])],
    }


def load_or_create_melody(number: int, hymn: dict, refresh: bool, psalm_140: dict) -> dict:
    path = source_path(MELODY_SOURCE_DIR, number)
    if path.exists() and not refresh:
        return melody_from_json(read_json(path))

    first_lines = hymn["verses"][0]["lines"]
    if number == 1:
        melody = psalm_140
        source = "psalmen.abc Psalm 140"
    else:
        melody = midi_melody_lines(number, len(first_lines), line_token_counts(first_lines))
        source = f"https://www.online-bijbel.nl/midi/12gezang_{number:03d}.mid"
    write_json(path, melody_to_json(number, source, melody))
    return melody


def split_events_into_lines(events: list[dict], line_count: int, token_counts: list[int]) -> list[list[dict]]:
    if line_count <= 1:
        return [events]
    total_tokens = max(1, sum(max(1, count) for count in token_counts))
    cumulative = 0
    splits = []
    for count in token_counts[:-1]:
        cumulative += max(1, count)
        split = max(0, min(len(events) - 2, round(len(events) * cumulative / total_tokens) - 1))
        if not splits or split > splits[-1]:
            splits.append(split)
    result = []
    start = 0
    for split in splits[:line_count - 1]:
        result.append(events[start:split + 1])
        start = split + 1
    result.append(events[start:])
    while len(result) < line_count:
        result.append([])
    return result


def is_prefix_word(word: str) -> bool:
    trimmed = word.strip().rstrip(",.;:!?")
    return len(trimmed) == 2 and trimmed[0] in "'‘’`" and trimmed[1].isalpha()


def test_vowel_at(word: str, index: int) -> bool:
    return word[index:index + 2].lower() == "ij" or word[index] in VOWELS


def vowel_group_end(word: str, index: int) -> int:
    if word[index:index + 2].lower() == "ij":
        return index + 2
    cursor = index + 1
    while cursor < len(word) and word[cursor] in VOWELS:
        cursor += 1
    return cursor


def split_word_into_syllables(word: str) -> list[str]:
    punctuation_match = re.search(r"[,.!?;:'\")\]]+$", word)
    punctuation = punctuation_match.group(0) if punctuation_match else ""
    base = word[:-len(punctuation)] if punctuation else word
    if base.lower() == "godsdienst":
        return [base[:4], base[4:] + punctuation]
    if not base or len(base) <= 3 or re.match(r"^['’]?[kst]$", base.lower()):
        return [word]
    groups = []
    index = 0
    while index < len(base):
        if test_vowel_at(base, index):
            end = vowel_group_end(base, index)
            groups.append((index, end))
            index = end
        else:
            index += 1
    if len(groups) <= 1:
        return [word]
    boundaries = [0]
    for index in range(len(groups) - 1):
        current_end = groups[index][1]
        next_start = groups[index + 1][0]
        cluster_length = next_start - current_end
        boundary = current_end if cluster_length <= 1 else current_end + 1
        if 0 < boundary < len(base) and base[boundary - 1:boundary + 1].lower() == "ch":
            cluster = base[current_end:next_start].lower()
            boundary = current_end if cluster == "ch" else boundary + 1
        boundaries.append(boundary)
    boundaries.append(len(base))
    syllables = [base[boundaries[index]:boundaries[index + 1]] for index in range(len(boundaries) - 1)]
    syllables = [syllable for syllable in syllables if syllable]
    rebalance_syllables(syllables)
    if punctuation and syllables:
        syllables[-1] += punctuation
    return syllables


def rebalance_syllables(syllables: list[str]) -> None:
    for index in range(len(syllables) - 1):
        left = syllables[index]
        right = syllables[index + 1]
        left_lower = left.lower()
        right_lower = right.lower()
        if left_lower.endswith("s") and right_lower.startswith("ch"):
            syllables[index] = left[:-1]
            syllables[index + 1] = left[-1] + right
        elif left_lower.endswith("d") and right_lower.startswith("sv"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower == "god" and right_lower.startswith("s"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower == "dood" and right_lower.startswith("sge"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower.endswith("d") and right_lower.startswith("stge"):
            syllables[index] = left + right[:2]
            syllables[index + 1] = right[2:]
        elif left_lower in ("leid", "een") and right_lower.startswith("sg"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower == "geg" and right_lower.startswith("r"):
            syllables[index] = left[:-1]
            syllables[index + 1] = left[-1] + right
        elif left_lower.endswith("von") and right_lower.startswith("dst"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower in ("trot", "groot") and right_lower.startswith("sheid"):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower.endswith("t") and (right_lower.startswith("saard") or right_lower.startswith("ssteen") or right_lower.startswith("sheer")):
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]
        elif left_lower.endswith("on") and right_lower.startswith("t") and len(right) > 1:
            syllables[index] = left + right[0]
            syllables[index + 1] = right[1:]


def lyric_tokens(line: str, target_count: int) -> list[dict]:
    tokens: list[dict] = []
    pending_prefix = ""
    for word in [part for part in re.split(r"\s+", line.strip()) if part]:
        if is_prefix_word(word):
            pending_prefix = f"{pending_prefix} {word}".strip()
            continue
        syllables = split_word_into_syllables(word)
        if pending_prefix and syllables:
            syllables[0] = f"{pending_prefix} {syllables[0]}"
            pending_prefix = ""
        for index, syllable in enumerate(syllables):
            if not syllable:
                continue
            if len(syllables) == 1:
                syllabic = "single"
            elif index == 0:
                syllabic = "begin"
            elif index == len(syllables) - 1:
                syllabic = "end"
            else:
                syllabic = "middle"
            tokens.append({"text": syllable, "syllabic": syllabic})
    if pending_prefix:
        tokens.append({"text": pending_prefix, "syllabic": "single"})
    while len(tokens) > target_count and len(tokens) > 1:
        last = tokens.pop()
        tokens[-1] = {"text": f"{tokens[-1]['text']} {last['text']}", "syllabic": "single"}
    return tokens


def duration_type(duration: int) -> tuple[str, bool]:
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


def note_xml(note: Note, lyric: dict | None) -> str:
    note_attributes = ' print-object="no"' if note.hidden else ""
    if note.rest:
        pitch = "<rest />"
    else:
        alter = f"<alter>{note.alter}</alter>" if note.alter else ""
        pitch = f"<pitch><step>{note.step}</step>{alter}<octave>{note.octave}</octave></pitch>"
    note_type, dotted = duration_type(note.duration)
    dot = "<dot/>" if dotted else ""
    lyric_tag = f"<lyric number=\"1\"><syllabic>{lyric['syllabic']}</syllabic><text>{escape_xml(lyric['text'])}</text></lyric>" if lyric else ""
    return f"      <note{note_attributes}>{pitch}<duration>{note.duration}</duration><voice>1</voice><type>{note_type}</type>{dot}{lyric_tag}</note>"


def pad_short_melody_lines(melody_lines: list[list[Note]]) -> list[list[Note]]:
    target_duration = max((sum(note.duration for note in notes) for notes in melody_lines), default=0)
    padded_lines: list[list[Note]] = []
    for notes in melody_lines:
        duration = sum(note.duration for note in notes)
        padded_notes = list(notes)
        remaining = target_duration - duration
        while remaining > 0:
            rest_duration = min(96, remaining)
            padded_notes.append(Note(duration=rest_duration, rest=True, hidden=True))
            remaining -= rest_duration
        padded_lines.append(padded_notes)
    return padded_lines


def escape_xml(value: str | None) -> str:
    return html.escape(value or "", quote=False)


def write_musicxml(hymn: dict, melody: dict, verse: dict, file_name: str):
    lines = verse["lines"]
    melody_lines = pad_short_melody_lines(melody["lines"])
    xml = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<score-partwise version="4.0">',
        f"  <work><work-title>Gezang {hymn['number']} vers {verse['number']}</work-title></work>",
        f"  <identification><creator type=\"composer\">{escape_xml(melody.get('composer', ''))}</creator></identification>",
        '  <part-list>',
        '    <score-part id="P1"><part-name>Gezang</part-name></score-part>',
        '  </part-list>',
        '  <part id="P1">',
    ]
    for line_index, notes in enumerate(melody_lines):
        text_line = lines[line_index] if line_index < len(lines) else ""
        lyric_note_count = sum(1 for note in notes if not note.rest)
        tokens = lyric_tokens(text_line, max(1, lyric_note_count))
        lyric_index = 0
        xml.append(f"    <measure number=\"{line_index + 1}\">")
        if line_index == 0:
            xml.append(f"      <attributes><divisions>{DIVISIONS}</divisions><key><fifths>{melody['fifths']}</fifths></key><clef><sign>G</sign><line>2</line></clef></attributes>")
        for note in notes:
            lyric = None
            if not note.rest and lyric_index < len(tokens):
                lyric = tokens[lyric_index]
                lyric_index += 1
            xml.append(note_xml(note, lyric))
        xml.append("    </measure>")
    xml.extend(['  </part>', '</score-partwise>'])
    (ASSETS_DIR / file_name).write_text("\n".join(xml), encoding="utf-8")


def display_text_line(line: str) -> str:
    return re.sub(r"_+", "", line)


def lyric_line_source(line: str, notes: list[Note]) -> dict:
    lyric_note_count = sum(1 for note in notes if not note.rest)
    tokens = lyric_tokens(line, max(1, lyric_note_count))
    return {
        "raw": line,
        "display": display_text_line(line),
        "tokens": [
            {
                "text": token["text"],
                "syllabic": token["syllabic"],
            }
            for token in tokens
        ],
    }


def write_lyric_source(hymn: dict, melody: dict):
    melody_lines = pad_short_melody_lines(melody["lines"])
    data = {
        "number": hymn["number"],
        "title": hymn["title"],
        "sourceText": relative_source_path("texts", hymn["number"]),
        "sourceMelody": relative_source_path("melodies", hymn["number"]),
        "verses": [],
    }
    for verse in hymn["verses"]:
        lines = verse["lines"]
        data["verses"].append({
            "number": verse["number"],
            "lines": [
                lyric_line_source(lines[index] if index < len(lines) else "", notes)
                for index, notes in enumerate(melody_lines)
            ],
        })
    write_json(source_path(LYRIC_SOURCE_DIR, hymn["number"]), data)


def line_token_counts(lines: list[str]) -> list[int]:
    return [len(lyric_tokens(line, 10_000)) for line in lines]


def remove_temporary_text_assets():
    for path in ASSETS_DIR.glob("Gezang*.txt"):
        path.unlink()


def load_manifest():
    if MANIFEST_PATH.exists():
        text = MANIFEST_PATH.read_text(encoding="utf-8-sig")
        return json.loads(text)
    return {"items": []}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ids", nargs="+", default=["1..12"])
    parser.add_argument("--refresh-sources", action="store_true", help="Fetch text/MIDI again and overwrite local source JSON files.")
    parser.add_argument("--write-musicxml", action="store_true", help="Also write legacy per-verse MusicXML files to app assets.")
    args = parser.parse_args()

    ids = parse_id_range(args.ids)
    remove_temporary_text_assets()
    manifest = load_manifest()
    existing_items = [item for item in manifest.get("items", []) if item.get("book") not in ("hymns", "gezangen")]
    new_items = []
    psalm_140 = parse_abc_psalm(140)

    for hymn_id in ids:
        hymn = load_or_fetch_hymn(hymn_id, args.refresh_sources)
        melody = load_or_create_melody(hymn_id, hymn, args.refresh_sources, psalm_140)
        write_lyric_source(hymn, melody)
        for verse in hymn["verses"]:
            file_name = f"Gezang{hymn_id}_v{verse['number']}.xml"
            if args.write_musicxml:
                write_musicxml(hymn, melody, verse, file_name)
            new_items.append({
                "book": "gezangen",
                "number": hymn_id,
                "verse": verse["number"],
                "fileName": file_name,
                "firstLine": verse["lines"][0] if verse["lines"] else hymn["title"],
                "title": hymn["title"],
            })
        print(f"Gezang {hymn_id} ({hymn['title']}): generated {len(hymn['verses'])} verse(s).")

    MANIFEST_PATH.write_text(json.dumps({"items": existing_items + new_items}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Updated bundled-content-manifest.json with {len(existing_items) + len(new_items)} items.")


if __name__ == "__main__":
    main()