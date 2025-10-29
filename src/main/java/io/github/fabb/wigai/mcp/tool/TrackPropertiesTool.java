package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Tool for setting track properties (mute, solo, arm, volume, pan, sends) in Bitwig Studio.
 */
public class TrackPropertiesTool {

    /**
     * Creates the MCP tool specification for setting track properties.
     */
    public static McpServerFeatures.SyncToolSpecification setTrackPropertiesSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var setTrackPropertiesSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "mute": {
                  "type": "boolean",
                  "description": "Mute state (optional)"
                },
                "solo": {
                  "type": "boolean",
                  "description": "Solo state (optional)"
                },
                "arm": {
                  "type": "boolean",
                  "description": "Arm/record enable state (optional)"
                },
                "volume": {
                  "type": "number",
                  "description": "Volume level 0.0-1.0 (optional)",
                  "minimum": 0.0,
                  "maximum": 1.0
                },
                "pan": {
                  "type": "number",
                  "description": "Pan position 0.0-1.0, where 0.5 is center (optional)",
                  "minimum": 0.0,
                  "maximum": 1.0
                }
              },
              "required": ["track_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("set_track_properties")
            .description("Set track properties: mute, solo, arm, volume, pan. All properties except track_index are optional.")
            .inputSchema(setTrackPropertiesSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "set_track_properties",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "set_track_properties");
                    
                    // All other properties are optional
                    Boolean mute = args.containsKey("mute") ? (Boolean) args.get("mute") : null;
                    Boolean solo = args.containsKey("solo") ? (Boolean) args.get("solo") : null;
                    Boolean arm = args.containsKey("arm") ? (Boolean) args.get("arm") : null;
                    
                    Double volume = null;
                    if (args.containsKey("volume")) {
                        Object volumeObj = args.get("volume");
                        volume = volumeObj instanceof Integer ? ((Integer) volumeObj).doubleValue() : (Double) volumeObj;
                    }
                    
                    Double pan = null;
                    if (args.containsKey("pan")) {
                        Object panObj = args.get("pan");
                        pan = panObj instanceof Integer ? ((Integer) panObj).doubleValue() : (Double) panObj;
                    }

                    // Apply the properties
                    bitwigApiFacade.setTrackProperties(trackIndex, mute, solo, arm, volume, pan);

                    // Build response
                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "track_properties_set");
                    responseData.put("track_index", trackIndex);
                    
                    if (mute != null) responseData.put("mute", mute);
                    if (solo != null) responseData.put("solo", solo);
                    if (arm != null) responseData.put("arm", arm);
                    if (volume != null) responseData.put("volume", volume);
                    if (pan != null) responseData.put("pan", pan);
                    
                    responseData.put("message", "Successfully set track properties");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for setting track send properties.
     */
    public static McpServerFeatures.SyncToolSpecification setTrackSendSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var setTrackSendSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "send_index": {
                  "type": "integer",
                  "description": "Send index (0-based)",
                  "minimum": 0
                },
                "volume": {
                  "type": "number",
                  "description": "Send volume level 0.0-1.0 (optional)",
                  "minimum": 0.0,
                  "maximum": 1.0
                },
                "enabled": {
                  "type": "boolean",
                  "description": "Send enabled/active state (optional)"
                }
              },
              "required": ["track_index", "send_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("set_track_send")
            .description("Set track send properties: volume and enabled state. Send goes to FX/Return tracks.")
            .inputSchema(setTrackSendSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "set_track_send",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "set_track_send");
                    Integer sendIndex = ParameterValidator.validateRequiredInteger(args, "send_index", "set_track_send");
                    
                    Double volume = null;
                    if (args.containsKey("volume")) {
                        Object volumeObj = args.get("volume");
                        volume = volumeObj instanceof Integer ? ((Integer) volumeObj).doubleValue() : (Double) volumeObj;
                    }
                    
                    Boolean enabled = args.containsKey("enabled") ? (Boolean) args.get("enabled") : null;

                    // Apply the send properties
                    bitwigApiFacade.setTrackSend(trackIndex, sendIndex, volume, enabled);

                    // Build response
                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "track_send_set");
                    responseData.put("track_index", trackIndex);
                    responseData.put("send_index", sendIndex);
                    
                    if (volume != null) responseData.put("volume", volume);
                    if (enabled != null) responseData.put("enabled", enabled);
                    
                    responseData.put("message", "Successfully set send properties");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
