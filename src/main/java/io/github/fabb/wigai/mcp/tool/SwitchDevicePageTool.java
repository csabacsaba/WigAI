package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for switching device remote controls pages in Bitwig.
 * Allows switching between different parameter pages (e.g., "Gains", "Freqs", "Qs" for EQ+).
 */
public class SwitchDevicePageTool {

    private static final String TOOL_NAME = "switch_device_page";

    /**
     * Creates the MCP tool specification for device page switching.
     *
     * @param bitwigApi The Bitwig API facade
     * @param logger The structured logger
     * @return MCP tool specification
     */
    public static McpServerFeatures.SyncToolSpecification switchDevicePageSpecification(
            BitwigApiFacade bitwigApi, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "page_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based page index to switch to"
                }
              },
              "required": ["page_index"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Switch the remote controls page for the currently selected device. Device pages contain different parameter mappings (e.g., page 0='Gains', page 1='Freqs', page 2='Qs' for EQ+)")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        SwitchDevicePageArguments args = parseArguments(req.arguments());

                        // Switch to the specified page
                        bitwigApi.switchDeviceRemoteControlsPage(args.pageIndex());

                        // Get current state after switching
                        int currentPage = bitwigApi.getCurrentDeviceRemoteControlsPageIndex();
                        int totalPages = bitwigApi.getDeviceRemoteControlsPageCount();

                        Map<String, Object> result = new HashMap<>();
                        result.put("action", "page_switched");
                        result.put("current_page_index", currentPage);
                        result.put("total_pages", totalPages);
                        result.put("message", "Switched to page " + currentPage + " of " + totalPages);

                        return result;
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Parses and validates the MCP tool arguments.
     *
     * @param arguments Raw arguments map from MCP request
     * @return Parsed and validated arguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static SwitchDevicePageArguments parseArguments(Map<String, Object> arguments) {
        int pageIndex = ParameterValidator.validateRequiredInteger(arguments, "page_index", TOOL_NAME);
        
        if (pageIndex < 0) {
            throw new BitwigApiException(ErrorCode.INVALID_RANGE, TOOL_NAME,
                "page_index must be non-negative, got: " + pageIndex);
        }

        return new SwitchDevicePageArguments(pageIndex);
    }

    /**
     * Data record for validated arguments.
     *
     * @param pageIndex Zero-based page index
     */
    public record SwitchDevicePageArguments(
        @JsonProperty("page_index") int pageIndex
    ) {}
}
