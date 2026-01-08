import os

TARGET_DIR = "../images/UI/skillFrame/"

REPLACE_MAP = {
    "Wrath": "CRIMSON",
    "Lust": "SCARLET",
    "Sloth": "AMBER",
    "Gluttony": "SHAMROCK",
    "Gloom": "AZURE",
    "Pride": "INDIGO",
    "Envy": "VIOLET",
}

for filename in os.listdir(TARGET_DIR):
    old_path = os.path.join(TARGET_DIR, filename)
    if not os.path.isfile(old_path):
        continue

    new_name = filename
    for src, dst in REPLACE_MAP.items():
        new_name = new_name.replace(src, dst)

    if new_name != filename:
        new_path = os.path.join(TARGET_DIR, new_name)
        print(f"{filename} -> {new_name}")
        os.rename(old_path, new_path)

