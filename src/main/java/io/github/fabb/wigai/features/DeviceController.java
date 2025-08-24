package io.github.fabb.wigai.features;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.data.ParameterSetting;
import io.github.fabb.wigai.common.data.ParameterSettingResult;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Controller class for device parameter control features.
 * Bridges between MCP tools and Bitwig API operations for device parameter control.
 */
public class DeviceController {
    private final BitwigApiFacade bitwigApiFacade;
    private final Logger logger;

    /**
     * Creates a new DeviceController instance.
     *
     * @param bitwigApiFacade The facade for Bitwig API interactions
     * @param logger          The logger for logging operations
     */
    public DeviceController(BitwigApiFacade bitwigApiFacade, Logger logger) {
        this.bitwigApiFacade = bitwigApiFacade;
        this.logger = logger;
    }

    /**
     * Gets the parameters of the currently selected device.
     *
     * @return A DeviceParametersResult containing the device name and parameters
     * @throws BitwigApiException if no device is selected or operation fails
     */
    public DeviceParametersResult getSelectedDeviceParameters() throws BitwigApiException {
        logger.info("DeviceController: Getting selected device parameters");

        try {
            String deviceName = bitwigApiFacade.getSelectedDeviceName();
            List<ParameterInfo> parameters = bitwigApiFacade.getSelectedDeviceParameters();

            logger.info("DeviceController: Retrieved device '" + deviceName + "' with " + parameters.size() + " parameters");

            return new DeviceParametersResult(deviceName, parameters);

        } catch (BitwigApiException e) {
            logger.error("DeviceController: Error getting selected device parameters: " + e.getMessage());
            throw e; // Re-throw BitwigApiException as-is
        } catch (Exception e) {
            logger.error("DeviceController: Unexpected error getting selected device parameters: " + e.getMessage());
            throw new BitwigApiException(ErrorCode.INTERNAL_ERROR, "getSelectedDeviceParameters", e.getMessage(), e);
        }
    }

    /**
     * Sets a specific parameter value for the currently selected device.
     *
     * @param parameterIndex The index of the parameter to set (0-7)
     * @param value          The value to set (0.0-1.0)
     * @throws BitwigApiException if parameterIndex is out of range, value is out of range, no device is selected, or Bitwig API error occurs
     */
    public void setSelectedDeviceParameter(int parameterIndex, double value) throws BitwigApiException {
        logger.info("DeviceController: Setting parameter " + parameterIndex + " to " + value);

        try {
            // Use BitwigApiFacade to perform the actual parameter setting
            // This will handle all validation (parameter index, value range, device selection)
            bitwigApiFacade.setSelectedDeviceParameter(parameterIndex, value);

            logger.info("DeviceController: Successfully set parameter " + parameterIndex + " to " + value);

        } catch (BitwigApiException e) {
            logger.error("DeviceController: Error setting parameter " + parameterIndex + ": " + e.getMessage());
            throw e; // Re-throw BitwigApiException as-is
        } catch (Exception e) {
            logger.error("DeviceController: Unexpected error setting parameter " + parameterIndex + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.INTERNAL_ERROR, "setSelectedDeviceParameter", e.getMessage(), e);
        }
    }

    /**
     * Sets multiple parameter values for the currently selected device.
     * Processes each parameter independently, returning structured results for each.
     * Supports partial success - some parameters may succeed while others fail.
     *
     * @param parameters List of parameter settings to apply
     * @return List of results indicating success/failure for each parameter
     * @throws RuntimeException if no device is selected (top-level error)
     */
    public List<ParameterSettingResult> setMultipleSelectedDeviceParameters(List<ParameterSetting> parameters) {
        logger.info("DeviceController: Setting " + parameters.size() + " parameters");

        // First, validate device selection (top-level validation)
        try {
            bitwigApiFacade.getSelectedDeviceName(); // This will throw BitwigApiException if no device selected
        } catch (BitwigApiException e) {
            logger.error("DeviceController: No device selected for batch parameter setting");
            throw e; // Re-throw as-is
        } catch (Exception e) {
            logger.error("DeviceController: Unexpected error checking device selection for batch parameter setting");
            throw new BitwigApiException(ErrorCode.INTERNAL_ERROR, "setMultipleSelectedDeviceParameters", e.getMessage(), e);
        }

        List<ParameterSettingResult> results = new ArrayList<>();

        for (ParameterSetting param : parameters) {
            try {
                logger.info("DeviceController: Processing parameter " + param.parameter_index() + " = " + param.value());

                // Use existing single parameter setting method which handles validation
                bitwigApiFacade.setSelectedDeviceParameter(param.parameter_index(), param.value());

                // Create success result
                ParameterSettingResult result = new ParameterSettingResult(
                    param.parameter_index(),
                    "success",
                    param.value(),
                    null,
                    null
                );
                results.add(result);

                logger.info("DeviceController: Successfully set parameter " + param.parameter_index() + " to " + param.value());

            } catch (BitwigApiException e) {
                // Structured error handling
                ParameterSettingResult result = new ParameterSettingResult(
                    param.parameter_index(),
                    "error",
                    null,
                    e.getErrorCode().getCode(),
                    e.getMessage()
                );
                results.add(result);

                logger.error("DeviceController: BitwigApi error for parameter " + param.parameter_index() + ": " + e.getMessage());

            } catch (Exception e) {
                // Other unexpected errors
                ParameterSettingResult result = new ParameterSettingResult(
                    param.parameter_index(),
                    "error",
                    null,
                    "INTERNAL_ERROR",
                    "Unexpected error setting parameter: " + e.getMessage()
                );
                results.add(result);

                logger.error("DeviceController: Unexpected error setting parameter " + param.parameter_index() + ": " + e.getMessage());
            }
        }

        long successCount = results.stream().filter(r -> "success".equals(r.status())).count();
        long errorCount = results.size() - successCount;
        logger.info("DeviceController: Batch operation completed - " + successCount + " succeeded, " + errorCount + " failed");

        return results;
    }

    /**
     * Gets detailed device information including device properties, remote controls, and remote control pages.
     *
     * @param trackIndex The track index (nullable)
     * @param trackName The track name (nullable)
     * @param deviceIndex The device index (nullable)
     * @param deviceName The device name (nullable)
     * @param getForSelectedDevice Whether to get selected device (nullable)
     * @return DeviceDetailsResult containing all device information
     * @throws BitwigApiException if device/track not found or parameters invalid
     */
    public DeviceDetailsResult getDeviceDetails(Integer trackIndex, String trackName,
                                               Integer deviceIndex, String deviceName,
                                               Boolean getForSelectedDevice) throws BitwigApiException {
        logger.info("DeviceController: Getting device details");

        try {
            return bitwigApiFacade.getDeviceDetails(trackIndex, trackName, deviceIndex, deviceName, getForSelectedDevice);

        } catch (BitwigApiException e) {
            logger.error("DeviceController: Error getting device details: " + e.getMessage());
            throw e; // Re-throw BitwigApiException as-is
        } catch (Exception e) {
            logger.error("DeviceController: Unexpected error getting device details: " + e.getMessage());
            throw new BitwigApiException(ErrorCode.INTERNAL_ERROR, "getDeviceDetails", e.getMessage(), e);
        }
    }

    /**
     * Result record for device parameter queries.
     */
    public record DeviceParametersResult(
        String deviceName,           // Nullable
        List<ParameterInfo> parameters
    ) {}

    /**
     * Result record for device details queries including remote controls and pages.
     */
    public static class DeviceDetailsResult {
        private final int trackIndex;
        private final String trackName;
        private final int index;
        private final String name;
        private final String type;
        private final boolean isBypassed;
        private final boolean isSelected;
        private final List<ParameterInfo> remoteControls;

        public DeviceDetailsResult(int trackIndex, String trackName, int index, String name, String type,
                                 boolean isBypassed, boolean isSelected,
                                 List<ParameterInfo> remoteControls) {
            this.trackIndex = trackIndex;
            this.trackName = trackName;
            this.index = index;
            this.name = name;
            this.type = type;
            this.isBypassed = isBypassed;
            this.isSelected = isSelected;
            this.remoteControls = remoteControls;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("track_index", trackIndex);
            result.put("track_name", trackName);
            result.put("index", index);
            result.put("name", name);
            result.put("type", type);
            result.put("is_bypassed", isBypassed);
            result.put("is_selected", isSelected);

            List<Map<String, Object>> controlsArray = new ArrayList<>();
            for (ParameterInfo control : remoteControls) {
                Map<String, Object> controlMap = new LinkedHashMap<>();
                controlMap.put("index", control.index());
                controlMap.put("exists", true); // Always true since only existing parameters are included
                controlMap.put("name", control.name());
                controlMap.put("value", control.value());
                controlMap.put("raw_value", null); // Not available from ParameterInfo
                controlMap.put("display_value", control.display_value());
                controlsArray.add(controlMap);
            }
            result.put("remote_controls", controlsArray);

            return result;
        }
    }
}
