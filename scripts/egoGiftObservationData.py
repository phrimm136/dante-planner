import json
from pathlib import Path

INPUT_PATH = Path("../raw/Json/mirror-dungeon-egogift-observation-data-md6.json")
OUTPUT_PATH = Path("../static/data/egoGiftObservationData.json")

OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

with INPUT_PATH.open("r", encoding="utf-8") as f:
    data = json.load(f)

# md6 기준이므로 첫 번째 항목만 사용
entry = data["list"][0]

# cost data list 유지
cost_data_list = entry.get("observationEgoGiftCostDataList", [])

# gift id 병합 + 중복 제거
merged_ids = set()
for item in entry.get("observationEgoGiftDataList", []):
    merged_ids.update(item.get("egogiftIdList", []))

sorted_ids = sorted(merged_ids)

result = {
    "observationEgoGiftCostDataList": cost_data_list,
    "observationEgoGiftDataList": sorted_ids,
}

with OUTPUT_PATH.open("w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

