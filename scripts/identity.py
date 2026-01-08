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
from lang_config import LANGS, get_raw_file, get_raw_pattern, get_lang_dir, lang_dir_exists

RAW_DIR = "../raw/Json"
DATA_DIR = "../static/data/identity"
I18N_DIR = "../static/i18n"
SKILL_STRUCTURE_PATH = "idSkillStructure.json"

TAG_RE = re.compile(r"<[^>]+>")
FILENAME_RE = re.compile(r"^personality-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
SKILL_FILE_RE = re.compile(r"^personality-skill-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
PASSIVE_LIST_RE = re.compile(r"^(personality-)*passive-(0[1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")

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
        if not lang_dir_exists(lang):
            continue
        input_path = get_raw_file(lang, "Personalities.json")
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
            "abilityScriptList", "range", "arearange", "actionScript",
            "iconId", "iconID"  # Handle separately for normalization
        }

        for k, v in entry.items():
            if k not in skip_fields:
                new_entry[k] = deepcopy(v)

        # Normalize iconID: always string, consistent field name
        icon_id = entry.get("iconId") or entry.get("iconID")
        if icon_id is not None:
            new_entry["iconID"] = str(icon_id)

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
            # Use textID for i18n lookup; defaults to id if not present
            text_id = skill.get("textID", None)
            skill_entry = {
                "id": skill_id,
                "skillTier": tier,
                "skillData": normalize_skill_data(skill.get("skillData", []))
            }

            if text_id is not None:
                skill_entry["textID"] = text_id

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
# Passive IDs to exclude from processing
EXCLUDED_PASSIVE_IDS = {1021202}


def normalize_passive_list(passive_list):
    result = [[] for _ in range(LEVEL_COUNT)]

    for entry in passive_list:
        level = entry.get("level")
        if not level or not (1 <= level <= LEVEL_COUNT):
            continue
        # Filter out excluded passive IDs
        passive_ids = [
            pid for pid in entry.get("passiveIDList", [])
            if pid not in EXCLUDED_PASSIVE_IDS
        ]
        result[level - 1] = passive_ids

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
            passive_id = entry.get("id")
            # Skip excluded passive IDs
            if passive_id in EXCLUDED_PASSIVE_IDS:
                continue
            passive_id = str(passive_id)

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
def get_coin_string(coin_descs):
    """
    Derive coin string from coinDescs array.
    Each coin is 'C' (normal) or 'U' (unbreakable/super coin).
    Super coins are identified by [SuperCoin] in the description.
    """
    if not coin_descs:
        return ''
    return ''.join('U' if '[SuperCoin]' in desc else 'C' for desc in coin_descs)


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
    base = get_raw_file(lang, "Skills.json")
    if os.path.exists(base):
        files.append(base)

    files.extend(glob.glob(get_raw_pattern(lang, "Skills_personality-*.json")))
    return files


def step_skill_desc():
    """Add skill descriptions to i18n files."""
    print("[5/8] skill_desc: Adding skill descriptions...")

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
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

        # For EN only: add coinString to data files
        if lang == "EN":
            data_count = 0
            for identity_id, skills in identities.items():
                data_path = os.path.join(DATA_DIR, f"{identity_id}.json")
                if not os.path.exists(data_path):
                    continue

                data = load_json(data_path)
                data_skills = data.get("skills", {})

                modified = False
                for skill_key in ["skill1", "skill2", "skill3", "skillDef"]:
                    for skill_entry in data_skills.get(skill_key, []):
                        skill_id = str(skill_entry.get("id"))
                        text_id = str(skill_entry.get("textID", skill_id))

                        level_items = skills.get(text_id) or skills.get(skill_id)
                        if not level_items:
                            continue

                        # Get first non-empty coinDescs
                        coin_descs = []
                        for item in level_items:
                            coinlist = item.get("coinlist", [])
                            if coinlist:
                                for coin in coinlist:
                                    if coin and "coindescs" in coin:
                                        texts = [strip_tags(c.get("desc", "")) for c in coin.get("coindescs", [])]
                                        coin_descs.append("\n".join(texts))
                                    else:
                                        coin_descs.append("")
                                break

                        coin_string = get_coin_string(coin_descs)
                        skill_data = skill_entry.get("skillData", [])
                        if skill_data and isinstance(skill_data[0], dict):
                            skill_data[0]["coinString"] = coin_string
                            modified = True

                if modified:
                    save_json(data_path, data)
                    data_count += 1

            print(f"  [EN] {data_count} data files updated with coinString")


# =============================================================================
# Step 6: passive_desc - i18n 파일에 패시브 설명 추가
# =============================================================================
def collect_passive_files(lang):
    """Collect all identity passive files for a language."""
    files = []
    base = get_raw_file(lang, "Passives.json")
    if os.path.exists(base):
        files.append(base)

    # Additional passive files (check4, fools, etc.)
    additional_patterns = [
        "Passives_check*.json",
        "Passives_fools.json",
        "Passives-*.json",  # e.g., KR_Passives-ycgd.json
    ]
    for pattern in additional_patterns:
        files.extend(glob.glob(get_raw_pattern(lang, pattern)))

    return files


def step_passive_desc():
    """Add passive descriptions to i18n files."""
    print("[6/8] passive_desc: Adding passive descriptions...")

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        passive_files = collect_passive_files(lang)
        if not passive_files:
            continue

        count = 0
        for input_path in passive_files:
            data = load_json(input_path)

            for item in data.get("dataList", []):
                item_id = item.get("id")
                # Skip excluded passive IDs
                if item_id in EXCLUDED_PASSIVE_IDS:
                    continue
                raw_id = str(item_id).zfill(7)
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
# Step 7: keyword - Build battleKeywords, normalize descriptions, extract skillKeywordList
# =============================================================================
# The 7 main status effect keywords to extract for skillKeywordList
STATUS_EFFECT_KEYWORDS = {
    "Combustion", "Laceration", "Vibration", "Burst",
    "Sinking", "Breath", "Charge"
}

# Regex pattern for [Keyword] in skill descriptions
BRACKET_PATTERN = re.compile(r"\[([^\[\]]+)\]")


def extract_bracketed_keywords(text: str) -> set:
    """Extract all [KeywordID] patterns from text."""
    if not text:
        return set()
    return set(BRACKET_PATTERN.findall(text))


def collect_used_keywords_from_i18n(i18n_data: dict) -> set:
    """Collect all bracketed keyword IDs from an i18n file."""
    keywords = set()

    # Scan skills
    for skill_data in i18n_data.get("skills", {}).values():
        for desc_entry in skill_data.get("descs", []):
            if not desc_entry:
                continue
            keywords.update(extract_bracketed_keywords(desc_entry.get("desc", "")))
            for coin_desc in desc_entry.get("coinDescs", []):
                keywords.update(extract_bracketed_keywords(coin_desc))

    # Scan passives
    for passive_data in i18n_data.get("passives", {}).values():
        keywords.update(extract_bracketed_keywords(passive_data.get("desc", "")))

    return keywords


def scan_all_used_keywords(i18n_dir: str) -> set:
    """Scan all i18n files in a directory and collect unique keyword IDs."""
    all_keywords = set()

    if not os.path.exists(i18n_dir):
        return all_keywords

    for filename in os.listdir(i18n_dir):
        if not filename.endswith(".json"):
            continue

        i18n_path = os.path.join(i18n_dir, filename)
        i18n_data = load_json(i18n_path)
        all_keywords.update(collect_used_keywords_from_i18n(i18n_data))

    return all_keywords


def load_battle_keywords_raw():
    """Load raw BattleKeywords from game data for all languages."""
    all_keywords = {}

    for lang in LANGS:
        if not lang_dir_exists(lang):
            continue
        lang_keywords = {}
        pattern = get_raw_pattern(lang, "BattleKeywords*.json")

        for file_path in glob.glob(pattern):
            data = load_json(file_path)
            for obj in data.get("dataList", []):
                keyword_id = obj.get("id")
                if keyword_id:
                    lang_keywords[keyword_id] = {
                        "name": obj.get("name", ""),
                        "desc": obj.get("desc", ""),
                        "iconId": None,
                        "buffType": None,
                    }

        all_keywords[lang] = lang_keywords

    return all_keywords


def merge_buff_info(keyword_map):
    """Merge buff icon and type info into keyword map."""
    buff_files = glob.glob(os.path.join(RAW_DIR, "*buff*.json"))

    for file_path in buff_files:
        data = load_json(file_path)
        for buff in data.get("list", []):
            buff_id = buff.get("id")
            if buff_id in keyword_map:
                entry = keyword_map[buff_id]
                icon_id = buff.get("iconId") or buff.get("iconID")
                if icon_id is not None and entry["iconId"] is None:
                    entry["iconId"] = str(icon_id)

                buff_type = buff.get("buffType")
                if buff_type is not None and entry["buffType"] is None:
                    entry["buffType"] = buff_type

    return keyword_map


def build_keyword_name_mapping(lang_keywords):
    """Build mapping from localized name -> keyword ID with pre-compiled regexes."""
    patterns = []

    for keyword_id, info in lang_keywords.items():
        name = info.get("name", "")
        if not name or not isinstance(name, str) or len(name) < 2:
            continue

        escaped_name = re.escape(name)
        if name.isascii() and name.isalpha():
            pattern = rf"(?<!\[)\b{escaped_name}\b(?!\])"
        else:
            pattern = rf"(?<!\[){escaped_name}(?!\])"

        # Pre-compile regex and store with replacement
        patterns.append((len(name), re.compile(pattern), f"[{keyword_id}]"))

    # Sort by length (longest first) to handle overlapping names
    patterns.sort(key=lambda x: x[0], reverse=True)
    return [(p, r) for _, p, r in patterns]


def normalize_text(text, compiled_patterns):
    """Replace localized keywords with [KeywordID] format using pre-compiled patterns."""
    if not text or not compiled_patterns:
        return text

    result = text
    for pattern, replacement in compiled_patterns:
        result = pattern.sub(replacement, result)

    return result


def normalize_i18n_file(i18n_data, compiled_patterns):
    """Normalize all descriptions in an i18n file."""
    modified = False

    # Normalize skills
    for skill_data in i18n_data.get("skills", {}).values():
        for desc_entry in skill_data.get("descs", []):
            if not desc_entry:
                continue

            # Normalize desc
            if "desc" in desc_entry and desc_entry["desc"]:
                original = desc_entry["desc"]
                normalized = normalize_text(original, compiled_patterns)
                if original != normalized:
                    desc_entry["desc"] = normalized
                    modified = True

            # Normalize coinDescs
            if "coinDescs" in desc_entry:
                for i, coin_desc in enumerate(desc_entry["coinDescs"]):
                    if coin_desc:
                        normalized = normalize_text(coin_desc, compiled_patterns)
                        if coin_desc != normalized:
                            desc_entry["coinDescs"][i] = normalized
                            modified = True

    # Normalize passives
    for passive_data in i18n_data.get("passives", {}).values():
        if "desc" in passive_data and passive_data["desc"]:
            original = passive_data["desc"]
            normalized = normalize_text(original, compiled_patterns)
            if original != normalized:
                passive_data["desc"] = normalized
                modified = True

    return modified


def extract_status_keywords(text: str) -> set:
    """Extract status effect keywords from normalized text."""
    if not text:
        return set()
    matches = BRACKET_PATTERN.findall(text)
    return {m for m in matches if m in STATUS_EFFECT_KEYWORDS}


def collect_keywords_from_i18n(i18n_data: dict) -> set:
    """Collect all status keywords from an i18n file's skills."""
    keywords = set()

    for skill_data in i18n_data.get("skills", {}).values():
        for desc_entry in skill_data.get("descs", []):
            if not desc_entry:
                continue

            keywords.update(extract_status_keywords(desc_entry.get("desc", "")))
            for coin_desc in desc_entry.get("coinDescs", []):
                keywords.update(extract_status_keywords(coin_desc))

    return keywords


def step_keyword():
    """Build battleKeywords from descriptions, normalize, extract skillKeywordList."""
    print("[7/9] keyword: Building battleKeywords from descriptions...")

    # Step 1: Scan all identity descriptions to find used keyword IDs
    used_keywords = set()
    for lang in LANGS:
        i18n_dir = os.path.join(I18N_DIR, lang, "identity")
        used_keywords.update(scan_all_used_keywords(i18n_dir))

    print(f"  Found {len(used_keywords)} unique keywords in identity descriptions")

    # Step 2: Load raw battle keywords and filter to only used ones
    all_keywords_raw = load_battle_keywords_raw()

    # Step 3: Build battleKeywords.json with only used keywords for each language
    for lang in LANGS:
        lang_keywords_raw = all_keywords_raw.get(lang, {})
        # Filter to only keywords found in descriptions
        filtered_keywords = {k: v for k, v in lang_keywords_raw.items() if k in used_keywords}
        # Merge buff info
        filtered_keywords = merge_buff_info(filtered_keywords)
        output_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
        save_json(output_path, filtered_keywords)

    print(f"  battleKeywords.json created for {len(LANGS)} languages (filtered)")

    # Step 4: Normalize descriptions using battleKeywords
    for lang in LANGS:
        keywords_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
        if not os.path.exists(keywords_path):
            continue

        lang_keywords = load_json(keywords_path)
        compiled_patterns = build_keyword_name_mapping(lang_keywords)
        i18n_dir = os.path.join(I18N_DIR, lang, "identity")

        if not os.path.exists(i18n_dir):
            continue

        normalized_count = 0
        for filename in os.listdir(i18n_dir):
            if not filename.endswith(".json"):
                continue

            i18n_path = os.path.join(i18n_dir, filename)
            i18n_data = load_json(i18n_path)

            if normalize_i18n_file(i18n_data, compiled_patterns):
                save_json(i18n_path, i18n_data)
                normalized_count += 1

        print(f"  [{lang}] Normalized {normalized_count} files")

    # Step 5: Extract skillKeywordList from EN normalized descriptions
    en_i18n_dir = os.path.join(I18N_DIR, "EN", "identity")
    keyword_count = 0

    for filename in os.listdir(DATA_DIR):
        if not filename.endswith(".json"):
            continue

        i18n_path = os.path.join(en_i18n_dir, filename)
        data_path = os.path.join(DATA_DIR, filename)

        if not os.path.exists(i18n_path):
            continue

        i18n_data = load_json(i18n_path)
        keywords = collect_keywords_from_i18n(i18n_data)

        data = load_json(data_path)
        data["skillKeywordList"] = sorted(keywords)
        save_json(data_path, data)
        keyword_count += 1

    print(f"  {keyword_count} identity files updated with skillKeywordList")


# =============================================================================
# Step 8: name_list - i18n 이름 목록 집계
# =============================================================================
def step_name_list():
    """Aggregate identity names into list files."""
    print("[8/9] name_list: Aggregating name lists...")

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
# Step 9: spec_list - data 스펙 목록 집계
# =============================================================================
def step_spec_list():
    """Aggregate identity specs into list file."""
    print("[9/9] spec_list: Aggregating spec list...")

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
    "keyword": step_keyword,
    "name_list": step_name_list,
    "spec_list": step_spec_list,
}

STEP_ORDER = ["name", "spec", "skill", "passive", "skill_desc", "passive_desc", "keyword", "name_list", "spec_list"]


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
