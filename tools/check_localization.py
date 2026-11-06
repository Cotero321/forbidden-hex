"""Check Simplified Chinese coverage and Minecraft translation arguments."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re


FORMAT = re.compile(r"%(?:(\d+)\$)?([sd%])")


def load_language(path: Path) -> dict[str, str]:
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{path.name}: duplicate key {key}")
            if not isinstance(value, str) or not value.strip():
                raise ValueError(f"{path.name}: empty/non-string translation {key}")
            result[key] = value
        return result

    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_pairs)


def arguments(value: str) -> Counter:
    implicit = 0
    result = Counter()
    for match in FORMAT.finditer(value):
        position, kind = match.groups()
        if kind == "%":
            continue
        if position is None:
            implicit += 1
            position = implicit
        result[(int(position), kind)] += 1
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--lang-dir", type=Path, default=Path(__file__).resolve().parents[1]
                        / "src/main/resources/assets/forbidden_hex/lang")
    parser.add_argument("--report", type=Path, help="Optional explicit JSON output path")
    args = parser.parse_args()
    en = load_language(args.lang_dir / "en_us.json")
    zh = load_language(args.lang_dir / "zh_cn.json")
    missing = sorted(en.keys() - zh.keys())
    mismatched = sorted(key for key in en.keys() & zh.keys()
                        if arguments(en[key]) != arguments(zh[key]))
    report = {"en_keys": len(en), "zh_keys": len(zh), "missing_zh_cn": missing,
              "argument_mismatches": mismatched, "zh_only_compatibility_keys": sorted(zh.keys() - en.keys()),
              "passed": not missing and not mismatched}
    if args.report:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Localization {'PASS' if report['passed'] else 'FAIL'}: "
          f"{len(en)} English / {len(zh)} Chinese keys; "
          f"{len(missing)} missing, {len(mismatched)} argument mismatches")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
