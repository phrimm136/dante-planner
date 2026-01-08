#!/usr/bin/env python3
"""
Unified EGO Gift data processing script.

Merges functionality from:
- giftSpec.py
- giftDesc.py
- giftThemePack.py
- egoGiftNameList.py
- egoGiftSpecList.py

Usage:
    python gift.py              # Run all steps in order
    python gift.py --step spec  # Run specific step
    python gift.py --list       # Show available steps
"""

import json
import glob
import os
import re
import argparse
from pathlib import Path

# --- 설정 ---
from lang_config import LANGS, get_raw_pattern, get_lang_dir, lang_dir_exists

RAW_DIR = "../raw/Json"
DATA_DIR = "../static/data/egoGift"
I18N_DIR = "../static/i18n"
THEME_PACK_PATH = "../static/data/themePackList.json"
COMMON_DATA_PATTERN = "../raw/Json/*mirror-dungeon-common-data*.json"

LUNAR_MEMORY_ID = 9083
PLACEHOLDER_OBTAIN = "Shop"

# --- 공용 함수 ---

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data):
    dir_name = os.path.dirname(path)
    if dir_name:
        os.makedirs(dir_name, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


# =============================================================================
# Step 1: spec - data 파일 생성
# =============================================================================
def step_spec():
    """Create EGO gift data files."""
    print("[1/5] spec: Creating data files...")

    os.makedirs(DATA_DIR, exist_ok=True)

    input_pattern = os.path.join(RAW_DIR, "ego-gift-*.json")
    extreme_gift_pattern = os.path.join(RAW_DIR, "ego-gift-mirrordungeon*-hb.json")
    path_regex = re.compile(r"a\d+c\d+p\d+")

    # 하드/익스트림 전용 ID 로드
    hard_only_ids = set()
    extreme_only_ids = set()
    recipes = {}

    # Glob all common data files and merge recipes
    for common_path in glob.glob(COMMON_DATA_PATTERN):
        common_data = load_json(common_path)

        combine_table = common_data.get("egoGiftCombineFixedTable", {})
        non_easy_ids = combine_table.get("nonAcquireableInEasyIds", [])
        hard_only_ids.update(set(non_easy_ids) - {LUNAR_MEMORY_ID})

        # 조합식 파싱 (combineFixed)
        for item in combine_table.get("combineFixed", []):
            result_id = item.get("resultEgoGiftId")
            required = item.get("requiredEgoGiftIds", [])
            if result_id and required:
                if result_id not in recipes:
                    recipes[result_id] = {"materials": [required]}
                elif "materials" in recipes[result_id]:
                    # Avoid duplicate recipes
                    if required not in recipes[result_id]["materials"]:
                        recipes[result_id]["materials"].append(required)

        # 조합식 파싱 (combineMixed)
        for item in combine_table.get("combineMixed", []):
            result_id = item.get("resultEgoGiftId")
            if result_id:
                recipes[result_id] = {
                    "type": "mixed",
                    "a": {
                        "ids": item.get("aEgoGiftIds", []),
                        "count": item.get("aEgoGiftRequiredNum", 0)
                    },
                    "b": {
                        "ids": item.get("bEgoGiftIds", []),
                        "count": item.get("bEgoGiftRequiredNum", 0)
                    }
                }

    # 익스트림 전용 ID 로드
    for extreme_path in glob.glob(extreme_gift_pattern):
        extreme_data = load_json(extreme_path)
        for item in extreme_data.get("list", []):
            extreme_only_ids.add(item.get("id"))

    # 기프트 데이터 처리
    count = 0
    for file_path in glob.glob(input_pattern):
        if path_regex.search(file_path):
            continue

        data = load_json(file_path)

        for item in data.get("list", []):
            gift_id = item.get("id")
            id_str = str(gift_id)

            # id가 9로 시작하는 4자리 숫자인지 확인
            if len(id_str) == 4 and id_str.startswith("9") and id_str.isdigit():
                output_data = {
                    "attributeType": item.get("attributeType"),
                    "tag": item.get("tag"),
                    "keyword": item.get("keyword"),
                    "price": item.get("price"),
                    "themePack": [],
                }

                if gift_id in hard_only_ids:
                    output_data["hardOnly"] = True

                if gift_id in extreme_only_ids:
                    output_data["extremeOnly"] = True

                if gift_id in recipes:
                    output_data["recipe"] = recipes[gift_id]

                output_path = os.path.join(DATA_DIR, f"{id_str}.json")
                save_json(output_path, output_data)
                count += 1

    print(f"  {count} files created")


# =============================================================================
# Step 2: desc - i18n 파일 생성
# =============================================================================
def parse_gift_id(ability_id: int):
    """Returns (base_id: int, stage: int)"""
    s = str(ability_id)
    if len(s) == 4:
        return int(s), 0
    elif len(s) == 5:
        return int(s[1:]), int(s[0])
    else:
        raise ValueError(f"Unexpected id format: {ability_id}")


def step_desc():
    """Create i18n files with gift descriptions."""
    print("[2/5] desc: Creating i18n files...")

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        output_dir = os.path.join(I18N_DIR, lang, "egoGift")
        os.makedirs(output_dir, exist_ok=True)

        gifts = {}

        pattern = get_raw_pattern(lang, "EGOgift_*.json")
        for path in glob.glob(pattern):
            data = load_json(path)

            for entry in data.get("dataList", []):
                try:
                    base_id, stage = parse_gift_id(entry["id"])
                except ValueError:
                    continue

                if stage > 2:
                    continue

                if base_id < 9000:
                    continue

                if base_id not in gifts:
                    gifts[base_id] = {
                        "name": "",
                        "descs": ["", "", ""],
                        "obtain": PLACEHOLDER_OBTAIN
                    }

                if stage == 0:
                    gifts[base_id]["name"] = entry.get("name", "")

                gifts[base_id]["descs"][stage] = entry.get("desc", "")

        count = 0
        for base_id, gift in gifts.items():
            out_path = os.path.join(output_dir, f"{base_id}.json")
            save_json(out_path, gift)
            count += 1

        print(f"  [{lang}] {count} files created")


# =============================================================================
# Step 3: theme_pack - data 파일에 테마팩 정보 추가
# =============================================================================
def step_theme_pack():
    """Add theme pack info to gift data files."""
    print("[3/5] theme_pack: Adding theme pack info...")

    if not os.path.exists(THEME_PACK_PATH):
        print(f"  [WARN] Theme pack file not found: {THEME_PACK_PATH}")
        return

    theme_packs = load_json(THEME_PACK_PATH)
    count = 0

    for pack_id, pack_data in theme_packs.items():
        gift_ids = pack_data.get("specificEgoGiftPool", [])

        if not gift_ids:
            continue

        for gift_id in gift_ids:
            gift_path = os.path.join(DATA_DIR, f"{gift_id}.json")

            if not os.path.exists(gift_path):
                continue

            gift_data = load_json(gift_path)

            if "themePack" not in gift_data:
                gift_data["themePack"] = []

            if pack_id not in gift_data["themePack"]:
                gift_data["themePack"].append(pack_id)
                save_json(gift_path, gift_data)
                count += 1

    print(f"  {count} theme pack assignments added")


# =============================================================================
# Step 4: name_list - i18n 이름 목록 집계
# =============================================================================
def step_name_list():
    """Aggregate gift names into list files."""
    print("[4/5] name_list: Aggregating name lists...")

    for lang in LANGS:
        gift_dir = Path(I18N_DIR) / lang / "egoGift"
        output_path = Path(I18N_DIR) / lang / "egoGiftNameList.json"

        if not gift_dir.exists():
            print(f"  [{lang}] directory not found")
            continue

        temp = {}
        for json_path in gift_dir.glob("*.json"):
            try:
                with json_path.open("r", encoding="utf-8") as f:
                    data = json.load(f)

                file_id = json_path.stem
                name = data.get("name")

                if name is not None:
                    temp[file_id] = name
            except Exception as e:
                print(f"  [ERROR] {json_path}: {e}")

        result = dict(sorted(temp.items(), key=lambda x: x[0]))
        save_json(str(output_path), result)
        print(f"  [{lang}] {len(result)} entries")


# =============================================================================
# Step 5: spec_list - data 스펙 목록 집계
# =============================================================================
def step_spec_list():
    """Aggregate gift specs into list file."""
    print("[5/5] spec_list: Aggregating spec list...")

    base_dir = Path(DATA_DIR)
    output_path = Path("../static/data/egoGiftSpecList.json")

    result = {}

    for json_path in sorted(base_dir.glob("*.json")):
        gift_id = json_path.stem

        with json_path.open("r", encoding="utf-8") as f:
            data = json.load(f)

        entry = {
            "tag": data.get("tag"),
            "keyword": data.get("keyword"),
            "attributeType": data.get("attributeType"),
            "themePack": data.get("themePack"),
        }

        # Only include optional fields when present
        if data.get("hardOnly"):
            entry["hardOnly"] = True
        if data.get("extremeOnly"):
            entry["extremeOnly"] = True
        if data.get("recipe"):
            entry["recipe"] = data["recipe"]

        result[gift_id] = entry

    result = dict(sorted(result.items(), key=lambda x: x[0]))
    save_json(str(output_path), result)
    print(f"  {len(result)} entries")


# =============================================================================
# 메인
# =============================================================================
STEPS = {
    "spec": step_spec,
    "desc": step_desc,
    "theme_pack": step_theme_pack,
    "name_list": step_name_list,
    "spec_list": step_spec_list,
}

STEP_ORDER = ["spec", "desc", "theme_pack", "name_list", "spec_list"]


def main():
    parser = argparse.ArgumentParser(description="Unified EGO Gift data processor")
    parser.add_argument("--step", "-s", choices=STEPS.keys(), help="Run specific step only")
    parser.add_argument("--list", "-l", action="store_true", help="List available steps")

    args = parser.parse_args()

    # 스크립트 디렉토리로 이동
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)

    if args.list:
        print("Available steps (in dependency order):")
        for i, step in enumerate(STEP_ORDER, 1):
            print(f"  {i}. {step}")
        return 0

    if args.step:
        print(f"Running step: {args.step}")
        STEPS[args.step]()
    else:
        print("Running all steps in dependency order...\n")
        for step in STEP_ORDER:
            STEPS[step]()
            print()

    print("Done!")
    return 0


if __name__ == "__main__":
    exit(main())
