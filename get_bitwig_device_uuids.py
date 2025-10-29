#!/usr/bin/env python3
"""
Script to extract Bitwig device UUIDs by opening Bitwig and copying device IDs.
Prerequisites: config.json must have "can-copy-device-and-param-ids": true
"""

import subprocess
import time
import pyperclip
import json

# List of common Bitwig devices to extract UUIDs from
BITWIG_DEVICES = [
    "EQ+",
    "Polysynth",
    "Phase-4",
    "FM-4",
    "Sampler",
    "Delay+",
    "Compressor+",
    "Limiter",
    "Reverb",
    "Chorus",
    "Flanger",
    "Phaser",
    "Distortion",
    "Filter+",
    "Tool",
    "Poly Grid",
    "FX Grid",
    "Note Grid",
    "Polymer",
    "Drum Machine",
    "Audio Receiver",
    "Note Receiver",
    "HW Instrument",
    "Clip Launcher",
]

def main():
    print("Bitwig Device UUID Extractor")
    print("=" * 50)
    print("This script requires manual interaction:")
    print("1. Open Bitwig Studio")
    print("2. Create a new project")
    print("3. For each device, add it to a track")
    print("4. Right-click the device → Copy Device ID")
    print("5. The UUID will be automatically captured")
    print()
    print("Press Enter when ready to start...")
    input()
    
    device_uuids = {}
    
    for device_name in BITWIG_DEVICES:
        print(f"\n📌 Device: {device_name}")
        print(f"   1. Add '{device_name}' to a track in Bitwig")
        print(f"   2. Right-click on it → 'Copy Device ID'")
        print(f"   3. Press Enter when done (or 's' to skip)")
        
        user_input = input("   > ")
        
        if user_input.lower() == 's':
            print(f"   ⏭️  Skipped {device_name}")
            continue
        
        # Get UUID from clipboard
        try:
            uuid = pyperclip.paste().strip()
            if uuid and len(uuid) == 36:  # UUID format check
                device_uuids[device_name] = uuid
                print(f"   ✅ Captured: {uuid}")
            else:
                print(f"   ❌ Invalid UUID format: {uuid}")
        except Exception as e:
            print(f"   ❌ Error reading clipboard: {e}")
    
    # Save to JSON file
    output_file = "bitwig_device_uuids.json"
    with open(output_file, 'w') as f:
        json.dump(device_uuids, f, indent=2)
    
    print(f"\n✅ Saved {len(device_uuids)} UUIDs to {output_file}")
    print(json.dumps(device_uuids, indent=2))

if __name__ == "__main__":
    main()
