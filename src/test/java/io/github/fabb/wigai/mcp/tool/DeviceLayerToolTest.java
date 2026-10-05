package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.fabb.wigai.bitwig.DeviceLayerFacade;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeviceLayerTool (list_device_layers, set_device_layer_properties).
 */
class DeviceLayerToolTest {

    @Mock
    private DeviceLayerFacade layerFacade;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;
    @Mock
    private McpSyncServerExchange exchange;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    @Test
    void testListSpecification() {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.listDeviceLayersSpecification(layerFacade, structuredLogger);

        assertEquals("list_device_layers", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
        assertNotNull(spec.callHandler());
    }

    @Test
    void testSetSpecification() {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.setDeviceLayerPropertiesSpecification(layerFacade, structuredLogger);

        assertEquals("set_device_layer_properties", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
        assertNotNull(spec.callHandler());
    }

    @Test
    void testListLayersSuccess() throws Exception {
        Map<String, Object> layer = new LinkedHashMap<>();
        layer.put("index", 0);
        layer.put("name", "Big and Fat Pulser");
        layer.put("volume", 0.79);
        layer.put("mute", false);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("track_index", 5);
        data.put("device_index", 0);
        data.put("device_name", "Instrument Layer");
        data.put("layer_count", 1);
        data.put("layers", List.of(layer));
        when(layerFacade.listLayers(5, 0)).thenReturn(data);

        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.listDeviceLayersSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "list_device_layers", Map.of("track_index", 5, "device_index", 0)));

        JsonNode node = McpResponseTestUtils.validateObjectResponse(result);
        assertEquals("Instrument Layer", node.get("device_name").asText());
        assertEquals(1, node.get("layer_count").asInt());
        assertEquals("Big and Fat Pulser", node.get("layers").get(0).get("name").asText());
        verify(layerFacade).listLayers(5, 0);
    }

    @Test
    void testListLayersMissingParameter() throws Exception {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.listDeviceLayersSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "list_device_layers", Map.of("track_index", 5)));

        McpResponseTestUtils.validateErrorResponse(result);
        verifyNoInteractions(layerFacade);
    }

    @Test
    void testListLayersFacadeError() throws Exception {
        when(layerFacade.listLayers(5, 1)).thenThrow(new BitwigApiException(
            ErrorCode.RESOURCE_UNAVAILABLE, "listDeviceLayers", "Device 'Compressor+' has no layers"));

        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.listDeviceLayersSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "list_device_layers", Map.of("track_index", 5, "device_index", 1)));

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("RESOURCE_UNAVAILABLE", error.get("code").asText());
    }

    @Test
    void testSetLayerPropertiesSuccess() throws Exception {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.setDeviceLayerPropertiesSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "set_device_layer_properties",
            Map.of("track_index", 5, "device_index", 0, "layer_index", 1, "volume", 0.7, "mute", true)));

        JsonNode node = McpResponseTestUtils.validateActionResponse(result, "device_layer_properties_set");
        assertEquals(1, node.get("layer_index").asInt());
        assertEquals(0.7, node.get("volume").asDouble(), 1e-9);
        assertTrue(node.get("mute").asBoolean());
        assertFalse(node.has("pan"));
        verify(layerFacade).setLayerProperties(eq(5), eq(0), eq(1), eq(true), isNull(), eq(0.7), isNull());
    }

    @Test
    void testSetLayerPropertiesAcceptsIntegerVolume() throws Exception {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.setDeviceLayerPropertiesSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "set_device_layer_properties",
            Map.of("track_index", 5, "device_index", 0, "layer_index", 0, "volume", 1)));

        McpResponseTestUtils.validateActionResponse(result, "device_layer_properties_set");
        verify(layerFacade).setLayerProperties(eq(5), eq(0), eq(0), isNull(), isNull(), eq(1.0), isNull());
    }

    @Test
    void testSetLayerPropertiesMissingLayerIndex() throws Exception {
        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.setDeviceLayerPropertiesSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "set_device_layer_properties", Map.of("track_index", 5, "device_index", 0)));

        McpResponseTestUtils.validateErrorResponse(result);
        verifyNoInteractions(layerFacade);
    }

    @Test
    void testSetLayerPropertiesFacadeError() throws Exception {
        doThrow(new BitwigApiException(ErrorCode.INVALID_RANGE, "setDeviceLayerProperties", "Volume must be between 0.0 and 1.0"))
            .when(layerFacade).setLayerProperties(eq(5), eq(0), eq(1), isNull(), isNull(), eq(2.0), isNull());

        McpServerFeatures.SyncToolSpecification spec =
            DeviceLayerTool.setDeviceLayerPropertiesSpecification(layerFacade, structuredLogger);
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request(
            "set_device_layer_properties",
            Map.of("track_index", 5, "device_index", 0, "layer_index", 1, "volume", 2.0)));

        JsonNode error = McpResponseTestUtils.validateErrorResponse(result);
        assertEquals("INVALID_RANGE", error.get("code").asText());
    }

    private static McpSchema.CallToolRequest request(String name, Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder().name(name).arguments(arguments).build();
    }
}
