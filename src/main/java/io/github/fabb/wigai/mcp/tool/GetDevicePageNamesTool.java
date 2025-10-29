package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Tool for getting available remote control page names for a device.
 */
public class GetDevicePageNamesTool {

    public static McpServerFeatures.SyncToolSpecification specification(
        BitwigApiFacade bitwigApiFacade,
        StructuredLogger logger
    ) {
        var inputSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "The track index (0-based)"
                },
                "device_position": {
                  "type": "integer",
                  "description": "The device position on the track (0-based)"
                }
              },
              "required": ["track_index", "device_position"]
            }
            """;

        var tool = McpSchema.Tool.builder()
            .name("get_device_page_names")
            .description("""
                Get the available remote control page names for a device. Returns a list of page names with their 0-based indices. \
                Use this to discover what pages are available before calling switch_device_page_on_tracks. \
                Example: For EQ+ device, returns ["Gains", "Freqs", "Qs"] where Gains=0, Freqs=1, Qs=2. \
                IMPORTANT: Always call this tool first to find the correct page_index number before using switch_device_page_on_tracks.
                """)
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "get_device_page_names",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "get_device_page_names");
                    Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "get_device_page_names");

                    List<String> pageNames = bitwigApiFacade.getDevicePageNames(trackIndex, devicePosition);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("track_index", trackIndex);
                    response.put("device_position", devicePosition);
                    response.put("page_count", pageNames.size());
                    response.put("pages", pageNames);

                    return response;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
