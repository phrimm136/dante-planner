"""
Language configuration for localization scripts.

Defines paths and prefixes for each supported language.
- KR/EN/JP: Official game data in raw/Json/{LANG}/{LANG}_*.json
- CN: Community localization in raw/LocalizeLimbusCompany/LLC_zh-CN/*.json (no prefix)
"""

import os
from typing import Optional

# Supported languages
LANGS = ["KR", "EN", "JP", "CN"]

# Base directories (relative to scripts/)
RAW_JSON_DIR = "../raw/Json"
RAW_CN_DIR = "../raw/LocalizeLimbusCompany/LLC_zh-CN"
I18N_DIR = "../static/i18n"

# Language configuration: {lang: (directory, prefix)}
LANG_CONFIG = {
    "KR": (f"{RAW_JSON_DIR}/KR", "KR_"),
    "EN": (f"{RAW_JSON_DIR}/EN", "EN_"),
    "JP": (f"{RAW_JSON_DIR}/JP", "JP_"),
    "CN": (RAW_CN_DIR, ""),  # CN has no prefix
}


def get_lang_dir(lang: str) -> str:
    """Get the raw data directory for a language."""
    return LANG_CONFIG[lang][0]


def get_lang_prefix(lang: str) -> str:
    """Get the file prefix for a language (e.g., 'KR_' or '' for CN)."""
    return LANG_CONFIG[lang][1]


def get_raw_file(lang: str, filename: str) -> str:
    """
    Get the full path to a raw data file for a language.

    Args:
        lang: Language code (KR, EN, JP, CN)
        filename: Base filename without prefix (e.g., 'Personalities.json')

    Returns:
        Full path like '../raw/Json/KR/KR_Personalities.json' or
        '../raw/LocalizeLimbusCompany/LLC_zh-CN/Personalities.json'
    """
    directory, prefix = LANG_CONFIG[lang]
    return os.path.join(directory, f"{prefix}{filename}")


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
    directory, prefix = LANG_CONFIG[lang]
    return os.path.join(directory, f"{prefix}{pattern}")


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
