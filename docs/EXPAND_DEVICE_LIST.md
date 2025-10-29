# How to Add Full Device List (334 Devices)

## Current Status
✅ Integration complete with 33 devices (21 instruments + 12 effects)
⚠️  Optional: Expand to full 334 devices from DrivenByMoss

## Quick Method - Copy from DrivenByMoss

If you want all 334 devices, run this Python script in the WigAI project root:

```python
#!/usr/bin/env python3
import json

# Read DrivenByMoss device files
instruments_txt = open('/Users/csabagodor/IdeaProjects/DrivenByMoss/src/main/resources/devices/Instruments.txt', 'r').read()
effects_txt = open('/Users/csabagodor/IdeaProjects/DrivenByMoss/src/main/resources/devices/AudioEffects.txt', 'r').read()

def parse_devices(text):
    devices = {'BITWIG': {}, 'CLAP': {}, 'VST2': {}, 'VST3': {}}
    for line in text.strip().split('\n'):
        if not line:
            continue
        parts = line.split('$')
        if len(parts) == 3:
            plugin_type, name, uuid = parts
            devices[plugin_type][name] = uuid
    return devices

instruments = parse_devices(instruments_txt)
effects = parse_devices(effects_txt)

output = {
    "metadata": {
        "version": "2.0.0",
        "bitwig_version": "5.0+",
        "last_updated": "2025-10-25",
        "source": "DrivenByMoss device registry",
        "notes": "Complete registry of Bitwig, CLAP, VST2, and VST3 device UUIDs"
    },
    "devices": {
        "bitwig_instruments": instruments['BITWIG'],
        "bitwig_audio_fx": effects['BITWIG'],
        "clap_instruments": instruments['CLAP'],
        "clap_audio_fx": effects['CLAP'],
        "vst2_instruments": instruments['VST2'],
        "vst2_audio_fx": effects['VST2'],
        "vst3_instruments": instruments['VST3'],
        "vst3_audio_fx": effects['VST3']
    }
}

with open('src/main/resources/bitwig-device-uuids-full.json', 'w') as f:
    json.dump(output, f, indent=2, sort_keys=True)

print(f"✅ Generated full device list:")
print(f"  - Bitwig Instruments: {len(instruments['BITWIG'])}")
print(f"  - Bitwig Audio FX: {len(effects['BITWIG'])}")
print(f"  - CLAP Instruments: {len(instruments['CLAP'])}")
print(f"  - CLAP Audio FX: {len(effects['CLAP'])}")
print(f"  - VST2 Instruments: {len(instruments['VST2'])}")
print(f"  - VST2 Audio FX: {len(effects['VST2'])}")
print(f"  - VST3 Instruments: {len(instruments['VST3'])}")
print(f"  - VST3 Audio FX: {len(effects['VST3'])}")
print(f"  - Total: {sum([len(v) for v in output['devices'].values()])}")
```

Save as `scripts/generate_full_device_list.py` and run:
```bash
cd /Users/csabagodor/IdeaProjects/WigAI
python3 scripts/generate_full_device_list.py
./gradlew build
```

## Manual Method

Edit `src/main/resources/bitwig-device-uuids-full.json` and add devices manually following this format:

```json
{
  "devices": {
    "bitwig_instruments": {
      "Device Name": "uuid-here"
    }
  }
}
```

## Notes

- Current 33 devices are enough for basic testing
- Full list adds VST2/VST3 plugins (reference only, can't be inserted via UUID)
- Only Bitwig native devices can be inserted via `wigai:insert_bitwig_device`
- CLAP/VST plugins listed for reference/future use

## Priority Bitwig Devices

If you want to add more selectively, these are the most commonly used:

**Instruments:**
- ✅ Polysynth
- ✅ Sampler  
- ✅ Polymer
- ✅ Drum Machine
- Phase-4
- FM-4
- Poly Grid

**Effects:**
- ✅ EQ+
- ✅ Reverb
- ✅ Compressor
- ✅ Delay+
- Gate
- Limiter
- Transient Control
