# Static Game Data Structure

## Overview

All game data (identities, EGOs, gifts) is hosted on Cloudflare R2 as static JSON files and WebP images. The frontend fetches this data directly—the backend does not store or serve game data.

## Directory Structure

```
assets/
├── images/
│   ├── identities/
│   │   ├── 10101.webp              # Yi Sang LCB Sinner
│   │   ├── 10201.webp              # Yi Sang Seven Association South Section 6
│   │   └── ...                     # 300+ identity images
│   ├── egos/
│   │   ├── 20101.webp              # Yi Sang - Wishing Cairn
│   │   ├── 20102.webp              # Yi Sang - 4th Match Flame
│   │   └── ...                     # 200+ EGO images
│   └── gifts/
│       ├── 0.webp                  # First gift
│       ├── 1.webp
│       └── ...                     # 150+ gift images
├── data/
│   ├── identities/
│   │   └── IDSpecList.json
│   ├── egos/
│   │   └── EGOSpecList.json
│   └── gifts/
│       └── EGoGiftSpecList.json
└── i18n/
    ├── EN/
    │   ├── identities/
    │   │   └── IDNameList.json
    │   ├── egos/
    │   │   └── EGONameList.json
    │   └── gifts/
    │       └── EGOGiftList.json
    ├── JP/
    │   ├── identities/
    │   │   └── IDNameList.json
    │   ├── egos/
    │   │   └── EGONameList.json
    │   └── gifts/
    │       └── EGOGiftList.json
    ├── KR/
    │   ├── identities/
    │   │   └── IDNameList.json
    │   ├── egos/
    │   │   └── EGONameList.json
    │   └── gifts/
    │       └── EGOGiftList.json
    └── CN/
        ├── identities/
    │   │   └── IDNameList.json
        ├── egos/
        │   └── EGONameList.json
        └── gifts/
    │       └── EGOGiftList.json
```

## ID System: Project Moon's Five-Digit Format

**Format**: `XYYCC`

- **X**: Entity type (1 digit)
  - `1` = Identity
  - `2` = EGO
- **YY**: Sinner ID (2 digits, zero-padded)
  - `01` = Yi Sang
  - `02` = Faust
  - `03` = Don Quixote
  - `04` = Ryo Shu
  - `05` = Meursault
  - `06` = Hong Lu
  - `07` = Heathcliff
  - `08` = Ishmael
  - `09` = Rodion
  - `11` = Sinclair
  - `12` = Outis
  - `13` = Gregor
- **CC**: Entity number within sinner (2 digits, zero-padded)
  - `01`, `02`, `03`, etc.

**Examples**:
- `10101` = Yi Sang's first identity (1-01-01)
- `10507` = Mersault's seventh identity (1-05-07)
- `11303` = Gregor's third identity (1-13-03)
- `20101` = Yi Sang's first EGO (2-01-01)
- `20507` = Mersault's seventh EGO (2-05-07)

## JSON Schemas

### Identity Name List

**File**: `/assets/i18n/identities/{EN,KR,JP,etc.}/IDNameList.json`

```json
{
  "1": {
    "1": {
      "Name": "LCB Sinner"
    },
    "2": {
      "Name": "Seven Association South Section 6"
    }
  },
  "Faust": {
    "0": {
      "Name": "LCB Sinner"
    }
  }
}
```

**Structure**:
- Top level: Sinner name (PascalCase, no spaces)
- Second level: Entity number (string, zero-padded)
- Fields: Names for each language

### Identity Spec List

**File**: `/assets/data/identities/IDSpecList.json`

```json
{
  "1": {
    "1": {
      "Star": 1,
      "Traits": [],
      "Keywords": []
    },
    "2": {
      "Star": 3,
      "Traits": ["Heishou"],
      "Keywords": ["Rupture"]
    }
  }
}
```

**Fields**:
- `Star`: Rarity (1-3)
- `Traits`: Array of trait strings
- `Keywords`: Array of keyword strings

### Identity Full Spec (Individual)

**File**: Individual identities have complete specifications.

**Reference**: See `Identity Spec.json` in Google Drive.

```json
{
  "Grade": 3,
  "HP": 254,
  "MinSpeed": 4,
  "MaxSpeed": 7,
  "DefLV": "+5",
  "Resist": [0.5, 2.0, 1.0],
  "Stagger": [0.3, 0.6],
  "Traits": "",
  "Skills": {
    "Uptie3": {
      "Skill1": [
        {
          "BasePower": 3,
          "CoinPower": 4,
          "CoinEA": "CC",
          "Sin": "Sloth",
          "AtkType": "Slash",
          "AtkWeight": 1,
          "LV": 1,
          "Quantity": 3
        }
      ],
      "Skill2": [...],
      "Skill3": [...]
    },
    "Uptie4": {
      "Skill1": [...],
      "Skill2": [...],
      "Skill3": [...]
    }
  }
}
```

**Key Fields**:
- `Grade`: Rarity (1-3)
- `HP`: Health points
- `Speed`: Speed range (e.g., "4-7")
- `DefLV`: Defense level (e.g., "60(+5)")
- `Resist`: Resistance array [Slash, Pierce, Blunt]
- `Stagger`: Stagger thresholds [66%, 33%]
- `Skills`: Nested by Uptie tier, then skill slot

**Skill Fields**:
- `BasePower`: Base damage
- `CoinPower`: Coin damage
- `CoinEA`: Coin count (e.g., "CC" = 2 coins)
- `Sin`: Sin affinity ("Wrath", "Lust", "Sloth", etc.)
- `AtkType`: Attack type ("Slash", "Pierce", "Blunt")
- `AtkWeight`: Attack weight (1-7+)
- `LV`: Level bonus
- `Quantity`: Skill quantity

### EGO Spec List

**File**: `/assets/data/egos/EGOSpecList.json`

```json
{
  "1": {
    "1": {
      "Grade": "Zayin",
      "Keywords": []
    },
    "2": {
      "Grade": "Teth",
      "Keywords": ["Poise"]
    }
  }
}
```

**Structure**:
- First level: Sinner ID (string)
- Second level: EGO number (string)
- `Grade`: EGO grade (Zayin, Teth, He, Waw, Aleph)

### EGO Full Spec (Individual)

**Reference**: See `EGO Spec.json` in Google Drive.

```json
{
  "Rank": 3,
  "Resistances": [],
  "Costs": [],
  "Skills": {
    "Threadspin3": {
      "Awakening": [
        {
          "BasePower": 3,
          "CoinPower": 4,
          "CoinEA": "CC",
          "Sin": "Sloth",
          "AtkType": "Slash",
          "AtkWeight": 1,
          "LV": 1,
          "SanityCost": 10
        }
      ],
      "Corrosion": [
        {
          "BasePower": 4,
          "CoinPower": 5,
          "CoinEA": "CC",
          "Sin": "Envy",
          "AtkType": "Slash",
          "AtkWeight": 1,
          "LV": 2,
          "SanityCost": 25
        }
      ]
    },
    "Threadspin4": {
      "Awakening": [...],
      "Corrosion": [...]
    }
  }
}
```

**Key Fields**:
- `Rank`: EGO rank (1-5 corresponding to grades: Zayin, Teth, He, Waw, Aleph)
- `Resistances`: Damage resistances
- `Costs`: Resource costs for activation
- `Skills`: Nested by Thraedspin tier, then "Awakening" / "Corrosion"

**Skill Fields** (same as Identity, plus):
- `SanityCost`: Sanity consumed to use

### Gift Spec List

**File**: `/assets/data/gifts/EGOGiftSpecList.json`

```json
{
  "0": {
    "Category": "[Combustion]",
    "Keywords": ["[Combustion]"],
    "Cost": 222
  },
  "1": {
    "Category": "[Common]",
    "Keywords": ["[Mobility]", "[Haste]"],
    "Cost": 111
  }
}
```

**Fields**:
- `Category`: Gift category (burn, bleed, tremor, rupture, sinking, poise, charge, slash, pierce, blunt, common)
- `Keywords`: Array of related keywords
- `Cost`: Gift cost (3-digit number)

### Gift Name List

**File**: `/assets/i18n/gifts/{EN,KR,JP,CN}/EGOGiftNameList.json`

```json
{
  "0": {
    "Name": "Fire Fly"
  },
  "1": {
    "Name": "Mobility"
  }
}
```

## Image Naming Conventions

### Identity Images
- **Pattern**: `{5-digit-id}.webp`
- **Example**: `10101.webp` (Yi Sang LCB Sinner)
- **Location**: `identities/images/`

### EGO Images
- **Pattern**: `2{sinner-id}{ego-num}.webp` (5 digits)
- **Example**: `20101.webp` (Yi Sang's first EGO)
- **Location**: `egos/images/`

### Gift Images
- **Pattern**: `{id}.webp`
- **Example**: `0.webp`, `42.webp`
- **Location**: `gifts/images/`