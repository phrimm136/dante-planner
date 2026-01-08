import json
import os
from glob import glob
from lang_config import LANGS, get_raw_file, lang_dir_exists

for lang in LANGS:
    if not lang_dir_exists(lang):
        continue
    # 1. UI JSON 읽기
    ui_file_pattern = get_raw_file(lang, "MirrorDungeonUI_5.json")
    ui_files = glob(ui_file_pattern)
    if not ui_files:
        print(f"{lang} UI 파일을 찾을 수 없습니다: {ui_file_pattern}")
        continue
    ui_file = ui_files[0]
    
    with open(ui_file, "r", encoding="utf-8") as f:
        ui_data = json.load(f)

    # UI 필터링: id가 'mirror_dungeon_5_buffs_title_'로 시작
    ui_mapping = {
        entry["id"]: entry["content"]
        for entry in ui_data.get("dataList", [])
        if entry["id"].startswith("mirror_dungeon_5_buffs_title_")
    }

    # 2. MD6 JSON 읽기
    md6_file_pattern = get_raw_file(lang, "DungeonStartBuffs_MD6.json")
    md6_files = glob(md6_file_pattern)
    if not md6_files:
        print(f"{lang} MD6 파일을 찾을 수 없습니다: {md6_file_pattern}")
        md6_mapping = {}
    else:
        with open(md6_files[0], "r", encoding="utf-8") as f:
            md6_data = json.load(f)
        # MD6 필터링: description 존재하는 것만
        md6_mapping = {
            entry["id"]: entry["description"]
            for entry in md6_data.get("dataList", [])
            if entry.get("description")
        }

    # 3. 최종 dict: UI mapping 먼저, MD6 mapping 뒤
    final_data = {}
    final_data.update(ui_mapping)
    final_data.update(md6_mapping)

    # 4. 출력
    output_dir = f"../static/i18n/{lang}/MD6"
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, "startBuffs.json")
    
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(final_data, f, ensure_ascii=False, indent=2)

    print(f"{lang} startBuffs.json 생성 완료: {output_file}")
