
import os
from PIL import Image

# 입력과 출력 경로
input_dir = "../raw/Mirror of the Dreaming"
output_dir = "../static/images/startBuffs/MD6"

# 출력 폴더가 없으면 생성
os.makedirs(output_dir, exist_ok=True)

# 폴더 순회
for root, dirs, files in os.walk(input_dir):
    for file in files:
        if file.lower().endswith(".png"):
            input_path = os.path.join(root, file)
            output_file = os.path.splitext(file)[0] + ".webp"
            output_path = os.path.join(output_dir, output_file)
            
            # 이미지 열고 webp로 저장
            with Image.open(input_path) as img:
                img.save(output_path, "WEBP")

            print(f"Converted {input_path} -> {output_path}")
