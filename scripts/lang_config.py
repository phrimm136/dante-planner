"""
Language configuration for localization scripts.

Defines paths for each supported language.
- KR/EN/JP/CN: Community localization in raw/LocalizeLimbusCompany/{lang}/*.json 
"""

import os
from typing import Optional

# Supported languages
LANGS = ["KR", "EN", "JP", "CN"]

# Base directories (relative to scripts/)
RAW_JSON_DIR = "../raw/LocalizeLimbusCompany"
I18N_DIR = "../static/i18n"

# Language configuration: {lang: directory}
LANG_CONFIG = {
    "KR": f"{RAW_JSON_DIR}/KR",
    "EN": f"{RAW_JSON_DIR}/EN",
    "JP": f"{RAW_JSON_DIR}/JP",
    "CN": f"{RAW_JSON_DIR}/LLC_zh-CN",
}


def get_lang_dir(lang: str) -> str:
    """Get the raw data directory for a language."""
    return LANG_CONFIG[lang][0]



def get_raw_file(lang: str, filename: str) -> str:
    """
    Get the full path to a raw data file for a language.

    Args:
        lang: Language code (KR, EN, JP, CN)
        filename: Base filename (e.g., 'Personalities.json')

    Returns:
        Full path like '../raw/Json/KR/KR_Personalities.json' or
        '../raw/LocalizeLimbusCompany/LLC_zh-CN/Personalities.json'
    """
    directory = LANG_CONFIG[lang]
    return os.path.join(directory, f"{filename}")


def get_raw_pattern(lang: str, pattern: str) -> str:
    """
    Get a glob pattern for raw data files.

    Args:
        lang: Language code
        pattern: Pattern with placeholder (e.g., 'BattleKeywords*.json')

    Returns:
        Full pattern like '../raw/Json/KR/KR_BattleKeywords*.json' or
        '../raw/LocalizeLimbusCompany/LLC_zh-CN/BattleKeywords*.json'
    """
    directory = LANG_CONFIG[lang]
    return os.path.join(directory, f"{pattern}")


def lang_dir_exists(lang: str) -> bool:
    """Check if the raw data directory for a language exists."""
    return os.path.exists(get_lang_dir(lang))


def get_i18n_dir(lang: str, subdir: Optional[str] = None) -> str:
    """
    Get the i18n output directory for a language.

    Args:
        lang: Language code
        subdir: Optional subdirectory (e.g., 'identity', 'ego')

    Returns:
        Path like '../static/i18n/KR' or '../static/i18n/KR/identity'
    """
    if subdir:
        return os.path.join(I18N_DIR, lang, subdir)
    return os.path.join(I18N_DIR, lang)
