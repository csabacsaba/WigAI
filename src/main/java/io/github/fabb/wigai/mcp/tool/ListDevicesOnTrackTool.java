package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for listing all devices on a specific track with detailed information.
 * Supports track identification by index, name, or selected track.
 */
public class ListDevicesOnTrackTool {

    /**
     * Creates a "list_devices_on_track" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for track and device operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "list_devices_on_track" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "0-based index of the track",
                  "minimum": 0
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive, exact match)"
                },
                "get_selected": {
                  "type": "boolean",
                  "description": "If true, lists devices for the currently selected track. Defaults to true when no parameter is provided"
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_devices_on_track")
            .description("List all devices on a specific track with detailed information including name, type, and key states (bypassed, selected). Track can be identified by index, name, or as the currently selected track.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "list_devices_on_track",
                req.arguments(),
                logger,
                ListDevicesOnTrackTool::validateParameters,
                (validatedParams) -> bitwigApiFacade.getDevicesOnTrack(
                    validatedParams.trackIndex(),
                    validatedParams.trackName(),
                    validatedParams.getSelected()
                )
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validates the parameters for the list_devices_on_track tool.
     * Ensures exactly one of track_index, track_name, or get_selected is provided.
     *
     * @param arguments The raw arguments map
     * @param operation The operation name for error context
     * @return Validated parameters
     */
    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        Integer trackIndex = null;
        String trackName = null;
        Boolean getSelected = null;

        // Extract parameters
        if (arguments.containsKey("track_index")) {
            Object indexObj = arguments.get("track_index");
            if (indexObj instanceof Number) {
                int index = ((Number) indexObj).intValue();
                if (index < 0) {
                    throw new IllegalArgumentException("track_index must be >= 0");
                }
                trackIndex = index;
            } else if (indexObj != null) {
                throw new IllegalArgumentException("Parameter 'track_index' must be an integer");
            }
        }

        if (arguments.containsKey("track_name")) {
            Object nameObj = arguments.get("track_name");
            if (nameObj instanceof String) {
                String name = ((String) nameObj).trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Parameter 'track_name' cannot be empty");
                }
                trackName = name;
            } else if (nameObj != null) {
                throw new IllegalArgumentException("Parameter 'track_name' must be a string");
            }
        }

        if (arguments.containsKey("get_selected")) {
            Object selectedObj = arguments.get("get_selected");
            if (selectedObj instanceof Boolean) {
                getSelected = (Boolean) selectedObj;
            } else if (selectedObj != null) {
                throw new IllegalArgumentException("Parameter 'get_selected' must be a boolean");
            }
        }

        // Validate exactly one parameter is provided
        int paramCount = 0;
        if (trackIndex != null) paramCount++;
        if (trackName != null) paramCount++;
        if (getSelected != null && getSelected) paramCount++;  // Only count if explicitly true

        if (paramCount > 1) {
            throw new IllegalArgumentException(
                "Exactly one of 'track_index', 'track_name', or 'get_selected' may be provided");
        }

        // Default to get_selected=true when no parameters provided
        if (paramCount == 0) {
            getSelected = true;
        }

        return new ValidatedParams(trackIndex, trackName, getSelected);
    }

    /**
     * Record to hold validated parameters for the list_devices_on_track tool.
     */
    private record ValidatedParams(Integer trackIndex, String trackName, Boolean getSelected) {}
}
