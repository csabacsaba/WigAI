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
 * MCP tool for launching (triggering) a scene by its index.
 */
public class tbLaunchSceneTool {

    private static final String TOOL_NAME = "launch_scene";

    /**
     * Creates a "launch_scene" tool specification.
     *
     * @param bitwigApiFacade The BitwigApiFacade for scene operations
     * @param logger The structured logger for operation logging
     * @return A SyncToolSpecification for the "launch_scene" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "scene_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the scene to launch"
                }
              },
              "required": ["scene_index"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Launch (trigger) a scene by its index")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                TOOL_NAME,
                req.arguments(),
                logger,
                tbLaunchSceneTool::validateParameters,
                (validatedParams) -> {
                    boolean success = bitwigApiFacade.launchScene(validatedParams.sceneIndex());
                    
                    if (success) {
                        return Map.of(
                            "success", true,
                            "scene_index", validatedParams.sceneIndex(),
                            "message", "Scene launched successfully"
                        );
                    } else {
                        return Map.of(
                            "success", false,
                            "scene_index", validatedParams.sceneIndex(),
                            "error", "Failed to launch scene - scene may not exist"
                        );
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validates the parameters for the launch_scene tool.
     *
     * @param arguments The raw arguments map
     * @param operation The operation name for error context
     * @return Validated parameters
     */
    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        if (!arguments.containsKey("scene_index")) {
            throw new IllegalArgumentException("Missing required parameter: scene_index");
        }

        Object sceneIndexObj = arguments.get("scene_index");
        if (!(sceneIndexObj instanceof Number)) {
            throw new IllegalArgumentException("scene_index must be a number");
        }

        int sceneIndex = ((Number) sceneIndexObj).intValue();
        if (sceneIndex < 0) {
            throw new IllegalArgumentException("scene_index must be non-negative");
        }

        return new ValidatedParams(sceneIndex);
    }

    /**
     * Record to hold validated parameters for the launch_scene tool.
     */
    private record ValidatedParams(int sceneIndex) {}
}
