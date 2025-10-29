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
 * MCP Tool for creating clips and writing notes in Bitwig Studio.
 */
public class ClipWriterTool {

    /**
     * Creates the MCP tool specification for creating clips and writing notes.
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var createClipSchema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Track index (0-based)",
                  "minimum": 0
                },
                "slot_index": {
                  "type": "integer",
                  "description": "Clip slot index (0-based)",
                  "minimum": 0
                },
                "length_bars": {
                  "type": "integer",
                  "description": "Clip length in bars (1-16)",
                  "minimum": 1,
                  "maximum": 16
                }
              },
              "required": ["track_index", "slot_index", "length_bars"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("create_clip")
            .description("Create an empty clip in a specific track and slot in the Clip Launcher")
            .inputSchema(createClipSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "create_clip",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    Integer trackIndex = ParameterValidator.validateRequiredInteger(args, "track_index", "create_clip");
                    Integer slotIndex = ParameterValidator.validateRequiredInteger(args, "slot_index", "create_clip");
                    Integer lengthBars = ParameterValidator.validateRequiredInteger(args, "length_bars", "create_clip");

                    // Create the clip
                    bitwigApiFacade.createEmptyClip(trackIndex, slotIndex, lengthBars);

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "clip_created");
                    responseData.put("track_index", trackIndex);
                    responseData.put("slot_index", slotIndex);
                    responseData.put("length_bars", lengthBars);
                    responseData.put("message", String.format("Created %d-bar clip at track %d, slot %d",
                                                              lengthBars, trackIndex, slotIndex));

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for writing notes to clips.
     */
    public static McpServerFeatures.SyncToolSpecification writeNoteSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var writeNoteSchema = """
            {
              "type": "object",
              "properties": {
                "step_position": {
                  "type": "integer",
                  "description": "Step position in 16th notes (0-based)",
                  "minimum": 0,
                  "maximum": 255
                },
                "pitch": {
                  "type": "integer",
                  "description": "MIDI pitch (0-127, where 60 is Middle C)",
                  "minimum": 0,
                  "maximum": 127
                },
                "velocity": {
                  "type": "integer",
                  "description": "Note velocity (1-127)",
                  "minimum": 1,
                  "maximum": 127
                },
                "duration_steps": {
                  "type": "number",
                  "description": "Duration in 16th note steps (e.g., 4 = quarter note)",
                  "minimum": 0.25,
                  "maximum": 64
                }
              },
              "required": ["step_position", "pitch", "velocity", "duration_steps"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("write_note")
            .description("Write a single note to the currently selected clip")
            .inputSchema(writeNoteSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "write_note",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    Integer stepPosition = ParameterValidator.validateRequiredInteger(args, "step_position", "write_note");
                    Integer pitch = ParameterValidator.validateRequiredInteger(args, "pitch", "write_note");
                    Integer velocity = ParameterValidator.validateRequiredInteger(args, "velocity", "write_note");

                    // Duration can be a double
                    Object durationObj = args.get("duration_steps");
                    double durationSteps;
                    if (durationObj instanceof Integer) {
                        durationSteps = ((Integer) durationObj).doubleValue();
                    } else if (durationObj instanceof Double) {
                        durationSteps = (Double) durationObj;
                    } else {
                        throw new IllegalArgumentException("duration_steps must be a number");
                    }

                    // Write the note
                    bitwigApiFacade.writeNoteToClip(stepPosition, pitch, velocity, durationSteps);

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "note_written");
                    responseData.put("step_position", stepPosition);
                    responseData.put("pitch", pitch);
                    responseData.put("velocity", velocity);
                    responseData.put("duration_steps", durationSteps);
                    responseData.put("message", String.format("Wrote note: pitch=%d, step=%d, vel=%d, dur=%.2f",
                                                              pitch, stepPosition, velocity, durationSteps));

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for writing multiple notes in batch to clips.
     */
    public static McpServerFeatures.SyncToolSpecification writeNotesSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var writeNotesSchema = """
            {
              "type": "object",
              "properties": {
                "notes": {
                  "type": "array",
                  "description": "Array of note objects to write",
                  "items": {
                    "type": "object",
                    "properties": {
                      "p": {
                        "type": "integer",
                        "description": "MIDI pitch (0-127)",
                        "minimum": 0,
                        "maximum": 127
                      },
                      "s": {
                        "type": "integer",
                        "description": "Step position in 16th notes (0-based)",
                        "minimum": 0,
                        "maximum": 255
                      },
                      "d": {
                        "type": "number",
                        "description": "Duration in 16th note steps",
                        "minimum": 0.25,
                        "maximum": 64
                      },
                      "v": {
                        "type": "integer",
                        "description": "Note velocity (1-127)",
                        "minimum": 1,
                        "maximum": 127
                      }
                    },
                    "required": ["p", "s", "d", "v"]
                  }
                }
              },
              "required": ["notes"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("write_notes")
            .description("Write multiple MIDI notes to the currently selected clip in batch")
            .inputSchema(writeNotesSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "write_notes",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> notes = (List<Map<String, Object>>) args.get("notes");

                    if (notes == null || notes.isEmpty()) {
                        throw new IllegalArgumentException("notes array must not be empty");
                    }

                    // Write notes in batch
                    bitwigApiFacade.writeNotesToClip(notes);

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "notes_written");
                    responseData.put("count", notes.size());
                    responseData.put("message", String.format("Wrote %d notes to clip", notes.size()));

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates the MCP tool specification for clearing all notes from a clip.
     */
    public static McpServerFeatures.SyncToolSpecification clearClipSpecification(
            BitwigApiFacade bitwigApiFacade,
            StructuredLogger logger) {

        var clearClipSchema = """
            {
              "type": "object",
              "properties": {}
            }""";

        var tool = McpSchema.Tool.builder()
            .name("clear_clip")
            .description("Clear all notes from the currently selected clip")
            .inputSchema(clearClipSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "clear_clip",
                logger,
                () -> {
                    // Clear the clip
                    bitwigApiFacade.clearClip();

                    Map<String, Object> responseData = new LinkedHashMap<>();
                    responseData.put("action", "clip_cleared");
                    responseData.put("message", "Successfully cleared all notes from the clip");

                    return responseData;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
