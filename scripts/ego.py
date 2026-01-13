#!/usr/bin/env python3
"""
Unified EGO data processing script.

Merges functionality from:
- egoName.py, egoNameList.py
- egoSpec.py, egoSpecList.py
- egoSkill.py, egoSkillDesc.py
- egoPassive.py, egoPassiveDesc.py

Usage:
    python ego.py              # Run all steps in order
    python ego.py --step name  # Run specific step
    python ego.py --list       # Show available steps
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
DATA_DIR = "../static/data/ego"
I18N_DIR = "../static/i18n"

TAG_RE = re.compile(r"<[^>]+>")
EGO_FILE_RE = re.compile(r"^ego-([1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
EGO_SKILL_FILE_RE = re.compile(r"^ego-skill-([1-9]|1[0-2]|a[0-9]c[0-9]p[0-9])\.json$")
EGO_JSON_RE = re.compile(r"^\d{5}\.json$")

LEVEL_COUNT = 4

# Data corrections for known errors in raw game data
# Format: { "ego_id": { "field": corrected_value } }
DATA_CORRECTIONS = {
    # 20306 (Don Quixote Electric Screaming) has wrong year: 20230411 -> 20240411
    "20306": {"updatedDate": 20240411},
}

# Normalization target date: only normalize EGOs with updateDate <= this value
# Garden of Thorns (21205) updateDate: 20240314
NORMALIZATION_TARGET_DATE = 20240314

# --- 공용 함수 ---

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def apply_data_corrections(ego_id: str, data: dict) -> dict:
    """Apply known data corrections to an EGO entry."""
    if ego_id in DATA_CORRECTIONS:
        for field, value in DATA_CORRECTIONS[ego_id].items():
            if field in data:
                data[field] = value
    return data


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
    """Create base i18n files with EGO names."""
    print("[1/8] name: Creating i18n base files...")

    for lang in LANGS:
        input_path = get_raw_file(lang, "Egos.json")
        if not os.path.exists(input_path):
            continue

        data = load_json(input_path)
        out_dir = os.path.join(I18N_DIR, lang, "ego")
        os.makedirs(out_dir, exist_ok=True)

        count = 0
        for entry in data.get("dataList", []):
            raw_id = str(entry.get("id"))
            if len(raw_id) != 5:
                continue

            name = entry.get("name", "")
            out_path = os.path.join(out_dir, f"{raw_id}.json")

            base = {"name": name, "skills": {}, "passives": {}}
            save_json(out_path, base)
            count += 1

        print(f"  [{lang}] {count} files created")


# =============================================================================
# Step 2: spec - data 기본 파일 생성
# =============================================================================
def convert_attribute_list(items, key_name, value_name):
    result = {}
    for item in items or []:
        k = item.get(key_name)
        v = item.get(value_name)
        if k is not None:
            result[k] = v
    return result


def process_ego_entry(entry: dict) -> dict:
    return {
        "updatedDate": entry.get("updatedDate"),
        "egoType": entry.get("egoType"),
        "season": entry.get("season"),
        "attributeResist": convert_attribute_list(
            entry.get("attributeResistList"), "type", "value"
        ),
        "requirements": convert_attribute_list(
            entry.get("requirementList"), "attributeType", "num"
        ),
    }


def step_spec():
    """Create data files with EGO specs."""
    print("[2/8] spec: Creating data files...")

    os.makedirs(DATA_DIR, exist_ok=True)
    count = 0

    for filename in os.listdir(RAW_DIR):
        if not EGO_FILE_RE.match(filename):
            continue

        path = os.path.join(RAW_DIR, filename)
        data = load_json(path)

        for entry in data.get("list", []):
            ego_id = entry.get("id")
            if not ego_id:
                continue

            output_data = process_ego_entry(entry)
            output_data = apply_data_corrections(str(ego_id), output_data)
            output_path = os.path.join(DATA_DIR, f"{ego_id}.json")
            save_json(output_path, output_data)
            count += 1

    print(f"  {count} files created")


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
            "abilityScriptList", "actionScript", "range", "arearange"
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

    # 항상 4칸
    return [by_level.get(i, {}) for i in range(1, LEVEL_COUNT + 1)]


def classify_ego_skill(skill_id: int):
    s = str(skill_id)
    if len(s) < 6:
        return None
    if s[5] == "1":
        return "awaken"
    if s[5] == "2":
        return "erosion"
    return None


def step_skill():
    """Add skill data to EGO data files."""
    print("[3/8] skill: Adding skill data...")

    # EGO JSON 미리 로드
    egos = {}
    for path in glob.glob(os.path.join(DATA_DIR, "*.json")):
        eid = os.path.splitext(os.path.basename(path))[0]
        egos[eid] = load_json(path)
        egos[eid].setdefault("skills", {"awaken": [], "erosion": []})

    # 스킬 파일 순회
    for path in glob.glob(os.path.join(RAW_DIR, "ego-skill-*.json")):
        filename = os.path.basename(path)
        if not EGO_SKILL_FILE_RE.match(filename):
            continue

        data = load_json(path)

        for skill in data.get("list", []):
            skill_id = skill.get("id")
            if not skill_id:
                continue

            ego_id = str(skill_id)[:5]
            ego = egos.get(ego_id)
            if not ego:
                continue

            slot = classify_ego_skill(skill_id)
            if not slot:
                continue

            skill_entry = {
                "id": skill_id,
                "skillData": normalize_skill_data(skill.get("skillData", []))
            }

            ego["skills"][slot].append(skill_entry)

    # 저장
    for eid, data in egos.items():
        save_json(os.path.join(DATA_DIR, f"{eid}.json"), data)

    print(f"  {len(egos)} files updated")


# =============================================================================
# Step 4: passive - data 파일에 패시브 데이터 추가
# =============================================================================
def step_passive():
    """Add passive data to EGO data files."""
    print("[4/8] passive: Adding passive data...")

    count = 0
    for filename in os.listdir(DATA_DIR):
        if not EGO_JSON_RE.match(filename):
            continue

        ego_id = filename.replace(".json", "")
        path = os.path.join(DATA_DIR, filename)

        ego = load_json(path)
        passives = ego.setdefault("passives", {})

        passive_list = passives.setdefault(
            "passiveList", [[] for _ in range(LEVEL_COUNT)]
        )

        while len(passive_list) < LEVEL_COUNT:
            passive_list.append([])

        passive_id = f"{ego_id}11"
        if passive_id not in passive_list[1]:
            passive_list[1].append(passive_id)

        save_json(path, ego)
        count += 1

    print(f"  {count} files updated")


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


def collect_ego_skill_files(lang):
    files = []
    base = get_raw_file(lang, "Skills_Ego.json")
    if os.path.exists(base):
        files.append(base)

    files.extend(
        glob.glob(get_raw_pattern(lang, "Skills_Ego_Personality-*.json"))
    )
    return files


def step_skill_desc():
    """Add skill descriptions to i18n files."""
    print("[5/8] skill_desc: Adding skill descriptions...")

    for lang in LANGS:
        skill_files = collect_ego_skill_files(lang)
        if not skill_files:
            continue

        egos = {}

        for path in skill_files:
            data = load_json(path)
            for entry in data.get("dataList", []):
                raw_id = str(entry.get("id")).zfill(7)
                ego_id = raw_id[:5]
                skill_id = raw_id

                egos.setdefault(ego_id, {})
                egos[ego_id].setdefault(skill_id, [])

                for lv_item in entry.get("levelList", []):
                    egos[ego_id][skill_id].append(lv_item)

        count = 0
        for ego_id, skills in egos.items():
            out_path = os.path.join(I18N_DIR, lang, "ego", f"{ego_id}.json")
            if not os.path.exists(out_path):
                continue

            ego = load_json(out_path)
            ego.setdefault("skills", {})

            for skill_id, level_items in skills.items():
                if not level_items:
                    continue

                name = strip_tags(level_items[0].get("name", ""))
                descs = [{} for _ in range(4)]

                for item in level_items:
                    lv = item.get("level")
                    if isinstance(lv, int) and 1 <= lv <= 4:
                        descs[lv - 1] = convert_level_item(item)

                ego["skills"][skill_id] = {"name": name, "descs": descs}

            save_json(out_path, ego)
            count += 1

        print(f"  [{lang}] {count} files updated")

        # For EN only: add coinString to data files
        if lang == "EN":
            data_count = 0
            for ego_id, skills in egos.items():
                data_path = os.path.join(DATA_DIR, f"{ego_id}.json")
                if not os.path.exists(data_path):
                    continue

                data = load_json(data_path)
                data_skills = data.get("skills", {})

                modified = False
                for skill_key in ["awaken", "erosion"]:
                    for skill_entry in data_skills.get(skill_key, []):
                        skill_id = str(skill_entry.get("id"))

                        level_items = skills.get(skill_id)
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
def step_passive_desc():
    """Add passive descriptions to i18n files."""
    print("[6/8] passive_desc: Adding passive descriptions...")

    for lang in LANGS:
        input_path = get_raw_file(lang, "Passive_Ego.json")
        if not os.path.exists(input_path):
            continue

        data = load_json(input_path)
        count = 0

        for item in data.get("dataList", []):
            raw_id = str(item.get("id")).zfill(7)
            ego_id = raw_id[:5]
            passive_id = raw_id

            out_path = os.path.join(I18N_DIR, lang, "ego", f"{ego_id}.json")
            if not os.path.exists(out_path):
                continue

            ego = load_json(out_path)
            ego.setdefault("passives", {})

            ego["passives"][passive_id] = {
                "name": strip_tags(item.get("name", "")),
                "desc": strip_tags(item.get("desc", ""))
            }

            save_json(out_path, ego)
            count += 1

        print(f"  [{lang}] {count} passives added")


# =============================================================================
# Step 7: keyword - Normalize descriptions, extract skillKeywordList
# =============================================================================
# The 7 main status effect keywords to extract for skillKeywordList
STATUS_EFFECT_KEYWORDS = {
    "Combustion", "Laceration", "Vibration", "Burst",
    "Sinking", "Breath", "Charge"
}

# Keywords excluded from normalization (deprecated or cause mid-word matching bugs)
EXCLUDED_KEYWORDS = {
    "Burn",                         # Deprecated, use Combustion
    "Bleeding",                     # Deprecated, use Laceration
    "ThornyFall_LowMorale",         # 가시 matches inside 증가시키는
    "ThornyFall_Panic",             # 가시 matches inside 증가시키는
    "Anger",                        # 분노 matches mid-word
    "Blue_LowMorale",               # 우울 matches mid-word
    "Blue_Panic",                   # 우울 matches mid-word
    "WanderingFootsteps_LowMorale", # 파탄 matches mid-word
    "WanderingFootsteps_Panic",     # 파탄 matches mid-word
    "AaCePbBe",                     # 앙갚음 matches mid-word
    "AaCePbBf",                     # 앙갚음 matches mid-word
    "BulletFreischutz",             # 사용하지 않음 matches mid-word
    "DuelDeclaration",              # 결투 선포 matches mid-word
    "ConcentratedAttack",           # 집중 공격 matches mid-word
    "PinkRibbon",                   # Use PinkRibbon_Ishmael instead
}

# Regex pattern for [Keyword] in skill descriptions
BRACKET_PATTERN = re.compile(r"\[([^\[\]]+)\]")


def extract_bracketed_keywords(text: str) -> set:
    """Extract all [KeywordID] patterns from text."""
    if not text:
        return set()
    return set(BRACKET_PATTERN.findall(text))


def collect_used_keywords_from_ego_i18n(i18n_data: dict) -> set:
    """Collect all bracketed keyword IDs from an EGO i18n file."""
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


def scan_all_ego_keywords(i18n_dir: str) -> set:
    """Scan all EGO i18n files and collect unique keyword IDs."""
    all_keywords = set()

    if not os.path.exists(i18n_dir):
        return all_keywords

    for filename in os.listdir(i18n_dir):
        if not EGO_JSON_RE.match(filename):
            continue

        i18n_path = os.path.join(i18n_dir, filename)
        i18n_data = load_json(i18n_path)
        all_keywords.update(collect_used_keywords_from_ego_i18n(i18n_data))

    return all_keywords


def load_battle_keywords(lang):
    """Load battleKeywords.json for a language (created by identity.py)."""
    keywords_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
    if os.path.exists(keywords_path):
        return load_json(keywords_path)
    return {}


def load_battle_keywords_raw():
    """Load raw BattleKeywords from game data for all languages."""
    all_keywords = {}

    for lang in LANGS:
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
                    entry["iconId"] = icon_id

                buff_type = buff.get("buffType")
                if buff_type is not None and entry["buffType"] is None:
                    entry["buffType"] = buff_type

    return keyword_map


def build_keyword_name_mapping(lang_keywords, lang="EN"):
    """Build mapping from localized name -> keyword ID with pre-compiled regexes.

    Args:
        lang_keywords: Dict of keyword_id -> {name, desc, ...}
        lang: Language code for language-specific patterns (KR, EN, JP, CN)
    """
    patterns = []

    for keyword_id, info in lang_keywords.items():
        # Skip excluded keywords
        if keyword_id in EXCLUDED_KEYWORDS:
            continue

        name = info.get("name", "")
        if not name or not isinstance(name, str) or len(name) < 2:
            continue

        escaped_name = re.escape(name)
        if name.isascii() and name.isalpha():
            # ASCII words: use word boundary
            pattern = rf"(?<!\[)\b{escaped_name}\b(?!\])"
        elif any('\uAC00' <= c <= '\uD7A3' for c in name):
            # Korean (Hangul): block hangul before/after
            # e.g., 광신 in 광신도 → blocked by lookahead on 도
            pattern = rf"(?<![가-힣\[]){escaped_name}(?![가-힣\]])"
        elif lang == "CN":
            # Chinese (Simplified): allow kanji before (e.g., 级烧伤),
            # block kanji after (e.g., 狂信徒 → 狂信 blocked by 徒)
            kanji = r'一-鿿\u3400-\u4DBF'
            pattern = rf"(?<!\[){escaped_name}(?![{kanji}\]])"
        else:
            # Japanese: allow kana before (particles), block kanji before/after
            kanji = r'一-鿿\u3400-\u4DBF'
            pattern = rf"(?<![{kanji}\[]){escaped_name}(?![{kanji}\]])"

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


def normalize_ego_i18n_file(i18n_data, compiled_patterns):
    """Normalize all descriptions in an EGO i18n file."""
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


def collect_keywords_from_ego_i18n(i18n_data: dict) -> set:
    """Collect all status keywords from an EGO i18n file's skills."""
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
    """Normalize EGO descriptions first, then append keywords to battleKeywords."""
    print("[7/9] keyword: Normalizing EGO descriptions and updating battleKeywords...")

    # Step 1: Load ALL raw battle keywords (no filtering yet)
    all_keywords_raw = load_battle_keywords_raw()

    # Step 2: Merge buff info for all keywords
    for lang in LANGS:
        if lang in all_keywords_raw:
            all_keywords_raw[lang] = merge_buff_info(all_keywords_raw[lang])

    # Step 3: Normalize EGO descriptions using full keyword set
    for lang in LANGS:
        lang_keywords = all_keywords_raw.get(lang, {})
        if not lang_keywords:
            continue

        compiled_patterns = build_keyword_name_mapping(lang_keywords, lang)
        i18n_dir = os.path.join(I18N_DIR, lang, "ego")

        if not os.path.exists(i18n_dir):
            continue

        normalized_count = 0
        skipped_count = 0
        for filename in os.listdir(i18n_dir):
            if not EGO_JSON_RE.match(filename):
                continue

            # Check updateDate from data file
            data_path = os.path.join(DATA_DIR, filename)
            if os.path.exists(data_path):
                data = load_json(data_path)
                update_date = data.get("updatedDate", 0)
                if update_date > NORMALIZATION_TARGET_DATE:
                    skipped_count += 1
                    continue

            i18n_path = os.path.join(i18n_dir, filename)
            i18n_data = load_json(i18n_path)

            if normalize_ego_i18n_file(i18n_data, compiled_patterns):
                save_json(i18n_path, i18n_data)
                normalized_count += 1

        print(f"  [{lang}] Normalized {normalized_count} EGO files (skipped {skipped_count} newer)")

    # Step 4: Scan normalized EGO descriptions to find used keyword IDs
    ego_keywords = set()
    for lang in LANGS:
        i18n_dir = os.path.join(I18N_DIR, lang, "ego")
        ego_keywords.update(scan_all_ego_keywords(i18n_dir))

    print(f"  Found {len(ego_keywords)} unique keywords in normalized EGO descriptions")

    # Step 5: Append new EGO keywords to existing battleKeywords.json
    for lang in LANGS:
        existing_keywords = load_battle_keywords(lang)
        lang_keywords_raw = all_keywords_raw.get(lang, {})

        # Find new keywords not already in battleKeywords.json
        new_keywords = {k: lang_keywords_raw[k] for k in ego_keywords
                        if k not in existing_keywords and k in lang_keywords_raw}

        if new_keywords:
            existing_keywords.update(new_keywords)
            output_path = os.path.join(I18N_DIR, lang, "battleKeywords.json")
            save_json(output_path, existing_keywords)
            print(f"  [{lang}] Added {len(new_keywords)} new keywords to battleKeywords.json")

    # Step 6: Extract skillKeywordList from EN normalized descriptions
    en_i18n_dir = os.path.join(I18N_DIR, "EN", "ego")
    keyword_count = 0

    for filename in os.listdir(DATA_DIR):
        if not EGO_JSON_RE.match(filename):
            continue

        i18n_path = os.path.join(en_i18n_dir, filename)
        data_path = os.path.join(DATA_DIR, filename)

        if not os.path.exists(i18n_path):
            continue

        i18n_data = load_json(i18n_path)
        keywords = collect_keywords_from_ego_i18n(i18n_data)

        data = load_json(data_path)
        data["skillKeywordList"] = sorted(keywords)
        save_json(data_path, data)
        keyword_count += 1

    print(f"  {keyword_count} EGO files updated with skillKeywordList")


# =============================================================================
# Step 8: name_list - i18n 이름 목록 집계
# =============================================================================
def step_name_list():
    """Aggregate EGO names into list files."""
    print("[8/9] name_list: Aggregating name lists...")

    for lang in LANGS:
        ego_dir = Path(I18N_DIR) / lang / "ego"
        output_path = Path(I18N_DIR) / lang / "egoNameList.json"

        if not ego_dir.exists():
            continue

        temp = {}
        for json_path in ego_dir.glob("*.json"):
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
    """Aggregate EGO specs into list file."""
    print("[9/9] spec_list: Aggregating spec list...")

    input_dir = Path(DATA_DIR)
    output_file = Path("../static/data/egoSpecList.json")

    result = {}

    for json_file in sorted(input_dir.glob("*.json")):
        data = load_json(str(json_file))
        ego_id = json_file.stem
        data = apply_data_corrections(ego_id, data)

        attribute_types = set()
        atk_types = []

        skills = data.get("skills", {})
        for skill_group in skills.values():
            if not isinstance(skill_group, list):
                continue
            for skill in skill_group:
                for level_data in skill.get("skillData", []):
                    if not isinstance(level_data, dict):
                        continue

                    attr = level_data.get("attributeType")
                    if attr:
                        attribute_types.add(attr)

                    atk = level_data.get("atkType")
                    if atk:
                        atk_types.append(atk)

        result[ego_id] = {
            "updateDate": data.get("updatedDate"),
            "egoType": data.get("egoType"),
            "season": data.get("season"),
            "requirements": data.get("requirements", {}),
            "attributeType": sorted(attribute_types),
            "atkType": sorted(atk_types),
            "skillKeywordList": data.get("skillKeywordList", []),
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
    parser = argparse.ArgumentParser(description="Unified EGO data processor")
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
