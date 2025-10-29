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
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Tool for creating and deleting tracks in Bitwig Studio.
 */
public class TrackManagementTool {

    /**
     * Creates the MCP tool specification for creating instrument tracks.
     */
    public static McpServerFeatures.SyncToolSpecification createInstrumentTrackSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var createInstrumentTrackSchema = """
            {
              "type": "object",
              "properties": {}
            }""";

        var tool = McpSchema.Tool.builder()
            .name("create_instrument_track")
            .description("Create a new instrument track at the end of the track list")
            .inputSchema(createInstrumentTrackSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "create_instrument_track",
                logger,
                () -> {
                    bitwigApiFacade.createInstrumentTrack();

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "instrument_track_created");
                    responseData.put("message", "Successfully created instrument track");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for creating audio tracks.
     */
    public static McpServerFeatures.SyncToolSpecification createAudioTrackSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var createAudioTrackSchema = """
            {
              "type": "object",
              "properties": {}
            }""";

        var tool = McpSchema.Tool.builder()
            .name("create_audio_track")
            .description("Create a new audio track at the end of the track list")
            .inputSchema(createAudioTrackSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "create_audio_track",
                logger,
                () -> {
                    bitwigApiFacade.createAudioTrack();

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "audio_track_created");
                    responseData.put("message", "Successfully created audio track");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for creating effect tracks.
     */
    public static McpServerFeatures.SyncToolSpecification createEffectTrackSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var createEffectTrackSchema = """
            {
              "type": "object",
              "properties": {}
            }""";

        var tool = McpSchema.Tool.builder()
            .name("create_effect_track")
            .description("Create a new effect track at the end of the track list")
            .inputSchema(createEffectTrackSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "create_effect_track",
                logger,
                () -> {
                    bitwigApiFacade.createEffectTrack();

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "effect_track_created");
                    responseData.put("message", "Successfully created effect track");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for deleting tracks.
     */
    public static McpServerFeatures.SyncToolSpecification deleteTrackSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var deleteTrackSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index to delete (0-based). If not provided, deletes the currently selected track.",
                  "minimum": 0
                }
              }
            }""";

        var tool = McpSchema.Tool.builder()
            .name("delete_track")
            .description("Delete a track by index or delete the currently selected track")
            .inputSchema(deleteTrackSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "delete_track",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    Integer trackIndex = args.containsKey("track_index") 
                        ? ParameterValidator.validateRequiredInteger(args, "track_index", "delete_track")
                        : null;

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "track_deleted");

                    if (trackIndex != null) {
                        bitwigApiFacade.deleteTrack(trackIndex);
                        responseData.put("track_index", trackIndex);
                        responseData.put("message", "Successfully deleted track at index " + trackIndex);
                    } else {
                        bitwigApiFacade.deleteSelectedTrack();
                        responseData.put("message", "Successfully deleted selected track");
                    }

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
