package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for launching (jumping to) cue markers in the arranger.
 */
public class tbLaunchCueMarkerTool {

    private static final String TOOL_NAME = "launch_cue_marker";

    /**
     * Creates the MCP tool specification for cue marker launching.
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
              "properties": {
                "marker_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the cue marker to jump to"
                }
              },
              "required": ["marker_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Launch (jump to) a cue marker in the arranger by its index")
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
                        LaunchMarkerArguments args = parseArguments(req.arguments());

                        // Perform marker launch operation
                        boolean success = bitwigApiFacade.launchCueMarker(args.markerIndex());

                        if (success) {
                            return Map.of(
                                "action", "cue_marker_launched",
                                "marker_index", args.markerIndex(),
                                "message", "Jumped to cue marker " + args.markerIndex()
                            );
                        } else {
                            throw new BitwigApiException(
                                ErrorCode.OPERATION_FAILED,
                                TOOL_NAME,
                                "Failed to launch cue marker at index " + args.markerIndex()
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

    /**
     * Parses the MCP tool arguments into a structured format.
     *
     * @param arguments Raw arguments map from MCP request
     * @return Parsed and validated LaunchMarkerArguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static LaunchMarkerArguments parseArguments(Map<String, Object> arguments) {
        // Validate required parameters
        int markerIndex = ParameterValidator.validateRequiredInteger(arguments, "marker_index", TOOL_NAME);
        
        // Validate marker index is non-negative
        if (markerIndex < 0) {
            throw new IllegalArgumentException("marker_index must be >= 0");
        }

        return new LaunchMarkerArguments(markerIndex);
    }

    /**
     * Data record for validated launch marker arguments.
     *
     * @param markerIndex The zero-based marker index
     */
    public record LaunchMarkerArguments(
        @JsonProperty("marker_index") int markerIndex
    ) {}
}
