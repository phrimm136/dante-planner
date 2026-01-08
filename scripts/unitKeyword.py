import json
import os
import glob

from lang_config import LANGS, get_raw_pattern, get_i18n_dir, lang_dir_exists

for lang in LANGS:
    if not lang_dir_exists(lang):
        print(f"{lang}: raw directory not found, skipping")
        continue

    input_pattern = get_raw_pattern(lang, "UnitKeyword*.json")
    output_dir = get_i18n_dir(lang)
    output_path = f"{output_dir}/unitKeywords.json"

    os.makedirs(output_dir, exist_ok=True)

    transformed = {}

    # 모든 매칭 파일 읽기
    for file_path in glob.glob(input_pattern):
        with open(file_path, "r", encoding="utf-8") as f:
            data = json.load(f)
            for item in data.get("dataList", []):
                key = item["id"].replace("UnitKeyword_", "")
                transformed[key] = item["content"]

    if not transformed:
        print(f"{lang}: no UnitKeyword files found, skipping")
        continue

    # JSON 쓰기
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(transformed, f, ensure_ascii=False, indent=2)

    print(f"{lang} 변환 완료: {output_path}")
