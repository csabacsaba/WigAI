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

public class SetAllDevicePagesParametersTool {

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
                "pages": {
                  "type": "array",
                  "description": "Array of page objects with parameters to set"
                }
              },
              "required": ["track_index", "device_position", "pages"]
            }
            """;

        var tool = McpSchema.Tool.builder()
            .name("set_all_device_pages_parameters")
            .description("Set ALL page parameters. Writes pages in optimized order (Band Types first for EQ+). Server-side validation using displayedValue.")
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "set_all_device_pages_parameters",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "set_all_device_pages_parameters");
                    Integer devicePosition = ParameterValidator.validateRequiredInteger(args, "device_position", "set_all_device_pages_parameters");
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> pages = (List<Map<String, Object>>) args.get("pages");
                    if (pages == null || pages.isEmpty()) {
                        throw new IllegalArgumentException("pages array is required");
                    }

                    String deviceName = bitwigApiFacade.getDeviceNameOnTrack(trackIndex, devicePosition);
                    
                    // Reorder pages for EQ+ to write Band Types first
                    List<Map<String, Object>> orderedPages = new ArrayList<>(pages);
                    if (deviceName.equals("EQ+")) {
                        Map<String, Object> bandTypesPage = null;
                        for (Map<String, Object> p : orderedPages) {
                            if (((Number) p.get("page_index")).intValue() == 0) {
                                bandTypesPage = p;
                                break;
                            }
                        }
                        if (bandTypesPage != null) {
                            orderedPages.remove(bandTypesPage);
                            orderedPages.add(0, bandTypesPage);
                            logger.info("EQ+ detected - Band Types will be written first");
                        }
                    }
                    
                    List<Map<String, Object>> pageResults = new ArrayList<>();
                    
                    for (Map<String, Object> pageObj : orderedPages) {
                        Integer pageIndex = ((Number) pageObj.get("page_index")).intValue();
                        String pageName = (String) pageObj.getOrDefault("page_name", "Page-" + pageIndex);
                        
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> parameters = (List<Map<String, Object>>) pageObj.get("parameters");
                        if (parameters == null || parameters.isEmpty()) {
                            continue;
                        }

                        bitwigApiFacade.switchDevicePageOnTrack(trackIndex, devicePosition, pageIndex);
                        Thread.sleep(100);
                        
                        // Read current target device parameters to compare displayedValue
                        Map<Integer, String> currentDisplayedValues = new LinkedHashMap<>();
                        try {
                            var currentParams = bitwigApiFacade.getDevicePageParameters(trackIndex, devicePosition);
                            for (Map<String, Object> param : currentParams) {
                                Integer paramIndex = ((Number) param.get("index")).intValue();
                                String displayedValue = (String) param.get("displayedValue");
                                currentDisplayedValues.put(paramIndex, displayedValue);
                            }
                        } catch (Exception e) {
                            logger.warn("Could not read current parameters for displayedValue comparison: " + e.getMessage());
                        }
                        
                        List<Map<String, Object>> paramsToSet = new ArrayList<>();
                        List<Map<String, Object>> skippedParams = new ArrayList<>();
                        
                        for (Map<String, Object> param : parameters) {
                            Integer paramIndex = ((Number) param.get("index")).intValue();
                            Double paramValue = ((Number) param.get("value")).doubleValue();
                            String incomingDisplayedValue = (String) param.getOrDefault("displayedValue", "");
                            
                            // Check if displayedValue matches current value
                            String currentDisplayed = currentDisplayedValues.getOrDefault(paramIndex, "");
                            
                            if (!incomingDisplayedValue.isEmpty() && incomingDisplayedValue.equals(currentDisplayed)) {
                                // displayedValue matches - skip this parameter
                                Map<String, Object> skipped = new LinkedHashMap<>();
                                skipped.put("index", paramIndex);
                                skipped.put("value", paramValue);
                                skipped.put("displayedValue", incomingDisplayedValue);
                                skipped.put("reason", "displayedValue matches current value");
                                skippedParams.add(skipped);
                                logger.debug("Skipping param " + paramIndex + " on page " + pageIndex + " - displayedValue already matches: " + incomingDisplayedValue);
                            } else {
                                // displayedValue differs or not provided - write the parameter
                                Map<String, Object> p = new LinkedHashMap<>();
                                p.put("index", paramIndex);
                                p.put("value", paramValue);
                                paramsToSet.add(p);
                            }
                        }
                        
                        if (!paramsToSet.isEmpty()) {
                            bitwigApiFacade.setDevicePageParameters(trackIndex, devicePosition, paramsToSet);
                        }
                        
                        Map<String, Object> pageResult = new LinkedHashMap<>();
                        pageResult.put("page_index", pageIndex);
                        pageResult.put("page_name", pageName);
                        pageResult.put("parameters_sent", paramsToSet.size());
                        pageResult.put("parameters_skipped", skippedParams.size());
                        if (!skippedParams.isEmpty()) {
                            pageResult.put("skipped_details", skippedParams);
                        }
                        pageResults.add(pageResult);
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("device", deviceName);
                    response.put("track_index", trackIndex);
                    response.put("device_position", devicePosition);
                    response.put("pages_updated", pageResults.size());
                    response.put("pages", pageResults);

                    return response;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
