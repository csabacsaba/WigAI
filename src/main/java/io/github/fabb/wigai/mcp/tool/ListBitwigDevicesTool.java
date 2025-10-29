package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.DeviceRegistry;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool to list all available Bitwig devices with their UUIDs.
 * Returns the complete device registry loaded from bitwig-device-uuids-full.json
 * 
 * This tool provides:
 * - Bitwig native instruments and effects
 * - CLAP, VST2, and VST3 plugins (for reference)
 * 
 * Usage example:
 * {
 *   "name": "wigai_list_bitwig_devices",
 *   "arguments": {
 *     "category": "bitwig_instruments"  // optional: filter by category
 *   }
 * }
 */
public class ListBitwigDevicesTool {

    public static McpServerFeatures.SyncToolSpecification specification(StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "category": {
                  "type": "string",
                  "description": "Optional: filter by device category (e.g., 'bitwig_instruments', 'bitwig_audio_fx')"
                }
              }
            }""";
        
        var tool = McpSchema.Tool.builder()
            .name("wigai_list_bitwig_devices")
            .description("List all available Bitwig devices with their UUIDs from the device registry. " +
                        "Optionally filter by category: bitwig_instruments, bitwig_audio_fx, clap_instruments, " +
                        "clap_audio_fx, vst2_instruments, vst2_audio_fx, vst3_instruments, vst3_audio_fx")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "wigai_list_bitwig_devices",
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        DeviceRegistry deviceRegistry = DeviceRegistry.getInstance();
                        
                        // Extract arguments
                        Map<String, Object> args = req.arguments();
                        String category = args != null ? (String) args.get("category") : null;

                        Map<String, Object> responseData = new LinkedHashMap<>();
                        
                        if (category != null && !category.isEmpty()) {
                            // Return specific category
                            Map<String, String> devices = deviceRegistry.getDevicesByCategory(category);
                            if (devices == null) {
                                responseData.put("status", "error");
                                responseData.put("message", "Unknown category: " + category);
                                responseData.put("available_categories", List.of(
                                    "bitwig_instruments", "bitwig_audio_fx",
                                    "clap_instruments", "clap_audio_fx",
                                    "vst2_instruments", "vst2_audio_fx",
                                    "vst3_instruments", "vst3_audio_fx"
                                ));
                            } else {
                                responseData.put("status", "success");
                                responseData.put("category", category);
                                responseData.put("count", devices.size());
                                responseData.put("devices", devices);
                            }
                        } else {
                            // Return all devices with statistics
                            responseData.put("status", "success");
                            
                            Map<String, Integer> stats = new LinkedHashMap<>();
                            stats.put("bitwig_instruments", deviceRegistry.getBitwigInstruments().size());
                            stats.put("bitwig_audio_fx", deviceRegistry.getBitwigAudioFx().size());
                            stats.put("clap_instruments", deviceRegistry.getClapInstruments().size());
                            stats.put("clap_audio_fx", deviceRegistry.getClapAudioFx().size());
                            stats.put("vst2_instruments", deviceRegistry.getVst2Instruments().size());
                            stats.put("vst2_audio_fx", deviceRegistry.getVst2AudioFx().size());
                            stats.put("vst3_instruments", deviceRegistry.getVst3Instruments().size());
                            stats.put("vst3_audio_fx", deviceRegistry.getVst3AudioFx().size());
                            
                            responseData.put("statistics", stats);
                            responseData.put("all_devices", deviceRegistry.getAllDevices());
                        }

                        return responseData;
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}