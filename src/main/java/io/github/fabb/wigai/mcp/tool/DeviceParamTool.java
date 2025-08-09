package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.data.ParameterSetting;
import io.github.fabb.wigai.common.data.ParameterSettingResult;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.common.validation.ParameterValidator;
import io.github.fabb.wigai.features.DeviceController;
import io.github.fabb.wigai.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;

/**
 * MCP tool for device parameter operations in Bitwig using unified error handling architecture.
 */
public class DeviceParamTool {

    private static final String GET_PARAMETERS_TOOL = "get_selected_device_parameters";
    private static final String SET_PARAMETER_TOOL = "set_selected_device_parameter";
    private static final String SET_MULTIPLE_PARAMETERS_TOOL = "set_selected_device_parameters";

    /**
     * Creates a "get_selected_device_parameters" tool specification.
     *
     * @param deviceController The controller for device operations
     * @param logger           The structured logger for logging operations
     * @return A SyncToolSpecification for the "get_selected_device_parameters" tool
     */
    public static McpServerFeatures.SyncToolSpecification getSelectedDeviceParametersSpecification(
            DeviceController deviceController, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {}
            }""";
        var tool = McpSchema.Tool.builder()
            .name(GET_PARAMETERS_TOOL)
            .description("Get the names and values of all addressable parameters of the user-selected device in Bitwig.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                GET_PARAMETERS_TOOL,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        DeviceController.DeviceParametersResult result = deviceController.getSelectedDeviceParameters();
                        Map<String, Object> responseData = new LinkedHashMap<>();
                        responseData.put("device_name", result.deviceName());
                        List<Map<String, Object>> parametersArray = new ArrayList<>();
                        for (ParameterInfo param : result.parameters()) {
                            Map<String, Object> paramMap = new LinkedHashMap<>();
                            paramMap.put("index", param.index());
                            paramMap.put("name", param.name());
                            paramMap.put("value", param.value());
                            paramMap.put("display_value", param.display_value());
                            parametersArray.add(paramMap);
                        }
                        responseData.put("parameters", parametersArray);
                        return responseData;
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Creates a "set_selected_device_parameter" tool specification.
     *
     * @param deviceController The controller for device operations
     * @param logger           The structured logger for logging operations
     * @return A SyncToolSpecification for the "set_selected_device_parameter" tool
     */
    public static McpServerFeatures.SyncToolSpecification setSelectedDeviceParameterSpecification(
            DeviceController deviceController, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "parameter_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "The index of the parameter to set (0-based)"
                },
                "value": {
                  "type": "number",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "description": "The value to set (0.0-1.0)"
                }
              },
              "required": ["parameter_index", "value"]
            }""";
        var tool = McpSchema.Tool.builder()
            .name(SET_PARAMETER_TOOL)
            .description("Set a specific value for a single parameter (by its index) of the user-selected device in Bitwig.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SET_PARAMETER_TOOL,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        SetParameterArguments args = parseSetParameterArguments(req.arguments());
                        deviceController.setSelectedDeviceParameter(args.parameterIndex(), args.value());
                        return Map.of(
                            "action", "parameter_set",
                            "parameter_index", args.parameterIndex(),
                            "new_value", args.value(),
                            "message", "Parameter " + args.parameterIndex() + " set to " + args.value() + "."
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
     * Creates a "set_selected_device_parameters" tool specification.
     *
     * @param deviceController The controller for device operations
     * @param logger           The structured logger for logging operations
     * @return A SyncToolSpecification for the "set_selected_device_parameters" tool
     */
    public static McpServerFeatures.SyncToolSpecification setMultipleDeviceParametersSpecification(
            DeviceController deviceController, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "parameters": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "parameter_index": {
                        "type": "integer",
                        "minimum": 0,
                        "description": "The index of the parameter to set (0-based)"
                      },
                      "value": {
                        "type": "number",
                        "minimum": 0.0,
                        "maximum": 1.0,
                        "description": "The value to set (0.0-1.0)"
                      }
                    },
                    "required": ["parameter_index", "value"]
                  },
                  "description": "List of parameter settings to apply"
                }
              },
              "required": ["parameters"]
            }""";
        var tool = McpSchema.Tool.builder()
            .name(SET_MULTIPLE_PARAMETERS_TOOL)
            .description("Set multiple parameter values (by index) of the user-selected device in Bitwig simultaneously.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                SET_MULTIPLE_PARAMETERS_TOOL,
                logger,
                new McpErrorHandler.ToolOperation() {
                    @Override
                    public Object execute() throws Exception {
                        SetMultipleParametersArguments args = parseSetMultipleParametersArguments(req.arguments());
                        List<ParameterSettingResult> results = deviceController.setMultipleSelectedDeviceParameters(args.parameters());
                        List<Map<String, Object>> resultsArray = new ArrayList<>();
                        for (ParameterSettingResult result : results) {
                            Map<String, Object> resultMap = new LinkedHashMap<>();
                            resultMap.put("parameter_index", result.parameter_index());
                            resultMap.put("status", result.status());
                            if ("success".equals(result.status())) {
                                resultMap.put("new_value", result.new_value());
                            } else {
                                resultMap.put("error_code", result.error_code());
                                resultMap.put("message", result.message());
                            }
                            resultsArray.add(resultMap);
                        }
                        long successCount = results.stream().filter(r -> "success".equals(r.status())).count();
                        long errorCount = results.size() - successCount;
                        return Map.of(
                            "action", "multiple_parameters_set",
                            "results", resultsArray,
                            "message", "Batch operation completed: " + successCount + " succeeded, " + errorCount + " failed"
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
     * Parses the arguments for setting a single parameter.
     */
    private static SetParameterArguments parseSetParameterArguments(Map<String, Object> arguments) {
        int parameterIndex = ParameterValidator.validateRequiredInteger(arguments, "parameter_index", SET_PARAMETER_TOOL);

        double value = ParameterValidator.validateRequiredDouble(arguments, "value", SET_PARAMETER_TOOL);
        value = ParameterValidator.validateParameterValue(value, SET_PARAMETER_TOOL);

        return new SetParameterArguments(parameterIndex, value);
    }

    /**
     * Parses the arguments for setting multiple parameters.
     */
    @SuppressWarnings("unchecked")
    private static SetMultipleParametersArguments parseSetMultipleParametersArguments(Map<String, Object> arguments) {
        Object parametersObj = ParameterValidator.validateRequired(arguments, "parameters", SET_MULTIPLE_PARAMETERS_TOOL);

        if (!(parametersObj instanceof List)) {
            throw new IllegalArgumentException("'parameters' must be an array");
        }

        List<Object> parametersArray = (List<Object>) parametersObj;
        if (parametersArray.isEmpty()) {
            throw new IllegalArgumentException("'parameters' array cannot be empty");
        }

        List<ParameterSetting> parameterSettings = new ArrayList<>();
        for (Object paramObj : parametersArray) {
            if (!(paramObj instanceof Map)) {
                throw new IllegalArgumentException("Each parameter entry must be an object");
            }

            Map<String, Object> paramMap = (Map<String, Object>) paramObj;

            int parameterIndex = ParameterValidator.validateRequiredInteger(paramMap, "parameter_index", SET_MULTIPLE_PARAMETERS_TOOL);

            double value = ParameterValidator.validateRequiredDouble(paramMap, "value", SET_MULTIPLE_PARAMETERS_TOOL);
            value = ParameterValidator.validateParameterValue(value, SET_MULTIPLE_PARAMETERS_TOOL);

            parameterSettings.add(new ParameterSetting(parameterIndex, value));
        }

        return new SetMultipleParametersArguments(parameterSettings);
    }

    /**
     * Data record for validated set parameter arguments.
     */
    public record SetParameterArguments(
        @JsonProperty("parameter_index") int parameterIndex,
        @JsonProperty("value") double value
    ) {}

    /**
     * Data record for validated set multiple parameters arguments.
     */
    public record SetMultipleParametersArguments(
        @JsonProperty("parameters") List<ParameterSetting> parameters
    ) {}
}
