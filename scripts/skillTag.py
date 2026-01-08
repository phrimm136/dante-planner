import json
import os

from lang_config import LANGS, get_raw_file, get_i18n_dir, lang_dir_exists

for lang in LANGS:
    if not lang_dir_exists(lang):
        print(f"{lang}: raw directory not found, skipping")
        continue

    input_path = get_raw_file(lang, "SkillTag.json")
    output_path = f"{get_i18n_dir(lang)}/skillTag.json"

    if not os.path.exists(input_path):
        print(f"{lang}: SkillTag.json not found, skipping")
        continue

    # JSON 파일 읽기
    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # id: name 매핑 생성
    skill_map = {item["id"]: item["name"] for item in data.get("dataList", [])}

    # JSON 파일로 저장
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(skill_map, f, ensure_ascii=False, indent=2)

    print(f"{lang} 완료: {output_path}")
