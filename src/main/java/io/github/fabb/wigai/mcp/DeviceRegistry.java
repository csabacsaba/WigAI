package io.github.fabb.wigai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Singleton registry for Bitwig device UUIDs.
 * 
 * Loads device information from bitwig-device-uuids-full.json at initialization.
 * Provides access to device UUIDs by category (instruments, audio_fx, etc.)
 * 
 * Categories:
 * - bitwig_instruments: Native Bitwig instruments
 * - bitwig_audio_fx: Native Bitwig audio effects
 * - clap_instruments: CLAP format instruments
 * - clap_audio_fx: CLAP format effects
 * - vst2_instruments: VST2 format instruments
 * - vst2_audio_fx: VST2 format effects
 * - vst3_instruments: VST3 format instruments
 * - vst3_audio_fx: VST3 format effects
 */
public class DeviceRegistry {
    private static DeviceRegistry instance;
    
    private final Map<String, String> bitwigInstruments = new HashMap<>();
    private final Map<String, String> bitwigAudioFx = new HashMap<>();
    private final Map<String, String> clapInstruments = new HashMap<>();
    private final Map<String, String> clapAudioFx = new HashMap<>();
    private final Map<String, String> vst2Instruments = new HashMap<>();
    private final Map<String, String> vst2AudioFx = new HashMap<>();
    private final Map<String, String> vst3Instruments = new HashMap<>();
    private final Map<String, String> vst3AudioFx = new HashMap<>();
    
    private DeviceRegistry() {
        loadDevices();
    }
    
    public static synchronized DeviceRegistry getInstance() {
        if (instance == null) {
            instance = new DeviceRegistry();
        }
        return instance;
    }
    
    private void loadDevices() {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("bitwig-device-uuids-full.json");
            if (is == null) {
                System.err.println("ERROR: bitwig-device-uuids-full.json not found in resources!");
                return;
            }
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);
            JsonNode devices = root.get("devices");
            
            if (devices != null) {
                loadCategory(devices, "bitwig_instruments", bitwigInstruments);
                loadCategory(devices, "bitwig_audio_fx", bitwigAudioFx);
                loadCategory(devices, "clap_instruments", clapInstruments);
                loadCategory(devices, "clap_audio_fx", clapAudioFx);
                loadCategory(devices, "vst2_instruments", vst2Instruments);
                loadCategory(devices, "vst2_audio_fx", vst2AudioFx);
                loadCategory(devices, "vst3_instruments", vst3Instruments);
                loadCategory(devices, "vst3_audio_fx", vst3AudioFx);
            }
            
            System.out.println("✅ Device Registry loaded:");
            System.out.println("  - Bitwig Instruments: " + bitwigInstruments.size());
            System.out.println("  - Bitwig Audio FX: " + bitwigAudioFx.size());
            System.out.println("  - Total: " + getTotalDeviceCount());
            
        } catch (Exception e) {
            System.err.println("ERROR loading device registry: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void loadCategory(JsonNode devices, String categoryName, Map<String, String> targetMap) {
        JsonNode category = devices.get(categoryName);
        if (category != null && category.isObject()) {
            category.fields().forEachRemaining(entry -> {
                targetMap.put(entry.getKey(), entry.getValue().asText());
            });
        }
    }
    
    public Map<String, String> getBitwigInstruments() {
        return new HashMap<>(bitwigInstruments);
    }
    
    public Map<String, String> getBitwigAudioFx() {
        return new HashMap<>(bitwigAudioFx);
    }
    
    public Map<String, String> getClapInstruments() {
        return new HashMap<>(clapInstruments);
    }
    
    public Map<String, String> getClapAudioFx() {
        return new HashMap<>(clapAudioFx);
    }
    
    public Map<String, String> getVst2Instruments() {
        return new HashMap<>(vst2Instruments);
    }
    
    public Map<String, String> getVst2AudioFx() {
        return new HashMap<>(vst2AudioFx);
    }
    
    public Map<String, String> getVst3Instruments() {
        return new HashMap<>(vst3Instruments);
    }
    
    public Map<String, String> getVst3AudioFx() {
        return new HashMap<>(vst3AudioFx);
    }
    
    public Map<String, String> getDevicesByCategory(String category) {
        switch (category) {
            case "bitwig_instruments": return getBitwigInstruments();
            case "bitwig_audio_fx": return getBitwigAudioFx();
            case "clap_instruments": return getClapInstruments();
            case "clap_audio_fx": return getClapAudioFx();
            case "vst2_instruments": return getVst2Instruments();
            case "vst2_audio_fx": return getVst2AudioFx();
            case "vst3_instruments": return getVst3Instruments();
            case "vst3_audio_fx": return getVst3AudioFx();
            default: return null;
        }
    }
    
    public Map<String, Map<String, String>> getAllDevices() {
        Map<String, Map<String, String>> all = new HashMap<>();
        all.put("bitwig_instruments", getBitwigInstruments());
        all.put("bitwig_audio_fx", getBitwigAudioFx());
        all.put("clap_instruments", getClapInstruments());
        all.put("clap_audio_fx", getClapAudioFx());
        all.put("vst2_instruments", getVst2Instruments());
        all.put("vst2_audio_fx", getVst2AudioFx());
        all.put("vst3_instruments", getVst3Instruments());
        all.put("vst3_audio_fx", getVst3AudioFx());
        return all;
    }
    
    public int getTotalDeviceCount() {
        return bitwigInstruments.size() + bitwigAudioFx.size() +
               clapInstruments.size() + clapAudioFx.size() +
               vst2Instruments.size() + vst2AudioFx.size() +
               vst3Instruments.size() + vst3AudioFx.size();
    }
}