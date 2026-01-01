#!/usr/bin/env python3
"""
Unified identity data processing script.

Merges functionality from:
- idName.py, idNameList.py
- idSpec.py, idSpecList.py
- idSkill.py, idSkillDesc.py
- idPassive.py, idPassiveDesc.py

Usage:
    python identity.py              # Run all steps in order
    python identity.py --step name  # Run specific step
    python identity.py --list       # Show available steps
"""

import json
import glob
import os
import re
import argparse
from copy import deepcopy
from pathlib import Path

# --- 설정 ---
LANGS = ["KR", "EN", "JP"]
RAW_DIR = "../raw/Json"
DATA_DIR = "../static/data/identity"
I18N_DIR = "../static/i18n"
SKILL_STRUCTURE_PATH = "idSkillStructure.json"

TAG_RE = re.compile(r"<[^>]+>")
FILENAME_RE = re.compile(r"^personality-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
SKILL_FILE_RE = re.compile(r"^personality-skill-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
PASSIVE_LIST_RE = re.compile(r"^personality-passive-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")

LEVEL_COUNT = 4

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


def strip_tags(text: str) -> str:
    if not text:
        return ""
    return TAG_RE.sub("", text)


# =============================================================================
# Step 1: name - i18n 기본 파일 생성
# =============================================================================
def step_name():
    """Create base i18n files with identity names."""
    print("[1/8] name: Creating i18n base files...")

    for lang in LANGS:
        input_path = os.path.join(RAW_DIR, lang, f"{lang}_Personalities.json")
        if not os.path.exists(input_path):
            continue

        data = load_json(input_path)
        out_dir = os.path.join(I18N_DIR, lang, "identity")
        os.makedirs(out_dir, exist_ok=True)

        count = 0
        for entry in data.get("dataList", []):
            raw_id = str(entry.get("id"))
            if len(raw_id) != 5:
                continue

            title = entry.get("title", "")
            out_path = os.path.join(out_dir, f"{raw_id}.json")

            base = {"name": title, "skills": {}, "passives": {}}
            save_json(out_path, base)
            count += 1

        print(f"  [{lang}] {count} files created")


# =============================================================================
# Step 2: spec - data 기본 파일 생성
# =============================================================================
def transform_mental_condition(info: dict) -> dict:
    if not info:
        return info

    result = {}
    for key, entries in info.items():
        flat = []
        for entry in entries:
            for c in entry.get("conditionIDList", []):
                cid = c.get("conditionID")
                if cid:
                    flat.append(cid)
        result[key] = flat
    return result


def transform_unit(unit: dict) -> dict:
    out = {}

    keep_fields = [
        "updatedDate", "skillKeywordList", "panicType", "season", "rank",
        "hp", "defCorrection", "minSpeedList", "maxSpeedList",
    ]

    for field in keep_fields:
        if field in unit:
            out[field] = deepcopy(unit[field])

    # Merge unitKeywordList + associationList
    unit_keywords = deepcopy(unit.get("unitKeywordList", []))
    associations = unit.get("associationList", [])
    unit_keywords.extend(associations)
    out["unitKeywordList"] = unit_keywords

    if "breakSection" in unit:
        out["staggerList"] = unit["breakSection"]["sectionList"]

    if "resistInfo" in unit:
        out["ResistInfo"] = {
            kw["type"]: kw["value"] for kw in unit["resistInfo"]["atkResistList"]
        }

    if "mentalConditionInfo" in unit:
        out["mentalConditionInfo"] = transform_mental_condition(
            unit["mentalConditionInfo"]
        )

    return out


def step_spec():
    """Create data files with identity specs."""
    print("[2/8] spec: Creating data files...")

    os.makedirs(DATA_DIR, exist_ok=True)
    id_skill_structure = {}

    files = glob.glob(os.path.join(RAW_DIR, "personality-*.json"))
    count = 0

    for path in sorted(files):
        filename = os.path.basename(path)
        if not FILENAME_RE.match(filename):
            continue

        data = load_json(path)

        for unit in data.get("list", []):
            unit_id = unit.get("id")
            if not unit_id:
                continue

            defense_skills = unit.get("defenseSkillIDList", [])
            id_skill_structure[str(unit_id)] = [[], [], [], list(defense_skills)]

            transformed = transform_unit(unit)
            out_path = os.path.join(DATA_DIR, f"{unit_id}.json")
            save_json(out_path, transformed)
            count += 1

    save_json(SKILL_STRUCTURE_PATH, id_skill_structure)
    print(f"  {count} files created + idSkillStructure.json")


# =============================================================================
# Step 3: skill - data 파일에 스킬 데이터 추가
# =============================================================================
def normalize_skill_data(skill_data_list):
    by_level = {}

    for entry in skill_data_list:
        level = entry.get("gaksungLevel")
        if level is None:
            continue

        new_entry = {}
        skip_fields = {
            "defType", "skillTargetType", "canTeamKill", "canDuel",
            "canChangeTarget", "skillMotion", "viewType",
            "parryingCloseType", "coinList", "gaksungLevel",
            "abilityScriptList", "range", "arearange", "actionScript"
        }

        for k, v in entry.items():
            if k not in skip_fields:
                new_entry[k] = deepcopy(v)

        coin_list = entry.get("coinList")
        if coin_list:
            coin = coin_list[0]
            scale = coin.get("scale")
            if scale is not None:
                if coin.get("operatorType") == "SUB":
                    scale = -scale
                new_entry["scale"] = scale

        by_level[level] = new_entry

    if not by_level:
        return [{}, {}, {}, {}]

    # Always return 4 entries for uptie levels 1-4
    return [by_level.get(i, {}) for i in range(1, 5)]


def step_skill():
    """Add skill data to identity data files."""
    print("[3/8] skill: Adding skill data...")

    id_skill_structure = load_json(SKILL_STRUCTURE_PATH)

    personalities = {}
    for path in glob.glob(os.path.join(DATA_DIR, "*.json")):
        pid = os.path.splitext(os.path.basename(path))[0]
        personalities[pid] = load_json(path)
        personalities[pid].setdefault(
            "skills", {"skill1": [], "skill2": [], "skill3": [], "skillDef": []}
        )

    for path in glob.glob(os.path.join(RAW_DIR, "personality-skill-*.json")):
        filename = os.path.basename(path)
        if not SKILL_FILE_RE.match(filename):
            continue

        data = load_json(path)

        for skill in data.get("list", []):
            skill_id = skill.get("id")
            if not skill_id:
                continue

            personality_id = str(skill_id)[:5]
            personality = personalities.get(personality_id)
            if not personality:
                continue

            tier = skill.get("skillTier")
            skill_entry = {
                "id": skill_id,
                "skillData": normalize_skill_data(skill.get("skillData", []))
            }

            def_list = id_skill_structure.get(personality_id, [[], [], [], []])[3]
            if skill_id in def_list:
                personality["skills"]["skillDef"].append(skill_entry)
            elif tier in (1, 2, 3):
                personality["skills"][f"skill{tier}"].append(skill_entry)

            struct = id_skill_structure.setdefault(personality_id, [[], [], [], []])
            target_idx = 3 if skill_id in def_list else tier - 1
            if skill_id not in struct[target_idx]:
                struct[target_idx].append(skill_id)

    for pid, data in personalities.items():
        save_json(os.path.join(DATA_DIR, f"{pid}.json"), data)

    save_json(SKILL_STRUCTURE_PATH, id_skill_structure)
    print(f"  {len(personalities)} files updated")


# =============================================================================
# Step 4: passive - data 파일에 패시브 데이터 추가
# =============================================================================
def normalize_passive_list(passive_list):
    result = [[] for _ in range(LEVEL_COUNT)]

    for entry in passive_list:
        level = entry.get("level")
        if not level or not (1 <= level <= LEVEL_COUNT):
            continue
        result[level - 1] = deepcopy(entry.get("passiveIDList", []))

    return result


def ensure_passives(personality):
    if "passives" not in personality:
        personality["passives"] = {}

    personality["passives"].setdefault(
        "battlePassiveList", [{} for _ in range(LEVEL_COUNT)]
    )
    personality["passives"].setdefault(
        "supportPassiveList", [{} for _ in range(LEVEL_COUNT)]
    )
    personality["passives"].setdefault("conditions", {})


def step_passive():
    """Add passive data to identity data files."""
    print("[4/8] passive: Adding passive data...")

    personalities = {}
    for path in glob.glob(os.path.join(DATA_DIR, "*.json")):
        pid = os.path.splitext(os.path.basename(path))[0]
        personalities[pid] = load_json(path)
        ensure_passives(personalities[pid])

    # personality-passive-XX.json 처리
    for path in glob.glob(os.path.join(RAW_DIR, "personality-passive-*.json")):
        filename = os.path.basename(path)
        if not PASSIVE_LIST_RE.match(filename):
            continue

        data = load_json(path)

        for entry in data.get("list", []):
            pid = str(entry.get("personalityID"))
            personality = personalities.get(pid)
            if not personality:
                continue

            if "battlePassiveList" in entry:
                personality["passives"]["battlePassiveList"] = \
                    normalize_passive_list(entry["battlePassiveList"])

            if "supporterPassiveList" in entry:
                personality["passives"]["supportPassiveList"] = \
                    normalize_passive_list(entry["supporterPassiveList"])

    # passive.json (조건 메타) 처리
    passive_cond_path = os.path.join(RAW_DIR, "passive.json")
    if os.path.exists(passive_cond_path):
        passive_data = load_json(passive_cond_path)

        for entry in passive_data.get("list", []):
            passive_id = str(entry.get("id"))

            cond_type = None
            cond_list = None

            if "attributeStockCondition" in entry:
                cond_type = "STOCK"
                cond_list = entry["attributeStockCondition"]
            elif "attributeResonanceCondition" in entry:
                cond_type = "RESONANCE"
                cond_list = entry["attributeResonanceCondition"]
            else:
                continue

            values = {}
            for cond in cond_list:
                attr = cond.get("type")
                val = cond.get("value")
                if attr is not None and val is not None:
                    values[attr] = val

            if not values:
                continue

            personality_id = passive_id[:5]
            personality = personalities.get(personality_id)
            if not personality:
                continue

            personality["passives"]["conditions"][passive_id] = {
                "type": cond_type,
                "values": values
            }

    for pid, data in personalities.items():
        save_json(os.path.join(DATA_DIR, f"{pid}.json"), data)

    print(f"  {len(personalities)} files updated")


# =============================================================================
# Step 5: skill_desc - i18n 파일에 스킬 설명 추가
# =============================================================================
def convert_level_item(item):
    if not item:
        return {}

    coin_descs = []
    coinlist = item.get("coinlist", [])

    if not coinlist:
        coin_descs = []
    else:
        for coin in coinlist:
            if not coin or "coindescs" not in coin:
                coin_descs.append("")
            else:
                texts = [
                    strip_tags(c.get("desc", ""))
                    for c in coin.get("coindescs", [])
                ]
                coin_descs.append("\n".join(texts))

    return {
        "desc": strip_tags(item.get("desc", "")),
        "coinDescs": coin_descs
    }


def collect_skill_files(lang):
    files = []
    base = os.path.join(RAW_DIR, lang, f"{lang}_Skills.json")
    if os.path.exists(base):
        files.append(base)

    files.extend(
        glob.glob(os.path.join(RAW_DIR, lang, f"{lang}_Skills_personality-*.json"))
    )
    return files


def step_skill_desc():
    """Add skill descriptions to i18n files."""
    print("[5/8] skill_desc: Adding skill descriptions...")

    for lang in LANGS:
        skill_files = collect_skill_files(lang)
        if not skill_files:
            continue

        identities = {}

        for path in skill_files:
            data = load_json(path)
            for entry in data.get("dataList", []):
                raw_id = str(entry.get("id")).zfill(7)
                identity_id = raw_id[:5]
                skill_id = raw_id

                identities.setdefault(identity_id, {})
                identities[identity_id].setdefault(skill_id, [])

                for lv_item in entry.get("levelList", []):
                    identities[identity_id][skill_id].append(lv_item)

        count = 0
        for identity_id, skills in identities.items():
            out_path = os.path.join(I18N_DIR, lang, "identity", f"{identity_id}.json")
            if not os.path.exists(out_path):
                continue

            identity = load_json(out_path)
            identity.setdefault("skills", {})

            for skill_id, level_items in skills.items():
                if not level_items:
                    continue

                name = strip_tags(level_items[0].get("name", ""))
                descs = [{} for _ in range(4)]

                for item in level_items:
                    lv = item.get("level")
                    if isinstance(lv, int) and 1 <= lv <= 4:
                        descs[lv - 1] = convert_level_item(item)

                identity["skills"][skill_id] = {"name": name, "descs": descs}

            save_json(out_path, identity)
            count += 1

        print(f"  [{lang}] {count} files updated")


# =============================================================================
# Step 6: passive_desc - i18n 파일에 패시브 설명 추가
# =============================================================================
def collect_passive_files(lang):
    """Collect all identity passive files for a language."""
    files = []
    base = os.path.join(RAW_DIR, lang, f"{lang}_Passives.json")
    if os.path.exists(base):
        files.append(base)

    # Additional passive files (check4, fools, etc.)
    additional_patterns = [
        f"{lang}_Passives_check*.json",
        f"{lang}_Passives_fools.json",
        f"{lang}_Passives-*.json",  # e.g., KR_Passives-ycgd.json
    ]
    for pattern in additional_patterns:
        files.extend(glob.glob(os.path.join(RAW_DIR, lang, pattern)))

    return files


def step_passive_desc():
    """Add passive descriptions to i18n files."""
    print("[6/8] passive_desc: Adding passive descriptions...")

    for lang in LANGS:
        passive_files = collect_passive_files(lang)
        if not passive_files:
            continue

        count = 0
        for input_path in passive_files:
            data = load_json(input_path)

            for item in data.get("dataList", []):
                raw_id = str(item.get("id")).zfill(7)
                # Only process identity passives (IDs starting with 10 or 11)
                if not raw_id.startswith("10") and not raw_id.startswith("11"):
                    continue

                identity_id = raw_id[:5]
                passive_id = raw_id

                out_path = os.path.join(I18N_DIR, lang, "identity", f"{identity_id}.json")
                if not os.path.exists(out_path):
                    continue

                identity = load_json(out_path)
                identity.setdefault("passives", {})

                identity["passives"][passive_id] = {
                    "name": strip_tags(item.get("name", "")),
                    "desc": strip_tags(item.get("desc", ""))
                }

                save_json(out_path, identity)
                count += 1

        print(f"  [{lang}] {count} passives added")


# =============================================================================
# Step 7: name_list - i18n 이름 목록 집계
# =============================================================================
def step_name_list():
    """Aggregate identity names into list files."""
    print("[7/8] name_list: Aggregating name lists...")

    for lang in LANGS:
        id_dir = Path(I18N_DIR) / lang / "identity"
        output_path = Path(I18N_DIR) / lang / "identityNameList.json"

        if not id_dir.exists():
            continue

        temp = {}
        for json_path in id_dir.glob("*.json"):
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
# Step 8: spec_list - data 스펙 목록 집계
# =============================================================================
def step_spec_list():
    """Aggregate identity specs into list file."""
    print("[8/8] spec_list: Aggregating spec list...")

    input_dir = Path(DATA_DIR)
    output_file = Path("../static/data/identitySpecList.json")

    result = {}

    for json_file in sorted(input_dir.glob("*.json")):
        data = load_json(str(json_file))
        file_id = json_file.stem

        attribute_types = []
        atk_types = []

        skills = data.get("skills", {})
        for skill_group_key, skill_group_value in skills.items():
            if skill_group_key == "skillDef":
                continue

            if not skill_group_value:
                continue

            skill = skill_group_value[0]
            for level_data in skill.get("skillData", []):
                if not isinstance(level_data, dict):
                    continue

                attr = level_data.get("attributeType")
                if attr:
                    attribute_types.append(attr)

                atk = level_data.get("atkType")
                if atk:
                    atk_types.append(atk)

        result[file_id] = {
            "updateDate": data.get("updatedDate"),
            "skillKeywordList": data.get("skillKeywordList", []),
            "season": data.get("season", 0),
            "rank": data.get("rank"),
            "unitKeywordList": data.get("unitKeywordList", []),
            "attributeType": attribute_types,
            "atkType": atk_types,
        }

    save_json(str(output_file), result)
    print(f"  {len(result)} entries")


# =============================================================================
# 메인
# =============================================================================
STEPS = {
    "name": step_name,
    "spec": step_spec,
    "skill": step_skill,
    "passive": step_passive,
    "skill_desc": step_skill_desc,
    "passive_desc": step_passive_desc,
    "name_list": step_name_list,
    "spec_list": step_spec_list,
}

STEP_ORDER = ["name", "spec", "skill", "passive", "skill_desc", "passive_desc", "name_list", "spec_list"]


def main():
    parser = argparse.ArgumentParser(description="Unified identity data processor")
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
