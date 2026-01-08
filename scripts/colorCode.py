import json
import os

# 입력/출력 경로
input_path = "../raw/Json/ColorCode.json"
output_path = f"../static/data/colorCode.json"

# JSON 읽기
with open(input_path, "r", encoding="utf-8") as f:
    data = json.load(f)

# id: color 매핑 생성
color_map = {item["id"]: item["color"] for item in data.get("list", [])}

# JSON 저장
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(color_map, f, ensure_ascii=False, indent=2)

print(f"완료: {output_path}")

