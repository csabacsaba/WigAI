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
 * MCP Tool for sending MIDI messages to Bitwig Studio.
 * 
 * Supports three types of MIDI messages:
 * - Note On: Start playing a MIDI note
 * - Note Off: Stop playing a MIDI note
 * - Control Change: Send a MIDI CC message
 */
public class MidiTool {

    /**
     * Creates the MCP tool specification for sending MIDI messages.
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            BitwigApiFacade bitwigApiFacade, 
            StructuredLogger logger) {
        
        var schema = """
            {
              "type": "object",
              "properties": {
                "message_type": {
                  "type": "string",
                  "description": "Type of MIDI message to send",
                  "enum": ["note_on", "note_off", "cc"]
                },
                "channel": {
                  "type": "integer",
                  "description": "MIDI channel (0-15)",
                  "minimum": 0,
                  "maximum": 15
                },
                "pitch": {
                  "type": "integer",
                  "description": "MIDI note pitch (0-127, where 60 is middle C). Required for note_on and note_off.",
                  "minimum": 0,
                  "maximum": 127
                },
                "velocity": {
                  "type": "integer",
                  "description": "MIDI note velocity (0-127). Required for note_on.",
                  "minimum": 0,
                  "maximum": 127
                },
                "controller": {
                  "type": "integer",
                  "description": "MIDI controller number (0-127). Required for cc.",
                  "minimum": 0,
                  "maximum": 127
                },
                "value": {
                  "type": "integer",
                  "description": "MIDI controller value (0-127). Required for cc.",
                  "minimum": 0,
                  "maximum": 127
                }
              },
              "required": ["message_type", "channel"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name("send_midi")
            .description("Send MIDI messages (Note On, Note Off, or Control Change) to Bitwig Studio. " +
                       "Use 'note_on' to start playing a note, 'note_off' to stop a note, or 'cc' to send a control change message.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "send_midi",
                logger,
                () -> {
                    Map<String, Object> args = req.arguments();
                    
                    // Extract and validate message_type
                    String messageType = ParameterValidator.validateRequiredString(args, "message_type", "send_midi");
                    
                    // Extract and validate channel
                    Integer channel = ParameterValidator.validateRequiredInteger(args, "channel", "send_midi");
                    
                    // Execute based on message type
                    switch (messageType.toLowerCase()) {
                        case "note_on":
                            return executeNoteOn(bitwigApiFacade, args, channel);
                        case "note_off":
                            return executeNoteOff(bitwigApiFacade, args, channel);
                        case "cc":
                            return executeCC(bitwigApiFacade, args, channel);
                        default:
                            throw new IllegalArgumentException(
                                "Invalid message_type: " + messageType + ". Must be 'note_on', 'note_off', or 'cc'."
                            );
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Executes a MIDI Note On message.
     */
    private static Map<String, Object> executeNoteOn(BitwigApiFacade bitwigApi, Map<String, Object> args, int channel) throws Exception {
        Integer pitch = ParameterValidator.validateRequiredInteger(args, "pitch", "send_midi");
        Integer velocity = ParameterValidator.validateRequiredInteger(args, "velocity", "send_midi");
        
        // Send MIDI Note On
        bitwigApi.sendMidiNoteOn(channel, pitch, velocity);
        
        // Build response
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("action", "midi_note_on_sent");
        responseData.put("channel", channel);
        responseData.put("pitch", pitch);
        responseData.put("velocity", velocity);
        responseData.put("message", String.format("MIDI Note On sent: channel %d, pitch %d (velocity %d)", 
                                                  channel, pitch, velocity));
        
        return responseData;
    }

    /**
     * Executes a MIDI Note Off message.
     */
    private static Map<String, Object> executeNoteOff(BitwigApiFacade bitwigApi, Map<String, Object> args, int channel) throws Exception {
        Integer pitch = ParameterValidator.validateRequiredInteger(args, "pitch", "send_midi");
        
        // Send MIDI Note Off
        bitwigApi.sendMidiNoteOff(channel, pitch);
        
        // Build response
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("action", "midi_note_off_sent");
        responseData.put("channel", channel);
        responseData.put("pitch", pitch);
        responseData.put("message", String.format("MIDI Note Off sent: channel %d, pitch %d", channel, pitch));
        
        return responseData;
    }

    /**
     * Executes a MIDI Control Change message.
     */
    private static Map<String, Object> executeCC(BitwigApiFacade bitwigApi, Map<String, Object> args, int channel) throws Exception {
        Integer controller = ParameterValidator.validateRequiredInteger(args, "controller", "send_midi");
        Integer value = ParameterValidator.validateRequiredInteger(args, "value", "send_midi");
        
        // Send MIDI CC
        bitwigApi.sendMidiCC(channel, controller, value);
        
        // Build response
        Map<String, Object> responseData = new LinkedHashMap<>();
        responseData.put("action", "midi_cc_sent");
        responseData.put("channel", channel);
        responseData.put("controller", controller);
        responseData.put("value", value);
        responseData.put("message", String.format("MIDI CC sent: channel %d, controller %d, value %d", 
                                                  channel, controller, value));
        
        return responseData;
    }
}
