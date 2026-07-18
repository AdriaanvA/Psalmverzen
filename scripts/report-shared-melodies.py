import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ASSET_CONTENT_DIR = ROOT / "app" / "src" / "main" / "assets" / "content"


def read_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def item_sort_key(item: str):
    kind, number = item.split()
    kind_order = {"Psalm": 0, "Gezang": 1}
    return kind_order.get(kind, 99), int(number)


def collect_groups():
    groups = {}
    missing = []
    for directory, label in (
        (ASSET_CONTENT_DIR / "psalms" / "texts", "Psalm"),
        (ASSET_CONTENT_DIR / "gezangen" / "texts", "Gezang"),
    ):
        for path in sorted(directory.glob("*.json")):
            data = read_json(path)
            melody_file = data["melodyFile"]
            melody_path = ROOT / "app" / "src" / "main" / "assets" / melody_file
            item = f"{label} {data['number']}"
            groups.setdefault(melody_file, []).append(item)
            if not melody_path.exists():
                missing.append((item, melody_file))

    for items in groups.values():
        items.sort(key=item_sort_key)
    return groups, missing


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--all", action="store_true", help="Show unique melody groups too.")
    args = parser.parse_args()

    groups, missing = collect_groups()
    if missing:
        for item, melody_file in missing:
            print(f"missing melody for {item}: {melody_file}", file=sys.stderr)
        return 1

    selected = groups if args.all else {key: value for key, value in groups.items() if len(value) > 1}
    print(f"melody_count {len(groups)}")
    print(f"shared_melody_count {sum(1 for items in groups.values() if len(items) > 1)}")
    for melody_file, items in sorted(selected.items(), key=lambda entry: item_sort_key(entry[1][0])):
        print(f"{melody_file}: {', '.join(items)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())