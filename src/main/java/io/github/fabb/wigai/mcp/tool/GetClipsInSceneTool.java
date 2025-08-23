package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.ClipSceneController;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for retrieving detailed information about all clips within a specific scene.
 * Provides clip properties including track context, content details, and playback states.
 */
public class GetClipsInSceneTool {

    /**
     * Creates a "get_clips_in_scene" tool specification using the unified error handling system.
     *
     * @param clipSceneController The controller for clip and scene operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "get_clips_in_scene" tool
     */
    public static McpServerFeatures.SyncToolSpecification getClipsInSceneSpecification(
            ClipSceneController clipSceneController, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "scene_index": {
                  "type": "integer",
                  "description": "0-based index of the scene. Must be >= 0.",
                  "minimum": 0
                },
                "scene_name": {
                  "type": "string",
                  "description": "Name of the scene (case-insensitive, trimmed)"
                }
              },
              "oneOf": [
                {"required": ["scene_index"]},
                {"required": ["scene_name"]},
                {"required": ["scene_index", "scene_name"]}
              ]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("get_clips_in_scene")
            .description("Get detailed information for all clips within a specific scene, including track context, content properties, and playback states.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "get_clips_in_scene",
                req.arguments(),
                logger,
                (arguments, operation) -> {
                    // Validate that at least one parameter is provided
                    Object sceneIndexObj = arguments.get("scene_index");
                    String sceneName = (String) arguments.get("scene_name");

                    if (sceneIndexObj == null && (sceneName == null || sceneName.trim().isEmpty())) {
                        throw new io.github.fabb.wigai.common.error.BitwigApiException(
                            io.github.fabb.wigai.common.error.ErrorCode.MISSING_REQUIRED_PARAMETER,
                            operation,
                            "At least one of 'scene_index' or 'scene_name' must be provided",
                            Map.of()
                        );
                    }

                    // Validate scene_index if provided
                    Integer sceneIndex = null;
                    if (sceneIndexObj != null) {
                        if (sceneIndexObj instanceof Number) {
                            int index = ((Number) sceneIndexObj).intValue();
                            if (index < 0) {
                                throw new io.github.fabb.wigai.common.error.BitwigApiException(
                                    io.github.fabb.wigai.common.error.ErrorCode.INVALID_PARAMETER,
                                    operation,
                                    "Scene index must be >= 0, got: " + index,
                                    Map.of("scene_index", index)
                                );
                            }
                            sceneIndex = index;
                        } else {
                            throw new io.github.fabb.wigai.common.error.BitwigApiException(
                                io.github.fabb.wigai.common.error.ErrorCode.INVALID_PARAMETER_TYPE,
                                operation,
                                "Scene index must be a number",
                                Map.of("scene_index", sceneIndexObj)
                            );
                        }
                    }

                    // Normalize scene name if provided (case-insensitive, trimmed)
                    if (sceneName != null) {
                        sceneName = sceneName.trim();
                        if (sceneName.isEmpty()) {
                            sceneName = null;
                        }
                    }

                    return new GetClipsInSceneArguments(sceneIndex, sceneName);
                },
                (validatedArgs) -> clipSceneController.getClipsInScene(
                    validatedArgs.sceneIndex(),
                    validatedArgs.sceneName()
                )
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Validated arguments for the get_clips_in_scene tool.
     */
    public record GetClipsInSceneArguments(Integer sceneIndex, String sceneName) {}
}
