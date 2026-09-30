#!/usr/bin/env python3
"""
Latin display font builder.

The game ships Mikodacs (gluk, SIL OFL 1.1) as a CFF OpenType font whose
hinted CharStrings fail Chromium's OTS sanitizer ("CFF : Failed validating
CharStrings INDEX"), so the browser rejects the file outright. Removing the
CFF hints makes it load; outlines, advances and every other table are kept.

The OFL reserves the name "Mikodacs" for the original, so the modified font
is renamed PlannerLatin in the name table and the CFF top dict. The copyright
statement (name ID 13) and licence URL (name ID 14) are kept, and name ID 0
gains a line recording the modification.

raw/ is gitignored, so the source path is passed in:

    python3 frontend/scripts/build-latin-display-font.py <path/to/Mikodacs.ttf>
    MIKODACS_SOURCE=<path/to/Mikodacs.ttf> python3 frontend/scripts/build-latin-display-font.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

from fontTools.pens.recordingPen import RecordingPen
from fontTools.ttLib import TTFont

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "frontend/src/assets/fonts/planner-latin.otf"

ORIGINAL_NAME = "Mikodacs"
FAMILY = "PlannerLatin"
FULL_NAME = "PlannerLatin Regular"
POSTSCRIPT_NAME = "PlannerLatin-Regular"
MODIFICATION_NOTE = (
    "Modified version of Mikodacs by gluk: hinting removed and renamed PlannerLatin. "
    "Licensed under the SIL Open Font License, Version 1.1."
)

KEPT_NAME_IDS = {0, 2, 5, 8, 9, 11, 12, 13, 14}
RENAMED_NAME_IDS = {1, 3, 4, 6, 16, 17}
EXPECTED_UNITS_PER_EM = 1000
EXPECTED_CODEPOINTS = 910


def source_path() -> Path:
    arg = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("MIKODACS_SOURCE")
    if not arg:
        sys.exit("usage: build-latin-display-font.py <Mikodacs.ttf> (or set MIKODACS_SOURCE)")
    return Path(arg)


def renamed_value(name_id: int, version: str) -> str:
    return {
        1: FAMILY,
        3: f"{POSTSCRIPT_NAME} : {version.strip()}",
        4: FULL_NAME,
        6: POSTSCRIPT_NAME,
        16: FAMILY,
        17: "Regular",
    }[name_id]


def rename(font: TTFont) -> None:
    name = font["name"]
    version = name.getDebugName(5) or ""
    for record in name.names:
        if record.nameID in RENAMED_NAME_IDS:
            record.string = renamed_value(record.nameID, version)
        elif record.nameID == 0:
            record.string = record.toUnicode().rstrip("\n") + "\n" + MODIFICATION_NOTE + "\n"
        elif record.nameID not in KEPT_NAME_IDS and ORIGINAL_NAME in record.toUnicode():
            sys.exit(f"name ID {record.nameID} carries the reserved name and has no rename rule")

    cff = font["CFF "].cff
    cff.fontNames = [POSTSCRIPT_NAME]
    top = cff.topDictIndex[0]
    top.FullName = FULL_NAME
    top.FamilyName = FAMILY


def strip_hints(font: TTFont) -> None:
    cff = font["CFF "].cff
    cff.remove_hints()
    cff.remove_unused_subroutines()


def outlines(font: TTFont) -> dict[str, list]:
    glyph_set = font.getGlyphSet()
    recorded = {}
    for glyph_name in font.getGlyphOrder():
        pen = RecordingPen()
        glyph_set[glyph_name].draw(pen)
        recorded[glyph_name] = pen.value
    return recorded


def verify(original: TTFont, built: TTFont) -> None:
    assert built["head"].unitsPerEm == EXPECTED_UNITS_PER_EM
    assert len(built.getBestCmap()) == EXPECTED_CODEPOINTS
    assert built.getBestCmap() == original.getBestCmap()
    assert built["hmtx"].metrics == original["hmtx"].metrics
    assert outlines(built) == outlines(original)
    for record in built["name"].names:
        if record.nameID in RENAMED_NAME_IDS:
            assert ORIGINAL_NAME not in record.toUnicode(), record
    for name_id in (13, 14):
        assert built["name"].getDebugName(name_id) == original["name"].getDebugName(name_id)
    assert built["CFF "].cff.fontNames == [POSTSCRIPT_NAME]


def main() -> None:
    source = source_path()
    font = TTFont(source, recalcTimestamp=False)
    strip_hints(font)
    rename(font)
    font.save(OUT)

    verify(TTFont(source), TTFont(OUT))
    print(f"Wrote {OUT.relative_to(REPO)}")


if __name__ == "__main__":
    main()
