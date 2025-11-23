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
 * MCP tool for creating cue markers at the current playback position in the arranger.
 */
public class CreateCueMarkerTool {

    private static final String TOOL_NAME = "create_cue_marker";

    /**
     * Creates the MCP tool specification for cue marker creation.
     *
     * @param bitwigApiFacade The BitwigApiFacade for arranger operations
     * @param logger The structured logger for operation logging
     * @return MCP tool specification
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {}
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Create a new cue marker at the current playback position in the arranger")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        // Create cue marker at playback position
                        boolean success = bitwigApiFacade.addCueMarkerAtPlaybackPosition();

                        if (success) {
                            return Map.of(
                                "action", "cue_marker_created",
                                "message", "Created cue marker at current playback position"
                            );
                        } else {
                            throw new BitwigApiException(
                                ErrorCode.OPERATION_FAILED,
                                TOOL_NAME,
                                "Failed to create cue marker at playback position"
                            );
                        }
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
