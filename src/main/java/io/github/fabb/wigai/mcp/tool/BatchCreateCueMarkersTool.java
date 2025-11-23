package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for batch creating multiple cue markers at specified positions.
 */
public class BatchCreateCueMarkersTool {

    private static final String TOOL_NAME = "batch_create_cue_markers";

    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "markers": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "position_beats": {
                        "type": "number",
                        "minimum": 0,
                        "description": "Position in beats where to create the marker"
                      },
                      "name": {
                        "type": "string",
                        "description": "Optional name hint (Bitwig will create as 'Untitled', rename manually)"
                      }
                    },
                    "required": ["position_beats"]
                  },
                  "minItems": 1,
                  "description": "Array of markers to create with their positions in beats"
                }
              },
              "required": ["markers"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Batch create multiple cue markers at specified positions in beats. Markers will be created as 'Untitled' in Bitwig.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        BatchCreateMarkersArguments args = parseArguments(req.arguments());
                        
                        List<Map<String, Object>> createdMarkers = new ArrayList<>();
                        List<Map<String, Object>> failedMarkers = new ArrayList<>();
                        
                        for (MarkerRequest markerReq : args.markers()) {
                            try {
                                // Set playback position
                                bitwigApiFacade.setPlaybackPosition(markerReq.positionBeats());
                                
                                // Small delay to ensure position is set
                                Thread.sleep(100);
                                
                                // Create marker
                                boolean success = bitwigApiFacade.addCueMarkerAtPlaybackPosition();
                                
                                if (success) {
                                    createdMarkers.add(Map.of(
                                        "position_beats", markerReq.positionBeats(),
                                        "name_hint", markerReq.name() != null ? markerReq.name() : "N/A",
                                        "status", "created"
                                    ));
                                } else {
                                    failedMarkers.add(Map.of(
                                        "position_beats", markerReq.positionBeats(),
                                        "name_hint", markerReq.name() != null ? markerReq.name() : "N/A",
                                        "reason", "Bitwig API returned false"
                                    ));
                                }
                                
                                // Small delay between markers
                                Thread.sleep(50);
                                
                            } catch (Exception e) {
                                failedMarkers.add(Map.of(
                                    "position_beats", markerReq.positionBeats(),
                                    "name_hint", markerReq.name() != null ? markerReq.name() : "N/A",
                                    "reason", e.getMessage()
                                ));
                            }
                        }
                        
                        // Return to start
                        bitwigApiFacade.setPlaybackPosition(0.0);
                        
                        return Map.of(
                            "action", "batch_cue_markers_created",
                            "total_requested", args.markers().size(),
                            "successful", createdMarkers.size(),
                            "failed", failedMarkers.size(),
                            "created_markers", createdMarkers,
                            "failed_markers", failedMarkers,
                            "message", String.format("Created %d/%d cue markers. Note: All markers are created as 'Untitled' - rename manually in Bitwig.", 
                                createdMarkers.size(), args.markers().size())
                        );
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static BatchCreateMarkersArguments parseArguments(Map<String, Object> arguments) {
        if (!arguments.containsKey("markers")) {
            throw new IllegalArgumentException("Missing required parameter: markers");
        }
        
        Object markersObj = arguments.get("markers");
        if (!(markersObj instanceof List)) {
            throw new IllegalArgumentException("markers must be an array");
        }
        
        List<Map<String, Object>> markersRaw = (List<Map<String, Object>>) markersObj;
        if (markersRaw.isEmpty()) {
            throw new IllegalArgumentException("markers array cannot be empty");
        }
        
        List<MarkerRequest> markers = new ArrayList<>();
        for (Map<String, Object> markerMap : markersRaw) {
            if (!markerMap.containsKey("position_beats")) {
                throw new IllegalArgumentException("Each marker must have position_beats");
            }
            
            Object posObj = markerMap.get("position_beats");
            double positionBeats;
            if (posObj instanceof Number) {
                positionBeats = ((Number) posObj).doubleValue();
            } else {
                throw new IllegalArgumentException("position_beats must be a number");
            }
            
            if (positionBeats < 0) {
                throw new IllegalArgumentException("position_beats must be >= 0");
            }
            
            String name = markerMap.containsKey("name") ? markerMap.get("name").toString() : null;
            
            markers.add(new MarkerRequest(positionBeats, name));
        }
        
        return new BatchCreateMarkersArguments(markers);
    }

    public record MarkerRequest(
        @JsonProperty("position_beats") double positionBeats,
        @JsonProperty("name") String name
    ) {}
    
    public record BatchCreateMarkersArguments(
        @JsonProperty("markers") List<MarkerRequest> markers
    ) {}
}
