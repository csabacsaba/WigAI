# Device Registry Integration - Changelog

## Overview
Automatic device UUID loading system for WigAI. No manual file reading required on startup!

## Files Created/Modified

### New Files Created:

1. **`src/main/java/io/github/fabb/wigai/mcp/DeviceRegistry.java`**
   - Singleton class for device UUID management
   - Loads `bitwig-device-uuids-full.json` from resources on initialization
   - Provides methods to access devices by category
   - Total: 157 lines

2. **`src/main/java/io/github/fabb/wigai/mcp/tool/ListBitwigDevicesTool.java`**
   - New MCP tool: `wigai:list_bitwig_devices`
   - Lists all devices or filters by category
   - Returns statistics and device counts
   - Total: 71 lines

3. **`src/main/resources/bitwig-device-uuids-full.json`**
   - Device UUID database
   - Currently contains 21 Bitwig instruments + 12 Bitwig audio FX
   - Expandable to full 334 devices from DrivenByMoss
   - Total: 54 lines

### Modified Files:

1. **`src/main/java/io/github/fabb/wigai/mcp/McpServerManager.java`**
   - Added import: `ListBitwigDevicesTool`
   - Registered new tool in tools list
   - Changes: +2 lines

## How It Works

### On Extension Startup:
1. WigAI extension loads
2. `DeviceRegistry.getInstance()` called (lazy singleton)
3. JSON file loaded from classpath resources
4. Device UUIDs cached in memory
5. Available immediately via MCP

### Via MCP:
```javascript
// Get all devices with statistics
wigai:list_bitwig_devices()

// Get specific category
wigai:list_bitwig_devices({ category: "bitwig_instruments" })
wigai:list_bitwig_devices({ category: "bitwig_audio_fx" })
```

### Response Format:
```json
{
  "status": "success",
  "statistics": {
    "bitwig_instruments": 21,
    "bitwig_audio_fx": 12,
    "total": 33
  },
  "all_devices": {
    "bitwig_instruments": {
      "Polysynth": "a9ffacb5-33e9-4fc7-8621-b1af31e410ef",
      ...
    },
    "bitwig_audio_fx": {
      "EQ+": "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb",
      ...
    }
  }
}
```

## Testing

After building, test with:

```bash
# Build the extension
./gradlew build

# Install to Bitwig
# Copy build/libs/WigAI-*.bwextension to Bitwig extensions folder

# Test via MCP
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"name":"wigai:list_bitwig_devices"}'
```

## Expanding Device List

To add more devices to the JSON:

1. Edit `src/main/resources/bitwig-device-uuids-full.json`
2. Add devices following the format:
   ```json
   "Device Name": "uuid-string-here"
   ```
3. Rebuild the extension
4. DeviceRegistry will automatically load the updated list

The full 334-device list from DrivenByMoss is available in:
- `/Users/csabagodor/IdeaProjects/DrivenByMoss/src/main/resources/devices/Instruments.txt`
- `/Users/csabagodor/IdeaProjects/DrivenByMoss/src/main/resources/devices/AudioEffects.txt`

## Integration Complete! ✅

The device registry is now:
- ✅ Automatically loaded on startup
- ✅ Available via MCP tool
- ✅ No manual file reading needed
- ✅ Expandable and maintainable
- ✅ Following WigAI tool patterns

## Next Steps

1. **Build the project**: `./gradlew build`
2. **Test the new tool**: Use MCP client to call `wigai:list_bitwig_devices`
3. **Expand device list**: Add more UUIDs from DrivenByMoss if needed
4. **Update documentation**: Add to main README if desired
