# WigAI Device Pages Parameters Tools

## Overview

New tools for efficiently reading and writing ALL device page parameters in a single call, with automatic server-side page switching.

## Problem Solved

**Before:** Had to manually switch pages and copy parameters one by one using timer-based polling
- Multiple API calls needed
- Page switching delays
- No aggregated data
- Error-prone manual page management

**After:** Single call gets/sets all pages with aggregated JSON
- One API call per operation
- Automatic page switching on server
- Clean, aggregated response
- Poll/Push compatible

---

## Tool 1: `get_all_device_pages_parameters`

### Purpose
Reads ALL page parameters from a device (Modes, Gains, Freqs, Qs, etc.) in a single call.

### Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `track_index` | integer | Yes | 0-based track index |
| `device_position` | integer | Yes | 0-based device position on track |

### Response Format
```json
{
  "device": "EQ+",
  "track_index": 0,
  "device_position": 0,
  "page_count": 4,
  "pages": [
    {
      "page_index": 0,
      "page_name": "Modes",
      "parameters": [
        {
          "index": 0,
          "name": "1 Mode",
          "value": 0.14285714285714285,
          "displayedValue": "Low-cut 2P"
        },
        {
          "index": 1,
          "name": "2 Mode",
          "value": 0.5,
          "displayedValue": "Bell"
        }
      ]
    },
    {
      "page_index": 1,
      "page_name": "Gains",
      "parameters": [
        {
          "index": 0,
          "name": "1 Gain",
          "value": 0.5,
          "displayedValue": "0.0 dB"
        }
      ]
    }
  ]
}
```

### Code Example (JavaScript)
```javascript
// Read all pages from track 0, EQ+ device
const config = await wigai.get_all_device_pages_parameters({
  track_index: 0,
  device_position: 0
});

console.log(`Device: ${config.device}`);
console.log(`Total pages: ${config.page_count}`);

// Iterate through all pages
config.pages.forEach(page => {
  console.log(`Page ${page.page_index} (${page.page_name}):`);
  page.parameters.forEach(param => {
    console.log(`  - ${param.name}: ${param.value} (${param.displayedValue})`);
  });
});
```

### Internal Implementation (Java)
```java
// Reading actual device parameter values from Bitwig API
RemoteControl param = deviceParameterBank.getParameter(i);
paramInfo.put("value", param.value().get());          // ← READS FROM BITWIG
paramInfo.put("displayedValue", param.displayedValue().get());

// Server automatically switches pages
for (int pageIndex = 0; pageIndex < pageNames.size(); pageIndex++) {
    bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
    List<Map<String, Object>> parameters = bitwigApiFacade.getDevicePageParameters(trackIndex, devicePosition);
    // ... collect into aggregated response
}
```

---

## Tool 2: `set_all_device_pages_parameters`

### Purpose
Writes ALL page parameters to a device in a single call, automatically switching pages.

### Parameters
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `track_index` | integer | Yes | 0-based track index |
| `device_position` | integer | Yes | 0-based device position on track |
| `pages` | array | Yes | Array of page objects with parameters to set |

### Input Format
```json
{
  "track_index": 1,
  "device_position": 0,
  "pages": [
    {
      "page_index": 0,
      "page_name": "Modes",
      "parameters": [
        {"index": 0, "value": 0.14285714285714285},
        {"index": 1, "value": 0.5}
      ]
    },
    {
      "page_index": 1,
      "page_name": "Gains",
      "parameters": [
        {"index": 0, "value": 0.5},
        {"index": 1, "value": 0.5}
      ]
    }
  ]
}
```

### Response Format
```json
{
  "device": "EQ+",
  "track_index": 1,
  "device_position": 0,
  "pages_updated": 4,
  "pages": [
    {
      "page_index": 0,
      "page_name": "Modes",
      "parameters_set": 8
    },
    {
      "page_index": 1,
      "page_name": "Gains",
      "parameters_set": 8
    }
  ]
}
```

### Code Example (JavaScript)
```javascript
// 1. Read all pages from source device (track 0)
const sourceConfig = await wigai.get_all_device_pages_parameters({
  track_index: 0,
  device_position: 0
});

// 2. Apply configuration to destination device (track 1)
const result = await wigai.set_all_device_pages_parameters({
  track_index: 1,
  device_position: 0,
  pages: sourceConfig.pages
});

console.log(`✅ Successfully copied ${result.pages_updated} pages from track 0 to track 1!`);

// 3. Verify the copy worked
const destConfig = await wigai.get_all_device_pages_parameters({
  track_index: 1,
  device_position: 0
});

// Check if they match
const match = JSON.stringify(sourceConfig.pages) === JSON.stringify(destConfig.pages);
console.log(`Configuration match: ${match}`);
```

### Internal Implementation (Java)
```java
// Writing actual device parameter values to Bitwig API
for (Map<String, Object> param : parameters) {
    Integer paramIndex = ((Number) param.get("parameter_index")).intValue();
    Double paramValue = ((Number) param.get("value")).doubleValue();
    
    if (paramIndex >= 0 && paramIndex < deviceParameterBank.getParameterCount()) {
        RemoteControl remote = deviceParameterBank.getParameter(paramIndex);
        remote.value().set(paramValue);  // ← WRITES TO BITWIG
    }
}

// Server automatically switches pages
bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
bitwigApiFacade.setDevicePageParameters(trackIndex, devicePosition, paramsToSet);
```

---

## Complete Workflow Example

### Copy EQ+ settings from track 0 to track 1

```javascript
// 1. Read entire configuration from source
console.log("📖 Reading EQ+ configuration from track 0...");
const sourceConfig = await wigai.get_all_device_pages_parameters({
  track_index: 0,
  device_position: 0
});

console.log(`Found device: ${sourceConfig.device}`);
console.log(`Pages: ${sourceConfig.pages.map(p => p.page_name).join(", ")}`);

// 2. Apply to destination
console.log("✍️ Applying configuration to track 1...");
const copyResult = await wigai.set_all_device_pages_parameters({
  track_index: 1,
  device_position: 0,
  pages: sourceConfig.pages
});

console.log(`✅ Updated ${copyResult.pages_updated} pages`);

// 3. Verify success
console.log("🔍 Verifying...");
const destConfig = await wigai.get_all_device_pages_parameters({
  track_index: 1,
  device_position: 0
});

const isIdentical = sourceConfig.pages.every((srcPage, idx) => {
  const destPage = destConfig.pages[idx];
  return srcPage.parameters.every((param, pIdx) => 
    param.value === destPage.parameters[pIdx].value
  );
});

console.log(`Result: ${isIdentical ? "✅ IDENTICAL" : "⚠️ DIFFERENT"}`);
```

---

## Important Notes

### Value Normalization
- All parameter `value` fields are **normalized between 0.0 and 1.0**
- Do NOT use raw values like Hz or dB
- The `displayedValue` field shows the human-readable representation

### Page Switching
- Server automatically handles all page switching
- No manual page management needed
- Uses caching to avoid unnecessary switches

### Performance
- Single API call to read all pages
- Single API call to write all pages
- Much faster than manual page-by-page copying
- Suitable for real-time syncing with poll/push mechanisms

### Thread Safety
- Includes Thread.sleep() calls between operations for Bitwig API stability
- Safe for repeated calls
- Proper error handling with BitwigApiException

---

## Integration with BitwigApiFacade

Three new methods added:

```java
public List<Map<String, Object>> getDevicePageParameters(int trackIndex, int deviceIndex)
    // Reads all parameters from current page

public String getDeviceNameOnTrack(int trackIndex, int deviceIndex)
    // Gets the human-readable device name

public void setDevicePageParameters(int trackIndex, int deviceIndex, List<Map<String, Object>> parameters)
    // Writes parameters to current page
```

All use the existing `deviceParameterBank` mechanism and thread-safe device selection.
