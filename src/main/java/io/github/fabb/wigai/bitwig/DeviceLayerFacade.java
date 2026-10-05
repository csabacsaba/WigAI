package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Access to the layers of devices that have layers (e.g. the Instrument Layer, FX Layer or
 * Drum Machine-like containers).
 * <p>
 * Bitwig requires banks to be created during extension initialization, so a layer bank cannot be
 * created per device (that would be 128 tracks x 128 devices). Instead there is one layer bank on
 * the cursor device. To read or change the layers of an arbitrary device, that device is first
 * selected with {@link Device#selectInEditor()}, after which the cursor layer bank reflects it.
 * <p>
 * Side effect: the device (and its track) becomes the selected one in Bitwig.
 */
public class DeviceLayerFacade {

    /** Maximum number of layers that can be addressed per device. */
    public static final int MAX_LAYERS = 16;

    private static final long SELECTION_TIMEOUT_MS = 2000;
    private static final long POLL_INTERVAL_MS = 25;
    private static final long SETTLE_MS = 150;

    private final Logger logger;
    private final CursorTrack cursorTrack;
    private final CursorDevice cursorDevice;
    private final TrackBank trackBank;
    private final List<DeviceBank> trackDeviceBanks;
    private final DeviceLayerBank layerBank;

    /**
     * Must be constructed during extension initialization (it creates a bank and observers).
     */
    public DeviceLayerFacade(Logger logger,
                             CursorTrack cursorTrack,
                             CursorDevice cursorDevice,
                             TrackBank trackBank,
                             List<DeviceBank> trackDeviceBanks) {
        this.logger = logger;
        this.cursorTrack = cursorTrack;
        this.cursorDevice = cursorDevice;
        this.trackBank = trackBank;
        this.trackDeviceBanks = trackDeviceBanks;

        this.layerBank = cursorDevice.createLayerBank(MAX_LAYERS);

        cursorDevice.position().markInterested();
        cursorDevice.hasLayers().markInterested();

        for (int i = 0; i < MAX_LAYERS; i++) {
            DeviceLayer layer = layerBank.getItemAt(i);
            layer.exists().markInterested();
            layer.name().markInterested();
            layer.isActivated().markInterested();
            layer.mute().markInterested();
            layer.solo().markInterested();
            layer.volume().value().markInterested();
            layer.volume().displayedValue().markInterested();
            layer.pan().value().markInterested();
            layer.pan().displayedValue().markInterested();
        }
    }

    /**
     * Lists the layers of the device at the given track/device position.
     */
    public Map<String, Object> listLayers(int trackIndex, int deviceIndex) throws BitwigApiException {
        final String operation = "listDeviceLayers";
        try {
            selectDevice(trackIndex, deviceIndex, operation);

            List<Map<String, Object>> layers = new ArrayList<>();
            for (int i = 0; i < MAX_LAYERS; i++) {
                DeviceLayer layer = layerBank.getItemAt(i);
                if (!layer.exists().get()) {
                    continue;
                }
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("index", i);
                info.put("name", layer.name().get());
                info.put("volume", layer.volume().value().get());
                info.put("volume_display", layer.volume().displayedValue().get());
                info.put("pan", layer.pan().value().get());
                info.put("pan_display", layer.pan().displayedValue().get());
                info.put("mute", layer.mute().get());
                info.put("solo", layer.solo().get());
                info.put("activated", layer.isActivated().get());
                layers.add(info);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("track_index", trackIndex);
            result.put("device_index", deviceIndex);
            result.put("device_name", cursorDevice.name().get());
            result.put("layer_count", layers.size());
            result.put("layers", layers);
            return result;
        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("DeviceLayerFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to list device layers: " + e.getMessage());
        }
    }

    /**
     * Sets properties of one layer of the device at the given track/device position.
     * All of mute, solo, volume and pan are optional.
     */
    public void setLayerProperties(int trackIndex, int deviceIndex, int layerIndex,
                                   Boolean mute, Boolean solo, Double volume, Double pan)
            throws BitwigApiException {
        final String operation = "setDeviceLayerProperties";
        try {
            if (layerIndex < 0 || layerIndex >= MAX_LAYERS) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Layer index must be between 0 and " + (MAX_LAYERS - 1) + ", got: " + layerIndex);
            }
            if (volume != null && (volume < 0.0 || volume > 1.0)) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Volume must be between 0.0 and 1.0, got: " + volume);
            }
            if (pan != null && (pan < 0.0 || pan > 1.0)) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Pan must be between 0.0 and 1.0, got: " + pan);
            }

            selectDevice(trackIndex, deviceIndex, operation);

            DeviceLayer layer = layerBank.getItemAt(layerIndex);
            if (!layer.exists().get()) {
                throw new BitwigApiException(ErrorCode.RESOURCE_UNAVAILABLE, operation,
                    "Layer at index " + layerIndex + " does not exist on device '"
                        + cursorDevice.name().get() + "'");
            }

            if (mute != null) {
                layer.mute().set(mute);
            }
            if (solo != null) {
                layer.solo().set(solo);
            }
            if (volume != null) {
                setNormalized(layer.volume(), volume);
            }
            if (pan != null) {
                setNormalized(layer.pan(), pan);
            }
            logger.info("DeviceLayerFacade: Updated layer " + layerIndex + " of device " + deviceIndex
                + " on track " + trackIndex);
        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("DeviceLayerFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to set device layer properties: " + e.getMessage());
        }
    }

    /**
     * Selects the device so the cursor layer bank reflects it, and waits until Bitwig has caught up.
     */
    private void selectDevice(int trackIndex, int deviceIndex, String operation) throws BitwigApiException {
        if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank() || trackIndex >= trackDeviceBanks.size()) {
            throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex);
        }
        Track track = trackBank.getItemAt(trackIndex);
        if (!track.exists().get()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "Track at index " + trackIndex + " does not exist");
        }

        DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
        if (deviceIndex < 0 || deviceIndex >= deviceBank.getSizeOfBank()) {
            throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                "Device index must be between 0 and " + (deviceBank.getSizeOfBank() - 1) + ", got: " + deviceIndex);
        }
        Device device = deviceBank.getItemAt(deviceIndex);
        if (!device.exists().get()) {
            throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                "No device at index " + deviceIndex + " on track " + trackIndex);
        }

        final String trackName = track.name().get();
        if (!isCursorOn(trackName, deviceIndex)) {
            device.selectInEditor();
            waitUntil(() -> isCursorOn(trackName, deviceIndex));
        }
        if (!isCursorOn(trackName, deviceIndex)) {
            // Retry: select the track first, then the device
            logger.info("DeviceLayerFacade: device selection did not take effect, retrying via track selection");
            track.selectInMixer();
            sleep(SETTLE_MS);
            device.selectInEditor();
            waitUntil(() -> isCursorOn(trackName, deviceIndex));
        }
        if (!isCursorOn(trackName, deviceIndex)) {
            throw new BitwigApiException(ErrorCode.BITWIG_TIMEOUT, operation,
                "Timed out waiting for Bitwig to select device " + deviceIndex + " on track " + trackIndex
                    + " ('" + trackName + "'). Cursor is on track '" + cursorTrack.name().get()
                    + "', device position " + cursorDevice.position().get()
                    + " (device exists: " + cursorDevice.exists().get() + ")");
        }

        // Wait for the layer information of the newly selected device to arrive.
        waitUntil(() -> cursorDevice.hasLayers().get());
        if (!cursorDevice.hasLayers().get()) {
            throw new BitwigApiException(ErrorCode.RESOURCE_UNAVAILABLE, operation,
                "Device '" + cursorDevice.name().get() + "' has no layers");
        }
        sleep(SETTLE_MS);
    }

    /**
     * Sets a normalized (0.0-1.0) value through the parameter's value object. Writing via
     * {@code parameter.set(...)} is silently ignored for channel parameters, while
     * {@code parameter.value().setImmediately(...)} is applied (same approach as the device
     * parameter code in BitwigApiFacade).
     */
    private static void setNormalized(Parameter parameter, double normalized) {
        parameter.value().setImmediately(normalized);
    }

    private boolean isCursorOn(String trackName, int deviceIndex) {
        return cursorTrack.exists().get()
            && cursorDevice.exists().get()
            && trackName.equals(cursorTrack.name().get())
            && cursorDevice.position().get() == deviceIndex;
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + SELECTION_TIMEOUT_MS;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(POLL_INTERVAL_MS);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
