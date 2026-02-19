import json
import os
from glob import glob
from lang_config import LANGS, get_raw_file, lang_dir_exists
from md_config import MD_VERSION, UI_VERSION

for lang in LANGS:
    if not lang_dir_exists(lang):
        continue
    # 1. UI JSON 읽기
    ui_file_pattern = get_raw_file(lang, f"MirrorDungeonUI_{UI_VERSION}.json")
    ui_files = glob(ui_file_pattern)
    if not ui_files:
        print(f"{lang} UI 파일을 찾을 수 없습니다: {ui_file_pattern}")
        continue
    ui_file = ui_files[0]

    with open(ui_file, "r", encoding="utf-8") as f:
        ui_data = json.load(f)

    # UI 필터링: id가 'mirror_dungeon_{UI_VERSION}_buffs_title_'로 시작
    ui_mapping = {
        entry["id"]: entry["content"]
        for entry in ui_data.get("dataList", [])
        if entry["id"].startswith(f"mirror_dungeon_{UI_VERSION}_buffs_title_")
    }

    # 2. MD JSON 읽기
    md_file_pattern = get_raw_file(lang, f"DungeonStartBuffs_MD{MD_VERSION}.json")
    md_files = glob(md_file_pattern)
    if not md_files:
        print(f"{lang} MD{MD_VERSION} 파일을 찾을 수 없습니다: {md_file_pattern}")
        md_mapping = {}
    else:
        with open(md_files[0], "r", encoding="utf-8") as f:
            md_data = json.load(f)
        # MD 필터링: description 존재하는 것만
        md_mapping = {
            entry["id"]: entry["description"]
            for entry in md_data.get("dataList", [])
            if entry.get("description")
        }

    # 3. 최종 dict: UI mapping 먼저, MD mapping 뒤
    final_data = {}
    final_data.update(ui_mapping)
    final_data.update(md_mapping)

    # 4. 출력
    output_dir = f"../static/i18n/{lang}/MD{MD_VERSION}"
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, "startBuffs.json")

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(final_data, f, ensure_ascii=False, indent=2)

    print(f"{lang} startBuffs.json 생성 완료: {output_file}")
