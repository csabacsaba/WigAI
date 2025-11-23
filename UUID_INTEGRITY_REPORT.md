# UUID Integrity Check Report

**Date:** 2025-01-23  
**Verified Against:** DrivenByMoss v25.0.0

## Summary

✅ **Fixed:** 2 UUID mismatches corrected  
✅ **Verified:** 12 devices now match DrivenByMoss  
⚠️ **Warning:** 1 device (Note Receiver) not in DrivenByMoss (may be newer)

## Changes Made

### Fixed UUIDs

1. **Compressor+ → Compressor**
   - Old: `42b32cd2-6275-4ff1-970f-4fac71d15ad9`
   - New: `2b1b4787-8d74-4138-877b-9197209eef0f` ✅
   - Source: DrivenByMoss AudioEffects.txt

2. **Filter+ → Filter**
   - Old: `6d621c1c-ab64-43b4-aea3-dad37e6f649c`
   - New: `4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42` ✅
   - Source: DrivenByMoss AudioEffects.txt

### Verified Correct UUIDs

**Instruments:**
- ✅ Polysynth: `a9ffacb5-33e9-4fc7-8621-b1af31e410ef`
- ✅ Sampler: `468bc14b-b2e7-45a1-9666-e83117fe404e`
- ✅ Polymer: `8f58138b-03aa-4e9d-83bd-a038c99a4ed5`
- ✅ Drum Machine: `8ea97e45-0255-40fd-bc7e-94419741e9d1`

**Audio FX:**
- ✅ EQ+: `e4815188-ba6f-4d14-bcfc-2dcb8f778ccb`
- ✅ Compressor: `2b1b4787-8d74-4138-877b-9197209eef0f` (FIXED)
- ✅ Delay+: `f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9`
- ✅ Reverb: `5a1cb339-1c4a-4cc7-9cae-bd7a2058153d`
- ✅ Filter: `4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42` (FIXED)
- ✅ Distortion: `b5b2b08e-730e-4192-be71-f572ceb5069b`

**Utility:**
- ✅ Tool: `e67b9c56-838d-4fba-8e3e-ae4e02cccbcb`
- ✅ Audio Receiver: `46b3e40a-629c-42c2-9e14-a1ccbcaa903b`
- ⚠️ Note Receiver: `c6153773-ed96-4cca-a767-5cf3d5dceacb` (not in DrivenByMoss)

## Files Updated

1. `bitwig-device-uuids.json` - Device UUID registry
2. `src/main/java/io/github/fabb/wigai/mcp/tool/BatchOperationsTool.java` - Tool documentation
3. Extension rebuilt and copied to Bitwig Extensions directory

## Next Steps

1. ✅ Restart Bitwig Studio to load the updated extension
2. Test batch_operations tool with corrected UUIDs
3. Consider adding more devices from DrivenByMoss registry

## DrivenByMoss Reference

DrivenByMoss maintains comprehensive device UUID lists at:
- `/src/main/resources/devices/AudioEffects.txt`
- `/src/main/resources/devices/Instruments.txt`

Total devices available:
- **160 Audio Effects** (Bitwig + VST2/VST3/CLAP)
- **174 Instruments** (Bitwig + VST2/VST3/CLAP)
