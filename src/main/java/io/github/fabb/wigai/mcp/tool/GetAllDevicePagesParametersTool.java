package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class GetAllDevicePagesParametersTool {

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
            .name("get_all_device_pages_parameters")
            .description("Get ALL page parameters from a device in a single call. Reads parameters from push without page switching.")
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "get_all_device_pages_parameters",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "get_all_device_pages_parameters");
                    Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "get_all_device_pages_parameters");

                    String deviceName = bitwigApiFacade.getDeviceNameOnTrack(trackIndex, devicePosition);
                    List<Map<String, Object>> allPages = new ArrayList<>();
                    
                    // Determine max pages based on device type
                    int maxPages = 8; // Default
                    if ("EQ+".equals(deviceName)) {
                        maxPages = 4; // EQ+ has exactly 4 pages: Band Types, Gains, Freqs, Qs
                        logger.info("EQ+ detected - will read exactly 4 pages from push");
                    } else if ("Filter+".equals(deviceName)) {
                        maxPages = 3; // Filter+ typically has 3 pages
                        logger.info("Filter+ detected - will read max 3 pages from push");
                    }
                    
                    for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
                        try {
                            // NO PAGE SWITCH! Just read parameters for each page index from push
                            // The push buffer on server side contains all pages automatically!
                            
                            List<Map<String, Object>> parameters = bitwigApiFacade.getDevicePageParametersForPageIndex(trackIndex, devicePosition, pageIndex);
                            
                            if (parameters.isEmpty()) {
                                logger.info("Page " + pageIndex + " has no parameters - stopping");
                                break;
                            }
                            
                            Map<String, Object> pageObj = new LinkedHashMap<>();
                            pageObj.put("page_index", pageIndex);
                            pageObj.put("page_name", "Page-" + pageIndex);
                            pageObj.put("parameters", parameters);
                            allPages.add(pageObj);
                            logger.info("Collected page " + pageIndex + " with " + parameters.size() + " parameters");
                        } catch (Exception e) {
                            logger.info("Page " + pageIndex + " exception: " + e.getMessage());
                            break;
                        }
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("device", deviceName);
                    response.put("track_index", trackIndex);
                    response.put("device_position", devicePosition);
                    response.put("page_count", allPages.size());
                    response.put("pages", allPages);
                    logger.info("Final page count: " + allPages.size());
                    return response;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
