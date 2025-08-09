package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
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
 * MCP tool for listing all tracks in the current project with summary information.
 * Supports optional filtering by track type.
 */
public class ListTracksTool {

    /**
     * Creates a "list_tracks" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for track operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "list_tracks" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "type": {
                  "type": "string",
                  "description": "Optional filter by track type (e.g., 'audio', 'instrument', 'group', 'effect', 'master')",
                  "enum": ["audio", "instrument", "group", "effect", "master", "hybrid"]
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_tracks")
            .description("List all tracks in the current project with summary information (name, type, selection state, parent group, basic device list). Supports optional filtering by track type.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "list_tracks",
                req.arguments(),
                logger,
                ListTracksTool::validateParameters,
                (validatedParams) -> bitwigApiFacade.getAllTracksInfo(validatedParams.typeFilter())
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validates the parameters for the list_tracks tool.
     *
     * @param arguments The raw arguments map
     * @param operation The operation name for error context
     * @return Validated parameters
     */
    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        String typeFilter = null;

        if (arguments.containsKey("type")) {
            Object typeObj = arguments.get("type");
            if (typeObj instanceof String) {
                typeFilter = ((String) typeObj).toLowerCase().trim();

                // Validate that the type is one of the allowed values
                if (!typeFilter.isEmpty() &&
                    !List.of("audio", "instrument", "group", "effect", "master", "hybrid").contains(typeFilter)) {
                    throw new IllegalArgumentException(
                        "Invalid track type '" + typeFilter + "'. Must be one of: audio, instrument, group, effect, master, hybrid");
                }

                // Convert empty string to null
                if (typeFilter.isEmpty()) {
                    typeFilter = null;
                }
            } else if (typeObj != null) {
                throw new IllegalArgumentException("Parameter 'type' must be a string");
            }
        }

        return new ValidatedParams(typeFilter);
    }

    /**
     * Record to hold validated parameters for the list_tracks tool.
     */
    private record ValidatedParams(String typeFilter) {}
}
