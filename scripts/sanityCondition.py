import json
import glob
import os
from lang_config import LANGS, get_raw_pattern, lang_dir_exists

RAW_BASE = "../raw/Json"
OUT_BASE = "../static/i18n"

KEY_MAP = {
    "add": "inc",
    "min": "dec",
}

for lang in LANGS:
    if not lang_dir_exists(lang):
        continue
    pattern = get_raw_pattern(lang, "MentalCondition*.json")
    files = glob.glob(pattern)

    result = {}

    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)

        for item in data.get("dataList", []):
            item_id = item.get("id")
            if not item_id:
                continue

            converted = {}
            for k, v in item.items():
                if k == "id":
                    continue
                converted[KEY_MAP.get(k, k)] = v

            result[item_id] = converted

    out_dir = os.path.join(OUT_BASE, lang)
    os.makedirs(out_dir, exist_ok=True)

    out_path = os.path.join(out_dir, "sanityCondition.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"[{lang}] saved -> {out_path}")
