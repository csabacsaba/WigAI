package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.DeviceLayerFacade;
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
 * MCP tools for the layers of devices with layers (Instrument Layer, FX Layer, ...).
 * <p>
 * Note: Instrument Layer layers show up as separate channels in Bitwig's mixer but are not tracks,
 * so they cannot be reached with list_tracks / set_track_properties.
 */
public class DeviceLayerTool {

    private static final String SELECTION_NOTE =
        " The device is selected in Bitwig as a side effect. Use list_devices_on_track to find the device index.";

    public static McpServerFeatures.SyncToolSpecification listDeviceLayersSpecification(
            DeviceLayerFacade layerFacade,
            StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "device_index": {
                  "type": "integer",
                  "description": "Index of the device on the track (0-based), as returned by list_devices_on_track",
                  "minimum": 0
                }
              },
              "required": ["track_index", "device_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_device_layers")
            .description("List the layers of a device that has layers (e.g. Instrument Layer, FX Layer): "
                + "name, volume, pan, mute, solo and activated state per layer." + SELECTION_NOTE)
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "list_device_layers",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    int trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "list_device_layers");
                    int deviceIndex = ParameterValidator.validateRequiredInteger(args, "device_index", "list_device_layers");
                    return layerFacade.listLayers(trackIndex, deviceIndex);
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    public static McpServerFeatures.SyncToolSpecification setDeviceLayerPropertiesSpecification(
            DeviceLayerFacade layerFacade,
            StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "device_index": {
                  "type": "integer",
                  "description": "Index of the device on the track (0-based), as returned by list_devices_on_track",
                  "minimum": 0
                },
                "layer_index": {
                  "type": "integer",
                  "description": "Layer index (0-based), as returned by list_device_layers",
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
              "required": ["track_index", "device_index", "layer_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("set_device_layer_properties")
            .description("Set mute, solo, volume and/or pan of one layer of a device with layers "
                + "(e.g. Instrument Layer). All properties except the indices are optional." + SELECTION_NOTE)
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "set_device_layer_properties",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    String op = "set_device_layer_properties";
                    int trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", op);
                    int deviceIndex = ParameterValidator.validateRequiredInteger(args, "device_index", op);
                    int layerIndex = ParameterValidator.validateRequiredInteger(args, "layer_index", op);

                    Boolean mute = args.containsKey("mute") ? (Boolean) args.get("mute") : null;
                    Boolean solo = args.containsKey("solo") ? (Boolean) args.get("solo") : null;
                    Double volume = args.containsKey("volume") ? ((Number) args.get("volume")).doubleValue() : null;
                    Double pan = args.containsKey("pan") ? ((Number) args.get("pan")).doubleValue() : null;

                    layerFacade.setLayerProperties(trackIndex, deviceIndex, layerIndex, mute, solo, volume, pan);

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "device_layer_properties_set");
                    responseData.put("track_index", trackIndex);
                    responseData.put("device_index", deviceIndex);
                    responseData.put("layer_index", layerIndex);
                    if (mute != null) responseData.put("mute", mute);
                    if (solo != null) responseData.put("solo", solo);
                    if (volume != null) responseData.put("volume", volume);
                    if (pan != null) responseData.put("pan", pan);
                    responseData.put("message", "Successfully set device layer properties");
                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
