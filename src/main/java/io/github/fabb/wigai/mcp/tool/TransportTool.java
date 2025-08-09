package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.TransportController;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tools for transport control in Bitwig using unified error handling architecture.
 */
public class TransportTool {

    /**
     * Creates a "transport_start" tool specification using the unified error handling system.
     *
     * @param transportController The controller for transport operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "transport_start" tool
     */
    public static McpServerFeatures.SyncToolSpecification transportStartSpecification(
            TransportController transportController, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {}
            }""";
        var tool = McpSchema.Tool.builder()
            .name("transport_start")
            .description("Start Bitwig's transport playbook.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "transport_start",
                logger,
                () -> {
                    String resultMessage = transportController.startTransport();
                    return Map.of(
                        "action", "transport_started",
                        "message", resultMessage
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates a "transport_stop" tool specification using the unified error handling system.
     *
     * @param transportController The controller for transport operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "transport_stop" tool
     */
    public static McpServerFeatures.SyncToolSpecification transportStopSpecification(
            TransportController transportController, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {}
            }""";
        var tool = McpSchema.Tool.builder()
            .name("transport_stop")
            .description("Stop Bitwig's transport playback.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "transport_stop",
                logger,
                () -> {
                    String resultMessage = transportController.stopTransport();
                    return Map.of(
                        "action", "transport_stopped",
                        "message", resultMessage
                    );
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
