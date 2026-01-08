import json
import os

# 입력 파일
input_file = "../raw/Json/mirrordungeon-start-buffs-06-second.json"
output_file = "../static/data/startBuffsMD6.json"

# JSON 읽기
with open(input_file, "r", encoding="utf-8") as f:
    data = json.load(f)

# 결과 dict
result = {}

# 각 dungeon의 buffs 처리
for dungeon in data.get("list", []):
    for buff in dungeon.get("buffs", []):
        buff_id = str(buff.get("id"))
        # 필요한 필드만 선택
        buff_data = {
            "level": buff.get("level"),
            "baseId": buff.get("baseId"),
            "cost": buff.get("cost"),
            "localizeId": buff.get("localizeId"),
            "effects": buff.get("effects"),
            "uiConfig": buff.get("uiConfig")
        }
        result[buff_id] = buff_data

# 출력 디렉토리 생성
os.makedirs(os.path.dirname(output_file), exist_ok=True)

# JSON 저장
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(result, f, ensure_ascii=False, indent=2)

print(f"변환 완료: {output_file}")

