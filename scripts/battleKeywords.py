import json
import re
import glob
import os
from lang_config import LANGS, get_raw_pattern, get_lang_dir, lang_dir_exists

# --- 설정 ---
base_after = "../static/i18n"
base_before = "../raw/Json"

# regex for [Keyword]
bracket_pattern = re.compile(r"\[([^\[\]]+)\]")

# --- 함수 정의 ---

def extract_keywords_from_text(text):
    return bracket_pattern.findall(text)

def extract_keywords_from_json_file(file_path):
    keywords = set()
    
    def recursive_scan(obj):
        if isinstance(obj, dict):
            for v in obj.values():
                recursive_scan(v)
        elif isinstance(obj, list):
            for item in obj:
                recursive_scan(item)
        elif isinstance(obj, str):
            kws = extract_keywords_from_text(obj)
            keywords.update(kws)
    
    with open(file_path, encoding="utf-8") as f:
        data = json.load(f)
    recursive_scan(data)
    return keywords

def load_battle_keywords(lang):
    keyword_map = {}
    pattern = get_raw_pattern(lang, "BattleKeywords*.json")
    battle_files = sorted(glob.glob(pattern))
    for file in battle_files:
        with open(file, encoding="utf-8") as f:
            data = json.load(f)
            for obj in data.get("dataList", []):
                keyword_map[obj["id"]] = {
                    "name": obj.get("name", ""),
                    "desc": obj.get("desc", ""),
                    "iconId": None,
                    "buffType": None,
                }
    return keyword_map

def merge_buff_info(keyword_map):
    buff_files = glob.glob(f"{base_before}/*buff*.json")
    for file in buff_files:
        with open(file, encoding="utf-8") as f:
            data = json.load(f)
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

# --- 메인 루프 ---
for lang in LANGS:
    if not lang_dir_exists(lang):
        continue
    # 1. 텍스트에서 [Keyword] 추출
    keyword_files = glob.glob(f"{base_after}/{lang}/identity/*.json") + \
                    glob.glob(f"{base_after}/{lang}/ego/*.json") + \
                    glob.glob(f"{base_after}/{lang}/egoGift/*.json")
    
    used_keywords = set()
    for file in keyword_files:
        used_keywords.update(extract_keywords_from_json_file(file))

    
    # 2. BattleKeywords 로 기본 정보 채우기
    keyword_map = load_battle_keywords(lang)

    # 3. 사용된 키워드 필터링
    keyword_map = {k: v for k, v in keyword_map.items() if k in used_keywords}
    
    # 4. Buff 정보 병합
    keyword_map = merge_buff_info(keyword_map)
    
    # 5. 출력
    out_file = f"{base_after}/{lang}/battleKeywords.json"
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(keyword_map, f, ensure_ascii=False, indent=2)
    
    print(f"[{lang}] battleKeywords.json 생성 완료: {len(keyword_map)} 개 키워드")

