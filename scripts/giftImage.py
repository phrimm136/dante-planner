#!/usr/bin/env python3
"""
EGO Gift 이미지 파일명 변환 스크립트

영문 이름의 PNG 파일을 ID 기반 WEBP 파일로 변환합니다.
예: "Hellterfly's Dream.png" → "9001.webp"

사용법:
  python renameEgoGiftImages.py              # dry-run (미리보기)
  python renameEgoGiftImages.py --execute    # 실제 변환 실행
"""

import argparse
import json
import re
from pathlib import Path

from PIL import Image

# 경로 설정 (스크립트 위치 기준 상대 경로)
BASE_DIR = Path(__file__).parent.parent
INPUT_DIR = BASE_DIR / "raw" / "EGO Gifts"
OUTPUT_DIR = BASE_DIR / "static" / "images" / "icon" / "egoGift"
NAME_LIST_PATH = BASE_DIR / "static" / "i18n" / "EN" / "egoGiftNameList.json"

QUALITY = 85

# 제외할 디렉토리 (현재 없음)
EXCLUDE_DIRS = set()

# 오타 → 올바른 ID 수동 매핑
MANUAL_MAPPING = {
    "Prosthetic Join Servos": "9738",  # → Prosthetic Joint Servos
    "Lightening Axe": "9802",          # → Lightning Axe
    "Melty Eyeball": "9024",           # → Melted Eyeball
    "Barbed Snare": "9047",            # → Barbed Lasso
    "Sticky Resin": "9019",            # → Sticky Muck
}


def normalize_for_lookup(name: str) -> str:
    """검색용 정규화: 특수문자 통일, 공백 정리"""
    normalized = name.lower()
    # 아포스트로피/언더스코어 통일
    normalized = normalized.replace("'", "_")
    # 콜론 제거
    normalized = normalized.replace(":", "")
    # 연속 공백 → 단일 공백
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()


def build_reverse_mapping(name_list: dict[str, str]) -> dict[str, str]:
    """이름 → ID 역매핑 생성 (다양한 변형 포함)"""
    reverse_map = {}
    for gift_id, name in name_list.items():
        # 정규화된 형태
        normalized = normalize_for_lookup(name)
        reverse_map[normalized] = gift_id

        # 원본 (소문자)
        reverse_map[name.lower()] = gift_id

        # 아포스트로피 유지 버전 (JSON 원본)
        reverse_map[name.lower().replace(":", "")] = gift_id

        # ID 자체도 매핑 (숫자 파일명 대응)
        reverse_map[gift_id] = gift_id

    return reverse_map


def try_match(stem: str, reverse_map: dict[str, str]) -> str | None:
    """여러 방식으로 매칭 시도"""
    # 0. 수동 매핑 우선 확인
    if stem in MANUAL_MAPPING:
        return MANUAL_MAPPING[stem]

    # 1. 숫자만 있으면 ID로 간주
    if stem.isdigit():
        return stem if stem in reverse_map else None

    # 2. 정규화 후 검색
    normalized = normalize_for_lookup(stem)
    if normalized in reverse_map:
        return reverse_map[normalized]

    # 3. 언더스코어 → 아포스트로피 변환 후 검색
    with_apostrophe = stem.replace("_", "'").lower()
    if with_apostrophe in reverse_map:
        return reverse_map[with_apostrophe]

    # 4. 콜론 없는 버전
    no_colon = with_apostrophe.replace(":", "")
    if no_colon in reverse_map:
        return reverse_map[no_colon]

    return None


def find_png_files(input_dir: Path) -> list[Path]:
    """PNG 파일 찾기 (제외 디렉토리 필터링)"""
    result = []
    for png in input_dir.rglob("*.png"):
        # 제외 디렉토리 확인
        if any(excluded in png.parts for excluded in EXCLUDE_DIRS):
            continue
        result.append(png)
    return result


def convert_to_webp(src: Path, dst: Path, quality: int = QUALITY) -> None:
    """PNG → WEBP 변환"""
    with Image.open(src) as img:
        # RGBA 유지
        if img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info):
            img = img.convert("RGBA")
        else:
            img = img.convert("RGB")

        img.save(
            dst,
            format="WEBP",
            quality=quality,
            method=6,
            lossless=False,
        )


def main():
    parser = argparse.ArgumentParser(description="EGO Gift 이미지 파일명 변환")
    parser.add_argument(
        "--execute",
        action="store_true",
        help="실제 변환 실행 (기본값: dry-run)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=OUTPUT_DIR,
        help=f"출력 디렉토리 (기본값: {OUTPUT_DIR})",
    )
    args = parser.parse_args()

    dry_run = not args.execute
    output_dir = args.output

    # JSON 로드
    if not NAME_LIST_PATH.exists():
        print(f"[ERROR] {NAME_LIST_PATH} 파일을 찾을 수 없습니다.")
        return 1

    with open(NAME_LIST_PATH, encoding="utf-8") as f:
        name_list = json.load(f)

    reverse_map = build_reverse_mapping(name_list)

    # PNG 파일 찾기
    png_files = find_png_files(INPUT_DIR)
    if not png_files:
        print(f"[WARN] {INPUT_DIR}에서 PNG 파일을 찾을 수 없습니다.")
        return 1

    print(f"{'[DRY-RUN] ' if dry_run else ''}EGO Gift 이미지 변환")
    print(f"입력: {INPUT_DIR}")
    print(f"출력: {output_dir}")
    print(f"발견된 PNG 파일: {len(png_files)}개")
    print("-" * 60)

    matched = []
    unmatched = []

    for png_path in sorted(png_files):
        stem = png_path.stem

        # ID 찾기 (여러 방식 시도)
        gift_id = try_match(stem, reverse_map)

        if gift_id:
            webp_name = f"{gift_id}.webp"
            matched.append((png_path, webp_name, stem))
        else:
            unmatched.append((png_path, stem))

    # 매칭 결과 출력
    print(f"\n[OK] 매칭 성공: {len(matched)}개")
    for png_path, webp_name, original in matched[:10]:
        print(f"  {original} → {webp_name}")
    if len(matched) > 10:
        print(f"  ... 외 {len(matched) - 10}개")

    if unmatched:
        print(f"\n[WARN] 매칭 실패: {len(unmatched)}개")
        for png_path, original in unmatched[:20]:
            print(f"  {original}")
            # 유사한 이름 찾기
            for name in name_list.values():
                if original.lower()[:10] in name.lower():
                    print(f"    → 유사: {name}")
                    break
        if len(unmatched) > 20:
            print(f"  ... 외 {len(unmatched) - 20}개")

    # 실제 변환
    if not dry_run and matched:
        print(f"\n변환 시작...")
        output_dir.mkdir(parents=True, exist_ok=True)

        converted = 0
        skipped = 0

        for png_path, webp_name, original in matched:
            dst = output_dir / webp_name

            if dst.exists():
                skipped += 1
                continue

            try:
                convert_to_webp(png_path, dst)
                print(f"  [OK] {original} → {webp_name}")
                converted += 1
            except Exception as e:
                print(f"  [ERROR] {original}: {e}")

        print(f"\n완료: {converted}개 변환됨, {skipped}개 스킵 (이미 존재)")
        print(f"출력 디렉토리: {output_dir}")

    elif dry_run:
        print(f"\n[DRY-RUN] --execute 옵션으로 실제 변환을 실행하세요.")

    return 0


if __name__ == "__main__":
    exit(main())
