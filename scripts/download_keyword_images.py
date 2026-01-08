#!/usr/bin/env python3
"""
battleKeywords.json 키 기반으로 lc.gesellschaft.cc 에서 이미지 다운로드
"""

import json
import os
import time
import io
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from PIL import Image

# --- 설정 ---
BASE_URL = "https://lc.gesellschaft.cc/img/status"
JSON_PATH = "../static/i18n/EN/battleKeywords.json"
OUTPUT_DIR = "../static/images/icon/battleKeywords"
RETRY_COUNT = 3
DELAY = 0.2  # seconds

# --- 함수 정의 ---

def load_keywords():
    """battleKeywords.json에서 키 + iconId 로드"""
    with open(JSON_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)
    # (key, iconId) 튜플 리스트 반환. iconId 없으면 key 사용
    return [(k, v.get("iconId") or k) for k, v in data.items()]


def download_image(keyword, icon_id, output_dir, downloaded_icons):
    """
    단일 이미지 다운로드 후 WebP로 변환 (최대 압축)
    iconId가 있으면 해당 이미지 다운로드, 파일명도 iconId 사용
    Returns: (keyword, success, message)
    """
    url = f"{BASE_URL}/{icon_id}.png"
    output_path = os.path.join(output_dir, f"{icon_id}.webp")

    # 같은 icon_id가 이미 다운로드되었으면 스킵 (중복 방지)
    if icon_id in downloaded_icons:
        return (keyword, True, f"중복 스킵 (icon: {icon_id})")

    # 파일 이미 존재하면 스킵
    if os.path.exists(output_path):
        downloaded_icons.add(icon_id)
        return (keyword, True, f"이미 존재 (icon: {icon_id})")

    for attempt in range(RETRY_COUNT):
        try:
            request = Request(
                url,
                headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                }
            )

            with urlopen(request, timeout=10) as response:
                content = response.read()

                if len(content) < 100:
                    return (keyword, False, "응답 크기 너무 작음")

                # PNG → WebP 변환 (pngToWebp.py 패턴)
                img = Image.open(io.BytesIO(content))
                img.save(
                    output_path,
                    format="WEBP",
                    quality=85,
                    method=6,      # best compression
                    lossless=False
                )

            downloaded_icons.add(icon_id)
            return (keyword, True, f"다운로드 완료 (icon: {icon_id})")

        except HTTPError as e:
            if e.code == 404:
                return (keyword, False, "404 Not Found")
            if attempt < RETRY_COUNT - 1:
                time.sleep(1)
                continue
            return (keyword, False, f"HTTP Error: {e.code}")

        except URLError as e:
            if attempt < RETRY_COUNT - 1:
                time.sleep(1)
                continue
            return (keyword, False, f"URL Error: {e.reason}")

        except Exception as e:
            return (keyword, False, f"Error: {str(e)}")

    return (keyword, False, "최대 재시도 초과")


# --- 메인 ---
if __name__ == "__main__":
    # 스크립트 디렉토리 기준으로 경로 설정
    script_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(script_dir)

    # 출력 디렉토리 생성
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # 키워드 로드
    print(f"키워드 로드 중: {JSON_PATH}")
    keyword_list = load_keywords()
    print(f"총 {len(keyword_list)}개 키워드 발견\n")

    # 다운로드
    success_count = 0
    skip_count = 0
    dup_count = 0
    fail_count = 0
    failed_list = []
    downloaded_icons = set()  # 중복 방지용

    print("이미지 다운로드 중...")
    print("-" * 60)

    for i, (kw, icon_id) in enumerate(keyword_list, 1):
        keyword, success, message = download_image(kw, icon_id, OUTPUT_DIR, downloaded_icons)

        if success:
            if "중복 스킵" in message:
                dup_count += 1
                status = "DUP"
            elif "이미 존재" in message:
                skip_count += 1
                status = "SKIP"
            else:
                success_count += 1
                status = "OK"
        else:
            fail_count += 1
            failed_list.append((keyword, message))
            status = "FAIL"

        print(f"[{i:4d}/{len(keyword_list)}] {status:4s} | {keyword[:40]:<40} | {message}")
        time.sleep(DELAY)

    # 결과 요약
    print("-" * 60)
    print(f"\n결과 요약:")
    print(f"  다운로드: {success_count}")
    print(f"  스킵:     {skip_count}")
    print(f"  중복:     {dup_count}")
    print(f"  실패:     {fail_count}")
    print(f"  총:       {len(keyword_list)}")
    print(f"  고유 아이콘: {len(downloaded_icons)}")

    if failed_list:
        print(f"\n실패 목록 ({len(failed_list)}개):")
        for kw, msg in failed_list[:20]:
            print(f"  - {kw}: {msg}")
        if len(failed_list) > 20:
            print(f"  ... 외 {len(failed_list) - 20}개")

    print(f"\n출력 디렉토리: {os.path.abspath(OUTPUT_DIR)}")
