//package io.github.fabb.wigai.mcp.tool;
//
//import io.github.fabb.wigai.bitwig.BitwigApiFacade;
//import io.github.fabb.wigai.common.error.BitwigApiException;
//import io.github.fabb.wigai.common.error.ErrorCode;
//import io.github.fabb.wigai.common.logging.StructuredLogger;
//import io.modelcontextprotocol.server.McpSyncServerExchange;
//import io.modelcontextprotocol.spec.McpSchema;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.ArgumentCaptor;
//
//import java.util.HashMap;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * Unit tests for MidiTool functionality.
// * Tests MIDI message sending capabilities including Note On, Note Off, and Control Change.
// */
//class MidiToolTest {
//
//    private BitwigApiFacade mockBitwigApi;
//    private StructuredLogger mockLogger;
//    private McpSyncServerExchange mockExchange;
//
//    @BeforeEach
//    void setUp() {
//        mockBitwigApi = mock(BitwigApiFacade.class);
//        mockLogger = mock(StructuredLogger.class);
//        mockExchange = mock(McpSyncServerExchange.class);
//    }
//
//    @Test
//    void testSendMidiNoteOn_Success() throws Exception {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "note_on");
//        args.put("channel", 0);
//        args.put("pitch", 60);
//        args.put("velocity", 100);
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi).sendMidiNoteOn(0, 60, 100);
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//        assertEquals(1, content.size());
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("midi_note_on_sent"));
//        assertTrue(textContent.text().contains("\"channel\":0"));
//        assertTrue(textContent.text().contains("\"pitch\":60"));
//        assertTrue(textContent.text().contains("\"velocity\":100"));
//    }
//
//    @Test
//    void testSendMidiNoteOff_Success() throws Exception {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "note_off");
//        args.put("channel", 1);
//        args.put("pitch", 64);
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi).sendMidiNoteOff(1, 64);
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("midi_note_off_sent"));
//        assertTrue(textContent.text().contains("\"channel\":1"));
//        assertTrue(textContent.text().contains("\"pitch\":64"));
//    }
//
//    @Test
//    void testSendMidiCC_Success() throws Exception {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "cc");
//        args.put("channel", 2);
//        args.put("controller", 7);
//        args.put("value", 127);
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi).sendMidiCC(2, 7, 127);
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("midi_cc_sent"));
//        assertTrue(textContent.text().contains("\"channel\":2"));
//        assertTrue(textContent.text().contains("\"controller\":7"));
//        assertTrue(textContent.text().contains("\"value\":127"));
//    }
//
//    @Test
//    void testSendMidiNoteOn_MissingVelocity() {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "note_on");
//        args.put("channel", 0);
//        args.put("pitch", 60);
//        // Missing velocity parameter
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi, never()).sendMidiNoteOn(anyInt(), anyInt(), anyInt());
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("error"));
//        assertTrue(textContent.text().toLowerCase().contains("velocity"));
//    }
//
//    @Test
//    void testSendMidiCC_MissingController() {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "cc");
//        args.put("channel", 0);
//        args.put("value", 64);
//        // Missing controller parameter
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi, never()).sendMidiCC(anyInt(), anyInt(), anyInt());
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("error"));
//        assertTrue(textContent.text().toLowerCase().contains("controller"));
//    }
//
//    @Test
//    void testSendMidi_InvalidMessageType() {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "invalid_type");
//        args.put("channel", 0);
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi, never()).sendMidiNoteOn(anyInt(), anyInt(), anyInt());
//        verify(mockBitwigApi, never()).sendMidiNoteOff(anyInt(), anyInt());
//        verify(mockBitwigApi, never()).sendMidiCC(anyInt(), anyInt(), anyInt());
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("error"));
//        assertTrue(textContent.text().toLowerCase().contains("message_type"));
//    }
//
//    @Test
//    void testSendMidiNoteOn_ApiException() throws Exception {
//        // Arrange
//        Map<String, Object> args = new HashMap<>();
//        args.put("message_type", "note_on");
//        args.put("channel", 0);
//        args.put("pitch", 60);
//        args.put("velocity", 100);
//
//        doThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "send_midi", "API error occurred"))
//            .when(mockBitwigApi).sendMidiNoteOn(anyInt(), anyInt(), anyInt());
//
//        var request = McpSchema.CallToolRequest.builder()
//            .params(McpSchema.CallToolParams.builder()
//                .name("send_midi")
//                .arguments(args)
//                .build())
//            .build();
//
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//        var result = spec.handler().apply(mockExchange, request);
//
//        // Assert
//        verify(mockBitwigApi).sendMidiNoteOn(0, 60, 100);
//
//        assertNotNull(result);
//        assertTrue(result.isContent());
//
//        var content = result.content();
//        assertNotNull(content);
//
//        var textContent = content.get(0);
//        assertTrue(textContent.text().contains("error"));
//    }
//
//    @Test
//    void testToolSpecification() {
//        // Act
//        var spec = MidiTool.specification(mockBitwigApi, mockLogger);
//
//        // Assert
//        assertNotNull(spec);
//        assertNotNull(spec.tool());
//        assertEquals("send_midi", spec.tool().name());
//        assertNotNull(spec.tool().description());
//        assertTrue(spec.tool().description().contains("MIDI"));
//        assertNotNull(spec.tool().inputSchema());
//        assertNotNull(spec.handler());
//    }
//}
