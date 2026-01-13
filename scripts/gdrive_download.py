#!/usr/bin/env python3
"""
Google Drive Folder Downloader (OAuth2)

Downloads all files from Google Drive folders (recursively)
with parallel downloads and MD5 change detection. Output: PROJECT_ROOT/raw

Requirements:
    pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib tqdm

Setup:
    1. Go to https://console.cloud.google.com/
    2. Create project → Enable "Google Drive API"
    3. Create OAuth 2.0 Client ID (Desktop app)
    4. Download credentials.json to ~/.private/
    5. Edit FOLDER_URLS constant in this script
    6. First run will open browser for OAuth consent

Usage:
    python gdrive_download.py
    python gdrive_download.py --dry-run
"""

import argparse
import hashlib
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from threading import Lock

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload
from googleapiclient.errors import HttpError
from tqdm import tqdm


# --- Constants ---
FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
MAX_RETRIES = 3
RETRY_DELAY = 2  # seconds
PAGE_SIZE = 100
MAX_WORKERS = 12 # Parallel download threads

# OAuth2 settings
SCOPES = ["https://www.googleapis.com/auth/drive.readonly"]
CREDENTIALS_DIR = Path.home() / ".private"
CREDENTIALS_FILE = CREDENTIALS_DIR / "credentials.json"
TOKEN_FILE = CREDENTIALS_DIR / "token.json"

# Output directory (PROJECT_ROOT/raw)
PROJECT_ROOT = Path(__file__).parent.parent
OUTPUT_DIR = PROJECT_ROOT / "raw"

# Target folders to download (URL or folder ID)
FOLDER_URLS = [
    "https://drive.google.com/drive/folders/1c2A3EPLYMyykEG4riHLElu0wQwEVnlzk",
    "https://drive.google.com/drive/folders/1LFS3FuC-lpkxcbjm9pQydWzwbL7LLxJR",
    "https://drive.google.com/drive/folders/1SpXSol_vMaztp90diYdJ7kJIAoX8Gu6z",
    "https://drive.google.com/drive/folders/1JqaJHdxrzegMH_0O_B3DxfNyEwQUZ_eM",
    "https://drive.google.com/drive/folders/1cPu9VqNXsrnYO22vLHqrOMudcpaFSDOo",
    "https://drive.google.com/drive/folders/1l-n5IEtK3ZdZjUiKPVOF7WQBlXkjadMf",
    "https://drive.google.com/drive/folders/1CW9wiMc5-KbTtFFSC2RNCWxQ3el6tN--",
]


def get_credentials() -> Credentials:
    """Get or refresh OAuth2 credentials."""
    creds = None

    if TOKEN_FILE.exists():
        creds = Credentials.from_authorized_user_file(str(TOKEN_FILE), SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not CREDENTIALS_FILE.exists():
                print(f"Error: {CREDENTIALS_FILE} not found")
                print("Download OAuth credentials from Google Cloud Console")
                sys.exit(1)

            flow = InstalledAppFlow.from_client_secrets_file(str(CREDENTIALS_FILE), SCOPES)
            creds = flow.run_local_server(port=0)

        # Save credentials for next run
        with open(TOKEN_FILE, "w") as token:
            token.write(creds.to_json())

    return creds


def build_service():
    """Build Google Drive API service with OAuth2."""
    creds = get_credentials()
    return build("drive", "v3", credentials=creds)


def extract_folder_id(url_or_id: str) -> str:
    """Extract folder ID from Google Drive URL or return as-is if already an ID."""
    patterns = [
        r"drive\.google\.com/drive/(?:u/\d+/)?folders/([a-zA-Z0-9_-]+)",
        r"^([a-zA-Z0-9_-]{20,})$",  # Raw folder ID
    ]

    for pattern in patterns:
        match = re.search(pattern, url_or_id)
        if match:
            return match.group(1)

    raise ValueError(f"Could not extract folder ID from: {url_or_id}")


def list_files_in_folder(service, folder_id: str) -> list[dict]:
    """List all files in a folder with pagination."""
    files = []
    page_token = None

    while True:
        query = f"'{folder_id}' in parents and trashed = false"
        response = service.files().list(
            q=query,
            pageSize=PAGE_SIZE,
            fields="nextPageToken, files(id, name, mimeType, size, md5Checksum)",
            pageToken=page_token,
        ).execute()

        files.extend(response.get("files", []))
        page_token = response.get("nextPageToken")

        if not page_token:
            break

    return files


def collect_all_files(service, folder_id: str, path_prefix: str = "") -> list[dict]:
    """Recursively collect all files from folder and subfolders."""
    all_files = []
    items = list_files_in_folder(service, folder_id)

    for item in items:
        item["source_path"] = path_prefix

        if item["mimeType"] == FOLDER_MIME_TYPE:
            subfolder_path = f"{path_prefix}/{item['name']}" if path_prefix else item["name"]
            all_files.extend(collect_all_files(service, item["id"], subfolder_path))
        else:
            all_files.append(item)

    return all_files


def get_output_path(filename: str, source_path: str) -> str:
    """Get output path preserving full directory structure."""
    if not source_path:
        return filename
    return f"{source_path}/{filename}"


def calculate_md5(file_path: Path) -> str:
    """Calculate MD5 hash of a local file."""
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()


def should_download(dest_path: Path, remote_md5: str | None, remote_size: int) -> bool:
    """Check if file should be downloaded based on MD5 or size."""
    if not dest_path.exists():
        return True

    # If remote has MD5, compare hashes
    if remote_md5:
        local_md5 = calculate_md5(dest_path)
        return local_md5 != remote_md5

    # Fallback to size comparison
    return dest_path.stat().st_size != remote_size


def download_file_worker(
    creds: Credentials,
    file_info: dict,
    dest_path: Path,
) -> tuple[str, bool, str]:
    """
    Worker function for parallel downloads.
    Returns: (rel_path, success, message)
    """
    rel_path = get_output_path(
        file_info["name"],
        file_info.get("source_path", ""),
    )

    # Check if download needed
    remote_md5 = file_info.get("md5Checksum")
    remote_size = int(file_info.get("size", 0))

    if not should_download(dest_path, remote_md5, remote_size):
        return (rel_path, None, "SKIP")  # None = skipped

    dest_path.parent.mkdir(parents=True, exist_ok=True)

    # Build service per thread (not thread-safe to share)
    service = build("drive", "v3", credentials=creds)

    for attempt in range(MAX_RETRIES):
        try:
            request = service.files().get_media(fileId=file_info["id"])

            with open(dest_path, "wb") as f:
                downloader = MediaIoBaseDownload(f, request)
                done = False
                while not done:
                    _, done = downloader.next_chunk()

            return (rel_path, True, "OK")

        except HttpError as e:
            if e.resp.status in [403, 429]:  # Rate limited
                wait_time = RETRY_DELAY * (attempt + 1)
                if attempt < MAX_RETRIES - 1:
                    time.sleep(wait_time)
                    continue
                return (rel_path, False, f"Rate limited")
            elif e.resp.status == 404:
                return (rel_path, False, "404 Not Found")
            else:
                return (rel_path, False, f"HTTP {e.resp.status}")

        except Exception as e:
            if attempt < MAX_RETRIES - 1:
                time.sleep(RETRY_DELAY)
                continue
            return (rel_path, False, str(e))

    return (rel_path, False, "Max retries exceeded")


def get_folder_name(service, folder_id: str) -> str:
    """Get folder name from Google Drive."""
    folder = service.files().get(fileId=folder_id, fields="name").execute()
    return folder.get("name", folder_id)


def download_folder(
    creds: Credentials,
    folder_url_or_id: str,
    output_dir: str,
    dry_run: bool = False,
) -> dict:
    """Download all files from a Google Drive folder with parallel downloads."""
    service = build("drive", "v3", credentials=creds)
    folder_id = extract_folder_id(folder_url_or_id)

    # Get folder name and create subdirectory
    folder_name = get_folder_name(service, folder_id)
    output_path = Path(output_dir) / folder_name
    output_path.mkdir(parents=True, exist_ok=True)

    print(f"Folder: {folder_name}")
    print(f"Scanning folder {folder_id}...")
    all_files = collect_all_files(service, folder_id)
    print(f"Found {len(all_files)} files\n")

    if dry_run:
        print("-" * 60)
        for f in all_files:
            size = int(f.get("size", 0))
            path = f["source_path"] + "/" if f["source_path"] else ""
            print(f"[{size:>12,} B] {path}{f['name']}")
        print("-" * 60)
        return {"total": len(all_files), "downloaded": 0, "skipped": 0, "failed": 0}

    # Stats tracking
    stats = {"total": len(all_files), "downloaded": 0, "skipped": 0, "failed": 0}
    failed_list: list[tuple[str, str]] = []
    print_lock = Lock()

    print(f"Downloading with {MAX_WORKERS} parallel threads...")
    print("-" * 60)

    # Prepare download tasks
    tasks = []
    for file_info in all_files:
        rel_path = get_output_path(
            file_info["name"],
            file_info.get("source_path", ""),
        )
        dest_path = output_path / rel_path
        tasks.append((file_info, dest_path))

    # Progress bar
    with tqdm(total=len(tasks), unit="file", desc="Progress") as pbar:
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = {
                executor.submit(download_file_worker, creds, file_info, dest_path): rel_path
                for file_info, dest_path in tasks
                for rel_path in [get_output_path(file_info["name"], file_info.get("source_path", ""))]
            }

            for future in as_completed(futures):
                rel_path, success, message = future.result()
                pbar.update(1)

                with print_lock:
                    if success is None:  # Skipped
                        stats["skipped"] += 1
                    elif success:
                        stats["downloaded"] += 1
                    else:
                        stats["failed"] += 1
                        failed_list.append((rel_path, message))
                        tqdm.write(f"FAIL | {rel_path[:50]} | {message}")

    # Result summary
    print("-" * 60)
    print(f"\nResult:")
    print(f"  Downloaded: {stats['downloaded']}")
    print(f"  Skipped:    {stats['skipped']}")
    print(f"  Failed:     {stats['failed']}")
    print(f"  Total:      {stats['total']}")

    if failed_list:
        print(f"\nFailed files ({len(failed_list)}):")
        for name, msg in failed_list[:20]:
            print(f"  - {name}: {msg}")
        if len(failed_list) > 20:
            print(f"  ... and {len(failed_list) - 20} more")

    print(f"\nOutput: {output_path.absolute()}")
    return stats


def main():
    parser = argparse.ArgumentParser(
        description="Download Google Drive folder contents",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List files without downloading",
    )

    args = parser.parse_args()

    if not FOLDER_URLS:
        print("Error: No folders specified. Set FOLDER_URLS constant in script.")
        sys.exit(1)

    print(f"Output: {OUTPUT_DIR}")
    print("Authenticating...")
    creds = get_credentials()

    total_stats = {"total": 0, "downloaded": 0, "skipped": 0, "failed": 0}

    for i, folder_url in enumerate(FOLDER_URLS, 1):
        print(f"\n{'='*60}")
        print(f"[{i}/{len(FOLDER_URLS)}] {folder_url[:60]}")
        print(f"{'='*60}\n")

        try:
            stats = download_folder(
                creds=creds,
                folder_url_or_id=folder_url,
                output_dir=str(OUTPUT_DIR),
                dry_run=args.dry_run,
            )
            for key in total_stats:
                total_stats[key] += stats[key]

        except ValueError as e:
            print(f"Error: {e}")
        except HttpError as e:
            print(f"API Error: {e}")

    if len(FOLDER_URLS) > 1:
        print(f"\n{'='*60}")
        print("TOTAL:")
        print(f"  Downloaded: {total_stats['downloaded']}")
        print(f"  Skipped:    {total_stats['skipped']}")
        print(f"  Failed:     {total_stats['failed']}")
        print(f"  Total:      {total_stats['total']}")
        print(f"{'='*60}")


if __name__ == "__main__":
    main()
