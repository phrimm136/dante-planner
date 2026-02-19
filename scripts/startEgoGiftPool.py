import json
from pathlib import Path

from md_config import MD_VERSION

INPUT_PATH = Path("../raw/Json/mirror-dungeon-common-data.json")
OUTPUT_PATH = Path(f"../static/data/MD{MD_VERSION}/startEgoGiftPools.json")


def transform_ego_gift_pools(data: dict) -> dict:
    """
    Transform the startEgoGiftPools array into a keyword-keyed dictionary.
    
    Each entry's 'keyword' becomes a key, with the normalpool array as the value.
    """
    pools = data.get("startEgoGiftPools", [])
    
    return {pool["keyword"]: pool["normalpool"] for pool in pools}


def main():
    # Read input
    with INPUT_PATH.open("r", encoding="utf-8") as f:
        data = json.load(f)
    
    # Transform
    transformed = transform_ego_gift_pools(data)
    
    # Ensure output directory exists
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    
    # Write output
    with OUTPUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(transformed, f, indent="\t", ensure_ascii=False)
    
    print(f"Successfully transformed {len(transformed)} pools")
    print(f"Output written to: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
