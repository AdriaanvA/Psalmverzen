import argparse
import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONTENT_DIR = ROOT / "content"
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
REPORT_DIR = ROOT / "build" / "reports"
DEFAULT_REVIEW_TSV = REPORT_DIR / "lyric-review.tsv"
SUSPECT_BOUNDARIES = {"c-h", "s-c", "s-z", "t-s", "d-s", "d-z"}


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def lyric_source_paths() -> list[Path]:
    return sorted((CONTENT_DIR / "psalmen" / "lyrics").glob("Psalm*.json")) + sorted((CONTENT_DIR / "gezangen" / "lyrics").glob("Gezang*.json"))


def book_name(path: Path) -> str:
    return "gezangen" if path.name.startswith("Gezang") else "psalms"


def display_book(book: str) -> str:
    return "Gezang" if book == "gezangen" else "Psalm"


def token_count(line: dict) -> int:
    return len(line.get("tokens", []))


def format_tokens(tokens: list[dict]) -> str:
    return " | ".join(f"{token.get('text', '')}{{{token.get('syllabic', 'single')}}}" for token in tokens)


def format_hyphenated(tokens: list[dict]) -> str:
    parts = []
    for token in tokens:
        text = token.get("text", "")
        syllabic = token.get("syllabic", "single")
        if parts and syllabic in ("middle", "end"):
            parts[-1] = f"{parts[-1]}-{text}"
        else:
            parts.append(text)
    return " ".join(parts)


def parse_tokens(value: str) -> list[dict]:
    tokens = []
    for part in [part.strip() for part in value.split("|") if part.strip()]:
        if part.endswith("}") and "{" in part:
            text, syllabic = part.rsplit("{", 1)
            tokens.append({"text": text.strip(), "syllabic": syllabic[:-1].strip() or "single"})
        else:
            tokens.append({"text": part, "syllabic": "single"})
    return tokens


def parse_hyphenated(value: str) -> list[dict]:
    tokens = []
    for word in [part for part in value.strip().split() if part]:
        syllables = [part for part in word.split("-") if part]
        if len(syllables) <= 1:
            tokens.append({"text": word, "syllabic": "single"})
            continue
        for index, syllable in enumerate(syllables):
            if index == 0:
                syllabic = "begin"
            elif index == len(syllables) - 1:
                syllabic = "end"
            else:
                syllabic = "middle"
            tokens.append({"text": syllable, "syllabic": syllabic})
    return tokens


def first_letter(value: str) -> str:
    for character in value.strip():
        if character.isalpha():
            return character.lower()
    return ""


def last_letter(value: str) -> str:
    for character in reversed(value.strip()):
        if character.isalpha():
            return character.lower()
    return ""


def is_same_word_boundary(left: dict, right: dict) -> bool:
    return left.get("syllabic") in ("begin", "middle") and right.get("syllabic") in ("middle", "end")


def suspect_boundaries(line: dict) -> list[str]:
    tokens = line.get("tokens", [])
    result = []
    for index in range(len(tokens) - 1):
        left = tokens[index]
        right = tokens[index + 1]
        if not is_same_word_boundary(left, right):
            continue
        left_text = left.get("text", "")
        right_text = right.get("text", "")
        if left_text.lower() == "ont" and right_text.lower().startswith("s"):
            continue
        if is_accepted_boundary(left_text, right_text):
            continue
        boundary = f"{last_letter(left.get('text', ''))}-{first_letter(right.get('text', ''))}"
        if boundary in SUSPECT_BOUNDARIES:
            result.append(boundary)
    return result


def is_accepted_boundary(left: str, right: str) -> bool:
    left_lower = left.lower().lstrip("'’`\u2018\u2019\ufffd")
    right_lower = right.lower().rstrip(".,;:!?\"”")
    if left_lower in ("nood", "vreed") and right_lower.startswith("z"):
        return True
    if left_lower.endswith(("snood", "raad", "blijd", "bloed", "dood", "leids", "voed", "bood", "breed", "oud", "vond")) and right_lower.startswith("s"):
        return True
    if left_lower.endswith("bos") and right_lower.startswith("z"):
        return True
    if left_lower.endswith("wijdst") and right_lower.startswith("g"):
        return True
    if left_lower.endswith(("trot", "groot", "heet", "toet", "voet", "zoet", "laat", "plaat", "rot", "bit", "uit", "noot", "ont")) and right_lower.startswith("s"):
        return True
    if left_lower.endswith("uit") and right_lower.startswith("spraak"):
        return True
    return False


def fix_ch_split(tokens: list[dict]) -> int:
    fixes = 0
    for index in range(len(tokens) - 1):
        left = tokens[index].get("text", "")
        right = tokens[index + 1].get("text", "")
        if not left.lower().endswith("c") or not right.lower().startswith("h"):
            continue
        if right.lower().startswith("ht"):
            tokens[index]["text"] = left + right[0]
            tokens[index + 1]["text"] = right[1:]
        else:
            tokens[index]["text"] = left[:-1]
            tokens[index + 1]["text"] = left[-1] + right
        fixes += 1
    return fixes


def rebalance_suspect_split(left: dict, right: dict) -> bool:
    left_text = left.get("text", "")
    right_text = right.get("text", "")
    left_lower = left_text.lower()
    right_lower = right_text.lower()
    if not is_same_word_boundary(left, right):
        return False

    if left_lower.endswith("s") and right_lower.startswith("ch"):
        left["text"] = left_text[:-1]
        right["text"] = left_text[-1] + right_text
        return True

    if left_lower.endswith("d") and right_lower.startswith("sv"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower == "god" and right_lower.startswith("s"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower == "dood" and right_lower.startswith("sge"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower.endswith("d") and right_lower.startswith("stge"):
        left["text"] = left_text + right_text[:2]
        right["text"] = right_text[2:]
        return True

    if left_lower in ("leid", "een") and right_lower.startswith("sg"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower == "geg" and right_lower.startswith("r"):
        left["text"] = left_text[:-1]
        right["text"] = left_text[-1] + right_text
        return True

    if left_lower.endswith("von") and right_lower.startswith("dst"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower in ("trot", "groot") and right_lower.startswith("sheid"):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower.endswith("t") and (right_lower.startswith("saard") or right_lower.startswith("ssteen") or right_lower.startswith("sheer")):
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    if left_lower.endswith("on") and right_lower.startswith("t") and len(right_text) > 1:
        left["text"] = left_text + right_text[0]
        right["text"] = right_text[1:]
        return True

    return False


def fix_suspect_splits(tokens: list[dict]) -> int:
    fixes = 0
    for index in range(len(tokens) - 1):
        if rebalance_suspect_split(tokens[index], tokens[index + 1]):
            fixes += 1
    return fixes


def repair_question_mark_tokens(line: dict) -> int:
    raw = line.get("raw", "")
    if "?" not in "".join(token.get("text", "") for token in line.get("tokens", [])):
        return 0

    fixes = 0
    cursor = 0
    for token in line.get("tokens", []):
        text = token.get("text", "")
        if not text:
            continue
        repaired = []
        for character in text:
            if character == "?":
                while cursor < len(raw) and raw[cursor].isspace():
                    cursor += 1
                replacement = raw[cursor] if cursor < len(raw) else character
                repaired.append(replacement)
                cursor += 1
                if replacement != character:
                    fixes += 1
                continue

            position = raw.find(character, cursor)
            if position >= 0:
                cursor = position + 1
            elif cursor < len(raw):
                cursor += 1
            repaired.append(character)
        token["text"] = "".join(repaired)
    return fixes


def source_lookup() -> dict[tuple[str, int], Path]:
    result = {}
    for path in lyric_source_paths():
        data = read_json(path)
        result[(book_name(path), int(data["number"]))] = path
    return result


def app_text_paths() -> list[Path]:
    psalm_paths = sorted((ASSETS_DIR / "content" / "psalms" / "texts").glob("Psalm*.json"))
    gezang_paths = sorted((ASSETS_DIR / "content" / "gezangen" / "texts").glob("Gezang*.json"))
    return psalm_paths + gezang_paths


def melody_slot_counts(text_asset: dict) -> list[int]:
    melody_file = text_asset["melodyFile"].removeprefix("content/")
    melody = read_json(ASSETS_DIR / "content" / melody_file)
    return [sum(1 for note in line if note.get("lyricSlot")) for line in melody["melody"]["lines"]]


def audit_rows(include_line_length: bool) -> list[dict]:
    rows = []
    sources = source_lookup()
    for text_path in app_text_paths():
        text_asset = read_json(text_path)
        book = text_asset["book"]
        number = int(text_asset["number"])
        source_path = sources.get((book, number), text_path)
        slots_by_line = melody_slot_counts(text_asset)
        for verse in text_asset.get("verses", []):
            for line_index, line in enumerate(verse.get("lines", []), start=1):
                expected = slots_by_line[line_index - 1] if line_index <= len(slots_by_line) else ""
                actual = token_count(line)
                issues = []
                boundaries = suspect_boundaries(line)
                if include_line_length and expected != "" and actual != expected:
                    issues.append("line-length")
                if boundaries:
                    issues.append("suspect-split")
                if not issues:
                    continue
                hyphenated = format_hyphenated(line.get("tokens", []))
                rows.append({
                    "issues": ",".join(issues),
                    "suspectBoundaries": ",".join(boundaries),
                    "book": display_book(book),
                    "number": number,
                    "verse": verse["number"],
                    "line": line_index,
                    "expectedSlots": expected,
                    "actualTokens": actual,
                    "delta": "" if expected == "" else actual - expected,
                    "raw": line.get("raw", ""),
                    "hyphenated": hyphenated,
                    "correctedHyphenated": hyphenated,
                    "tokens": format_tokens(line.get("tokens", [])),
                    "correctedTokens": format_tokens(line.get("tokens", [])),
                    "sourcePath": str(source_path.relative_to(ROOT)),
                })
    return rows


def fix_ch_splits_in_sources() -> int:
    changed_files = 0
    total_fixes = 0
    for path in lyric_source_paths():
        data = read_json(path)
        file_fixes = 0
        for verse in data.get("verses", []):
            for line in verse.get("lines", []):
                file_fixes += fix_ch_split(line.get("tokens", []))
        if file_fixes:
            write_json(path, data)
            changed_files += 1
            total_fixes += file_fixes
    print(f"ch_split_fixes {total_fixes}")
    print(f"changed_files {changed_files}")
    return total_fixes


def fix_suspect_splits_in_sources() -> int:
    changed_files = 0
    total_fixes = 0
    for path in lyric_source_paths():
        data = read_json(path)
        file_fixes = 0
        for verse in data.get("verses", []):
            for line in verse.get("lines", []):
                file_fixes += repair_question_mark_tokens(line)
                file_fixes += fix_suspect_splits(line.get("tokens", []))
        if file_fixes:
            write_json(path, data)
            changed_files += 1
            total_fixes += file_fixes
    print(f"suspect_split_fixes {total_fixes}")
    print(f"changed_files {changed_files}")
    return total_fixes


def write_review_tsv(path: Path, include_line_length: bool) -> int:
    path = path if path.is_absolute() else ROOT / path
    rows = audit_rows(include_line_length)
    path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ["issues", "suspectBoundaries", "book", "number", "verse", "line", "expectedSlots", "actualTokens", "delta", "raw", "hyphenated", "correctedHyphenated", "tokens", "correctedTokens", "sourcePath"]
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print(f"review_rows {len(rows)}")
    print(f"review_tsv {path.relative_to(ROOT)}")
    return len(rows)


def apply_review_tsv(path: Path) -> int:
    path = path if path.is_absolute() else ROOT / path
    grouped: dict[Path, list[dict]] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle, delimiter="\t"):
            hyphenated_changed = row.get("correctedHyphenated", "").strip() != row.get("hyphenated", "").strip()
            tokens_changed = row.get("correctedTokens", "").strip() != row.get("tokens", "").strip()
            if not hyphenated_changed and not tokens_changed:
                continue
            grouped.setdefault(ROOT / row["sourcePath"], []).append(row)

    changed_lines = 0
    for source_path, rows in grouped.items():
        data = read_json(source_path)
        for row in rows:
            verse_number = int(row["verse"])
            line_number = int(row["line"])
            verse = next(verse for verse in data["verses"] if int(verse["number"]) == verse_number)
            if row.get("correctedHyphenated", "").strip() != row.get("hyphenated", "").strip():
                verse["lines"][line_number - 1]["tokens"] = parse_hyphenated(row["correctedHyphenated"])
            else:
                verse["lines"][line_number - 1]["tokens"] = parse_tokens(row["correctedTokens"])
            changed_lines += 1
        write_json(source_path, data)
    print(f"applied_lines {changed_lines}")
    print(f"changed_files {len(grouped)}")
    return changed_lines


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix-ch", action="store_true", help="Repair token splits that separate c and h.")
    parser.add_argument("--fix-suspect", action="store_true", help="Repair common Dutch consonant split mistakes such as be-scho and Gods-vrees.")
    parser.add_argument("--include-line-length", action="store_true", help="Also include lines whose token count differs from the melody lyric-slot count.")
    parser.add_argument("--write-review-tsv", type=Path, default=DEFAULT_REVIEW_TSV)
    parser.add_argument("--apply-review-tsv", type=Path)
    args = parser.parse_args()

    if args.fix_ch:
        fix_ch_splits_in_sources()
    if args.fix_suspect:
        fix_suspect_splits_in_sources()
    if args.apply_review_tsv:
        apply_review_tsv(args.apply_review_tsv)
    write_review_tsv(args.write_review_tsv, args.include_line_length)


if __name__ == "__main__":
    main()