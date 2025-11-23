# UUID Validation and Corrections Report

**Date:** 2025-01-23
**Validated Against:** DrivenByMoss v25.0.0 (`/Users/csabagodor/IdeaProjects/DrivenByMoss/src/main/resources/devices/`)

## Summary

All device UUIDs in WigAI have been validated against the authoritative DrivenByMoss device registry and corrected where necessary.

## Corrections Made

### 1. **Reverb Device**
- **Previous (INCORRECT):** `b94713ba-65c8-4a28-9916-f08ac5aa73dc`
- **Current (CORRECT):** `5a1cb339-1c4a-4cc7-9cae-bd7a2058153d`
- **Source:** `BITWIG$Reverb$5a1cb339-1c4a-4cc7-9cae-bd7a2058153d` (AudioEffects.txt:49)

### 2. **Delay+ Device**
- **Previous (INCORRECT):** `c5b38bbd-6530-47e4-af32-d09477e1ae40`
- **Current (CORRECT):** `f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9`
- **Source:** `BITWIG$Delay+$f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9` (AudioEffects.txt:14)

### 3. **Filter Device Name Correction**
- **Previous Name:** "Filter+" (INCORRECT - this device doesn't exist)
- **Current Name:** "Filter" (CORRECT)
- **Previous UUID:** `6d621c1c-ab64-43b4-aea3-dad37e6f649c` (unknown/invalid)
- **Current UUID:** `4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42`
- **Source:** `BITWIG$Filter$4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42` (AudioEffects.txt:24)
- **Note:** Bitwig has "Filter" (classic) and separately "Flanger+", "Chorus+", "Phaser+" but NO "Filter+"

## Verified Correct UUIDs

### 4. **EQ+ Device** ✓
- **UUID:** `e4815188-ba6f-4d14-bcfc-2dcb8f778ccb`
- **Source:** `BITWIG$EQ+$e4815188-ba6f-4d14-bcfc-2dcb8f778ccb` (AudioEffects.txt:20)
- **Status:** Already correct

### 5. **Compressor Device** ✓
- **UUID:** `2b1b4787-8d74-4138-877b-9197209eef0f`
- **Source:** `BITWIG$Compressor$2b1b4787-8d74-4138-877b-9197209eef0f` (AudioEffects.txt:9)
- **Status:** Already correct

## Files Updated

1. **`src/main/resources/bitwig-device-parameters.json`**
   - Fixed Reverb UUID
   - Fixed Delay+ UUID
   - Renamed "Filter+" to "Filter" and corrected UUID

2. **`src/main/java/io/github/fabb/wigai/mcp/tool/DeviceInsertTool.java`**
   - Updated tool description with correct UUIDs
   - Renamed "Filter+" to "Filter"

3. **`src/test/java/io/github/fabb/wigai/integration/DeviceKnowledgeIntegrationTest.java`**
   - Updated EXPECTED_DEVICE_UUIDS map with correct values
   - Renamed test method from `testFilterPlusHasOnePageWithEightParameters` to `testFilterHasOnePageWithEightParameters`
   - Fixed EQ+ test to expect 4 pages (was incorrectly checking for 3)
   - Corrected EQ+ page names: Band Types, Gains, Freqs, Qs

## Important Notes About Bitwig Device Naming

**Devices WITH "+" suffix (newer/enhanced versions):**
- Chorus+ (`1b8f2226-c432-4a0a-9830-69bc76d1a276`)
- Delay+ (`f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9`)
- EQ+ (`e4815188-ba6f-4d14-bcfc-2dcb8f778ccb`)
- Flanger+ (`a99f8c3c-7813-4e6b-a18a-302c74286efc`)
- Phaser+ (`fd7a9e6c-6992-40c2-be3b-ac8ed48553e9`)

**Devices WITHOUT "+" suffix (classic versions):**
- Chorus (`d275f9a6-0e4a-409c-9dc4-d74af90bc7ae`)
- Filter (`4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42`) - **No Filter+ exists!**
- Flanger (`8393c436-b11b-4fee-85dd-b2ef0a2ed380`)
- Phaser (`fc87ae07-1624-449f-8dae-2db5d93e1aa9`)

**Other classic devices:**
- Compressor (`2b1b4787-8d74-4138-877b-9197209eef0f`) - No "+" version
- Reverb (`5a1cb339-1c4a-4cc7-9cae-bd7a2058153d`) - No "+" version
- Distortion (`b5b2b08e-730e-4192-be71-f572ceb5069b`) - No "+" version

## Validation Process

1. Extracted all UUIDs from WigAI `bitwig-device-parameters.json`
2. Cross-referenced with DrivenByMoss `AudioEffects.txt` and `Instruments.txt`
3. Identified discrepancies
4. Updated all affected files
5. Verified consistency across codebase

## Future Maintenance

When adding new Bitwig devices:
1. **Always** check DrivenByMoss device registry first
2. Verify exact device name (with or without "+")
3. Copy UUID exactly from DrivenByMoss
4. Update all three files:
   - `bitwig-device-parameters.json`
   - `DeviceInsertTool.java`
   - `DeviceKnowledgeIntegrationTest.java`
