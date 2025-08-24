package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for ListDevicesOnTrackTool.
 */
class ListDevicesOnTrackToolTest {

    @Mock
    private BitwigApiFacade bitwigApiFacade;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;
    @Mock
    private McpSyncServerExchange exchange;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock the logger chain
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        assertNotNull(spec);
        assertEquals("list_devices_on_track", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
        assertNotNull(spec.callHandler());
    }

    @Test
    void testSuccessfulCallWithTrackIndex() throws Exception {
        // Arrange
        List<Map<String, Object>> mockDevices = createMockDevicesList();
        when(bitwigApiFacade.getDevicesOnTrack(eq(1), isNull(), isNull())).thenReturn(mockDevices);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_index", 1);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(1, result.content().size());
        assertTrue(result.content().get(0) instanceof McpSchema.TextContent);

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);

        assertEquals("success", response.get("status").asText());
        assertTrue(response.get("data").isArray());
        assertEquals(2, response.get("data").size());

        // Verify device data
        JsonNode firstDevice = response.get("data").get(0);
        assertEquals(0, firstDevice.get("index").asInt());
        assertEquals("EQ Eight", firstDevice.get("name").asText());
        assertEquals("AudioFX", firstDevice.get("type").asText());
        assertEquals(false, firstDevice.get("bypassed").asBoolean());
        assertEquals(false, firstDevice.get("is_selected").asBoolean());
    }

    @Test
    void testSuccessfulCallWithTrackName() throws Exception {
        // Arrange
        List<Map<String, Object>> mockDevices = createMockDevicesList();
        when(bitwigApiFacade.getDevicesOnTrack(isNull(), eq("Drums"), isNull())).thenReturn(mockDevices);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_name", "Drums");
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("success", response.get("status").asText());
    }

    @Test
    void testSuccessfulCallWithGetSelected() throws Exception {
        // Arrange
        List<Map<String, Object>> mockDevices = createMockDevicesList();
        when(bitwigApiFacade.getDevicesOnTrack(isNull(), isNull(), eq(true))).thenReturn(mockDevices);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("get_selected", true);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("success", response.get("status").asText());
    }

    @Test
    void testDefaultBehaviorWithNoParameters() throws Exception {
        // Arrange - no parameters should default to get_selected=true
        List<Map<String, Object>> mockDevices = createMockDevicesList();
        when(bitwigApiFacade.getDevicesOnTrack(isNull(), isNull(), eq(true))).thenReturn(mockDevices);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of(); // Empty arguments
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("success", response.get("status").asText());
    }

    @Test
    void testEmptyDevicesList() throws Exception {
        // Arrange
        List<Map<String, Object>> emptyDevices = new ArrayList<>();
        when(bitwigApiFacade.getDevicesOnTrack(eq(0), isNull(), isNull())).thenReturn(emptyDevices);

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_index", 0);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertFalse(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("success", response.get("status").asText());
        assertTrue(response.get("data").isArray());
        assertEquals(0, response.get("data").size());
    }

    @Test
    void testInvalidParameterCombination() throws Exception {
        // Arrange - providing multiple parameters should fail
        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of(
            "track_index", 1,
            "track_name", "Drums"
        );
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("error", response.get("status").asText());
        assertEquals("INVALID_PARAMETER", response.get("error").get("code").asText());
    }

    @Test
    void testInvalidTrackIndex() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_index", -1);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("error", response.get("status").asText());
        assertEquals("INVALID_PARAMETER", response.get("error").get("code").asText());
    }

    @Test
    void testEmptyTrackName() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_name", "");
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("error", response.get("status").asText());
        assertEquals("INVALID_PARAMETER", response.get("error").get("code").asText());
    }

    @Test
    void testTrackNotFoundError() throws Exception {
        // Arrange
        when(bitwigApiFacade.getDevicesOnTrack(eq(99), isNull(), isNull()))
            .thenThrow(new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "list_devices_on_track", "Track not found"));

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_index", 99);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("error", response.get("status").asText());
        assertEquals("TRACK_NOT_FOUND", response.get("error").get("code").asText());
        assertEquals("list_devices_on_track", response.get("error").get("operation").asText());
    }

    @Test
    void testBitwigApiError() throws Exception {
        // Arrange
        when(bitwigApiFacade.getDevicesOnTrack(eq(1), isNull(), isNull()))
            .thenThrow(new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "list_devices_on_track", "API error"));

        McpServerFeatures.SyncToolSpecification spec = ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger);

        Map<String, Object> arguments = Map.of("track_index", 1);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_devices_on_track")
            .arguments(arguments)
            .build();

        // Act
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        // Assert
        assertNotNull(result);
        assertTrue(result.isError());

        String responseJson = ((McpSchema.TextContent) result.content().get(0)).text();
        JsonNode response = objectMapper.readTree(responseJson);
        assertEquals("error", response.get("status").asText());
        assertEquals("BITWIG_API_ERROR", response.get("error").get("code").asText());
    }

    /**
     * Creates mock devices list for testing.
     */
    private List<Map<String, Object>> createMockDevicesList() {
        List<Map<String, Object>> devices = new ArrayList<>();

        Map<String, Object> device1 = new LinkedHashMap<>();
        device1.put("index", 0);
        device1.put("name", "EQ Eight");
        device1.put("type", "AudioFX");
        device1.put("bypassed", false);
        device1.put("is_selected", false);
        devices.add(device1);

        Map<String, Object> device2 = new LinkedHashMap<>();
        device2.put("index", 1);
        device2.put("name", "Compressor");
        device2.put("type", "AudioFX");
        device2.put("bypassed", false);
        device2.put("is_selected", true);
        devices.add(device2);

        return devices;
    }
}
