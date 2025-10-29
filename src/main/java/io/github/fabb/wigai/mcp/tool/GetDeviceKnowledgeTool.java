package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP Tool that provides device parameter knowledge for Bitwig native devices.
 */
public class GetDeviceKnowledgeTool {

    private static final String RESOURCE_FILE = "/bitwig-device-parameters.json";

    public static McpServerFeatures.SyncToolSpecification specification(StructuredLogger logger) {
        var inputSchema = """
            {
              "type": "object",
              "properties": {
                "device_name": {
                  "type": "string",
                  "description": "Optional: Filter by device name (e.g., 'EQ+'). If omitted, returns all devices."
                }
              }
            }
            """;

        var tool = McpSchema.Tool.builder()
            .name("get_device_knowledge")
            .description("""
                Get complete information for Bitwig native devices including: \
                - Device UUIDs (REQUIRED for insert_bitwig_device tool) \
                - Page names and indices \
                - Parameter names, indices, units, ranges \
                - Common use cases with example values \
                \
                IMPORTANT: ALWAYS call this tool BEFORE inserting devices to get the correct UUID! \
                IMPORTANT: ALWAYS call this tool BEFORE manipulating device parameters to understand what each parameter does. \
                \
                Available devices: EQ+, Filter+, Compressor, Delay+, Reverb \
                \
                Example: To insert Filter+ device, first call this tool to get UUID: 6d621c1c-ab64-43b4-aea3-dad37e6f649c
                """)
            .inputSchema(inputSchema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "get_device_knowledge",
                logger,
                () -> {
                    try {
                        // Load the JSON file from resources
                        InputStream is = GetDeviceKnowledgeTool.class.getResourceAsStream(RESOURCE_FILE);
                        if (is == null) {
                            return Map.of("error", "Device knowledge base not found");
                        }

                        String jsonContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = req.arguments();
                        String deviceName = args != null ? (String) args.get("device_name") : null;

                        if (deviceName != null && !deviceName.isEmpty()) {
                            logger.info("GetDeviceKnowledgeTool: Filtering for device: " + deviceName);
                            // For now, return full JSON - filtering can be added later if needed
                        }

                        logger.info("GetDeviceKnowledgeTool: Loaded device knowledge, size: " + jsonContent.length() + " bytes");

                        // Return the JSON content as a string in a map
                        return Map.of("knowledge_base", jsonContent);

                    } catch (Exception e) {
                        logger.info("GetDeviceKnowledgeTool: Error loading device knowledge: " + e.getMessage());
                        e.printStackTrace();
                        return Map.of("error", e.getMessage());
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
