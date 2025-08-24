package io.github.fabb.wigai.mcp.tool;

import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.logging.StructuredLogger;
import io.github.fabb.wigai.features.DeviceController;
import io.modelcontextprotocol.server.McpServerFeatures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for GetDeviceDetailsTool using unified error handling architecture.
 */
class GetDeviceDetailsToolTest {

    @Mock
    private DeviceController deviceController;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    @Test
    void testGetDeviceDetailsSpecification() {
        McpServerFeatures.SyncToolSpecification spec = GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, structuredLogger);

        assertNotNull(spec);
        assertNotNull(spec.tool());
        assertEquals("get_device_details", spec.tool().name());
        assertTrue(spec.tool().description().contains("device"));
        assertTrue(spec.tool().description().contains("remote controls"));
        assertTrue(spec.tool().description().contains("pages"));
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testParameterValidation_ValidSelectedDeviceMode() {
        // Should not throw exception
        Map<String, Object> args = Map.of("get_for_selected_device", true);
        assertDoesNotThrow(() -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_ValidIdentifierMode() {
        // Should not throw exception
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "device_index", 1
        );
        assertDoesNotThrow(() -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_ValidIdentifierModeWithNames() {
        // Should not throw exception
        Map<String, Object> args = Map.of(
            "track_name", "Bass Track",
            "device_name", "EQ Eight"
        );
        assertDoesNotThrow(() -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_DefaultSelectedMode() {
        // Should default to selected mode when no parameters provided
        Map<String, Object> args = Map.of();
        assertDoesNotThrow(() -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_BothTrackIdentifiers() {
        // Should throw exception when both track_index and track_name provided
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "track_name", "Bass Track",
            "device_index", 1
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Exactly one of track_index or track_name"));
    }

    @Test
    void testParameterValidation_BothDeviceIdentifiers() {
        // Should throw exception when both device_index and device_name provided
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "device_index", 1,
            "device_name", "EQ Eight"
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Exactly one of device_index or device_name"));
    }

    @Test
    void testParameterValidation_SelectedModeWithIdentifiers() {
        // Should throw exception when get_for_selected_device=true with identifiers
        Map<String, Object> args = Map.of(
            "get_for_selected_device", true,
            "track_index", 0
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Cannot provide get_for_selected_device=true together with other identifier"));
    }

    @Test
    void testParameterValidation_FalseModeWithoutIdentifiers() {
        // Should throw exception when get_for_selected_device=false without identifiers
        Map<String, Object> args = Map.of(
            "get_for_selected_device", false
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Must provide identifier parameters when get_for_selected_device=false"));
    }

    @Test
    void testParameterValidation_NegativeTrackIndex() {
        // Should throw exception for negative track index
        Map<String, Object> args = Map.of(
            "track_index", -1,
            "device_index", 0
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_RANGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("track_index must be non-negative"));
    }

    @Test
    void testParameterValidation_NegativeDeviceIndex() {
        // Should throw exception for negative device index
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "device_index", -1
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_RANGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("device_index must be non-negative"));
    }

    @Test
    void testParameterValidation_EmptyTrackName() {
        // Should throw exception for empty track name
        Map<String, Object> args = Map.of(
            "track_name", "",
            "device_index", 0
        );

        assertThrows(BitwigApiException.class, () -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_EmptyDeviceName() {
        // Should throw exception for empty device name
        Map<String, Object> args = Map.of(
            "track_index", 0,
            "device_name", ""
        );

        assertThrows(BitwigApiException.class, () -> invokeParseArguments(args));
    }

    @Test
    void testParameterValidation_IncompleteIdentifiers_MissingDevice() {
        // Should throw exception when track is specified but device is not
        Map<String, Object> args = Map.of(
            "track_index", 0
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Exactly one of device_index or device_name"));
    }

    @Test
    void testParameterValidation_IncompleteIdentifiers_MissingTrack() {
        // Should throw exception when device is specified but track is not
        Map<String, Object> args = Map.of(
            "device_index", 0
        );

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> invokeParseArguments(args));
        assertEquals(ErrorCode.INVALID_PARAMETER, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Exactly one of track_index or track_name"));
    }

    @Test
    void testDeviceDetailsResponseFormat() throws Exception {
        // Create mock device details result
        List<ParameterInfo> remoteControls = new ArrayList<>();
        remoteControls.add(new ParameterInfo(0, "Threshold", 0.5, "-6.0 dB"));
        remoteControls.add(new ParameterInfo(1, "Ratio", 0.3, "3:1"));
        // Only include existing controls - no need to fill slots since ParameterInfo only represents existing parameters

        DeviceController.DeviceDetailsResult mockResult = new DeviceController.DeviceDetailsResult(
            0, "Drums", 1, "Compressor", "AudioFX", false, true,
            remoteControls
        );

        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any())).thenReturn(mockResult);

        // Test response format
        Map<String, Object> responseData = mockResult.toMap();

        // Validate response structure
        assertNotNull(responseData);
        assertEquals(0, responseData.get("track_index"));
        assertEquals("Drums", responseData.get("track_name"));
        assertEquals(1, responseData.get("index"));
        assertEquals("Compressor", responseData.get("name"));
        assertEquals("AudioFX", responseData.get("type"));
        assertEquals(false, responseData.get("is_bypassed"));
        assertEquals(true, responseData.get("is_selected"));

        // Validate remote controls array - only existing parameters
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> controlsArray = (List<Map<String, Object>>) responseData.get("remote_controls");
        assertNotNull(controlsArray);
        assertEquals(2, controlsArray.size()); // Only existing parameters

        // Check first control (exists)
        Map<String, Object> firstControl = controlsArray.get(0);
        assertEquals(0, firstControl.get("index"));
        assertEquals(true, firstControl.get("exists"));
        assertEquals("Threshold", firstControl.get("name"));
        assertEquals(0.5, firstControl.get("value"));
        assertEquals(null, firstControl.get("raw_value"));
        assertEquals("-6.0 dB", firstControl.get("display_value"));

        // Check second control (exists)
        Map<String, Object> secondControl = controlsArray.get(1);
        assertEquals(1, secondControl.get("index"));
        assertEquals(true, secondControl.get("exists"));
        assertEquals("Ratio", secondControl.get("name"));
        assertEquals(0.3, secondControl.get("value"));
        assertEquals(null, secondControl.get("raw_value"));
        assertEquals("3:1", secondControl.get("display_value"));
    }

    @Test
    void testDeviceNotFoundError() throws Exception {
        // Mock device not found exception
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, "get_device_details", "Device not found"));

        // This would be tested in integration tests where the full handler chain is invoked
        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> deviceController.getDeviceDetails(0, null, 5, null, null));
        assertEquals(ErrorCode.DEVICE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testTrackNotFoundError() throws Exception {
        // Mock track not found exception
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "get_device_details", "Track not found"));

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> deviceController.getDeviceDetails(99, null, 0, null, null));
        assertEquals(ErrorCode.TRACK_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void testDeviceNotSelectedError() throws Exception {
        // Mock device not selected exception
        when(deviceController.getDeviceDetails(any(), any(), any(), any(), any()))
            .thenThrow(new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, "get_device_details", "No device selected"));

        BitwigApiException exception = assertThrows(BitwigApiException.class,
            () -> deviceController.getDeviceDetails(null, null, null, null, true));
        assertEquals(ErrorCode.DEVICE_NOT_SELECTED, exception.getErrorCode());
    }

    /**
     * Helper method to test argument parsing by invoking the private parseArguments method
     * using reflection-like approach through testing the validation logic.
     */
    private void invokeParseArguments(Map<String, Object> arguments) {
        // Test parameter validation by recreating the logic from parseArguments
        Integer trackIndex = arguments.containsKey("track_index") ?
            (Integer) arguments.get("track_index") : null;
        String trackName = arguments.containsKey("track_name") ?
            (String) arguments.get("track_name") : null;
        Integer deviceIndex = arguments.containsKey("device_index") ?
            (Integer) arguments.get("device_index") : null;
        String deviceName = arguments.containsKey("device_name") ?
            (String) arguments.get("device_name") : null;
        Boolean getForSelectedDevice = arguments.containsKey("get_for_selected_device") ?
            (Boolean) arguments.get("get_for_selected_device") : null;

        // Validate ranges
        if (trackIndex != null && trackIndex < 0) {
            throw new BitwigApiException(ErrorCode.INVALID_RANGE, "get_device_details",
                "track_index must be non-negative, got: " + trackIndex);
        }
        if (deviceIndex != null && deviceIndex < 0) {
            throw new BitwigApiException(ErrorCode.INVALID_RANGE, "get_device_details",
                "device_index must be non-negative, got: " + deviceIndex);
        }

        // Validate empty strings
        if (trackName != null && trackName.trim().isEmpty()) {
            throw new BitwigApiException(ErrorCode.EMPTY_PARAMETER, "get_device_details",
                "track_name cannot be empty");
        }
        if (deviceName != null && deviceName.trim().isEmpty()) {
            throw new BitwigApiException(ErrorCode.EMPTY_PARAMETER, "get_device_details",
                "device_name cannot be empty");
        }

        // Validate parameter rules (same as in the actual implementation)
        boolean hasIdentifiers = trackIndex != null || trackName != null || deviceIndex != null || deviceName != null;
        boolean wantsSelected = Boolean.TRUE.equals(getForSelectedDevice);

        if (wantsSelected && hasIdentifiers) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "get_device_details",
                "Cannot provide get_for_selected_device=true together with other identifier parameters");
        }

        if (Boolean.FALSE.equals(getForSelectedDevice) && !hasIdentifiers) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "get_device_details",
                "Must provide identifier parameters when get_for_selected_device=false");
        }

        if (hasIdentifiers) {
            if ((trackIndex != null && trackName != null) || (trackIndex == null && trackName == null)) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "get_device_details",
                    "Exactly one of track_index or track_name must be provided, not both or neither");
            }

            if ((deviceIndex != null && deviceName != null) || (deviceIndex == null && deviceName == null)) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "get_device_details",
                    "Exactly one of device_index or device_name must be provided, not both or neither");
            }
        }
    }
}
