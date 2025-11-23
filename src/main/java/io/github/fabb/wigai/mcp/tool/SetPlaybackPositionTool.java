package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
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
 * MCP tool for setting the playback position in the arranger.
 */
public class SetPlaybackPositionTool {

    private static final String TOOL_NAME = "set_playback_position";

    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "beats": {
                  "type": "number",
                  "minimum": 0,
                  "description": "Position in beats (0.0 = start of timeline)"
                }
              },
              "required": ["beats"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Set the playback position in beats. If playing, jumps immediately.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        SetPositionArguments args = parseArguments(req.arguments());
                        bitwigApiFacade.setPlaybackPosition(args.beats());
                        
                        return Map.of(
                            "action", "playback_position_set",
                            "beats", args.beats(),
                            "message", String.format("Set playback position to %.2f beats", args.beats())
                        );
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static SetPositionArguments parseArguments(Map<String, Object> arguments) {
        double beats = ParameterValidator.validateRequiredDouble(arguments, "beats", TOOL_NAME);
        
        if (beats < 0) {
            throw new IllegalArgumentException("beats must be >= 0");
        }

        return new SetPositionArguments(beats);
    }

    public record SetPositionArguments(
        @JsonProperty("beats") double beats
    ) {}
}
