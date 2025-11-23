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
 * MCP Tool for inserting devices in Bitwig Studio using UUID-based matching.
 */
public class DeviceInsertTool {

    /**
     * Creates the MCP tool specification for inserting Bitwig devices by UUID.
     */
    public static McpServerFeatures.SyncToolSpecification insertBitwigDeviceSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var insertBitwigDeviceSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "device_position": {
                  "type": "integer",
                  "description": "Position in device chain (0 = first position)",
                  "minimum": 0
                },
                "device_uuid": {
                  "type": "string",
                  "description": "UUID of the Bitwig device (e.g., '2d1e8daf-38d6-4d9b-90cc-1f0c0a3e4c3b' for EQ+)",
                  "minLength": 36,
                  "maxLength": 36
                }
              },
              "required": ["track_index", "device_position", "device_uuid"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("insert_bitwig_device")
            .description("""
                Insert a Bitwig native device by UUID at a specific position on a track. \
                \
                IMPORTANT: You MUST call 'get_device_knowledge' tool FIRST to get the correct UUID for the device you want to insert! \
                Do NOT guess or make up UUIDs - they will not work! \
                \
                Available devices (call get_device_knowledge for UUIDs): \
                - EQ+ (UUID: e4815188-ba6f-4d14-bcfc-2dcb8f778ccb) \
                - Filter (UUID: 4ccfc70e-59bd-4e97-a8a7-d8cdce88bf42) \
                - Compressor (UUID: 2b1b4787-8d74-4138-877b-9197209eef0f) \
                - Delay+ (UUID: f2baa2a8-36c5-4a79-b1d9-a4e461c45ee9) \
                - Reverb (UUID: 5a1cb339-1c4a-4cc7-9cae-bd7a2058153d)
                """)
            .inputSchema(insertBitwigDeviceSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "insert_bitwig_device",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "insert_bitwig_device");
                    Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "insert_bitwig_device");
                    String deviceUuid = ParameterValidator.validateRequiredString(args, "device_uuid", "insert_bitwig_device");

                    // Try to insert the Bitwig device
                    boolean success = bitwigApiFacade.insertBitwigDeviceByUuid(trackIndex, devicePosition, deviceUuid);

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    if (success) {
                        responseData.put("action", "device_inserted");
                        responseData.put("track_index", trackIndex);
                        responseData.put("device_position", devicePosition);
                        responseData.put("device_uuid", deviceUuid);
                        responseData.put("message", String.format("Inserted Bitwig device (UUID: %s) at track %d, position %d",
                                                                  deviceUuid, trackIndex, devicePosition));
                    } else {
                        responseData.put("action", "device_insert_failed");
                        responseData.put("track_index", trackIndex);
                        responseData.put("device_position", devicePosition);
                        responseData.put("device_uuid", deviceUuid);
                        responseData.put("message", "Device insertion not supported by current Bitwig API version or invalid UUID");
                    }

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
