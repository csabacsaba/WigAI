package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for listing all scenes in the current project with their information.
 */
public class ListScenesTool {

    /**
     * Creates a "list_scenes" tool specification using the unified error handling system.
     *
     * @param bitwigApiFacade The BitwigApiFacade for scene operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "list_scenes" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_scenes")
            .description("List all scenes in the current project with their name and color. Returns information about scene structure.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "list_scenes",
                req.arguments(),
                logger,
                ListScenesTool::validateParameters,
                (validatedParams) -> bitwigApiFacade.getAllScenesInfo()
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validates the parameters for the list_scenes tool.
     * Since this tool takes no parameters, this method simply returns an empty validated params object.
     *
     * @param arguments The raw arguments map
     * @param operation The operation name for error context
     * @return Validated parameters (empty for this tool)
     */
    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        // No parameters needed for list_scenes tool
        return new ValidatedParams();
    }

    /**
     * Record to hold validated parameters for the list_scenes tool.
     * Empty since no parameters are required.
     */
    private record ValidatedParams() {}
}