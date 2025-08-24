package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for retrieving detailed device information in Bitwig using unified error handling architecture.
 * Implements the get_device_details MCP command as specified in the API reference.
 */
public class GetDeviceDetailsTool {

    private static final String TOOL_NAME = "get_device_details";

    /**
     * Creates the MCP tool specification for device details retrieval.
     *
     * @param deviceController The controller for device operations
     * @param logger The structured logger for operation logging
     * @return MCP tool specification
     */
    public static McpServerFeatures.SyncToolSpecification getDeviceDetailsSpecification(
            DeviceController deviceController, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the track containing the device"
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the track containing the device (case-sensitive)"
                },
                "device_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the device on its track"
                },
                "device_name": {
                  "type": "string",
                  "description": "Name of the device (case-sensitive)"
                },
                "get_for_selected_device": {
                  "type": "boolean",
                  "description": "If true, retrieve details for the currently selected device. Defaults to true if no other identifiers are provided."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Get detailed information for a specific device (by track context + device context, or the currently selected device), including context, states, 8 remote controls (for current page), and 8 remote control pages (with selection state)")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        // Parse and validate arguments
                        GetDeviceDetailsArguments args = parseArguments(req.arguments());

                        // Perform device details retrieval operation
                        var result = deviceController.getDeviceDetails(
                            args.trackIndex(),
                            args.trackName(),
                            args.deviceIndex(),
                            args.deviceName(),
                            args.getForSelectedDevice()
                        );

                        return result.toMap();
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Parses the MCP tool arguments into a structured format.
     *
     * @param arguments Raw arguments map from MCP request
     * @return Parsed and validated GetDeviceDetailsArguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static GetDeviceDetailsArguments parseArguments(Map<String, Object> arguments) {
        // Extract optional parameters
        Integer trackIndex = null;
        String trackName = null;
        Integer deviceIndex = null;
        String deviceName = null;
        Boolean getForSelectedDevice = null;

        if (arguments.containsKey("track_index")) {
            trackIndex = ParameterValidator.validateRequiredInteger(arguments, "track_index", TOOL_NAME);
            if (trackIndex < 0) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, TOOL_NAME,
                    "track_index must be non-negative, got: " + trackIndex);
            }
        }

        if (arguments.containsKey("track_name")) {
            trackName = ParameterValidator.validateRequiredString(arguments, "track_name", TOOL_NAME);
            trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", TOOL_NAME);
        }

        if (arguments.containsKey("device_index")) {
            deviceIndex = ParameterValidator.validateRequiredInteger(arguments, "device_index", TOOL_NAME);
            if (deviceIndex < 0) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, TOOL_NAME,
                    "device_index must be non-negative, got: " + deviceIndex);
            }
        }

        if (arguments.containsKey("device_name")) {
            deviceName = ParameterValidator.validateRequiredString(arguments, "device_name", TOOL_NAME);
            deviceName = ParameterValidator.validateNotEmpty(deviceName, "device_name", TOOL_NAME);
        }

        if (arguments.containsKey("get_for_selected_device")) {
            Object value = arguments.get("get_for_selected_device");
            if (!(value instanceof Boolean)) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, TOOL_NAME,
                    "get_for_selected_device must be a boolean");
            }
            getForSelectedDevice = (Boolean) value;
        }

        // Apply parameter validation rules per story requirements
        validateParameterRules(trackIndex, trackName, deviceIndex, deviceName, getForSelectedDevice);

        return new GetDeviceDetailsArguments(trackIndex, trackName, deviceIndex, deviceName, getForSelectedDevice);
    }

    /**
     * Validates parameter combinations according to story requirements.
     */
    private static void validateParameterRules(Integer trackIndex, String trackName,
                                             Integer deviceIndex, String deviceName,
                                             Boolean getForSelectedDevice) {
        boolean hasIdentifiers = trackIndex != null || trackName != null || deviceIndex != null || deviceName != null;
        boolean wantsSelected = Boolean.TRUE.equals(getForSelectedDevice);

        // Rule: Providing get_for_selected_device=true together with any identifier is invalid
        if (wantsSelected && hasIdentifiers) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, TOOL_NAME,
                "Cannot provide get_for_selected_device=true together with other identifier parameters");
        }

        // Rule: If get_for_selected_device=false (or omitted) and no identifiers are provided → INVALID_PARAMETER
        if (Boolean.FALSE.equals(getForSelectedDevice) && !hasIdentifiers) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, TOOL_NAME,
                "Must provide identifier parameters when get_for_selected_device=false");
        }

        // If identifiers are provided, validate mutual exclusivity
        if (hasIdentifiers) {
            // Rule: Exactly one of track_index OR track_name must be provided; not both
            if ((trackIndex != null && trackName != null) || (trackIndex == null && trackName == null)) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, TOOL_NAME,
                    "Exactly one of track_index or track_name must be provided, not both or neither");
            }

            // Rule: Exactly one of device_index OR device_name must be provided; not both
            if ((deviceIndex != null && deviceName != null) || (deviceIndex == null && deviceName == null)) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, TOOL_NAME,
                    "Exactly one of device_index or device_name must be provided, not both or neither");
            }
        }
    }

    /**
     * Data record for validated get device details arguments.
     *
     * @param trackIndex Zero-based track index (nullable)
     * @param trackName Track name for exact matching (nullable)
     * @param deviceIndex Zero-based device index (nullable)
     * @param deviceName Device name for exact matching (nullable)
     * @param getForSelectedDevice Whether to get selected device details (nullable)
     */
    public record GetDeviceDetailsArguments(
        @JsonProperty("track_index") Integer trackIndex,
        @JsonProperty("track_name") String trackName,
        @JsonProperty("device_index") Integer deviceIndex,
        @JsonProperty("device_name") String deviceName,
        @JsonProperty("get_for_selected_device") Boolean getForSelectedDevice
    ) {}
}
