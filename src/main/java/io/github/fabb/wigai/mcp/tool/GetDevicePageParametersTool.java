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
 * MCP Tool for getting parameter information for the current page of a device.
 */
public class GetDevicePageParametersTool {

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
                },
                "page_index": {
                  "type": "integer",
                  "description": "Optional: The page index to switch to before getting parameters (0-based). If not specified, uses the currently active page."
                }
              },
              "required": ["track_index", "device_position"]
            }
            """;

        var tool = McpSchema.Tool.builder()
            .name("get_device_page_parameters")
            .description("""
                Get the parameter information for a device page. \
                Returns parameter index, name, current value (normalized 0.0-1.0), and displayed value (with units). \

                You can optionally specify page_index to switch to a specific page before reading parameters. \
                Example: get_device_page_parameters(track_index=0, device_position=0, page_index=1) switches to page 1 (Freqs) then returns parameters. \

                IMPORTANT: If you don't know the device_position (device index), use list_devices_on_track FIRST to find which index the device is at.
                """)
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "get_device_page_parameters",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "get_device_page_parameters");
                    Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "get_device_page_parameters");
                    Integer pageIndex = ParameterValidator.validateOptionalInteger(args, "page_index", "get_device_page_parameters");

                    // If page_index is specified, switch to that page first
                    if (pageIndex != null) {
                        bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
                    }

                    List<Map<String, Object>> parameters = bitwigApiFacade.getDevicePageParameters(trackIndex, devicePosition);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("track_index", trackIndex);
                    response.put("device_position", devicePosition);
                    if (pageIndex != null) {
                        response.put("page_index", pageIndex);
                    }
                    response.put("parameter_count", parameters.size());
                    response.put("parameters", parameters);

                    return response;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
