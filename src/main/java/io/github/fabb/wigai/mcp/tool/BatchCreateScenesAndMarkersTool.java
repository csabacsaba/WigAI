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
 * MCP tool for batch creating both scenes and cue markers with the same names.
 * Creates scenes (with programmatic names) and cue markers (as "Untitled") at specified positions.
 */
public class BatchCreateScenesAndMarkersTool {

    private static final String TOOL_NAME = "batch_create_scenes_and_markers";

    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "items": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "position_beats": {
                        "type": "number",
                        "minimum": 0,
                        "description": "Position in beats for the cue marker"
                      },
                      "scene_index": {
                        "type": "integer",
                        "minimum": 0,
                        "description": "Scene index (0-based) where to create the scene"
                      },
                      "name": {
                        "type": "string",
                        "description": "Name for the scene (cue marker will be 'Untitled')"
                      }
                    },
                    "required": ["position_beats", "scene_index", "name"]
                  },
                  "minItems": 1,
                  "description": "Array of items to create (both scenes and markers)"
                }
              },
              "required": ["items"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Create both named scenes and cue markers at specified positions in batch. " +
                    "Scenes will be automatically named, while cue markers will be created as 'Untitled' " +
                    "(rename manually to match scene names). Useful for creating track structure.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        BatchCreateItemsArguments args = parseArguments(req.arguments());
                        
                        List<Map<String, Object>> createdItems = new ArrayList<>();
                        List<Map<String, Object>> failedItems = new ArrayList<>();
                        
                        for (ItemRequest itemReq : args.items()) {
                            try {
                                // Step 1: Set playback position
                                bitwigApiFacade.setPlaybackPosition(itemReq.positionBeats());
                                Thread.sleep(100); // Wait for position to be set

                                // Step 2: Create cue marker at this position (will be "Untitled")
                                boolean markerCreated = bitwigApiFacade.addCueMarkerAtPlaybackPosition();
                                
                                // Step 3: Create/set scene name
                                boolean sceneCreated = bitwigApiFacade.createScene(itemReq.sceneIndex());
                                Thread.sleep(50); // Small delay before setting name
                                boolean sceneNamed = bitwigApiFacade.setSceneName(itemReq.sceneIndex(), itemReq.name());

                                if (markerCreated && sceneCreated && sceneNamed) {
                                    createdItems.add(Map.of(
                                        "position_beats", itemReq.positionBeats(),
                                        "scene_index", itemReq.sceneIndex(),
                                        "name", itemReq.name(),
                                        "marker_status", "created (Untitled)",
                                        "scene_status", "created and named",
                                        "status", "success"
                                    ));
                                } else {
                                    String error = "";
                                    if (!markerCreated) error += "marker creation failed; ";
                                    if (!sceneCreated) error += "scene creation failed; ";
                                    if (!sceneNamed) error += "scene naming failed; ";
                                    
                                    failedItems.add(Map.of(
                                        "position_beats", itemReq.positionBeats(),
                                        "scene_index", itemReq.sceneIndex(),
                                        "name", itemReq.name(),
                                        "status", "partial_failure",
                                        "error", error
                                    ));
                                }

                                Thread.sleep(50); // Delay between items

                            } catch (Exception e) {
                                logger.warn("Error creating item: " + e.getMessage());
                                failedItems.add(Map.of(
                                    "position_beats", itemReq.positionBeats(),
                                    "scene_index", itemReq.sceneIndex(),
                                    "name", itemReq.name(),
                                    "status", "failed",
                                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
                                ));
                            }
                        }

                        // Return to start position
                        try {
                            bitwigApiFacade.setPlaybackPosition(0.0);
                        } catch (Exception e) {
                            logger.warn("Could not return to start position: " + e.getMessage());
                        }

                        return Map.of(
                            "created_items", createdItems,
                            "failed_items", failedItems,
                            "total", args.items().size(),
                            "success", createdItems.size(),
                            "failed", failedItems.size(),
                            "message", String.format(
                                    "Created %d/%d items successfully. " +
                                    "Scenes are automatically named. " +
                                    "Cue markers are created as 'Untitled' - rename them manually in Bitwig to match scene names.",
                                    createdItems.size(), args.items().size())
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
    private static BatchCreateItemsArguments parseArguments(Map<String, Object> arguments) {
        if (!arguments.containsKey("items")) {
            throw new IllegalArgumentException("Missing required parameter: items");
        }
        
        Object itemsObj = arguments.get("items");
        if (!(itemsObj instanceof List)) {
            throw new IllegalArgumentException("items must be an array");
        }
        
        List<Map<String, Object>> itemsRaw = (List<Map<String, Object>>) itemsObj;
        if (itemsRaw.isEmpty()) {
            throw new IllegalArgumentException("items array cannot be empty");
        }
        
        List<ItemRequest> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsRaw) {
            if (!itemMap.containsKey("position_beats") || 
                !itemMap.containsKey("scene_index") || 
                !itemMap.containsKey("name")) {
                throw new IllegalArgumentException("Each item must have position_beats, scene_index, and name");
            }
            
            double positionBeats = ((Number) itemMap.get("position_beats")).doubleValue();
            int sceneIndex = ((Number) itemMap.get("scene_index")).intValue();
            String name = (String) itemMap.get("name");
            
            items.add(new ItemRequest(positionBeats, sceneIndex, name));
        }
        
        return new BatchCreateItemsArguments(items);
    }

    private record BatchCreateItemsArguments(@JsonProperty("items") List<ItemRequest> items) {}

    private record ItemRequest(
            @JsonProperty("position_beats") double positionBeats,
            @JsonProperty("scene_index") int sceneIndex,
            @JsonProperty("name") String name) {}
}
