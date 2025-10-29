# Bitwig Device UUIDs for WigAI

This directory contains a comprehensive registry of device UUIDs for programmatic device insertion in Bitwig Studio via the Controller API.

## Files

- `bitwig-device-uuids-full.json` - Complete device UUID registry (auto-generated from DrivenByMoss)
- `bitwig-device-uuids.json` - Original minimal registry (kept for backwards compatibility)

## Device Categories

The full registry includes:

### Bitwig Native Devices
- **bitwig_instruments**: 21 native instruments (Polysynth, Sampler, Polymer, etc.)
- **bitwig_audio_fx**: 66 native audio effects (EQ+, Compressor, Reverb, etc.)

### Third-Party Plugins
- **clap_instruments**: CLAP format instruments (13 devices)
- **clap_audio_fx**: CLAP format effects
- **vst2_instruments**: VST2 format instruments (65 devices)
- **vst2_audio_fx**: VST2 format effects
- **vst3_instruments**: VST3 format instruments (169 devices)
- **vst3_audio_fx**: VST3 format effects

**Total: 334 devices**

## Usage with WigAI

### Insert a Bitwig Native Device

```javascript
wigai:insert_bitwig_device({
  track_index: 0,
  device_position: 0,
  device_uuid: "a9ffacb5-33e9-4fc7-8621-b1af31e410ef"  // Polysynth
})
```

### Common Bitwig Device UUIDs

```json
{
  "Polysynth": "a9ffacb5-33e9-4fc7-8621-b1af31e410ef",
  "Sampler": "468bc14b-b2e7-45a1-9666-e83117fe404e",
  "Polymer": "8f58138b-03aa-4e9d-83bd-a038c99a4ed5",
  "Drum Machine": "8ea97e45-0255-40fd-bc7e-94419741e9d1",
  "EQ+": "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb",
  "Compressor": "2b1b4787-8d74-4138-877b-9197209eef0f",
  "Delay+": "f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9",
  "Reverb": "5a1cb339-1c4a-4cc7-9cae-bd7a2058153d"
}
```

## Device Chain Example

```javascript
// Create a complete instrument chain
const track = 0;

// 1. Add Polysynth
wigai:insert_bitwig_device({
  track_index: track,
  device_position: 0,
  device_uuid: "a9ffacb5-33e9-4fc7-8621-b1af31e410ef"
});

// 2. Add EQ+
wigai:insert_bitwig_device({
  track_index: track,
  device_position: 1,
  device_uuid: "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb"
});

// 3. Add Reverb
wigai:insert_bitwig_device({
  track_index: track,
  device_position: 2,
  device_uuid: "5a1cb339-1c4a-4cc7-9cae-bd7a2058153d"
});
```

## Regenerating the Full Registry

If you need to update the device list:

```bash
python3 -c "
import json

# Parse device files from DrivenByMoss
instruments_txt = open('/path/to/DrivenByMoss/src/main/resources/devices/Instruments.txt', 'r').read()
effects_txt = open('/path/to/DrivenByMoss/src/main/resources/devices/AudioEffects.txt', 'r').read()

def parse_devices(text):
    devices = {'BITWIG': {}, 'CLAP': {}, 'VST2': {}, 'VST3': {}}
    for line in text.strip().split('\n'):
        if not line:
            continue
        parts = line.split('\$')
        if len(parts) == 3:
            plugin_type, name, uuid = parts
            devices[plugin_type][name] = uuid
    return devices

instruments = parse_devices(instruments_txt)
effects = parse_devices(effects_txt)

output = {
    'metadata': {
        'version': '2.0.0',
        'bitwig_version': '5.0+',
        'source': 'DrivenByMoss'
    },
    'devices': {
        'bitwig_instruments': instruments['BITWIG'],
        'bitwig_audio_fx': effects['BITWIG'],
        'clap_instruments': instruments['CLAP'],
        'clap_audio_fx': effects['CLAP'],
        'vst2_instruments': instruments['VST2'],
        'vst2_audio_fx': effects['VST2'],
        'vst3_instruments': instruments['VST3'],
        'vst3_audio_fx': effects['VST3']
    }
}

with open('bitwig-device-uuids-full.json', 'w') as f:
    json.dump(output, f, indent=2, sort_keys=True)
"
```

## Credits

- Device UUID registry sourced from [DrivenByMoss](https://github.com/git-moss/DrivenByMoss)
- Maintained by csabagodor for WigAI integration
- Updated: 2025-10-25

## Notes

- Only Bitwig native devices can be inserted via UUID
- Third-party plugins (CLAP, VST2, VST3) are listed for reference but require different insertion methods
- Device availability depends on your Bitwig Studio installation and installed plugins
