package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for retrieving detailed information about a specific track by index, name,
 * or the currently selected track.
 */
public class GetTrackDetailsTool {

    private static final String TOOL_NAME = "get_track_details";

    /**
     * Creates a "get_track_details" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for track operations
     * @param logger          The structured logger for logging operations
     * @return A SyncToolSpecification for the "get_track_details" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "0-based index of the track"
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the track (case-sensitive)"
                },
                "get_selected": {
                  "type": "boolean",
                  "description": "If true, retrieves details for the currently selected track. Defaults to true when no parameter is provided."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Retrieve detailed information for a specific track by index, name, or the currently selected track.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                TOOL_NAME,
                req.arguments(),
                logger,
                GetTrackDetailsTool::validateParameters,
                (validated) -> switch (validated.target()) {
                    case INDEX -> bitwigApiFacade.getTrackDetailsByIndex(validated.trackIndex());
                    case NAME -> bitwigApiFacade.getTrackDetailsByName(validated.trackName());
                    case SELECTED -> {
                        Map<String, Object> details = bitwigApiFacade.getSelectedTrackDetails();
                        if (details == null) {
                            throw new BitwigApiException(
                                ErrorCode.TRACK_NOT_FOUND,
                                TOOL_NAME,
                                "No track is currently selected"
                            );
                        }
                        yield details;
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private enum Target { INDEX, NAME, SELECTED }

    private record ValidatedParams(Target target, int trackIndex, String trackName) {}

    /**
     * Validates that exactly one of track_index, track_name, or get_selected is provided.
     * If none are provided, defaults to SELECTED.
     */
    static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        boolean hasIndex = arguments.containsKey("track_index");
        boolean hasName = arguments.containsKey("track_name");
        boolean hasGetSelected = arguments.containsKey("get_selected");

        int provided = (hasIndex ? 1 : 0) + (hasName ? 1 : 0) + (hasGetSelected ? 1 : 0);
        if (provided > 1) {
            throw new IllegalArgumentException("Provide exactly one of 'track_index', 'track_name', or 'get_selected'");
        }

        // Default behavior: if none provided, act as get_selected=true
        if (provided == 0) {
            return new ValidatedParams(Target.SELECTED, -1, null);
        }

        if (hasIndex) {
            Object idxObj = arguments.get("track_index");
            if (!(idxObj instanceof Number)) {
                throw new IllegalArgumentException("Parameter 'track_index' must be an integer");
            }
            int index = ((Number) idxObj).intValue();
            if (index < 0) {
                throw new IllegalArgumentException("Parameter 'track_index' must be >= 0");
            }
            return new ValidatedParams(Target.INDEX, index, null);
        }

        if (hasName) {
            Object nameObj = arguments.get("track_name");
            if (!(nameObj instanceof String)) {
                throw new IllegalArgumentException("Parameter 'track_name' must be a string");
            }
            String name = ((String) nameObj).trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Parameter 'track_name' cannot be empty");
            }
            return new ValidatedParams(Target.NAME, -1, name);
        }

        // get_selected path
        Object selObj = arguments.get("get_selected");
        if (selObj instanceof Boolean) {
            boolean sel = (Boolean) selObj;
            if (!sel) {
                // Explicit false is invalid (no target specified)
                throw new IllegalArgumentException("If 'get_selected' is provided, it must be true");
            }
            return new ValidatedParams(Target.SELECTED, -1, null);
        } else if (selObj == null) {
            return new ValidatedParams(Target.SELECTED, -1, null);
        } else {
            throw new IllegalArgumentException("Parameter 'get_selected' must be a boolean");
        }
    }
}
