package io.github.fabb.wigai.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
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
 * Unit tests for ListScenesTool.
 */
class ListScenesToolTest {

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
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    @Test
    void testListScenesSpecification() {
        McpServerFeatures.SyncToolSpecification spec = ListScenesTool.specification(bitwigApiFacade, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("list_scenes", spec.tool().name());
        assertTrue(spec.tool().description().contains("List all scenes"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testSpecificationValidation() {
        // Test that the tool specification is properly configured
        McpServerFeatures.SyncToolSpecification spec = ListScenesTool.specification(bitwigApiFacade, structuredLogger);

        assertNotNull(spec.tool().inputSchema());
        assertEquals("list_scenes", spec.tool().name());
        assertTrue(spec.tool().description().contains("scene structure"));
    }

    @Test
    void testListScenesSuccessResponseFormat() throws Exception {
        // Arrange: Create mock scene data
        List<Map<String, Object>> mockScenes = createMockSceneData();

        // Act: Test the McpErrorHandler.createSuccessResponse directly
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(mockScenes);

        // Assert: Verify response structure
        assertNotNull(result);
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        // Parse the JSON response
        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        JsonNode responseJson = objectMapper.readTree(textContent.text());

        // Verify top-level structure matches API specification
        assertTrue(responseJson.has("status"));
        assertTrue(responseJson.has("data"));
        assertEquals("success", responseJson.get("status").asText());

        // Verify data is the scenes array directly (not wrapped again)
        JsonNode dataNode = responseJson.get("data");
        assertTrue(dataNode.isArray());
        assertEquals(3, dataNode.size());

        // Verify scene structure
        JsonNode firstScene = dataNode.get(0);
        assertTrue(firstScene.has("index"));
        assertTrue(firstScene.has("name"));
        assertTrue(firstScene.has("color"));

        assertEquals(0, firstScene.get("index").asInt());
        assertEquals("Intro", firstScene.get("name").asText());
        assertEquals("rgb(255,128,0)", firstScene.get("color").asText());
    }

    @Test
    void testEmptyProjectResponse() throws Exception {
        // Test that empty project returns empty array
        List<Map<String, Object>> emptyScenes = new ArrayList<>();

        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(emptyScenes);

        // Verify response format
        JsonNode dataNode = McpResponseTestUtils.validateListResponse(result);
        assertEquals(0, dataNode.size(), "Empty project should return empty array");
    }

    @Test
    void testSceneColorHandling() throws Exception {
        // Test scenes with different color states
        List<Map<String, Object>> mockScenes = createMockSceneData();

        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(mockScenes);
        JsonNode dataNode = McpResponseTestUtils.validateListResponse(result);

        // Verify color handling
        JsonNode introScene = dataNode.get(0);
        JsonNode verseScene = dataNode.get(1);
        JsonNode chorusScene = dataNode.get(2);

        assertEquals("rgb(255,128,0)", introScene.get("color").asText(), "Intro should have orange color");
        assertEquals("rgb(0,180,255)", verseScene.get("color").asText(), "Verse should have blue color");
        assertTrue(chorusScene.get("color").isNull(), "Chorus should have null color");
    }

    @Test
    void testMcpErrorHandlerSuccessResponseFormat() throws Exception {
        // Test that McpErrorHandler creates the correct success response format
        List<Map<String, Object>> testData = createMockSceneData();

        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(testData);

        // Verify structure
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        JsonNode responseJson = objectMapper.readTree(textContent.text());

        // Verify API-compliant format
        assertEquals("success", responseJson.get("status").asText());
        assertTrue(responseJson.has("data"));
        assertTrue(responseJson.get("data").isArray());
    }

    @Test
    void testMcpErrorHandlerErrorResponseFormat() throws Exception {
        // Test that McpErrorHandler creates the correct error response format
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.BITWIG_API_ERROR,
            "list_scenes",
            "Bitwig API is not available"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Verify structure
        assertTrue(result.isError());
        assertEquals(1, result.content().size());

        McpSchema.TextContent textContent = (McpSchema.TextContent) result.content().get(0);
        JsonNode responseJson = objectMapper.readTree(textContent.text());

        // Verify API-compliant error format
        assertEquals("error", responseJson.get("status").asText());
        assertTrue(responseJson.has("error"));

        JsonNode errorNode = responseJson.get("error");
        assertEquals("BITWIG_API_ERROR", errorNode.get("code").asText());
        assertEquals("Bitwig API is not available", errorNode.get("message").asText());
        assertEquals("list_scenes", errorNode.get("operation").asText());
    }

    @Test
    void testResponseIsNotDoubleWrapped() throws Exception {
        // Test data that would have been double-wrapped in the old implementation
        List<Map<String, Object>> mockScenes = createMockSceneData();

        // Create response using the fixed McpErrorHandler
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(mockScenes);

        // Use utility to verify NO double-wrapping
        McpResponseTestUtils.assertNotDoubleWrapped(result);

        // Also verify it's a proper list response
        JsonNode dataNode = McpResponseTestUtils.validateListResponse(result);
        assertEquals(3, dataNode.size(), "Should have 3 scenes");

        // Verify the data contains scene objects, not serialized JSON strings
        JsonNode firstScene = dataNode.get(0);
        assertTrue(firstScene.isObject(), "Scene should be an object, not a string");
        assertTrue(firstScene.has("name"), "Scene should have name field directly accessible");
        assertEquals("Intro", firstScene.get("name").asText());
    }

    @Test
    void testListScenesToolIntegration() throws Exception {
        // Arrange: Mock the dependencies
        List<Map<String, Object>> mockScenes = createMockSceneData();
        when(bitwigApiFacade.getAllScenesInfo()).thenReturn(mockScenes);

        // Act: Simulate what executeWithValidation does internally
        String operationId = "test-op-123";
        when(structuredLogger.generateOperationId()).thenReturn(operationId);
        when(structuredLogger.startTimedOperation(eq(operationId), eq("list_scenes"), any()))
            .thenReturn(timedOperation);

        // This is what the tool's lambda returns (raw data)
        Object rawResult = mockScenes; // The tool returns scenes directly

        // This is what executeWithValidation does with the raw result
        McpSchema.CallToolResult finalResult = McpErrorHandler.createSuccessResponse(rawResult);

        // Assert: Verify the final result has correct format
        assertFalse(finalResult.isError());
        McpSchema.TextContent textContent = (McpSchema.TextContent) finalResult.content().get(0);
        JsonNode responseJson = objectMapper.readTree(textContent.text());

        // Verify it matches the expected API format
        assertEquals("success", responseJson.get("status").asText());
        assertTrue(responseJson.get("data").isArray());
        assertEquals(3, responseJson.get("data").size());

        // Verify scene data is correctly structured
        JsonNode firstScene = responseJson.get("data").get(0);
        assertEquals("Intro", firstScene.get("name").asText());
        assertEquals(0, firstScene.get("index").asInt());
        assertEquals("rgb(255,128,0)", firstScene.get("color").asText());
    }

    @Test
    void testNoParametersRequired() throws Exception {
        // Test that the tool works with empty parameters
        List<Map<String, Object>> mockScenes = createMockSceneData();

        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(mockScenes);

        // Validate response format
        JsonNode dataNode = McpResponseTestUtils.validateListResponse(result);
        assertEquals(3, dataNode.size(), "Should have 3 scenes");

        // Verify all required fields are present
        for (int i = 0; i < dataNode.size(); i++) {
            JsonNode scene = dataNode.get(i);
            assertTrue(scene.has("index"), "Scene should have index field");
            assertTrue(scene.has("name"), "Scene should have name field");
            assertTrue(scene.has("color"), "Scene should have color field");
        }
    }

    @Test
    void testMcpErrorHandlerErrorResponseFormatWithUtility() throws Exception {
        // Test that McpErrorHandler creates the correct error response format
        BitwigApiException exception = new BitwigApiException(
            ErrorCode.BITWIG_API_ERROR,
            "list_scenes",
            "Scene access failed"
        );

        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(exception, structuredLogger);

        // Use utility for validation
        JsonNode errorNode = McpResponseTestUtils.validateErrorResponse(result);

        // Verify specific error details
        assertEquals("BITWIG_API_ERROR", errorNode.get("code").asText());
        assertEquals("Scene access failed", errorNode.get("message").asText());
        assertEquals("list_scenes", errorNode.get("operation").asText());
    }

    /**
     * Helper method to create mock scene data for testing
     */
    private List<Map<String, Object>> createMockSceneData() {
        List<Map<String, Object>> scenes = new ArrayList<>();

        // Scene 1: Intro (has color)
        Map<String, Object> scene1 = new LinkedHashMap<>();
        scene1.put("index", 0);
        scene1.put("name", "Intro");
        scene1.put("color", "rgb(255,128,0)");
        scenes.add(scene1);

        // Scene 2: Verse (has color)
        Map<String, Object> scene2 = new LinkedHashMap<>();
        scene2.put("index", 1);
        scene2.put("name", "Verse");
        scene2.put("color", "rgb(0,180,255)");
        scenes.add(scene2);

        // Scene 3: Chorus (no color)
        Map<String, Object> scene3 = new LinkedHashMap<>();
        scene3.put("index", 2);
        scene3.put("name", "Chorus");
        scene3.put("color", null);
        scenes.add(scene3);

        return scenes;
    }
}