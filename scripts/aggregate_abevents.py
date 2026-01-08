#!/usr/bin/env python3
"""
AbEvent Aggregation Script (Mirror Dungeon Focus)

Aggregates AbEvent data from raw JSON files into individual files.
Only includes events matching pattern: 9[0-9][1-9]xxx (6-digit parent IDs)
and their 8-digit selection events (parent ID + 2-digit choice index).

Output:
1. static/data/abEvent/{id}.json - Mechanics (judgement, rewards, choices)
2. static/i18n/{lang}/abEvent/{id}.json - Localized text (titles, descriptions)

Usage:
    python3 scripts/aggregate_abevents.py [--lang LANG] [--all-langs]

    --lang LANG: Language code (EN, KR, JP). Default: EN
    --all-langs: Process all available languages
"""

import json
import re
import sys
from pathlib import Path

# =============================================================================
# PATHS
# =============================================================================

RAW_JSON_DIR = Path(__file__).parent.parent / "raw" / "Json"
STATIC_DIR = Path(__file__).parent.parent / "static"
OUTPUT_DIR = STATIC_DIR / "data" / "abEvent"
I18N_OUTPUT_DIR = STATIC_DIR / "i18n"

# =============================================================================
# SUPPORTED LANGUAGES
# =============================================================================

SUPPORTED_LANGS = ["EN", "KR", "JP"]

# =============================================================================
# ID PATTERN MATCHING
# =============================================================================

# Parent event ID pattern: 9[0-9][1-9]xxx (6 digits, third digit is 1-9)
PARENT_ID_PATTERN = re.compile(r"^9[0-9][1-9]\d{3}$")

# Selection event ID pattern: 9[0-9][1-9]xxx + 2 digits (8 digits total)
SELECTION_ID_PATTERN = re.compile(r"^9[0-9][1-9]\d{5}$")


def is_valid_parent_id(event_id: int) -> bool:
    """Check if event ID matches parent pattern: 9[0-9][1-9]xxx"""
    return bool(PARENT_ID_PATTERN.match(str(event_id)))


def is_valid_selection_id(event_id: int) -> bool:
    """Check if event ID matches selection pattern: 9[0-9][1-9]xxxYY"""
    return bool(SELECTION_ID_PATTERN.match(str(event_id)))


def get_parent_id(event_id: int) -> int | None:
    """
    Extract parent event ID from a selection event.
    Selection events have 8 digits where the last 2 are the choice index.
    Returns None if not a valid selection event.
    """
    if is_valid_selection_id(event_id):
        return int(str(event_id)[:6])
    return None


def get_choice_index(event_id: int) -> int | None:
    """Extract choice index (last 2 digits) from selection event."""
    if is_valid_selection_id(event_id):
        return int(str(event_id)[-2:])
    return None


# =============================================================================
# HELPER FUNCTIONS
# =============================================================================


def load_json_safe(filepath: Path) -> dict | list | None:
    """Load JSON file, return None if not found or invalid."""
    if not filepath.exists():
        return None
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError:
        return None


def extract_event_data(event: dict) -> dict:
    """Extract mechanics data from a personality/coin event."""
    result = {
        "canSkip": event.get("canSkip", False),
        "eventType": event.get("eventType"),
    }

    if "personalityEvent" in event:
        pe = event["personalityEvent"]
        if pe.get("participantInfo"):
            result["participantInfo"] = pe.get("participantInfo")
        if pe.get("silenceCharacterUniqueIdList"):
            result["silenceList"] = pe.get("silenceCharacterUniqueIdList")
        if pe.get("adderInfo"):
            result["adderInfo"] = pe.get("adderInfo")

        if "judgement" in pe:
            j = pe["judgement"]
            result["judgement"] = {
                "successThreshold": j.get("inclusiveValue"),
                "bestThreshold": j.get("exclusiveValue"),
                "affinities": [d.get("attributeType") for d in j.get("judgementDataList", [])],
            }

        if "eventResults" in pe:
            result["results"] = []
            for er in pe["eventResults"]:
                parsed_result = {
                    "outcome": er.get("eventResult"),
                    "effects": [],
                }
                for erd in er.get("eventResultDataList", []):
                    for inner in erd.get("eventResultDataList", []):
                        rf = inner.get("resultForm", {})
                        effect = {
                            "effect": rf.get("resultEffect"),
                            "target": rf.get("resultEffectTarget"),
                            "condition": rf.get("resultEffectCondition1"),
                            "descId": rf.get("resultDescId"),
                        }
                        if "itemReward" in rf:
                            ir = rf["itemReward"]
                            effect["reward"] = {
                                "type": ir.get("type"),
                                "id": ir.get("rewardId"),
                                "num": ir.get("num"),
                                "prob": ir.get("prob"),
                            }
                        effect = {k: v for k, v in effect.items() if v is not None}
                        if effect:
                            parsed_result["effects"].append(effect)
                result["results"].append(parsed_result)

    result = {k: v for k, v in result.items() if v is not None and v != [] and v != {}}
    return result


def extract_action_event_data(event: dict) -> dict:
    """Extract mechanics data from an action event (parent with choices)."""
    result = {
        "canSkip": event.get("canSkip", False),
        "eventType": event.get("eventType"),
    }

    if "actionEvent" in event:
        ae = event["actionEvent"]
        if ae.get("isHideHint"):
            result["isHideHint"] = True
        result["choices"] = []

        for option in ae.get("eachOptionList", []):
            choice = {
                "index": option.get("index"),
            }
            if option.get("cantSelectInThisCase") and option.get("cantSelectInThisCase") != "None":
                choice["cantSelectInThisCase"] = option.get("cantSelectInThisCase")

            # Check for nextEventID (leads to coin toss) or direct results
            for res in option.get("resultList", []):
                if res.get("nextEventID") and res.get("nextEventID") != -1:
                    choice["nextEventId"] = res.get("nextEventID")
                    break
                elif res.get("eventResultDataList"):
                    # Direct results - no coin toss, effects applied immediately
                    effects = []
                    for erd in res.get("eventResultDataList", []):
                        rf = erd.get("resultForm", {})
                        effect = {
                            "effect": rf.get("resultEffect"),
                            "target": rf.get("resultEffectTarget"),
                            "condition": rf.get("resultEffectCondition1"),
                            "descId": rf.get("resultDescId"),
                        }
                        if "itemReward" in rf:
                            ir = rf["itemReward"]
                            effect["reward"] = {
                                "type": ir.get("type"),
                                "id": ir.get("rewardId"),
                                "num": ir.get("num"),
                                "prob": ir.get("prob"),
                            }
                        if rf.get("nextBattleID") and rf.get("nextBattleID") != -1:
                            effect["nextBattleId"] = rf.get("nextBattleID")
                        effect = {k: v for k, v in effect.items() if v is not None}
                        if effect and effect.get("effect") != "Nothing":
                            effects.append(effect)
                    if effects:
                        choice["directEffects"] = effects
                    break

            result["choices"].append(choice)

    result = {k: v for k, v in result.items() if v is not None and v != [] and v != {}}
    return result


def extract_text_data(event: dict) -> dict:
    """Extract i18n text from an AbEvents text file entry."""
    result = {}

    if event.get("title"):
        result["title"] = event["title"]
    if event.get("prevDesc"):
        result["prevDesc"] = event["prevDesc"]
    if event.get("eventDesc"):
        result["eventDesc"] = event["eventDesc"]
    if event.get("behaveDesc"):
        result["behaveDesc"] = event["behaveDesc"]
    if event.get("successDesc"):
        result["successDesc"] = event["successDesc"]
    if event.get("failureDesc"):
        result["failureDesc"] = event["failureDesc"]

    return result


def extract_action_text_data(event: dict) -> dict:
    """Extract i18n text from an ActionEvents text file entry."""
    result = {}

    if event.get("name"):
        result["name"] = event["name"]
    if event.get("subDesc"):
        result["subDesc"] = event["subDesc"]
    if event.get("desc"):
        result["desc"] = event["desc"]

    options = []
    for opt in event.get("options", []):
        opt_data = {}
        if opt.get("message"):
            opt_data["message"] = opt["message"]
        if opt.get("messageDesc"):
            opt_data["messageDesc"] = opt["messageDesc"]
        # Collect result text (outcome narrative after choosing this option)
        if opt.get("result"):
            # Filter out empty strings
            results = [r for r in opt["result"] if r and r.strip()]
            if results:
                opt_data["result"] = results
        if opt_data:
            options.append(opt_data)

    if options:
        result["options"] = options

    return result


# =============================================================================
# TITLE LOOKUP FUNCTIONS
# =============================================================================


def load_behave_to_title_map(lang: str) -> dict:
    """
    Load a mapping of behaveDesc -> title from all AbEvents files.
    Used to derive titles for selection events that have matching behaveDesc.
    Returns a dict mapping behaveDesc string -> title string.
    Note: If multiple events have the same behaveDesc but different titles,
    the first one found will be used (first-match-wins).
    """
    lang_dir = RAW_JSON_DIR / lang
    if not lang_dir.exists():
        return {}

    behave_to_title = {}
    for filepath in sorted(lang_dir.glob(f"{lang}_AbEvents*.json")):
        if "ResultLog" in filepath.name:
            continue

        data = load_json_safe(filepath)
        if not data or "dataList" not in data:
            continue

        for event in data["dataList"]:
            behave = (event.get("behaveDesc") or "").strip()
            title = (event.get("title") or "").strip()
            if behave and title and behave not in behave_to_title:
                behave_to_title[behave] = title

    return behave_to_title


def get_derived_title(behave_desc: str, behave_to_title: dict) -> str | None:
    """
    Look up a derived title for an event using its behaveDesc.
    Returns the title if a match is found, None otherwise.
    """
    if not behave_desc:
        return None
    return behave_to_title.get(behave_desc)


# =============================================================================
# AGGREGATION FUNCTIONS
# =============================================================================


def aggregate_mechanics_data() -> dict:
    """
    Aggregate all mechanics data from ALL choice event files.
    Only includes events matching 9[0-9][1-9]xxx pattern.
    """
    print("Aggregating AbEvent mechanics data...")
    print(f"  Pattern: 9[0-9][1-9]xxx (6-digit parent) + optional 2-digit choice")

    all_events = {}
    selection_events = {}  # Temporary storage for selection events

    # Collect ALL choice event files
    choice_files = list(RAW_JSON_DIR.glob("*choice*.json"))
    print(f"  Found {len(choice_files)} choice event files")

    for filepath in sorted(choice_files):
        data = load_json_safe(filepath)
        if not data or "list" not in data:
            continue

        file_count = 0
        for event in data["list"]:
            event_id = event.get("id")
            if not event_id:
                continue

            # Check if it's a valid parent ID (6-digit)
            if is_valid_parent_id(event_id):
                # This is a parent action event
                if "actionEvent" in event:
                    all_events[event_id] = extract_action_event_data(event)
                elif "personalityEvent" in event:
                    all_events[event_id] = extract_event_data(event)
                file_count += 1

            # Check if it's a valid selection ID (8-digit)
            elif is_valid_selection_id(event_id):
                parent_id = get_parent_id(event_id)
                choice_idx = get_choice_index(event_id)
                if parent_id and choice_idx is not None:
                    if parent_id not in selection_events:
                        selection_events[parent_id] = {}
                    selection_events[parent_id][choice_idx] = extract_event_data(event)
                    file_count += 1

        if file_count > 0:
            print(f"    {filepath.name}: {file_count} matching events")

    # Merge selection events into parent events
    for parent_id, choices in selection_events.items():
        if parent_id in all_events:
            all_events[parent_id]["selectionEvents"] = choices
        else:
            # Parent event not found, create placeholder
            all_events[parent_id] = {"selectionEvents": choices}

    print(f"  Total parent events: {len(all_events)}")
    return all_events


def collect_effect_refs(mechanics_data: dict) -> tuple[set, set]:
    """Collect all effect references from mechanics data.

    Returns:
        tuple: (desc_ids, effect_types)
        - desc_ids: Set of descId strings (e.g., "Choice_901001")
        - effect_types: Set of effect type strings (e.g., "Nothing", "LoseHpOnly_10")
    """
    desc_ids = set()
    effect_types = set()

    for event_data in mechanics_data.values():
        # From choices with directEffects
        for choice in event_data.get("choices", []):
            for effect in choice.get("directEffects", []):
                if effect.get("descId"):
                    desc_ids.add(effect["descId"])
                if effect.get("effect"):
                    effect_types.add(effect["effect"])

        # From selectionEvents
        for sel in event_data.get("selectionEvents", {}).values():
            for result in sel.get("results", []):
                for effect in result.get("effects", []):
                    if effect.get("descId"):
                        desc_ids.add(effect["descId"])
                    if effect.get("effect"):
                        effect_types.add(effect["effect"])

    return desc_ids, effect_types


def aggregate_i18n_data(lang: str, mechanics_data: dict) -> dict:
    """
    Aggregate all i18n text data from ALL text files for a language.
    Only includes events matching 9[0-9][1-9]xxx pattern.
    Filters shared resources to only include referenced entries.
    Derives titles for selection events by matching behaveDesc with titled events.
    """
    print(f"Aggregating AbEvent i18n data for {lang}...")

    # Load behaveDesc -> title mapping for derived title lookup
    behave_to_title = load_behave_to_title_map(lang)
    print(f"  Loaded {len(behave_to_title)} behaveDesc -> title mappings")

    # Collect all effect references from mechanics
    referenced_desc_ids, referenced_effect_types = collect_effect_refs(mechanics_data)

    lang_dir = RAW_JSON_DIR / lang
    if not lang_dir.exists():
        print(f"  Warning: Language directory not found: {lang_dir}")
        return {"events": {}, "resultLogs": {}, "effects": {}}

    all_events = {}
    selection_texts = {}  # Temporary storage for selection event texts
    result_logs = {}
    effects = {}

    # Collect ALL AbEvents text files
    ab_files = list(lang_dir.glob(f"{lang}_AbEvents*.json"))
    print(f"  Found {len(ab_files)} AbEvents files")

    for filepath in sorted(ab_files):
        # Skip ResultLog files (different structure)
        if "ResultLog" in filepath.name:
            continue

        data = load_json_safe(filepath)
        if not data or "dataList" not in data:
            continue

        file_count = 0
        for event in data["dataList"]:
            event_id = event.get("id")
            if not event_id:
                continue

            # Check if it's a valid parent ID (6-digit)
            if is_valid_parent_id(event_id):
                text_data = extract_text_data(event)
                if text_data:
                    all_events[event_id] = text_data
                    file_count += 1

            # Check if it's a valid selection ID (8-digit)
            elif is_valid_selection_id(event_id):
                parent_id = get_parent_id(event_id)
                choice_idx = get_choice_index(event_id)
                if parent_id and choice_idx is not None:
                    text_data = extract_text_data(event)
                    if text_data:
                        # Derive title by matching behaveDesc if current title is empty
                        if not event.get("title", "").strip():
                            behave = (event.get("behaveDesc") or "").strip()
                            derived_title = get_derived_title(behave, behave_to_title)
                            if derived_title:
                                text_data["title"] = derived_title
                        if parent_id not in selection_texts:
                            selection_texts[parent_id] = {}
                        selection_texts[parent_id][choice_idx] = text_data
                        file_count += 1

        if file_count > 0:
            print(f"    {filepath.name}: {file_count} matching entries")

    # Collect ALL ActionEvents text files
    action_files = list(lang_dir.glob(f"{lang}_ActionEvents*.json"))
    print(f"  Found {len(action_files)} ActionEvents files")

    for filepath in sorted(action_files):
        data = load_json_safe(filepath)
        if not data or "dataList" not in data:
            continue

        file_count = 0
        for event in data["dataList"]:
            event_id = event.get("id")
            if not event_id:
                continue

            # Only process valid parent IDs
            if is_valid_parent_id(event_id):
                text_data = extract_action_text_data(event)
                if text_data:
                    if event_id in all_events:
                        all_events[event_id].update(text_data)
                    else:
                        all_events[event_id] = text_data
                    file_count += 1

        if file_count > 0:
            print(f"    {filepath.name}: {file_count} matching entries")

    # Merge selection event texts into parent events
    for parent_id, choices in selection_texts.items():
        if parent_id in all_events:
            all_events[parent_id]["selectionTexts"] = choices
        else:
            all_events[parent_id] = {"selectionTexts": choices}

    # Collect effect descriptions (only referenced ones)
    all_effects = {}
    effect_files = list(lang_dir.glob(f"{lang}_ChoiceEventEffect*.json"))
    for filepath in sorted(effect_files):
        data = load_json_safe(filepath)
        if not data or "dataList" not in data:
            continue

        for entry in data["dataList"]:
            entry_id = entry.get("id")
            if entry_id and entry.get("content"):
                all_effects[entry_id] = entry["content"]

    # Filter to only referenced effects (by descId OR by effect type)
    effects = {}
    for k, v in all_effects.items():
        if k in referenced_desc_ids or k in referenced_effect_types:
            effects[k] = v
    print(f"    Effects: {len(effects)} used / {len(all_effects)} total")

    # Embed effect descriptions into events with directEffects
    for event_id, event_data in mechanics_data.items():
        choices = event_data.get("choices", [])
        choice_effects = {}

        for choice in choices:
            choice_idx = choice.get("index")
            if choice_idx is None:
                continue

            direct_effects = choice.get("directEffects", [])
            if direct_effects:
                effect_texts = []
                for effect in direct_effects:
                    desc_id = effect.get("descId")
                    if desc_id and desc_id in all_effects:
                        effect_texts.append(all_effects[desc_id])
                if effect_texts:
                    choice_effects[choice_idx] = effect_texts

        if choice_effects and event_id in all_events:
            all_events[event_id]["choiceEffects"] = choice_effects

    # Collect result logs (only those matching our event IDs)
    # Result log IDs follow pattern: {eventId}_{name}_{result}_{index}
    event_ids = set(mechanics_data.keys())
    result_log_files = list(lang_dir.glob(f"{lang}_AbEventsResultLog*.json"))
    for filepath in sorted(result_log_files):
        data = load_json_safe(filepath)
        if not data or "dataList" not in data:
            continue

        for entry in data["dataList"]:
            entry_id = entry.get("id")
            if entry_id and entry.get("content"):
                # Check if this log belongs to one of our events
                # Format: {eventId}_{name}_{result}_{index}
                parts = str(entry_id).split("_")
                if parts and parts[0].isdigit():
                    log_event_id = int(parts[0])
                    if log_event_id in event_ids:
                        result_logs[entry_id] = entry["content"]

    print(f"    Result logs: {len(result_logs)} matching our events")

    print(f"  Total: {len(all_events)} events")

    return {
        "events": all_events,
        "resultLogs": result_logs,
        "effects": effects,
    }


# =============================================================================
# OUTPUT FUNCTIONS
# =============================================================================


def save_individual_files(data: dict, output_dir: Path, description: str):
    """Save each item as an individual JSON file."""
    output_dir.mkdir(parents=True, exist_ok=True)

    # Clean old files
    for old_file in output_dir.glob("*.json"):
        old_file.unlink()

    count = 0
    for event_id, event_data in data.items():
        filepath = output_dir / f"{event_id}.json"
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(event_data, f, ensure_ascii=False, indent=2)
        count += 1

    print(f"  Saved {count} {description} files to {output_dir}")


def save_i18n_data(all_data: dict, lang: str):
    """Save i18n data as individual files plus shared resources."""
    lang_output_dir = I18N_OUTPUT_DIR / lang / "abEvent"
    lang_output_dir.mkdir(parents=True, exist_ok=True)

    # Clean old files
    for old_file in lang_output_dir.glob("*.json"):
        old_file.unlink()

    # Save individual event files
    events = all_data.get("events", {})
    for event_id, event_data in events.items():
        filepath = lang_output_dir / f"{event_id}.json"
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(event_data, f, ensure_ascii=False, indent=2)

    # Save shared resources (result logs and effects)
    shared = {}
    if all_data.get("resultLogs"):
        shared["resultLogs"] = all_data["resultLogs"]
    if all_data.get("effects"):
        shared["effects"] = all_data["effects"]

    if shared:
        shared_path = lang_output_dir / "_shared.json"
        with open(shared_path, "w", encoding="utf-8") as f:
            json.dump(shared, f, ensure_ascii=False, indent=2)

    print(f"  Saved {len(events)} event files + shared resources to {lang_output_dir}")


# =============================================================================
# MAIN
# =============================================================================


def main():
    """Main entry point."""
    print("AbEvent Aggregation (Mirror Dungeon)")
    print("=" * 50)
    print("ID Pattern: 9[0-9][1-9]xxx (e.g., 901002, 971005)")
    print("=" * 50)

    # Parse arguments
    all_langs = "--all-langs" in sys.argv

    if all_langs:
        langs = SUPPORTED_LANGS
    else:
        lang = "EN"
        if "--lang" in sys.argv:
            idx = sys.argv.index("--lang")
            if idx + 1 < len(sys.argv):
                lang = sys.argv[idx + 1]
        langs = [lang]

    # Aggregate and save mechanics data (language-agnostic)
    print("\n--- Mechanics Data ---")
    mechanics_data = aggregate_mechanics_data()
    save_individual_files(mechanics_data, OUTPUT_DIR, "mechanics")

    # Aggregate and save i18n data for each language
    for lang in langs:
        print(f"\n--- i18n Data ({lang}) ---")
        i18n_data = aggregate_i18n_data(lang, mechanics_data)
        save_i18n_data(i18n_data, lang)

    print("\n" + "=" * 50)
    print("Done!")


if __name__ == "__main__":
    main()
