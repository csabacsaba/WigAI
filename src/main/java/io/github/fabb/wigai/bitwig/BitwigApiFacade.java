package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.*;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.data.ParameterInfo;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;
import io.github.fabb.wigai.common.error.WigAIErrorHandler;
import io.github.fabb.wigai.common.validation.ParameterValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facade for Bitwig API interactions.
 * This class abstracts the Bitwig API and provides simplified methods for common operations.
 */
public class BitwigApiFacade {

    /**
     * Constants used throughout the BitwigApiFacade.
     */
    private static final class Constants {
        public static final String DEFAULT_PROJECT_NAME = "Unknown Project";
        public static final String DEFAULT_BEAT_POSITION = "1.1.1:0";
        public static final String DEFAULT_COLOR = "rgb(128,128,128)";
        public static final String DEFAULT_TIME_STRING = "0:00.000";
        public static final int MAX_TRACKS = 128;
        public static final int MAX_SCENES = 128;
        public static final int MAX_DEVICES_PER_TRACK = 128;
        public static final int TICKS_PER_SIXTEENTH = 240;
        public static final int BEATS_PER_MEASURE = 4;
        public static final int SIXTEENTHS_PER_BEAT = 4;
        public static final int DEVICE_PARAMETER_COUNT = 8;
        public static final int PROJECT_PARAMETER_COUNT = 8;

        private Constants() {} // Prevent instantiation
    }

    private final ControllerHost host;
    private final Transport transport;
    private final Application application;
    private final Logger logger;
    private final CursorDevice cursorDevice;
    private final CursorRemoteControlsPage deviceParameterBank;
    private final TrackBank trackBank;
    private final SceneBankFacade sceneBankFacade;
    private final ArrangerFacade arrangerFacade;
    private final CursorTrack cursorTrack;
    private final CursorRemoteControlsPage projectParameterBank;
    private final List<DeviceBank> trackDeviceBanks;
    private NoteInput noteInput; // Nullable - only available if MIDI port exists
    private final Clip cursorClip; // For writing notes to clips
    // private final VstPluginScanner vstPluginScanner; // For scanning available plugins - TEMPORARILY DISABLED

    // Cache for currently selected device and page to avoid unnecessary switches
    private Integer currentlySelectedTrackIndex = null;
    private Integer currentlySelectedDeviceIndex = null;
    private Integer currentlySelectedPageIndex = null;

    /**
     * Creates a new BitwigApiFacade instance.
     *
     * @param host   The Bitwig ControllerHost
     * @param logger The logger for logging operations
     */
    public BitwigApiFacade(ControllerHost host, Logger logger) {
        this.host = host;
        this.transport = host.createTransport();
        this.application = host.createApplication();
        this.logger = logger;

        // Mark transport properties as interested for status queries
        transport.isPlaying().markInterested();
        transport.isArrangerRecordEnabled().markInterested();
        transport.isArrangerLoopEnabled().markInterested();
        transport.isMetronomeEnabled().markInterested();
        transport.tempo().markInterested();
        transport.tempo().value().markInterested();
        transport.timeSignature().markInterested();
        transport.getPosition().markInterested();
        transport.playPositionInSeconds().markInterested();

        // Mark application properties as interested for status queries
        application.projectName().markInterested();
        application.hasActiveEngine().markInterested();

        // Initialize device control - use CursorTrack.createCursorDevice() instead of deprecated host.createCursorDevice()
        this.cursorTrack = host.createCursorTrack(0, 0);
        this.cursorDevice = cursorTrack.createCursorDevice();
        this.deviceParameterBank = cursorDevice.createCursorRemoteControlsPage(Constants.DEVICE_PARAMETER_COUNT);

        // Initialize project parameter access via MasterTrack (project parameters)
        MasterTrack masterTrack = host.createMasterTrack(0);
        this.projectParameterBank = masterTrack.createCursorRemoteControlsPage(Constants.PROJECT_PARAMETER_COUNT);

        // Initialize track bank for clip launching (support up to 128 tracks and 128 scenes for full functionality)
        this.trackBank = host.createTrackBank(Constants.MAX_TRACKS, 0, Constants.MAX_SCENES);
        this.sceneBankFacade = new SceneBankFacade(host, logger, Constants.MAX_SCENES); // Support up to 128 scenes for full functionality
        this.arrangerFacade = new ArrangerFacade(host, logger); // Support for Arranger cue markers

        // Initialize CursorClip for writing notes to clips
        // Grid: 256 steps (16 bars * 16 steps), 128 pitches (full MIDI range)
        this.cursorClip = host.createLauncherCursorClip(256, 128);
        this.cursorClip.scrollToKey(0); // Start at C-1 (MIDI note 0)

        // Initialize device banks for each track to enable device enumeration
        this.trackDeviceBanks = new ArrayList<>();
        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            DeviceBank deviceBank = track.createDeviceBank(Constants.MAX_DEVICES_PER_TRACK);
            trackDeviceBanks.add(deviceBank);
        }

        // Mark interest in device properties to enable value access
        cursorDevice.exists().markInterested();
        cursorDevice.name().markInterested();
        cursorDevice.isEnabled().markInterested();
        cursorDevice.deviceType().markInterested();

        // Mark interest in device parameter bank page navigation
        deviceParameterBank.pageCount().markInterested();
        deviceParameterBank.selectedPageIndex().markInterested();
        deviceParameterBank.pageNames().markInterested();

        // Mark interest in all device parameter properties to enable value access
        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Mark interest in project parameters to enable value access
        for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = projectParameterBank.getParameter(i);
            parameter.exists().markInterested();
            parameter.name().markInterested();
            parameter.value().markInterested();
            parameter.displayedValue().markInterested();
        }

        // Mark interest in cursor track properties for selected track details
        cursorTrack.exists().markInterested();
        cursorTrack.name().markInterested();
        cursorTrack.trackType().markInterested();
        cursorTrack.isGroup().markInterested();
        cursorTrack.mute().markInterested();
        cursorTrack.solo().markInterested();
        cursorTrack.arm().markInterested();
        cursorTrack.position().markInterested();
        cursorTrack.isMonitoring().markInterested();
        cursorTrack.monitorMode().markInterested();
        cursorTrack.volume().value().markInterested();
        cursorTrack.volume().displayedValue().markInterested();
        cursorTrack.pan().value().markInterested();
        cursorTrack.pan().displayedValue().markInterested();

        // Mark interest in track properties for clip launching and track listing
        for (int trackIndex = 0; trackIndex < trackBank.getSizeOfBank(); trackIndex++) {
            Track track = trackBank.getItemAt(trackIndex);
            track.name().markInterested();
            track.exists().markInterested();
            track.trackType().markInterested();
            track.isGroup().markInterested();
            track.isActivated().markInterested();
            track.color().markInterested();

            // Mark interest in device properties for this track
            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            for (int deviceIndex = 0; deviceIndex < deviceBank.getSizeOfBank(); deviceIndex++) {
                Device device = deviceBank.getItemAt(deviceIndex);
                device.exists().markInterested();
                device.name().markInterested();
                device.isEnabled().markInterested();
                device.deviceType().markInterested();
            }

            // Mark interest in commonly used channel controls
            track.mute().markInterested();
            track.solo().markInterested();
            track.arm().markInterested();
            track.volume().value().markInterested();
            track.volume().displayedValue().markInterested();
            track.pan().value().markInterested();
            track.pan().displayedValue().markInterested();
            track.isMonitoring().markInterested();
            track.monitorMode().markInterested();

            // Mark interest in send properties - only if send bank exists and has sends
            try {
                SendBank sendBank = track.sendBank();
                int sendBankSize = sendBank.getSizeOfBank();
                if (sendBankSize > 0) {
                    for (int sendIndex = 0; sendIndex < sendBankSize; sendIndex++) {
                        Send send = sendBank.getItemAt(sendIndex);
                        send.name().markInterested();
                        send.value().markInterested();
                        send.displayedValue().markInterested();
                        send.isEnabled().markInterested();
                    }
                }
            } catch (Exception e) {
                // Some tracks may not have send banks (e.g., master track)
            }

            ClipLauncherSlotBank trackSlots = track.clipLauncherSlotBank();
            for (int slotIndex = 0; slotIndex < trackSlots.getSizeOfBank(); slotIndex++) {
                ClipLauncherSlot slot = trackSlots.getItemAt(slotIndex);
                slot.hasContent().markInterested();
                slot.isPlaying().markInterested();
                slot.isRecording().markInterested();
                slot.isPlaybackQueued().markInterested();
                slot.isRecordingQueued().markInterested();
                slot.isStopQueued().markInterested();
                slot.color().markInterested();
                slot.name().markInterested();
            }
        }

        // Initialize MIDI note input for sending MIDI messages
        // Only create if MIDI ports are available
        try {
            this.noteInput = host.getMidiInPort(0).createNoteInput("WigAI MIDI", "??????");
            this.noteInput.setShouldConsumeEvents(false);
            logger.info("BitwigApiFacade: MIDI input initialized successfully");
        } catch (Exception e) {
            logger.info("BitwigApiFacade: MIDI input not available: " + e.getMessage());
            this.noteInput = null;
        }

        // Initialize VST Plugin Scanner - TEMPORARILY DISABLED
        // this.vstPluginScanner = new VstPluginScanner(host, logger);
        // logger.info("BitwigApiFacade: VST Plugin Scanner initialized");
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Finds a track by name using case-sensitive matching.
     *
     * @param trackName The name of the track to find
     * @return Optional containing the track if found, empty otherwise
     */
    private Optional<Track> findTrackByName(String trackName) {
        if (trackName == null || trackName.trim().isEmpty()) {
            return Optional.empty();
        }

        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            if (track.exists().get() && trackName.equals(track.name().get())) {
                return Optional.of(track);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds a track by index.
     *
     * @param index The index of the track to find
     * @return Optional containing the track if found and exists, empty otherwise
     */
    private Optional<Track> findTrackByIndex(int index) {
        if (index < 0 || index >= trackBank.getSizeOfBank()) {
            return Optional.empty();
        }

        Track track = trackBank.getItemAt(index);
        return track.exists().get() ? Optional.of(track) : Optional.empty();
    }

    /**
     * Gets the index of a track by name.
     *
     * @param trackName The name of the track
     * @return The track index, or -1 if not found
     */
    private int getTrackIndexByName(String trackName) {
        if (trackName == null || trackName.trim().isEmpty()) {
            return -1;
        }

        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            Track track = trackBank.getItemAt(i);
            if (track.exists().get() && trackName.equals(track.name().get())) {
                return i;
            }
        }
        return -1;
    }

    // ========================================
    // Public API Methods
    // ========================================

    /**
     * Returns the number of tracks in the track bank.
     *
     * @return the size of the track bank
     */
    public int getTrackBankSize() {
        return trackBank.getSizeOfBank();
    }

    /**
     * Returns the name of the track at the given index.
     *
     * @param index the track index
     * @return the track name
     * @throws BitwigApiException if the index is invalid or track doesn't exist
     */
    public String getTrackNameByIndex(int index) throws BitwigApiException {
        final String operation = "getTrackNameByIndex";

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate track index
            if (index < 0 || index >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                    Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
                );
            }

            Track track = trackBank.getItemAt(index);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + index + " does not exist",
                    Map.of("index", index)
                );
            }

            return track.name().get();
        });
    }

    /**
     * Starts the transport playback.
     */
    public void startTransport() {
        logger.info("BitwigApiFacade: Starting transport playback");
        transport.play();
    }

    /**
     * Stops the transport playback.
     */
    public void stopTransport() {
        logger.info("BitwigApiFacade: Stopping transport playback");
        transport.stop();
    }

    /**
     * Get the ControllerHost instance.
     *
     * @return The ControllerHost
     */
    public ControllerHost getHost() {
        return host;
    }

    /**
     * Checks if a device is currently selected.
     *
     * @return true if a device is selected, false otherwise
     */
    public boolean isDeviceSelected() {
        logger.info("BitwigApiFacade: Checking if device is selected");
        return cursorDevice.exists().get();
    }

    /**
     * Gets the name of the currently selected device.
     *
     * @return The device name
     * @throws BitwigApiException if no device is selected
     */
    public String getSelectedDeviceName() throws BitwigApiException {
        final String operation = "getSelectedDeviceName";
        logger.info("BitwigApiFacade: Getting selected device name");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }
            return cursorDevice.name().get();
        });
    }

    /**
     * Gets the parameters of the currently selected device.
     *
     * @return A list of ParameterInfo objects representing all addressable parameters
     */
    public List<ParameterInfo> getSelectedDeviceParameters() {
        logger.info("BitwigApiFacade: Getting selected device parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        if (!isDeviceSelected()) {
            logger.info("BitwigApiFacade: No device selected, returning empty parameters list");
            return parameters;
        }

        for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = deviceParameterBank.getParameter(i);
            boolean exists = parameter.exists().get();

            if (exists) {
                String name = parameter.name().get();
                double value = parameter.value().get();
                String displayValue = parameter.displayedValue().get();

                // Handle null or empty names
                if (name != null && name.trim().isEmpty()) {
                    name = null;
                }

                parameters.add(new ParameterInfo(i, name, value, displayValue));
            }
        }

        logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " parameters");
        return parameters;
    }

    /**
     * Sets the value of a specific parameter for the currently selected device.
     *
     * @param parameterIndex The index of the parameter to set (0 to parameterCount-1)
     * @param value          The value to set (0.0-1.0)
     * @throws BitwigApiException if parameterIndex is out of range, value is out of range, no device is selected, or Bitwig API error occurs
     */
    public void setSelectedDeviceParameter(int parameterIndex, double value) throws BitwigApiException {
        final String operation = "setSelectedDeviceParameter";
        logger.info("BitwigApiFacade: Setting parameter " + parameterIndex + " to " + value);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!isDeviceSelected()) {
                throw new BitwigApiException(
                    ErrorCode.DEVICE_NOT_SELECTED,
                    operation,
                    "No device is currently selected"
                );
            }

            // Validate parameter index against actual parameter count
            int parameterCount = deviceParameterBank.getParameterCount();
            ParameterValidator.validateParameterIndex(parameterIndex, parameterCount, operation);

            // Validate value range
            ParameterValidator.validateParameterValue(value, operation);

            // Set the parameter value using setImmediately (Moss-style approach)
            // This bypasses take-over modes and sets the value directly
            RemoteControl parameter = deviceParameterBank.getParameter(parameterIndex);
            parameter.value().setImmediately(value);

            logger.info("BitwigApiFacade: Successfully set parameter " + parameterIndex + " to " + value);
        });
    }

    /**
     * Finds a track by name using case-sensitive matching.
     *
     * @param trackName The name of the track to find
     * @return The track index if found
     * @throws BitwigApiException if the track is not found
     */
    public int findTrackIndexByName(String trackName) throws BitwigApiException {
        final String operation = "findTrackIndexByName";
        logger.info("BitwigApiFacade: Searching for track '" + trackName + "'");

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);

            int index = getTrackIndexByName(trackName);
            if (index == -1) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track '" + trackName + "' not found",
                    Map.of("trackName", trackName)
                );
            }

            logger.info("BitwigApiFacade: Found track '" + trackName + "' at index " + index);
            return index;
        });
    }

    /**
     * Checks if a track exists by name using case-sensitive matching.
     *
     * @param trackName The name of the track to check
     * @return true if the track exists, false otherwise
     */
    public boolean trackExists(String trackName) {
        try {
            findTrackIndexByName(trackName);
            return true;
        } catch (BitwigApiException e) {
            return false;
        }
    }

    /**
     * Gets the number of clip slots available for a track.
     *
     * @param trackName The name of the track
     * @return The number of clip slots, or 0 if track not found
     */
    public int getTrackClipCount(String trackName) {
        logger.info("BitwigApiFacade: Getting clip count for track '" + trackName + "'");

        Optional<Track> trackOpt = findTrackByName(trackName);
        if (trackOpt.isPresent()) {
            // Return the number of available clip launcher slots
            return trackOpt.get().clipLauncherSlotBank().getSizeOfBank();
        }

        logger.warn("BitwigApiFacade: Track '" + trackName + "' not found for clip count check");
        return 0;
    }

    /**
     * Launches a clip at the specified track and clip index.
     *
     * @param trackName The name of the track containing the clip
     * @param clipIndex The zero-based index of the clip slot to launch
     * @throws BitwigApiException if track is not found, clip index is invalid, or launch fails
     */
    public void launchClip(String trackName, int clipIndex) throws BitwigApiException {
        final String operation = "launchClip";
        logger.info("BitwigApiFacade: Launching clip at " + trackName + "[" + clipIndex + "]");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate parameters
            ParameterValidator.validateNotEmpty(trackName, "trackName", operation);
            ParameterValidator.validateClipIndex(clipIndex, operation);

            // Find the track using helper method
            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track '" + trackName + "' not found",
                    Map.of("trackName", trackName)
                );
            }

            Track targetTrack = trackOpt.get();

            // Validate clip index within track bounds
            ClipLauncherSlotBank slotBank = targetTrack.clipLauncherSlotBank();
            if (clipIndex >= slotBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Clip index " + clipIndex + " out of bounds for track '" + trackName + "' (max: " + (slotBank.getSizeOfBank() - 1) + ")",
                    Map.of("trackName", trackName, "clipIndex", clipIndex, "maxIndex", slotBank.getSizeOfBank() - 1)
                );
            }

            // Launch the clip
            ClipLauncherSlot slot = slotBank.getItemAt(clipIndex);
            slot.launch();

            logger.info("BitwigApiFacade: Successfully launched clip at " + trackName + "[" + clipIndex + "]");
        });
    }

    /**
     * Finds the first scene index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findSceneByName(String sceneName) {
        return sceneBankFacade.findSceneByName(sceneName);
    }

    /**
     * Gets the name of the scene at the given index, or null if not present.
     */
    public String getSceneName(int index) {
        return sceneBankFacade.getSceneName(index);
    }

    /**
     * Gets the number of scenes in the scene bank.
     */
    public int getSceneCount() {
        return sceneBankFacade.getSceneCount();
    }

    /**
     * Gets all scenes in the project with their details.
     *
     * @return A list of scene information maps containing index, name, and color
     */
    public List<Map<String, Object>> getAllScenesInfo() {
        logger.info("BitwigApiFacade: Getting all scenes info");
        return sceneBankFacade.getAllScenesInfo();
    }

    /**
     * Gets detailed clip slot information for a specific track and scene index.
     *
     * @param trackIndex The 0-based track index
     * @param trackName The name of the track
     * @param sceneIndex The 0-based scene index
     * @return Map containing detailed clip slot information
     */
    public Map<String, Object> getClipSlotDetails(int trackIndex, String trackName, int sceneIndex) {
        logger.info("BitwigApiFacade: Getting clip slot details for track " + trackIndex + " (" + trackName + ") at scene " + sceneIndex);

        Map<String, Object> slotInfo = new LinkedHashMap<>();

        try {
            // Get the track
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                return null; // Track doesn't exist
            }

            // Basic track information
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);

            // Get the clip launcher slot at the scene index
            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            if (sceneIndex >= slotBank.getSizeOfBank()) {
                // Scene index is beyond the available slots for this track
                return null;
            }

            ClipLauncherSlot slot = slotBank.getItemAt(sceneIndex);

            // Check if slot has content (marked as interested in constructor)
            boolean hasContent = slot.hasContent().get();
            slotInfo.put("has_content", hasContent);

            // Clip name (only if has content, marked as interested in constructor)
            String clipName = null;
            if (hasContent) {
                String name = slot.name().get();
                clipName = (name != null && name.trim().isEmpty()) ? null : name;
            }
            slotInfo.put("clip_name", clipName);

            // Clip color (only if has content, marked as interested in constructor)
            String clipColor = null;
            if (hasContent) {
                Color color = slot.color().get();
                if (color != null) {
                    clipColor = String.format("#%02X%02X%02X",
                        (int) (color.getRed() * 255),
                        (int) (color.getGreen() * 255),
                        (int) (color.getBlue() * 255));
                }
            }
            slotInfo.put("clip_color", clipColor);

            // Playback state flags (all properties marked as interested in constructor)
            slotInfo.put("is_playing", slot.isPlaying().get());
            slotInfo.put("is_recording", slot.isRecording().get());
            slotInfo.put("is_playback_queued", slot.isPlaybackQueued().get());
            slotInfo.put("is_recording_queued", slot.isRecordingQueued().get());
            slotInfo.put("is_stop_queued", slot.isStopQueued().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting clip slot details: " + e.getMessage());
            // Return basic structure with safe defaults
            slotInfo.put("track_index", trackIndex);
            slotInfo.put("track_name", trackName);
            slotInfo.put("has_content", false);
            slotInfo.put("clip_name", null);
            slotInfo.put("clip_color", null);
            slotInfo.put("is_playing", false);
            slotInfo.put("is_recording", false);
            slotInfo.put("is_playback_queued", false);
            slotInfo.put("is_recording_queued", false);
            slotInfo.put("is_stop_queued", false);
        }

        return slotInfo;
    }

    /**
     * Gets the current project name.
     *
     * @return The project name or "Unknown Project" if not available
     */
    public String getProjectName() {
        logger.info("BitwigApiFacade: Getting project name");
        String projectName = application.projectName().get();
        return projectName != null && !projectName.trim().isEmpty() ? projectName : Constants.DEFAULT_PROJECT_NAME;
    }

    /**
     * Checks if the audio engine is currently active.
     *
     * @return true if the audio engine is active, false otherwise
     */
    public boolean isAudioEngineActive() {
        logger.info("BitwigApiFacade: Checking audio engine status");
        return application.hasActiveEngine().get();
    }

    /**
     * Formats seconds into a time string in the format MM:SS.mmm or HH:MM:SS.mmm
     * @param seconds The time in seconds
     * @return Formatted time string with milliseconds
     */
    private String formatTimeString(double seconds) {
        try {
            int totalSeconds = (int) Math.floor(seconds);
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int secs = totalSeconds % 60;

            // Calculate milliseconds from the fractional part
            int milliseconds = (int) Math.round((seconds - Math.floor(seconds)) * 1000);

            // Handle edge case where rounding gives us 1000ms
            if (milliseconds >= 1000) {
                milliseconds = 0;
                secs += 1;
                if (secs >= 60) {
                    secs = 0;
                    minutes += 1;
                    if (minutes >= 60) {
                        minutes = 0;
                        hours += 1;
                    }
                }
            }

            if (hours > 0) {
                return String.format("%d:%02d:%02d.%03d", hours, minutes, secs, milliseconds);
            } else {
                return String.format("%d:%02d.%03d", minutes, secs, milliseconds);
            }
        } catch (Exception e) {
            return Constants.DEFAULT_TIME_STRING;
        }
    }

    /**
     * Gets the current transport status information.
     *
     * @return A map containing transport status data
     */
    public java.util.Map<String, Object> getTransportStatus() {
        logger.info("BitwigApiFacade: Getting transport status");
        java.util.Map<String, Object> transportMap = new java.util.LinkedHashMap<>();

        try {
            transportMap.put("playing", transport.isPlaying().get());
            transportMap.put("recording", transport.isArrangerRecordEnabled().get());
            transportMap.put("loop_active", transport.isArrangerLoopEnabled().get());
            transportMap.put("metronome_active", transport.isMetronomeEnabled().get());
            transportMap.put("current_tempo", transport.tempo().getRaw());
            transportMap.put("time_signature", transport.timeSignature().get());

            // Format position as Bitwig-style beat string
            double positionInBeats = transport.getPosition().get();
            String beatStr = formatBitwigBeatPosition(positionInBeats);
            transportMap.put("current_beat_str", beatStr);

            // Get time string using playPositionInSeconds
            double positionInSeconds = transport.playPositionInSeconds().get();
            String timeStr = formatTimeString(positionInSeconds);
            transportMap.put("current_time_str", timeStr);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Unable to get complete transport status: " + e.getMessage());
            // Provide default values if API calls fail
            transportMap.put("playing", false);
            transportMap.put("recording", false);
            transportMap.put("loop_active", false);
            transportMap.put("metronome_active", false);
            transportMap.put("current_tempo", 120.0);
            transportMap.put("time_signature", "4/4");
            transportMap.put("current_beat_str", Constants.DEFAULT_BEAT_POSITION);
            transportMap.put("current_time_str", Constants.DEFAULT_TIME_STRING);
        }

        return transportMap;
    }

    /**
     * Formats a position in beats to Bitwig-style format: measures.beats.sixteenths:ticks
     * Example: 1.1.1:0 = measure 1, beat 1, sixteenth 1, tick 0
     */
    private String formatBitwigBeatPosition(double positionInBeats) {
        try {
            // Assume 4/4 time signature for calculation
            int beatsPerMeasure = Constants.BEATS_PER_MEASURE;
            int sixteenthsPerBeat = Constants.SIXTEENTHS_PER_BEAT;
            int ticksPerSixteenth = Constants.TICKS_PER_SIXTEENTH; // Common MIDI resolution

            // Convert beats to total ticks
            int totalTicks = (int) Math.round(positionInBeats * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate measures (1-based)
            int measures = (totalTicks / (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            int remainingTicks = totalTicks % (beatsPerMeasure * sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate beats within measure (1-based)
            int beats = (remainingTicks / (sixteenthsPerBeat * ticksPerSixteenth)) + 1;
            remainingTicks = remainingTicks % (sixteenthsPerBeat * ticksPerSixteenth);

            // Calculate sixteenths within beat (1-based)
            int sixteenths = (remainingTicks / ticksPerSixteenth) + 1;
            int ticks = remainingTicks % ticksPerSixteenth;

            return String.format("%d.%d.%d:%d", measures, beats, sixteenths, ticks);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting beat position: " + e.getMessage());
            return Constants.DEFAULT_BEAT_POSITION;
        }
    }

    /**
     * Gets the project parameters from the project's remote controls page.
     * Only returns parameters where exists() is true.
     *
     * @return A list of ParameterInfo objects representing the existing project parameters
     */
    public List<ParameterInfo> getProjectParameters() {
        logger.info("BitwigApiFacade: Getting project parameters");
        List<ParameterInfo> parameters = new ArrayList<>();

        for (int i = 0; i < projectParameterBank.getParameterCount(); i++) {
            RemoteControl parameter = projectParameterBank.getParameter(i);
            boolean exists = parameter.exists().get();

            if (exists) {
                String name = parameter.name().get();
                double value = parameter.value().get();
                String displayValue = parameter.displayedValue().get();

                // Handle null or empty names
                if (name != null && name.trim().isEmpty()) {
                    name = null;
                }

                parameters.add(new ParameterInfo(i, name, value, displayValue));
            }
        }

        logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " existing project parameters");
        return parameters;
    }

    /**
     * Gets information about the currently selected track.
     *
     * @return A map containing selected track information, or null if no track is selected
     */
    public Map<String, Object> getSelectedTrackInfo() {
        logger.info("BitwigApiFacade: Getting selected track information");

        if (!cursorTrack.exists().get()) {
            logger.info("BitwigApiFacade: No track selected");
            return null;
        }

        Map<String, Object> trackInfo = new LinkedHashMap<>();

        try {
            // Get track index by finding it in the track bank using helper method
            String trackName = cursorTrack.name().get();
            int trackIndex = getTrackIndexByName(trackName);

            trackInfo.put("index", trackIndex);
            trackInfo.put("name", trackName);
            trackInfo.put("type", cursorTrack.trackType().get().toLowerCase());
            trackInfo.put("is_group", cursorTrack.isGroup().get());
            trackInfo.put("muted", cursorTrack.mute().get());
            trackInfo.put("soloed", cursorTrack.solo().get());
            trackInfo.put("armed", cursorTrack.arm().get());

            logger.info("BitwigApiFacade: Retrieved selected track info: " + trackName);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track info: " + e.getMessage());
            return null;
        }

        return trackInfo;
    }

    /**
     * Gets information about the currently selected device including track context, device info, and parameters.
     *
     * @return A map containing selected device information, or null if no device is selected
     */
    public Map<String, Object> getSelectedDeviceInfo() {
        logger.info("BitwigApiFacade: Getting selected device information");

        if (!cursorDevice.exists().get()) {
            logger.info("BitwigApiFacade: No device selected");
            return null;
        }

        Map<String, Object> deviceInfo = new LinkedHashMap<>();

        try {
            // Get track information where the device is located
            String trackName = cursorTrack.name().get();
            int trackIndex = getTrackIndexByName(trackName);

            deviceInfo.put("track_name", trackName);
            deviceInfo.put("track_index", trackIndex);

            // Get device position/index in the device chain
            // Note: Bitwig API doesn't directly expose device index in chain, so we use 0 as default
            // This could be enhanced in the future with more complex logic to determine actual position
            deviceInfo.put("index", 0);

            // Get device name and bypass status
            deviceInfo.put("name", cursorDevice.name().get());
            deviceInfo.put("bypassed", !cursorDevice.isEnabled().get());

            // Get device parameters
            List<Map<String, Object>> parametersArray = new ArrayList<>();
            for (ParameterInfo p : getSelectedDeviceParameters()) {
                    Map<String, Object> paramMap = new LinkedHashMap<>();
                    paramMap.put("index", p.index());
                    paramMap.put("name", p.name());
                    paramMap.put("value", p.value());
                    paramMap.put("display_value", p.display_value());
                    parametersArray.add(paramMap);
                            }
            deviceInfo.put("parameters", parametersArray);

            logger.info("BitwigApiFacade: Retrieved selected device info: " + cursorDevice.name().get());
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected device info: " + e.getMessage());
            return null;
        }

        return deviceInfo;
    }

    /**
     * Gets a list of all tracks in the project with summary information.
     *
     * @param typeFilter Optional filter by track type (e.g., "audio", "instrument", "group", "effect", "master")
     * @return A list of track information maps
     */
    public List<Map<String, Object>> getAllTracksInfo(String typeFilter) {
        logger.info("BitwigApiFacade: Getting all tracks info" + (typeFilter != null ? " filtered by type: " + typeFilter : ""));
        List<Map<String, Object>> tracksInfo = new ArrayList<>();

        try {
            // Get selected track name for comparison
            String selectedTrackName = null;
            if (cursorTrack.exists().get()) {
                selectedTrackName = cursorTrack.name().get();
            }

            // Create parent track mapping to determine parent group indices
            Map<String, Integer> parentGroupMapping = buildParentGroupMapping();

            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track track = trackBank.getItemAt(i);
                if (!track.exists().get()) {
                    continue; // Skip non-existent tracks
                }

                Map<String, Object> trackInfo = new LinkedHashMap<>();

                // Basic track properties
                trackInfo.put("index", i);
                String trackName = track.name().get();
                trackInfo.put("name", trackName);

                String trackType = track.trackType().get().toLowerCase();
                trackInfo.put("type", trackType);

                // Apply type filter if specified
                if (typeFilter != null && !typeFilter.toLowerCase().equals(trackType)) {
                    continue;
                }

                trackInfo.put("is_group", track.isGroup().get());

                // Get parent group index from mapping
                trackInfo.put("parent_group_index", parentGroupMapping.get(trackName));

                // Get track activation status
                trackInfo.put("activated", track.isActivated().get());

                // Get track color and convert to RGB format
                trackInfo.put("color", formatTrackColor(track.color().get()));

                // Check if this track is selected
                boolean isSelected = selectedTrackName != null && selectedTrackName.equals(trackName);
                trackInfo.put("is_selected", isSelected);

                // Get devices on this track using the pre-existing device bank
                List<Map<String, Object>> devices = getTrackDevices(i);
                trackInfo.put("devices", devices);

                tracksInfo.add(trackInfo);
            }

            logger.info("BitwigApiFacade: Retrieved " + tracksInfo.size() + " tracks");
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting tracks info: " + e.getMessage());
        }

        return tracksInfo;
    }

    /**
     * Gets device information for a specific track by index.
     *
     * @param trackIndex The index of the track to get devices from
     * @return A list of device information maps
     */
    private List<Map<String, Object>> getTrackDevices(int trackIndex) {
        List<Map<String, Object>> devices = new ArrayList<>();

        try {
            // Use the pre-existing device bank for this track that was created in the constructor
            // and already has its properties marked as interested
            if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
                logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
                return devices;
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            Track track = trackBank.getItemAt(trackIndex);

            // Create device info for each existing device
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);

                // Check if device exists - this should work since markInterested() was called in constructor
                if (!device.exists().get()) {
                    continue;
                }

                Map<String, Object> deviceInfo = new LinkedHashMap<>();
                deviceInfo.put("index", i);

                // Get device name
                String deviceName = device.name().get();
                deviceInfo.put("name", deviceName);

                // Get device type
                String deviceType = device.deviceType().get();
                deviceInfo.put("type", deviceType);

                // Get device enabled status (bypassed = !enabled)
                boolean isEnabled = device.isEnabled().get();
                deviceInfo.put("bypassed", !isEnabled);

                devices.add(deviceInfo);
            }

            logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting devices for track index " + trackIndex + ": " + e.getMessage());
        }

        return devices;
    }

    /**
     * Builds a mapping of track names to their parent group track indices.
     * This creates parent track objects for each track to determine hierarchy.
     *
     * @return A map where keys are track names and values are parent group indices (null if no parent)
     */
    private Map<String, Integer> buildParentGroupMapping() {
        Map<String, Integer> parentMapping = new LinkedHashMap<>();

        try {
            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track track = trackBank.getItemAt(i);
                if (!track.exists().get()) {
                    continue;
                }

                String trackName = track.name().get();
                Integer parentGroupIndex = null;

                try {
                    // Create parent track object to check for parent group
                    Track parentTrack = track.createParentTrack(0, 0);
                    if (parentTrack != null && parentTrack.exists().get()) {
                        String parentName = parentTrack.name().get();

                        // Find the index of the parent track in our track bank
                        for (int j = 0; j < trackBank.getSizeOfBank(); j++) {
                            Track candidateParent = trackBank.getItemAt(j);
                            if (candidateParent.exists().get() &&
                                candidateParent.isGroup().get() &&
                                parentName.equals(candidateParent.name().get())) {
                                parentGroupIndex = j;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("BitwigApiFacade: Error determining parent for track " + trackName + ": " + e.getMessage());
                }

                parentMapping.put(trackName, parentGroupIndex);
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error building parent group mapping: " + e.getMessage());
        }

        return parentMapping;
    }

    /**
     * Gets detailed information about a track by absolute project index.
     */
    public Map<String, Object> getTrackDetailsByIndex(int index) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (index < 0 || index >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + index,
                    Map.of("index", index, "max_index", trackBank.getSizeOfBank() - 1)
                );
            }
            Track track = trackBank.getItemAt(index);
            if (!track.exists().get()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation, "Track at index " + index + " does not exist", Map.of("index", index));
            }
            return buildDetailedTrackInfo(track, index);
        });
    }

    /**
     * Gets detailed information about a track by exact name (case-sensitive).
     */
    public Map<String, Object> getTrackDetailsByName(String trackName) throws BitwigApiException {
        final String operation = "get_track_details";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            ParameterValidator.validateNotEmpty(trackName, "track_name", operation);
            int index = findTrackIndexByName(trackName);
            return getTrackDetailsByIndex(index);
        });
    }

    /**
     * Gets detailed information about the currently selected track, or null if none.
     */
    public Map<String, Object> getSelectedTrackDetails() {
        try {
            if (!cursorTrack.exists().get()) {
                return null;
            }
            String name = cursorTrack.name().get();
            // Find index in current bank for consistency
            int index = -1;
            for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                Track t = trackBank.getItemAt(i);
                if (t.exists().get() && name.equals(t.name().get())) {
                    index = i;
                    break;
                }
            }
            // If not found in bank, attempt to build from cursor directly
            if (index >= 0) {
                return buildDetailedTrackInfo(trackBank.getItemAt(index), index);
            } else {
                // Build minimal from cursor and enrich where possible
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("index", -1);
                info.put("name", name);
                info.put("type", cursorTrack.trackType().get().toLowerCase());
                info.put("is_group", cursorTrack.isGroup().get());
                info.put("parent_group_index", null);
                info.put("activated", true);
                info.put("color", Constants.DEFAULT_COLOR);
                info.put("is_selected", true);
                info.put("devices", List.of());
                info.put("volume", cursorTrack.volume().value().get());
                info.put("volume_str", safeDisplay(cursorTrack.volume().displayedValue().get()));
                info.put("pan", cursorTrack.pan().value().get());
                info.put("pan_str", safeDisplay(cursorTrack.pan().displayedValue().get()));
                info.put("muted", cursorTrack.mute().get());
                info.put("soloed", cursorTrack.solo().get());
                info.put("armed", cursorTrack.arm().get());
                info.put("monitor_enabled", cursorTrack.isMonitoring().get());
                String mode = cursorTrack.monitorMode().get();
                boolean cursorAuto = mode != null && mode.toLowerCase().contains("auto");
                info.put("auto_monitor_enabled", cursorAuto);
                info.put("sends", List.of());
                info.put("clips", List.of());
                return info;
            }
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting selected track details: " + e.getMessage());
            return null;
        }
    }

    private String safeDisplay(String value) {
        return value != null ? value : "";
    }

    /**
     * Builds a detailed track info map including base fields, device summaries, channel params,
     * sends and clip launcher slots.
     */
    private Map<String, Object> buildDetailedTrackInfo(Track track, int index) {
        Map<String, Object> trackInfo = new LinkedHashMap<>();
        try {
            // Basic fields similar to getAllTracksInfo
            trackInfo.put("index", index);
            String trackName = track.name().get();
            trackInfo.put("name", trackName);
            String trackType = track.trackType().get().toLowerCase();
            trackInfo.put("type", trackType);
            trackInfo.put("is_group", track.isGroup().get());
            Map<String, Integer> parentMap = buildParentGroupMapping();
            trackInfo.put("parent_group_index", parentMap.get(trackName));
            trackInfo.put("activated", track.isActivated().get());
            trackInfo.put("color", formatTrackColor(track.color().get()));
            // Selected state
            boolean isSelected = cursorTrack.exists().get() && trackName.equals(cursorTrack.name().get());
            trackInfo.put("is_selected", isSelected);
            // Devices
            trackInfo.put("devices", getTrackDevices(index));

            // Channel parameters
            trackInfo.put("volume", track.volume().value().get());
            trackInfo.put("volume_str", safeDisplay(track.volume().displayedValue().get()));
            trackInfo.put("pan", track.pan().value().get());
            trackInfo.put("pan_str", safeDisplay(track.pan().displayedValue().get()));
            trackInfo.put("muted", track.mute().get());
            trackInfo.put("soloed", track.solo().get());
            trackInfo.put("armed", track.arm().get());
            // Monitoring (properties marked as interested in constructor)
            boolean monitoring = track.isMonitoring().get();
            String mode = track.monitorMode().get();
            boolean autoMon = mode != null && mode.toLowerCase().contains("auto");
            trackInfo.put("monitor_enabled", monitoring);
            trackInfo.put("auto_monitor_enabled", autoMon);

            // Sends
            List<Map<String, Object>> sends = new ArrayList<>();
            try {
                SendBank sendBank = track.sendBank();
                int sendCount = sendBank.getSizeOfBank();
                for (int i = 0; i < sendCount; i++) {
                    Send send = sendBank.getItemAt(i);
                    Map<String, Object> sendMap = new LinkedHashMap<>();
                    sendMap.put("name", send.name().get());
                    sendMap.put("volume", send.value().get());
                    sendMap.put("volume_str", safeDisplay(send.displayedValue().get()));
                    sendMap.put("activated", send.isEnabled().get());
                    sends.add(sendMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading sends for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("sends", sends);

            // Clips
            List<Map<String, Object>> clips = new ArrayList<>();
            try {
                ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
                int slots = slotBank.getSizeOfBank();
                for (int s = 0; s < slots; s++) {
                    ClipLauncherSlot slot = slotBank.getItemAt(s);
                    Map<String, Object> slotMap = new LinkedHashMap<>();
                    slotMap.put("slot_index", s);
                    // Scene name from scene bank facade
                    String sceneName = getSceneName(s);
                    slotMap.put("scene_name", sceneName);
                    boolean hasContent = false;
                    try { hasContent = slot.hasContent().get(); } catch (Exception ignored) {}
                    slotMap.put("has_content", hasContent);

                    // Clip name from slot name value if available
                    String clipName = null;
                    try {
                        clipName = slot.name().get();
                        if (clipName != null && clipName.trim().isEmpty()) clipName = null;
                    } catch (Exception ignored) {}
                    slotMap.put("clip_name", clipName);
                    try {
                        Color c = slot.color().get();
                        slotMap.put("clip_color", c != null ? formatTrackColor(c) : null);
                    } catch (Exception e) {
                        slotMap.put("clip_color", null);
                    }
                    // Removed unsupported length / is_looping fields

                    // Playback state flags
                    try { slotMap.put("is_playing", slot.isPlaying().get()); } catch (Exception e) { slotMap.put("is_playing", null); }
                    try { slotMap.put("is_recording", slot.isRecording().get()); } catch (Exception e) { slotMap.put("is_recording", null); }
                    try { slotMap.put("is_playback_queued", slot.isPlaybackQueued().get()); } catch (Exception e) { slotMap.put("is_playback_queued", null); }

                    clips.add(slotMap);
                }
            } catch (Exception e) {
                logger.warn("BitwigApiFacade: Error reading clip slots for track " + trackName + ": " + e.getMessage());
            }
            trackInfo.put("clips", clips);
        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error building detailed track info: " + e.getMessage());
        }
        return trackInfo;
    }

    /**
     * Formats a ColorValue object into an RGB string format.
     */
    private String formatTrackColor(Color colorValue) {
        try {
            return String.format("rgb(%d,%d,%d)",
                (int) (colorValue.getRed() * 255),
                (int) (colorValue.getGreen() * 255),
                (int) (colorValue.getBlue() * 255));

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error formatting track color: " + e.getMessage());
            return Constants.DEFAULT_COLOR; // Default gray fallback
        }
    }

    /**
     * Gets detailed device information for a specific track identified by index, name, or selected track.
     *
     * @param trackIndex The 0-based track index (optional)
     * @param trackName The exact track name (optional)
     * @param getSelected Whether to get devices for the selected track (optional)
     * @return List of device summary objects with detailed information
     * @throws BitwigApiException if the track is not found or API access fails
     */
    public List<Map<String, Object>> getDevicesOnTrack(Integer trackIndex, String trackName, Boolean getSelected)
            throws BitwigApiException {
        final String operation = "getDevicesOnTrack";

        try {
            Track targetTrack = null;
            int resolvedTrackIndex = -1;

            // Resolve target track based on parameters
            if (trackIndex != null) {
                // Track by index
                if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                    throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                        "Track index " + trackIndex + " is out of range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
                }

                targetTrack = trackBank.getItemAt(trackIndex);
                if (!targetTrack.exists().get()) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "Track at index " + trackIndex + " does not exist");
                }
                resolvedTrackIndex = trackIndex;

            } else if (trackName != null) {
                // Track by name - find exact match
                for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                    Track track = trackBank.getItemAt(i);
                    if (track.exists().get() && trackName.equals(track.name().get())) {
                        targetTrack = track;
                        resolvedTrackIndex = i;
                        break;
                    }
                }

                if (targetTrack == null) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "No track found with name '" + trackName + "'");
                }

            } else if (Boolean.TRUE.equals(getSelected)) {
                // Use selected track (cursor track)
                if (!cursorTrack.exists().get()) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "No track is currently selected");
                }

                // Find the index of the cursor track in the track bank
                String selectedTrackName = cursorTrack.name().get();
                for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
                    Track track = trackBank.getItemAt(i);
                    if (track.exists().get() && selectedTrackName.equals(track.name().get())) {
                        targetTrack = track;
                        resolvedTrackIndex = i;
                        break;
                    }
                }

                if (targetTrack == null) {
                    throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                        "Selected track not found in track bank");
                }
            }

            if (targetTrack == null) {
                throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                    "No valid track identifier provided");
            }

            // Get devices for the resolved track
            return getDetailedTrackDevices(resolvedTrackIndex, targetTrack);

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get devices for track: " + e.getMessage());
        }
    }

    /**
     * Gets detailed device information for a specific track with enhanced device details.
     *
     * @param trackIndex The resolved track index
     * @param track The target track object
     * @return List of detailed device information maps
     */
    private List<Map<String, Object>> getDetailedTrackDevices(int trackIndex, Track track) {
        List<Map<String, Object>> devices = new ArrayList<>();

        try {
            // Use the pre-existing device bank for this track
            if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
                logger.warn("BitwigApiFacade: Invalid track index for devices: " + trackIndex);
                return devices;
            }

            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);

            // Get cursor device info for selection comparison (only if we have a selected track and device)
            String selectedDeviceName = null;
            boolean isSelectedTrack = cursorTrack.exists().get() && track.name().get().equals(cursorTrack.name().get());
            if (isSelectedTrack && cursorDevice.exists().get()) {
                selectedDeviceName = cursorDevice.name().get();
            }

            // Iterate through device bank with proper enumeration
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);

                // Check if device exists
                if (!device.exists().get()) {
                    continue;
                }

                Map<String, Object> deviceInfo = new LinkedHashMap<>();
                deviceInfo.put("index", i);

                // Get device name
                String deviceName = device.name().get();
                deviceInfo.put("name", deviceName);

                // Get and map device type
                String rawDeviceType = device.deviceType().get();
                String mappedType = mapDeviceType(rawDeviceType);
                deviceInfo.put("type", mappedType);

                // Get device bypassed status (bypassed = !enabled)
                boolean isEnabled = device.isEnabled().get();
                deviceInfo.put("bypassed", !isEnabled);

                // Determine if this device is selected
                boolean isDeviceSelected = false;
                if (isSelectedTrack && selectedDeviceName != null) {
                    // Use name matching for device selection comparison
                    isDeviceSelected = deviceName.equals(selectedDeviceName);
                }
                deviceInfo.put("is_selected", isDeviceSelected);

                // Optional UI state fields - only include if available
                // Per story requirements, omit these fields if not available from API
                // deviceInfo.put("is_expanded", null);  // Omitted - not available from Controller API
                // deviceInfo.put("is_window_open", null);  // Omitted - not available from Controller API

                devices.add(deviceInfo);
            }

            logger.info("BitwigApiFacade: Found " + devices.size() + " devices on track: " + track.name().get());

        } catch (Exception e) {
            logger.warn("BitwigApiFacade: Error getting detailed devices for track index " + trackIndex + ": " + e.getMessage());
        }

        return devices;
    }

    /**
     * Maps Bitwig device types to standardized type names.
     *
     * @param rawDeviceType The raw device type from Bitwig API
     * @return Mapped device type: "Instrument", "AudioFX", "NoteFX", or "Unknown"
     */
    private String mapDeviceType(String rawDeviceType) {
        if (rawDeviceType == null) {
            return "Unknown";
        }

        String lowerType = rawDeviceType.toLowerCase();

        if (lowerType.contains("instrument")) {
            return "Instrument";
        } else if (lowerType.contains("note") || lowerType.contains("midi")) {
            return "NoteFX";
        } else if (lowerType.contains("audio") || lowerType.contains("effect") || lowerType.contains("fx")) {
            return "AudioFX";
        } else {
            return "Unknown";
        }
    }

    /**
     * Gets detailed device information including remote controls and pages.
     *
     * @param trackIndex The track index (nullable)
     * @param trackName The track name (nullable)
     * @param deviceIndex The device index (nullable)
     * @param deviceName The device name (nullable)
     * @param getForSelectedDevice Whether to get selected device (nullable)
     * @return DeviceDetailsResult containing complete device information
     * @throws BitwigApiException if device/track not found or parameters invalid
     */
    public io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName, Boolean getForSelectedDevice)
            throws BitwigApiException {
        final String operation = "getDeviceDetails";

        try {
            // Determine operation mode
            boolean isSelectedDeviceMode = Boolean.TRUE.equals(getForSelectedDevice) ||
                (trackIndex == null && trackName == null && deviceIndex == null && deviceName == null);

            if (isSelectedDeviceMode) {
                return getSelectedDeviceDetails();
            } else {
                return getTargetDeviceDetails(trackIndex, trackName, deviceIndex, deviceName);
            }

        } catch (BitwigApiException e) {
            throw e;
        } catch (Exception e) {
            logger.error("BitwigApiFacade: Unexpected error in " + operation + ": " + e.getMessage());
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to get device details: " + e.getMessage());
        }
    }

    /**
     * Gets details for the currently selected device.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getSelectedDeviceDetails()
            throws BitwigApiException {
        final String operation = "getSelectedDeviceDetails";

        // Check if device is selected
        if (!cursorDevice.exists().get()) {
            throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                "No device is currently selected");
        }

        // Check if cursor track exists
        if (!cursorTrack.exists().get()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "No track is currently selected");
        }

        // Get track index directly from cursor track position
        int resolvedTrackIndex = cursorTrack.position().get();
        String selectedTrackName = cursorTrack.name().get();

        // Verify the position is within our track bank range
        if (resolvedTrackIndex < 0 || resolvedTrackIndex >= trackBank.getSizeOfBank()) {
            throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                "Selected track position " + resolvedTrackIndex + " is outside track bank range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
        }

        // Get device basic properties
        String deviceName = cursorDevice.name().get();
        String rawDeviceType = cursorDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = cursorDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Find device index by comparing with devices in the track
        int deviceIndex = findDeviceIndexInTrack(resolvedTrackIndex, deviceName);

        // Get remote controls for the currently selected page
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromCursor();

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            selectedTrackName,
            deviceIndex,
            deviceName,
            mappedType,
            isBypassed,
            true, // is_selected = true since this is the selected device
            remoteControls
        );
    }

    /**
     * Gets details for a device specified by track and device identifiers.
     */
    private io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult getTargetDeviceDetails(
            Integer trackIndex, String trackName, Integer deviceIndex, String deviceName)
            throws BitwigApiException {
        final String operation = "getTargetDeviceDetails";

        // Resolve target track
        Track targetTrack;
        int resolvedTrackIndex;

        if (trackIndex != null) {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Track index " + trackIndex + " is out of range [0, " + (trackBank.getSizeOfBank() - 1) + "]");
            }
            Optional<Track> trackOpt = findTrackByIndex(trackIndex);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "Track at index " + trackIndex + " does not exist");
            }
            targetTrack = trackOpt.get();
            resolvedTrackIndex = trackIndex;
        } else if (trackName != null) {
            Optional<Track> trackOpt = findTrackByName(trackName);
            if (trackOpt.isEmpty()) {
                throw new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, operation,
                    "No track found with name '" + trackName + "'");
            }
            targetTrack = trackOpt.get();
            resolvedTrackIndex = getTrackIndexByName(trackName);
        } else {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Either trackIndex or trackName must be provided");
        }

        // Resolve target device
        DeviceBank deviceBank = trackDeviceBanks.get(resolvedTrackIndex);
        Device targetDevice = null;
        int resolvedDeviceIndex = -1;

        if (deviceIndex != null) {
            if (deviceIndex < 0 || deviceIndex >= deviceBank.getSizeOfBank()) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Device index " + deviceIndex + " is out of range [0, " + (deviceBank.getSizeOfBank() - 1) + "]");
            }
            targetDevice = deviceBank.getItemAt(deviceIndex);
            if (!targetDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "Device at index " + deviceIndex + " does not exist on track");
            }
            resolvedDeviceIndex = deviceIndex;
        } else if (deviceName != null) {
            for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
                Device device = deviceBank.getItemAt(i);
                if (device.exists().get() && deviceName.equals(device.name().get())) {
                    targetDevice = device;
                    resolvedDeviceIndex = i;
                    break;
                }
            }
            if (targetDevice == null) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_FOUND, operation,
                    "No device found with name '" + deviceName + "' on track");
            }
        } else {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Either deviceIndex or deviceName must be provided");
        }

        // Get device basic properties
        String actualDeviceName = targetDevice.name().get();
        String rawDeviceType = targetDevice.deviceType().get();
        String mappedType = mapDeviceType(rawDeviceType);
        boolean isEnabled = targetDevice.isEnabled().get();
        boolean isBypassed = !isEnabled;

        // Determine if this device is selected by comparing with cursor device
        boolean isSelected = isDeviceSelectedComparison(resolvedTrackIndex, targetTrack.name().get(),
                                                       resolvedDeviceIndex, actualDeviceName);

        // For non-selected devices, remote control access is limited
        List<ParameterInfo> remoteControls = getDeviceRemoteControlsFromDevice(targetDevice);

        return new io.github.fabb.wigai.features.DeviceController.DeviceDetailsResult(
            resolvedTrackIndex,
            targetTrack.name().get(),
            resolvedDeviceIndex,
            actualDeviceName,
            mappedType,
            isBypassed,
            isSelected,
            remoteControls
        );
    }

    /**
     * Gets remote controls from the cursor device (selected device).
     *
     * This directly returns the existing device parameters since they represent
     * the same data (remote controls for the currently selected page).
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromCursor() {
        // Direct access to selected device parameters - no conversion needed
        return getSelectedDeviceParameters();
    }

    /**
     * Gets remote controls from a specific device (non-cursor).
     *
     * Note: The Bitwig Controller API does not easily expose remote controls
     * for non-selected devices without temporarily selecting them, which would
     * disrupt the user experience. Therefore, this method returns an empty list.
     */
    private List<ParameterInfo> getDeviceRemoteControlsFromDevice(Device device) {
        // Limitation: Bitwig Controller API does not provide easy access to
        // remote controls for non-selected devices
        return new ArrayList<>();
    }

    /**
     * Finds the index of a device in a track by comparing names.
     */
    private int findDeviceIndexInTrack(int trackIndex, String deviceName) {
        if (trackIndex < 0 || trackIndex >= trackDeviceBanks.size()) {
            return -1;
        }

        DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
        for (int i = 0; i < deviceBank.getSizeOfBank(); i++) {
            Device device = deviceBank.getItemAt(i);
            if (device.exists().get() && deviceName.equals(device.name().get())) {
                return i;
            }
        }
        return -1; // Not found
    }

    /**
     * Determines if a device is selected by comparing with cursor device.
     */
    private boolean isDeviceSelectedComparison(int trackIndex, String trackName, int deviceIndex, String deviceName) {
        // Check if cursor device exists
        if (!cursorDevice.exists().get() || !cursorTrack.exists().get()) {
            return false;
        }

        // Compare track
        String selectedTrackName = cursorTrack.name().get();
        if (!trackName.equals(selectedTrackName)) {
            return false;
        }

        // Compare device name
        String selectedDeviceName = cursorDevice.name().get();
        return deviceName.equals(selectedDeviceName);
    }

    // ========================================
    // MIDI Operations
    // ========================================

    /**
     * Sends a MIDI note on message.
     *
     * @param channel The MIDI channel (0-15)
     * @param pitch The note pitch (0-127, where 60 is middle C)
     * @param velocity The note velocity (0-127)
     * @throws BitwigApiException if parameters are out of range
     */
    public void sendMidiNoteOn(int channel, int pitch, int velocity) throws BitwigApiException {
        final String operation = "sendMidiNoteOn";
        logger.info("BitwigApiFacade: Sending MIDI Note On - channel: " + channel + ", pitch: " + pitch + ", velocity: " + velocity);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if MIDI input is available
            if (noteInput == null) {
                throw new BitwigApiException(
                    ErrorCode.OPERATION_FAILED,
                    operation,
                    "MIDI functionality is not available. Please ensure a MIDI input port is configured in Bitwig."
                );
            }

            // Validate channel (0-15)
            if (channel < 0 || channel > 15) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI channel must be between 0 and 15, got: " + channel,
                    Map.of("channel", channel)
                );
            }

            // Validate pitch (0-127)
            if (pitch < 0 || pitch > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI pitch must be between 0 and 127, got: " + pitch,
                    Map.of("pitch", pitch)
                );
            }

            // Validate velocity (0-127)
            if (velocity < 0 || velocity > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI velocity must be between 0 and 127, got: " + velocity,
                    Map.of("velocity", velocity)
                );
            }

            // Send MIDI note on message
            // Status byte: 0x90 (note on) + channel
            int statusByte = 0x90 | (channel & 0x0F);
            noteInput.sendRawMidiEvent(statusByte, pitch, velocity);

            logger.info("BitwigApiFacade: Successfully sent MIDI Note On");
        });
    }

    /**
     * Sends a MIDI note off message.
     *
     * @param channel The MIDI channel (0-15)
     * @param pitch The note pitch (0-127)
     * @throws BitwigApiException if parameters are out of range
     */
    public void sendMidiNoteOff(int channel, int pitch) throws BitwigApiException {
        final String operation = "sendMidiNoteOff";
        logger.info("BitwigApiFacade: Sending MIDI Note Off - channel: " + channel + ", pitch: " + pitch);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if MIDI input is available
            if (noteInput == null) {
                throw new BitwigApiException(
                    ErrorCode.OPERATION_FAILED,
                    operation,
                    "MIDI functionality is not available. Please ensure a MIDI input port is configured in Bitwig."
                );
            }

            // Validate channel (0-15)
            if (channel < 0 || channel > 15) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI channel must be between 0 and 15, got: " + channel,
                    Map.of("channel", channel)
                );
            }

            // Validate pitch (0-127)
            if (pitch < 0 || pitch > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI pitch must be between 0 and 127, got: " + pitch,
                    Map.of("pitch", pitch)
                );
            }

            // Send MIDI note off message
            // Status byte: 0x80 (note off) + channel
            int statusByte = 0x80 | (channel & 0x0F);
            noteInput.sendRawMidiEvent(statusByte, pitch, 0);

            logger.info("BitwigApiFacade: Successfully sent MIDI Note Off");
        });
    }

    /**
     * Sends a MIDI Control Change message.
     *
     * @param channel The MIDI channel (0-15)
     * @param controller The controller number (0-127)
     * @param value The controller value (0-127)
     * @throws BitwigApiException if parameters are out of range
     */
    public void sendMidiCC(int channel, int controller, int value) throws BitwigApiException {
        final String operation = "sendMidiCC";
        logger.info("BitwigApiFacade: Sending MIDI CC - channel: " + channel + ", controller: " + controller + ", value: " + value);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if MIDI input is available
            if (noteInput == null) {
                throw new BitwigApiException(
                    ErrorCode.OPERATION_FAILED,
                    operation,
                    "MIDI functionality is not available. Please ensure a MIDI input port is configured in Bitwig."
                );
            }

            // Validate channel (0-15)
            if (channel < 0 || channel > 15) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI channel must be between 0 and 15, got: " + channel,
                    Map.of("channel", channel)
                );
            }

            // Validate controller (0-127)
            if (controller < 0 || controller > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI controller must be between 0 and 127, got: " + controller,
                    Map.of("controller", controller)
                );
            }

            // Validate value (0-127)
            if (value < 0 || value > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "MIDI CC value must be between 0 and 127, got: " + value,
                    Map.of("value", value)
                );
            }

            // Send MIDI CC message
            // Status byte: 0xB0 (control change) + channel
            int statusByte = 0xB0 | (channel & 0x0F);
            noteInput.sendRawMidiEvent(statusByte, controller, value);

            logger.info("BitwigApiFacade: Successfully sent MIDI CC");
        });
    }

    // ========================================
    // Clip Operations
    // ========================================

    /**
     * Creates an empty clip in the specified track and clip slot.
     *
     * @param trackIndex The track index (0-based)
     * @param slotIndex The clip slot index (0-based)
     * @param lengthInBars The length of the clip in bars
     * @throws BitwigApiException if parameters are invalid or operation fails
     */
    public void createEmptyClip(int trackIndex, int slotIndex, int lengthInBars) throws BitwigApiException {
        final String operation = "createEmptyClip";
        logger.info("BitwigApiFacade: Creating empty clip - track: " + trackIndex + ", slot: " + slotIndex + ", length: " + lengthInBars + " bars");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate track index
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            // Validate slot index
            if (slotIndex < 0 || slotIndex >= Constants.MAX_SCENES) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Slot index must be between 0 and " + (Constants.MAX_SCENES - 1) + ", got: " + slotIndex,
                    Map.of("slotIndex", slotIndex)
                );
            }

            // Validate length
            if (lengthInBars < 1 || lengthInBars > 16) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Length must be between 1 and 16 bars, got: " + lengthInBars,
                    Map.of("lengthInBars", lengthInBars)
                );
            }

            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }

            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            ClipLauncherSlot slot = slotBank.getItemAt(slotIndex);

            // Create empty clip - length in beats
            int lengthInBeats = lengthInBars * 4; // 4 beats per bar
            slot.createEmptyClip(lengthInBeats);
            slot.select(); // Select the slot so cursorClip points to it

            logger.info("BitwigApiFacade: Successfully created empty clip");
        });
    }

    /**
     * Writes multiple notes to the current cursor clip in a single batch operation.
     *
     * @param notes List of note data maps containing "p" (pitch), "s" (step), "v" (velocity), "d" (duration)
     * @throws BitwigApiException if parameters are invalid
     */
    public void writeNotesToClip(List<Map<String, Object>> notes) throws BitwigApiException {
        final String operation = "writeNotesToClip";
        logger.info("BitwigApiFacade: Writing " + notes.size() + " notes to clip in batch");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            int noteCount = 0;
            for (Map<String, Object> note : notes) {
                // Extract and validate parameters
                Object pObj = note.get("p");
                Object sObj = note.get("s");
                Object vObj = note.get("v");
                Object dObj = note.get("d");

                if (pObj == null || sObj == null || vObj == null || dObj == null) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_PARAMETER,
                        operation,
                        "Each note must have p, s, v, and d fields"
                    );
                }

                int pitch = ((Number) pObj).intValue();
                int stepPosition = ((Number) sObj).intValue();
                int velocity = ((Number) vObj).intValue();
                double durationInSteps = ((Number) dObj).doubleValue();

                // Validate ranges
                if (stepPosition < 0 || stepPosition >= 256) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Step position must be between 0 and 255, got: " + stepPosition + " for note " + noteCount
                    );
                }

                if (pitch < 0 || pitch > 127) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Pitch must be between 0 and 127, got: " + pitch + " for note " + noteCount
                    );
                }

                if (velocity < 1 || velocity > 127) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Velocity must be between 1 and 127, got: " + velocity + " for note " + noteCount
                    );
                }

                if (durationInSteps <= 0 || durationInSteps > 64) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Duration must be between 0 and 64 steps, got: " + durationInSteps + " for note " + noteCount
                    );
                }

                // Write the note
                double durationInBeats = durationInSteps / 4.0;
                cursorClip.setStep(stepPosition, pitch, velocity, durationInBeats);
                noteCount++;
            }

            logger.info("BitwigApiFacade: Successfully wrote " + noteCount + " notes to clip");
        });
    }

    /**
     * Writes a single note to the current cursor clip.
     *
     * @param stepPosition Step position in 16th notes (0-based)
     * @param pitch MIDI pitch (0-127, where 60 is Middle C)
     * @param velocity Note velocity (1-127)
     * @param durationInSteps Duration in 16th note steps
     * @throws BitwigApiException if parameters are invalid
     */
    public void writeNoteToClip(int stepPosition, int pitch, int velocity, double durationInSteps) throws BitwigApiException {
        final String operation = "writeNoteToClip";
        logger.info("BitwigApiFacade: Writing note - step: " + stepPosition + ", pitch: " + pitch + ", velocity: " + velocity + ", duration: " + durationInSteps + " steps (" + (durationInSteps/4.0) + " beats)");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate step position
            if (stepPosition < 0 || stepPosition >= 256) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Step position must be between 0 and 255, got: " + stepPosition,
                    Map.of("stepPosition", stepPosition)
                );
            }

            // Validate pitch
            if (pitch < 0 || pitch > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Pitch must be between 0 and 127, got: " + pitch,
                    Map.of("pitch", pitch)
                );
            }

            // Validate velocity
            if (velocity < 1 || velocity > 127) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Velocity must be between 1 and 127, got: " + velocity,
                    Map.of("velocity", velocity)
                );
            }

            // Validate duration
            if (durationInSteps <= 0 || durationInSteps > 64) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Duration must be between 0 and 64 steps, got: " + durationInSteps,
                    Map.of("durationInSteps", durationInSteps)
                );
            }

            // Write the note using setStep
            // Convert duration from 16th note steps to beats (4 steps = 1 beat)
            double durationInBeats = durationInSteps / 4.0;
            cursorClip.setStep(stepPosition, pitch, velocity, durationInBeats);

            logger.info("BitwigApiFacade: Successfully wrote note to clip");
        });
    }

    /**
     * Clears all notes from the current cursor clip.
     *
     * @throws BitwigApiException if operation fails
     */
    public void clearClip() throws BitwigApiException {
        final String operation = "clearClip";
        logger.info("BitwigApiFacade: Clearing clip");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            cursorClip.clearSteps();
            logger.info("BitwigApiFacade: Successfully cleared clip");
        });
    }

    /**
     * Sets the loop length of the current cursor clip.
     *
     * @param lengthInBars Length in bars
     * @throws BitwigApiException if parameters are invalid
     */
    public void setClipLoopLength(double lengthInBars) throws BitwigApiException {
        final String operation = "setClipLoopLength";
        logger.info("BitwigApiFacade: Setting clip loop length to " + lengthInBars + " bars");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (lengthInBars <= 0 || lengthInBars > 16) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Length must be between 0 and 16 bars, got: " + lengthInBars,
                    Map.of("lengthInBars", lengthInBars)
                );
            }

            double lengthInBeats = lengthInBars * 4.0; // 4 beats per bar
            cursorClip.getLoopLength().setRaw(lengthInBeats);
            cursorClip.getPlayStop().setRaw(lengthInBeats);

            logger.info("BitwigApiFacade: Successfully set clip loop length");
        });
    }

    /**
     * Selects a specific clip slot on a track in the clip launcher.
     */
    public void selectClipSlot(int trackIndex, int slotIndex) throws BitwigApiException {
        final String operation = "selectClipSlot";
        logger.info("BitwigApiFacade: Selecting clip slot - track: " + trackIndex + ", slot: " + slotIndex);
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }
            if (slotIndex < 0 || slotIndex >= Constants.MAX_SCENES) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Slot index must be between 0 and " + (Constants.MAX_SCENES - 1) + ", got: " + slotIndex,
                    Map.of("slotIndex", slotIndex)
                );
            }
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }
            ClipLauncherSlot slot = track.clipLauncherSlotBank().getItemAt(slotIndex);
            slot.select();
        });
    }

    /**
     * Clears all notes from the clip at the given track and slot.
     */
    public void clearClipAt(int trackIndex, int slotIndex) throws BitwigApiException {
        final String operation = "clearClipAt";
        logger.info("BitwigApiFacade: Clearing clip at track: " + trackIndex + ", slot: " + slotIndex);
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            selectClipSlot(trackIndex, slotIndex);
            clearClip();
        });
    }

    /**
     * Writes multiple notes to the clip at the given track and slot. Optionally replaces existing content.
     * Notes must follow the compact schema used by writeNotes (p,s,d,v).
     */
    public void writeNotesAt(int trackIndex, int slotIndex, List<Map<String, Object>> notes, boolean replace) throws BitwigApiException {
        final String operation = "writeNotesAt";
        logger.info("BitwigApiFacade: Writing " + (notes != null ? notes.size() : 0) + " notes at track: " + trackIndex + ", slot: " + slotIndex + ", replace=" + replace);
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (notes == null || notes.isEmpty()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Notes array must not be empty"
                );
            }
            selectClipSlot(trackIndex, slotIndex);
            if (replace) {
                cursorClip.clearSteps();
            }
            writeNotesToClip(notes);
        });
    }

    /**
     * Checks if a clip exists at the given track and slot.
     */
    public boolean hasClipAt(int trackIndex, int slotIndex) throws BitwigApiException {
        final String operation = "hasClipAt";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }
            if (slotIndex < 0 || slotIndex >= Constants.MAX_SCENES) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Slot index must be between 0 and " + (Constants.MAX_SCENES - 1) + ", got: " + slotIndex,
                    Map.of("slotIndex", slotIndex)
                );
            }
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                return false;
            }
            ClipLauncherSlotBank slotBank = track.clipLauncherSlotBank();
            ClipLauncherSlot slot = slotBank.getItemAt(slotIndex);
            return slot.hasContent().get();
        });
    }

    /**
     * Sets the name of the clip at the given track and slot.
     */
    public void setClipNameAt(int trackIndex, int slotIndex, String name) throws BitwigApiException {
        final String operation = "setClipNameAt";
        logger.info("BitwigApiFacade: Requested to set clip name at track: " + trackIndex + ", slot: " + slotIndex + " to '" + name + "'");
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Bitwig's public controller API does not support setting clip names directly via ClipLauncherSlot.name().
            // We expose a clear error to the caller so higher layers can report unsupported_property.
            throw new BitwigApiException(
                ErrorCode.OPERATION_FAILED,
                operation,
                "Setting clip name is not supported by the current Bitwig Controller API"
            );
        });
    }

    /**
     * Gets the name of the clip at the given track and slot, or null if empty.
     */
    public String getClipNameAt(int trackIndex, int slotIndex) throws BitwigApiException {
        final String operation = "getClipNameAt";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }
            ClipLauncherSlot slot = track.clipLauncherSlotBank().getItemAt(slotIndex);
            if (!slot.hasContent().get()) {
                return null;
            }
            String nm = slot.name().get();
            return (nm != null && nm.trim().isEmpty()) ? null : nm;
        });
    }

    /**
     * Sets the loop length in beats for the clip at the given track and slot.
     */
    public void setClipLoopLengthBeatsAt(int trackIndex, int slotIndex, double lengthBeats) throws BitwigApiException {
        final String operation = "setClipLoopLengthBeatsAt";
        logger.info("BitwigApiFacade: Setting clip loop length (beats) at track: " + trackIndex + ", slot: " + slotIndex + " to " + lengthBeats + " beats");
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (lengthBeats <= 0 || lengthBeats > 64) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Length (beats) must be between 0 and 64, got: " + lengthBeats,
                    Map.of("lengthBeats", lengthBeats)
                );
            }
            selectClipSlot(trackIndex, slotIndex);
            double bars = lengthBeats / 4.0;
            setClipLoopLength(bars);
        });
    }

    /**
     * Reads back the loop length (in beats) for the clip at the given track and slot.
     */
    public double getClipLoopLengthBeatsAt(int trackIndex, int slotIndex) throws BitwigApiException {
        final String operation = "getClipLoopLengthBeatsAt";
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            selectClipSlot(trackIndex, slotIndex);
            double beats = cursorClip.getLoopLength().get();
            return beats;
        });
    }

    /**
     * Inserts a Bitwig device using UUID-based matcher with reflection for API compatibility.
     * This method inserts the device at the specified position by using beforeDeviceInsertionPoint()
     * on the device currently at that position, or startOfDeviceChainInsertionPoint() for position 0.
     *
     * IMPORTANT: device_position parameter now works correctly. You can insert devices in normal order
     * (0, 1, 2, 3) and they will appear at the correct positions in the device chain.
     *
     * Example batch operation to insert 4 devices in sequence on tracks 0-19:
     * <pre>
     * {
     *   "name": "batch_operations",
     *   "arguments": {
     *     "operations": [
     *       {
     *         "type": "insert_device_on_tracks",
     *         "args": {
     *           "device_uuid": "e4815188-ba6f-4d14-bcfc-2dcb8f778ccb",
     *           "device_position": 0,
     *           "start_index": 0,
     *           "end_index": 19
     *         }
     *       },
     *       {
     *         "type": "insert_device_on_tracks",
     *         "args": {
     *           "device_uuid": "6d621c1c-ab64-43b4-aea3-dad37e6f649c",
     *           "device_position": 1,
     *           "start_index": 0,
     *           "end_index": 19
     *         }
     *       },
     *       {
     *         "type": "insert_device_on_tracks",
     *         "args": {
     *           "device_uuid": "2b1b4787-8d74-4138-877b-9197209eef0f",
     *           "device_position": 2,
     *           "start_index": 0,
     *           "end_index": 19
     *         }
     *       },
     *       {
     *         "type": "insert_device_on_tracks",
     *         "args": {
     *           "device_uuid": "d275f9a6-0e4a-409c-9dc4-d74af90bc7ae",
     *           "device_position": 3,
     *           "start_index": 0,
     *           "end_index": 19
     *         }
     *       }
     *     ]
     *   }
     * }
     * </pre>
     *
     * This will insert:
     * - Position 0: EQ+ (UUID: e4815188-ba6f-4d14-bcfc-2dcb8f778ccb)
     * - Position 1: Filter+ (UUID: 6d621c1c-ab64-43b4-aea3-dad37e6f649c)
     * - Position 2: Compressor+ (UUID: 2b1b4787-8d74-4138-877b-9197209eef0f)
     * - Position 3: Chorus+ (UUID: d275f9a6-0e4a-409c-9dc4-d74af90bc7ae)
     *
     * Technical implementation:
     * - Position 0: Uses startOfDeviceChainInsertionPoint() to insert at the beginning
     * - Position N (N>0): Uses afterDeviceInsertionPoint() on the device at position N-1
     * - If position N-1 is empty: Uses endOfDeviceChainInsertionPoint() to insert at the end
     *
     * @param trackIndex Track index (0-based)
     * @param devicePosition Position in device chain (0 = first position)
     * @param deviceUuidStr UUID string of the Bitwig device
     * @return true if device was inserted successfully, false otherwise
     * @throws BitwigApiException if parameters are invalid
     */
    public boolean insertBitwigDeviceByUuid(int trackIndex, int devicePosition, String deviceUuidStr) throws BitwigApiException {
        final String operation = "insertBitwigDeviceByUuid";
        logger.info("BitwigApiFacade: Inserting Bitwig device - track: " + trackIndex +
                    ", position: " + devicePosition + ", UUID: " + deviceUuidStr);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (devicePosition < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device position must be >= 0, got: " + devicePosition,
                    Map.of("devicePosition", devicePosition)
                );
            }

            Track track = trackBank.getItemAt(trackIndex);

            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    operation,
                    "Track does not exist at index: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            try {
                // Parse UUID
                java.util.UUID uuid = java.util.UUID.fromString(deviceUuidStr);

                // Get insertion point based on desired position
                Object insertionPoint = null;

                if (devicePosition == 0) {
                    // Insert at start of device chain
                    try {
                        java.lang.reflect.Method startOfChainMethod = track.getClass().getMethod("startOfDeviceChainInsertionPoint");
                        insertionPoint = startOfChainMethod.invoke(track);
                        logger.info("BitwigApiFacade: Using startOfDeviceChainInsertionPoint for position 0");
                    } catch (NoSuchMethodException e) {
                        logger.warn("BitwigApiFacade: startOfDeviceChainInsertionPoint not available, using endOfDeviceChainInsertionPoint");
                        java.lang.reflect.Method endOfChainMethod = track.getClass().getMethod("endOfDeviceChainInsertionPoint");
                        insertionPoint = endOfChainMethod.invoke(track);
                    }
                } else {
                    // Insert after the device at position (devicePosition - 1)
                    // This ensures we build the chain sequentially: 0, then after 0, then after 1, etc.
                    DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                    Device previousDevice = deviceBank.getItemAt(devicePosition - 1);

                    if (previousDevice.exists().get()) {
                        // Use afterDeviceInsertionPoint to insert after the previous device
                        java.lang.reflect.Method afterDeviceMethod = previousDevice.getClass().getMethod("afterDeviceInsertionPoint");
                        insertionPoint = afterDeviceMethod.invoke(previousDevice);
                        logger.info("BitwigApiFacade: Using afterDeviceInsertionPoint from device at position " + (devicePosition - 1));
                    } else {
                        // If the previous position doesn't have a device, insert at the end
                        java.lang.reflect.Method endOfChainMethod = track.getClass().getMethod("endOfDeviceChainInsertionPoint");
                        insertionPoint = endOfChainMethod.invoke(track);
                        logger.info("BitwigApiFacade: Position " + (devicePosition - 1) + " empty, using endOfDeviceChainInsertionPoint");
                    }
                }

                if (insertionPoint == null) {
                    logger.info("BitwigApiFacade: Could not get insertion point");
                    return false;
                }

                // Call insertBitwigDevice with UUID
                java.lang.reflect.Method insertBitwigDevice = insertionPoint.getClass().getMethod("insertBitwigDevice", java.util.UUID.class);
                insertBitwigDevice.invoke(insertionPoint, uuid);

                logger.info("BitwigApiFacade: Successfully inserted Bitwig device via UUID at position " + devicePosition);
                return true;

            } catch (java.lang.IllegalArgumentException e) {
                logger.info("BitwigApiFacade: Invalid UUID format: " + deviceUuidStr);
                return false;
            } catch (Exception e) {
                logger.info("BitwigApiFacade: Device insertion failed: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Gets the VST Plugin Scanner instance.
     *
     * @return The VST Plugin Scanner
     */
    public Object getVstPluginScanner() {
        // return vstPluginScanner; // TEMPORARILY DISABLED
        throw new UnsupportedOperationException("VstPluginScanner temporarily disabled");
    }

    // ========================================
    // Track Creation/Deletion Operations
    // ========================================

    /**
     * Creates a new instrument track at the end of the track list.
     *
     * @throws BitwigApiException if operation fails
     */
    public void createInstrumentTrack() throws BitwigApiException {
        final String operation = "createInstrumentTrack";
        logger.info("BitwigApiFacade: Creating instrument track");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            application.createInstrumentTrack(-1);
            logger.info("BitwigApiFacade: Successfully created instrument track");
        });
    }

    /**
     * Creates a new audio track at the end of the track list.
     *
     * @throws BitwigApiException if operation fails
     */
    public void createAudioTrack() throws BitwigApiException {
        final String operation = "createAudioTrack";
        logger.info("BitwigApiFacade: Creating audio track");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            application.createAudioTrack(-1);
            logger.info("BitwigApiFacade: Successfully created audio track");
        });
    }

    /**
     * Creates a new effect track at the end of the track list.
     *
     * @throws BitwigApiException if operation fails
     */
    public void createEffectTrack() throws BitwigApiException {
        final String operation = "createEffectTrack";
        logger.info("BitwigApiFacade: Creating effect track");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            application.createEffectTrack(-1);
            logger.info("BitwigApiFacade: Successfully created effect track");
        });
    }

    /**
     * Deletes the currently selected track.
     *
     * @throws BitwigApiException if no track is selected or operation fails
     */
    public void deleteSelectedTrack() throws BitwigApiException {
        final String operation = "deleteSelectedTrack";
        logger.info("BitwigApiFacade: Deleting selected track");

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (!cursorTrack.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "No track is currently selected"
                );
            }
            
            cursorTrack.deleteObject();
            logger.info("BitwigApiFacade: Successfully deleted selected track");
        });
    }

    /**
     * Deletes a track by index.
     *
     * @param trackIndex The index of the track to delete (0-based)
     * @throws BitwigApiException if track index is invalid or operation fails
     */
    public void deleteTrack(int trackIndex) throws BitwigApiException {
        final String operation = "deleteTrack";
        logger.info("BitwigApiFacade: Deleting track at index " + trackIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }

            track.deleteObject();
            logger.info("BitwigApiFacade: Successfully deleted track at index " + trackIndex);
        });
    }

    // ========================================
    // Track Properties Operations
    // ========================================

    /**
     * Sets track properties (mute, solo, arm, volume, pan, color).
     *
     * @param trackIndex The index of the track (0-based)
     * @param mute Optional mute state
     * @param solo Optional solo state
     * @param arm Optional arm state
     * @param volume Optional volume (0.0-1.0)
     * @param pan Optional pan (0.0-1.0, 0.5 is center)
     * @param color Optional color in rgb(r,g,b) format with values 0-255
     * @throws BitwigApiException if track index is invalid or operation fails
     */
    public void setTrackProperties(int trackIndex, Boolean mute, Boolean solo, Boolean arm, 
                                   Double volume, Double pan, String color) throws BitwigApiException {
        final String operation = "setTrackProperties";
        logger.info("BitwigApiFacade: Setting track properties for track " + trackIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }

            if (mute != null) {
                track.mute().set(mute);
                logger.info("BitwigApiFacade: Set mute to " + mute);
            }

            if (solo != null) {
                track.solo().set(solo);
                logger.info("BitwigApiFacade: Set solo to " + solo);
            }

            if (arm != null) {
                track.arm().set(arm);
                logger.info("BitwigApiFacade: Set arm to " + arm);
            }

            if (volume != null) {
                if (volume < 0.0 || volume > 1.0) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Volume must be between 0.0 and 1.0, got: " + volume
                    );
                }
                track.volume().set(volume);
                logger.info("BitwigApiFacade: Set volume to " + volume);
            }

            if (pan != null) {
                if (pan < 0.0 || pan > 1.0) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Pan must be between 0.0 and 1.0, got: " + pan
                    );
                }
                track.pan().set(pan);
                logger.info("BitwigApiFacade: Set pan to " + pan);
            }

            if (color != null && !color.isEmpty()) {
                try {
                    String[] rgb = color.replace("rgb(", "").replace(")", "").split(",");
                    float r = Float.parseFloat(rgb[0].trim()) / 255.0f;
                    float g = Float.parseFloat(rgb[1].trim()) / 255.0f;
                    float b = Float.parseFloat(rgb[2].trim()) / 255.0f;
                    track.color().set(r, g, b);
                    logger.info("BitwigApiFacade: Set color to " + color);
                } catch (Exception e) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_PARAMETER,
                        operation,
                        "Invalid color format. Expected rgb(r,g,b) with values 0-255, got: " + color
                    );
                }
            }

            logger.info("BitwigApiFacade: Successfully set track properties");
        });
    }

    /**
     * Sets send properties for a specific track and send index.
     *
     * @param trackIndex The index of the track (0-based)
     * @param sendIndex The index of the send (0-based)
     * @param volume Optional send volume (0.0-1.0)
     * @param enabled Optional send enabled state
     * @throws BitwigApiException if indices are invalid or operation fails
     */
    public void setTrackSend(int trackIndex, int sendIndex, Double volume, Boolean enabled) throws BitwigApiException {
        final String operation = "setTrackSend";
        logger.info("BitwigApiFacade: Setting send " + sendIndex + " for track " + trackIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            Track track = trackBank.getItemAt(trackIndex);
            if (!track.exists().get()) {
                throw new BitwigApiException(
                    ErrorCode.TRACK_NOT_FOUND,
                    operation,
                    "Track at index " + trackIndex + " does not exist"
                );
            }

            SendBank sendBank = track.sendBank();
            int sendCount = sendBank.getSizeOfBank();

            if (sendIndex < 0 || sendIndex >= sendCount) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Send index must be between 0 and " + (sendCount - 1) + ", got: " + sendIndex,
                    Map.of("trackIndex", trackIndex, "sendIndex", sendIndex, "sendCount", sendCount)
                );
            }

            Send send = sendBank.getItemAt(sendIndex);

            if (volume != null) {
                if (volume < 0.0 || volume > 1.0) {
                    throw new BitwigApiException(
                        ErrorCode.INVALID_RANGE,
                        operation,
                        "Send volume must be between 0.0 and 1.0, got: " + volume
                    );
                }
                send.value().set(volume);
                logger.info("BitwigApiFacade: Set send volume to " + volume);
            }

            if (enabled != null) {
                send.isEnabled().set(enabled);
                logger.info("BitwigApiFacade: Set send enabled to " + enabled);
            }

            logger.info("BitwigApiFacade: Successfully set send properties");
        });
    }

    // ========================================
    // Remote Controls Page Navigation
    // ========================================

    /**
     * Switches the remote controls page for the currently selected device.
     * Device pages contain different parameter mappings (e.g., "Gains", "Freqs", "Qs" for EQ+).
     *
     * @param pageIndex The zero-based page index to switch to
     * @throws BitwigApiException if no device is selected or page index is invalid
     */
    public void switchDeviceRemoteControlsPage(int pageIndex) throws BitwigApiException {
        final String operation = "switchDeviceRemoteControlsPage";
        logger.info("BitwigApiFacade: Switching device remote controls page to index: " + pageIndex);

        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                    "No device is currently selected");
            }

            // Validate page index
            if (pageIndex < 0) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Page index must be non-negative, got: " + pageIndex);
            }

            // Switch to the specified page
            deviceParameterBank.selectedPageIndex().set(pageIndex);
            
            logger.info("BitwigApiFacade: Successfully switched to page " + pageIndex);
        });
    }

    /**
     * Gets the current remote controls page index for the selected device.
     *
     * @return The current page index
     * @throws BitwigApiException if no device is selected
     */
    public int getCurrentDeviceRemoteControlsPageIndex() throws BitwigApiException {
        final String operation = "getCurrentDeviceRemoteControlsPageIndex";
        
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                    "No device is currently selected");
            }

            return deviceParameterBank.selectedPageIndex().get();
        });
    }

    /**
     * Gets the total number of remote controls pages for the selected device.
     *
     * @return The total page count
     * @throws BitwigApiException if no device is selected
     */
    public int getDeviceRemoteControlsPageCount() throws BitwigApiException {
        final String operation = "getDeviceRemoteControlsPageCount";

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Check if device is selected
            if (!cursorDevice.exists().get()) {
                throw new BitwigApiException(ErrorCode.DEVICE_NOT_SELECTED, operation,
                    "No device is currently selected");
            }

            return deviceParameterBank.pageCount().get();
        });
    }

    /**
     * Sets a device parameter value on a specific track.
     * Uses reflection to access device's CursorRemoteControlsPage and parameter.
     *
     * @param trackIndex The track index (0-based)
     * @param deviceIndex The device index on the track (0-based)
     * @param parameterIndex The parameter index on the current page (0-based, typically 0-7)
     * @param value The normalized value (0.0 to 1.0)
     * @return true if parameter was set successfully, false otherwise
     * @throws BitwigApiException if parameters are invalid
     */
    public boolean setDeviceParameterOnTrack(int trackIndex, int deviceIndex, int parameterIndex, double value) throws BitwigApiException {
        final String operation = "setDeviceParameterOnTrack";
        logger.info("BitwigApiFacade: Setting device parameter - track: " + trackIndex +
                    ", device: " + deviceIndex + ", param: " + parameterIndex + ", value: " + value);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            if (parameterIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Parameter index must be >= 0, got: " + parameterIndex,
                    Map.of("parameterIndex", parameterIndex)
                );
            }

            if (value < 0.0 || value > 1.0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Value must be between 0.0 and 1.0, got: " + value,
                    Map.of("value", value)
                );
            }

            try {
                // Select the track first
                Track track = trackBank.getItemAt(trackIndex);
                track.selectInEditor();

                // Get the device bank for this track and select the device
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at index " + deviceIndex + " does not exist on track " + trackIndex);
                    return false;
                }

                // Select the device - this makes cursorDevice point to this device
                device.selectInEditor();

                // Small delay to allow selection to take effect
                Thread.sleep(50);

                // Now use the already-initialized deviceParameterBank which follows cursorDevice
                RemoteControl parameter = deviceParameterBank.getParameter(parameterIndex);
                parameter.value().set(value);

                logger.info("BitwigApiFacade: Successfully set parameter " + parameterIndex + " to " + value);
                return true;

            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to set parameter: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Gets the displayed value of a device parameter (e.g., "107 Hz", "-6 dB").
     * This allows the LLM to see what the normalized value actually means.
     *
     * @param trackIndex The track index (0-based)
     * @param deviceIndex The device index on the track (0-based)
     * @param parameterIndex The parameter index on the current page (0-based, typically 0-7)
     * @return The displayed value string (e.g., "107 Hz"), or null if not available
     * @throws BitwigApiException if parameters are invalid
     */
    public String getDeviceParameterDisplayedValue(int trackIndex, int deviceIndex, int parameterIndex) throws BitwigApiException {
        final String operation = "getDeviceParameterDisplayedValue";

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            try {
                // Select the track and device (same as setDeviceParameterOnTrack)
                Track track = trackBank.getItemAt(trackIndex);
                track.selectInEditor();

                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    return null;
                }

                device.selectInEditor();
                Thread.sleep(50);

                // Get the displayed value from the parameter
                RemoteControl parameter = deviceParameterBank.getParameter(parameterIndex);
                String displayedValue = parameter.displayedValue().get();

                return displayedValue;

            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to get parameter displayed value: " + e.getClass().getSimpleName());
                return null;
            }
        });
    }

    /**
     * Gets the available remote control page names for a device at a specific position on a track.
     *
     * @param trackIndex  The index of the track (0-based)
     * @param deviceIndex The index of the device on the track (0-based)
     * @return List of page names, or empty list if device doesn't exist or no pages available
     * @throws BitwigApiException if parameters are invalid
     */
    public List<String> getDevicePageNames(int trackIndex, int deviceIndex) throws BitwigApiException {
        final String operation = "getDevicePageNames";
        logger.info("BitwigApiFacade: Getting device page names - track: " + trackIndex + ", device: " + deviceIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            try {
                // Select the track if needed
                if (currentlySelectedTrackIndex == null || !currentlySelectedTrackIndex.equals(trackIndex)) {
                    Track track = trackBank.getItemAt(trackIndex);
                    track.selectInEditor();
                    Thread.sleep(50);
                    currentlySelectedTrackIndex = trackIndex;
                }

                // Get the device bank for this track and check if device exists
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at index " + deviceIndex + " does not exist on track " + trackIndex);
                    return List.of();
                }

                // Select the device if needed - this makes cursorDevice point to this device
                // IMPORTANT: Always select the device to ensure cache is fresh, even if it was previously selected
                device.selectInEditor();
                Thread.sleep(150);
                currentlySelectedDeviceIndex = deviceIndex;
                currentlySelectedPageIndex = null; // Reset page when switching device
                logger.info("BitwigApiFacade: Selected device at index " + deviceIndex + " for fresh page names retrieval");

                // Wait for deviceParameterBank to sync with the newly selected device
                Thread.sleep(200);

                // CRITICAL: Force refresh of pageNames by first checking pageCount
                int pageCount = deviceParameterBank.pageCount().get();
                logger.info("BitwigApiFacade: Device has " + pageCount + " pages");
                Thread.sleep(100);

                // Now use the already-initialized deviceParameterBank which follows cursorDevice
                String[] pageNamesArray = deviceParameterBank.pageNames().get();
                List<String> pageNames = Arrays.asList(pageNamesArray);
                logger.info("BitwigApiFacade: Found " + pageNames.size() + " pages: " + pageNames);
                return pageNames;

            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to get page names: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return List.of();
            }
        });
    }

    /**
     * Gets the parameter information for the current page of a device.
     *
     * @param trackIndex  The index of the track (0-based)
     * @param deviceIndex The index of the device on the track (0-based)
     * @return List of parameter info maps containing name, value, valueString
     * @throws BitwigApiException if parameters are invalid
     */
    public List<Map<String, Object>> getDevicePageParameters(int trackIndex, int deviceIndex) throws BitwigApiException {
        final String operation = "getDevicePageParameters";
        logger.info("BitwigApiFacade: Getting device page parameters - track: " + trackIndex + ", device: " + deviceIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            try {
                // Select the track if needed
                if (currentlySelectedTrackIndex == null || !currentlySelectedTrackIndex.equals(trackIndex)) {
                    Track track = trackBank.getItemAt(trackIndex);
                    track.selectInEditor();
                    Thread.sleep(50);
                    currentlySelectedTrackIndex = trackIndex;
                }

                // Get the device bank for this track and check if device exists
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at index " + deviceIndex + " does not exist on track " + trackIndex);
                    return List.of();
                }

                // Select the device if needed - this makes cursorDevice point to this device
                // IMPORTANT: Always select the device to ensure cache is fresh
                device.selectInEditor();
                Thread.sleep(150);
                currentlySelectedDeviceIndex = deviceIndex;
                currentlySelectedPageIndex = null; // Reset page when switching device

                // Now use the already-initialized deviceParameterBank which follows cursorDevice
                List<Map<String, Object>> parameters = new ArrayList<>();
                for (int i = 0; i < deviceParameterBank.getParameterCount(); i++) {
                    RemoteControl param = deviceParameterBank.getParameter(i);
                    Map<String, Object> paramInfo = new LinkedHashMap<>();
                    paramInfo.put("index", i);
                    paramInfo.put("name", param.name().get());
                    paramInfo.put("value", param.value().get());
                    paramInfo.put("displayedValue", param.displayedValue().get());
                    parameters.add(paramInfo);
                }

                logger.info("BitwigApiFacade: Found " + parameters.size() + " parameters on current page");
                return parameters;

            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to get page parameters: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return List.of();
            }
        });
    }

    /**
     * Switches the remote controls page for a specific device on a specific track.
     * Uses reflection to access device's CursorRemoteControlsPage and set the page index.
     *
     * @param trackIndex The track index (0-based)
     * @param deviceIndex The device index on the track (0-based)
     * @param pageIndex The page index to switch to (0-based)
     * @return true if page was switched successfully, false otherwise
     * @throws BitwigApiException if parameters are invalid
     */
    public boolean switchDevicePageOnTrack(int trackIndex, int deviceIndex, int pageIndex) throws BitwigApiException {
        final String operation = "switchDevicePageOnTrack";
        logger.info("BitwigApiFacade: Switching device page - track: " + trackIndex +
                    ", device: " + deviceIndex + ", page: " + pageIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            if (pageIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Page index must be >= 0, got: " + pageIndex,
                    Map.of("pageIndex", pageIndex)
                );
            }

            try {
                // Check if we're already on the requested device and page
                // IMPORTANT: If currentlySelectedPageIndex is null, we must still switch the page!
                if (currentlySelectedTrackIndex != null && currentlySelectedTrackIndex.equals(trackIndex) &&
                    currentlySelectedDeviceIndex != null && currentlySelectedDeviceIndex.equals(deviceIndex) &&
                    currentlySelectedPageIndex != null && currentlySelectedPageIndex.equals(pageIndex)) {
                    logger.info("BitwigApiFacade: Already on track " + trackIndex + ", device " + deviceIndex + ", page " + pageIndex + " - skipping switch");
                    return true;
                }

                // Select the track if needed
                if (currentlySelectedTrackIndex == null || !currentlySelectedTrackIndex.equals(trackIndex)) {
                    Track track = trackBank.getItemAt(trackIndex);
                    track.selectInEditor();
                    Thread.sleep(50);
                    currentlySelectedTrackIndex = trackIndex;
                }

                // Get the device bank for this track and check if device exists
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at index " + deviceIndex + " does not exist on track " + trackIndex);
                    currentlySelectedDeviceIndex = null;
                    currentlySelectedPageIndex = null;
                    return false;
                }

                // Select the device if needed - this makes cursorDevice point to this device
                if (currentlySelectedDeviceIndex == null || !currentlySelectedDeviceIndex.equals(deviceIndex)) {
                    device.selectInEditor();
                    Thread.sleep(150);
                    currentlySelectedDeviceIndex = deviceIndex;
                    currentlySelectedPageIndex = null; // Reset page when switching device
                }

                // Switch page - ALWAYS perform the switch, even if cache says we're on that page
                // This is critical because the cache can be stale
                deviceParameterBank.selectedPageIndex().set(pageIndex);
                Thread.sleep(250);
                currentlySelectedPageIndex = pageIndex;

                logger.info("BitwigApiFacade: Successfully switched to track " + trackIndex + ", device " + deviceIndex + ", page " + pageIndex);
                return true;

            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to switch device page: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Gets the name of a device on a specific track.
     *
     * @param trackIndex The index of the track (0-based)
     * @param deviceIndex The index of the device on the track (0-based)
     * @return The device name
     * @throws BitwigApiException if parameters are invalid
     */
    public String getDeviceNameOnTrack(int trackIndex, int deviceIndex) throws BitwigApiException {
        final String operation = "getDeviceNameOnTrack";
        
        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            try {
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    return "Unknown Device";
                }

                return device.name().get();
            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to get device name: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                return "Unknown Device";
            }
        });
    }

    /**
     * Sets parameters for the current page of a device.
     *
     * @param trackIndex The index of the track (0-based)
     * @param deviceIndex The index of the device on the track (0-based)
     * @param parameters List of parameter maps with "parameter_index" and "value" keys
     * @throws BitwigApiException if parameters are invalid
     */
    public void setDevicePageParameters(int trackIndex, int deviceIndex, List<Map<String, Object>> parameters) throws BitwigApiException {
        final String operation = "setDevicePageParameters";
        
        WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            if (trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", trackIndex)
                );
            }

            if (deviceIndex < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device index must be >= 0, got: " + deviceIndex,
                    Map.of("deviceIndex", deviceIndex)
                );
            }

            try {
                // Select the track if needed
                if (currentlySelectedTrackIndex == null || !currentlySelectedTrackIndex.equals(trackIndex)) {
                    Track track = trackBank.getItemAt(trackIndex);
                    track.selectInEditor();
                    Thread.sleep(50);
                    currentlySelectedTrackIndex = trackIndex;
                }

                // Get the device bank for this track and check if device exists
                DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
                Device device = deviceBank.getItemAt(deviceIndex);

                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at index " + deviceIndex + " does not exist on track " + trackIndex);
                    return null;
                }

                // Select the device if needed
                if (currentlySelectedDeviceIndex == null || !currentlySelectedDeviceIndex.equals(deviceIndex)) {
                    device.selectInEditor();
                    Thread.sleep(150);
                    currentlySelectedDeviceIndex = deviceIndex;
                    currentlySelectedPageIndex = null;
                }

                // Set each parameter
                for (Map<String, Object> param : parameters) {
                    Integer paramIndex = ((Number) param.get("index")).intValue();
                    Double paramValue = ((Number) param.get("value")).doubleValue();
                    
                    if (paramIndex >= 0 && paramIndex < deviceParameterBank.getParameterCount()) {
                        RemoteControl remote = deviceParameterBank.getParameter(paramIndex);
                        remote.value().set(paramValue);
                        logger.info("BitwigApiFacade: Set parameter " + paramIndex + " to " + paramValue);
                    }
                }

                Thread.sleep(100);
                logger.info("BitwigApiFacade: Set " + parameters.size() + " parameters for device " + deviceIndex + " on track " + trackIndex);
                return null;
            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to set device parameters: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                throw new BitwigApiException(
                    ErrorCode.BITWIG_API_ERROR,
                    operation,
                    "Failed to set device parameters: " + e.getMessage(),
                    Map.of("trackIndex", trackIndex, "deviceIndex", deviceIndex, "parameterCount", parameters.size())
                );
            }
        });
    }

    /**
     * Gets device page parameters for a specific page index using Moss's approach.
     * Reads directly from deviceParameterBank push buffer WITHOUT page switching in the UI.
     * 
     * @param trackIndex Track index (0-based)
     * @param devicePosition Device position on track (0-based)
     * @param pageIndex The specific page index to read (0-based)
     * @return List of parameter info for the given page
     * @throws BitwigApiException if track/device is invalid
     */
    public List<Map<String, Object>> getDevicePageParametersForPageIndex(Integer trackIndex, Integer devicePosition, int pageIndex) throws BitwigApiException {
        final String operation = "getDevicePageParametersForPageIndex";
        logger.info("BitwigApiFacade: Getting device page parameters - track: " + trackIndex + 
                   ", device: " + devicePosition + ", page: " + pageIndex);

        return WigAIErrorHandler.executeWithErrorHandling(operation, () -> {
            // Validate track index
            if (trackIndex == null || trackIndex < 0 || trackIndex >= trackBank.getSizeOfBank()) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Track index must be between 0 and " + (trackBank.getSizeOfBank() - 1) + ", got: " + trackIndex,
                    Map.of("trackIndex", String.valueOf(trackIndex))
                );
            }

            // Validate device position
            if (devicePosition == null || devicePosition < 0) {
                throw new BitwigApiException(
                    ErrorCode.INVALID_RANGE,
                    operation,
                    "Device position must be >= 0, got: " + devicePosition,
                    Map.of("devicePosition", String.valueOf(devicePosition))
                );
            }

            List<Map<String, Object>> parameters = new ArrayList<>();

            try {
                Track track = trackBank.getItemAt(trackIndex);
                if (!track.exists().get()) {
                    throw new BitwigApiException(
                        ErrorCode.TRACK_NOT_FOUND,
                        operation,
                        "Track at index " + trackIndex + " does not exist",
                        Map.of("trackIndex", String.valueOf(trackIndex))
                    );
                }

                DeviceBank trackDeviceBank = trackDeviceBanks.get(trackIndex);
                if (devicePosition >= trackDeviceBank.getSizeOfBank()) {
                    logger.info("BitwigApiFacade: Device position " + devicePosition + " out of bounds for track " + trackIndex);
                    return parameters;
                }

                Device device = trackDeviceBank.getItemAt(devicePosition);
                if (!device.exists().get()) {
                    logger.info("BitwigApiFacade: Device at position " + devicePosition + " does not exist on track " + trackIndex);
                    return parameters;
                }

                // MOSS'S TRICK: Use the push buffer directly
                // Select the device
                device.selectInEditor();
                Thread.sleep(50);

                // Set the page index
                deviceParameterBank.selectedPageIndex().set(pageIndex);
                Thread.sleep(50);

                // Read all parameters from this page
                for (int i = 0; i < 8; i++) {
                    RemoteControl parameter = deviceParameterBank.getParameter(i);
                    boolean exists = parameter.exists().get();

                    if (exists) {
                        String name = parameter.name().get();
                        double value = parameter.value().get();
                        String displayValue = parameter.displayedValue().get();

                        // Handle null or empty names
                        if (name != null && name.trim().isEmpty()) {
                            name = null;
                        }

                        Map<String, Object> paramMap = new LinkedHashMap<>();
                        paramMap.put("index", i);
                        paramMap.put("name", name);
                        paramMap.put("value", value);
                        paramMap.put("display_value", displayValue);
                        parameters.add(paramMap);
                    }
                }

                logger.info("BitwigApiFacade: Retrieved " + parameters.size() + " parameters from page " + pageIndex);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BitwigApiException(
                    ErrorCode.OPERATION_FAILED,
                    operation,
                    "Thread interrupted while reading device parameters: " + e.getMessage(),
                    Map.of("pageIndex", String.valueOf(pageIndex))
                );
            } catch (Exception e) {
                logger.info("BitwigApiFacade: Failed to get page parameters: " + e.getClass().getSimpleName() +
                           " - " + (e.getMessage() != null ? e.getMessage() : ""));
                e.printStackTrace();
                return parameters;
            }

            return parameters;
        });
    }

    /**
     * Force a complete cache refresh for device page names.
     * Call this when switching between devices to ensure pageNames() is fresh.
     */
    public void forceRefreshDevicePageNamesCache(int trackIndex, int deviceIndex) throws BitwigApiException {
        final String operation = "forceRefreshDevicePageNamesCache";
        logger.info("BitwigApiFacade: Force refreshing page names cache - track: " + trackIndex + ", device: " + deviceIndex);

        try {
            // Select the track
            if (currentlySelectedTrackIndex == null || !currentlySelectedTrackIndex.equals(trackIndex)) {
                Track track = trackBank.getItemAt(trackIndex);
                track.selectInEditor();
                Thread.sleep(50);
                currentlySelectedTrackIndex = trackIndex;
            }

            // Get the device and select it
            DeviceBank deviceBank = trackDeviceBanks.get(trackIndex);
            Device device = deviceBank.getItemAt(deviceIndex);

            if (!device.exists().get()) {
                logger.info("BitwigApiFacade: Device does not exist at index " + deviceIndex);
                return;
            }

            // CRITICAL: Select device and wait longer
            device.selectInEditor();
            Thread.sleep(250);
            
            // Reset internal cache markers
            currentlySelectedDeviceIndex = deviceIndex;
            currentlySelectedPageIndex = null;
            
            // Force refresh by reading pageCount (this triggers Bitwig sync)
            int pageCount = deviceParameterBank.pageCount().get();
            logger.info("BitwigApiFacade: Page count after refresh: " + pageCount);
            
            Thread.sleep(150);
            
            // Now pageNames() should be fresh
            String[] pageNamesArray = deviceParameterBank.pageNames().get();
            logger.info("BitwigApiFacade: Cache refresh complete. Pages: " + Arrays.asList(pageNamesArray));

        } catch (Exception e) {
            logger.info("BitwigApiFacade: Failed to refresh cache: " + e.getClass().getSimpleName());
            e.printStackTrace();
        }
    }

    // ========================================
    // ARRANGER / CUE MARKER METHODS
    // ========================================

    /**
     * Gets all cue markers in the project with their details.
     *
     * @return A list of cue marker information maps containing index, name, position, and color
     */
    public List<Map<String, Object>> getAllCueMarkersInfo() {
        logger.info("BitwigApiFacade: Getting all cue markers info");
        return arrangerFacade.getAllCueMarkersInfo();
    }

    /**
     * Finds the first cue marker index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findCueMarkerByName(String markerName) {
        return arrangerFacade.findCueMarkerByName(markerName);
    }

    /**
     * Launches (jumps to) a cue marker by index.
     *
     * @param index The marker index to launch
     * @return true if successful, false otherwise
     */
    public boolean launchCueMarker(int index) {
        return arrangerFacade.launchCueMarker(index);
    }

    /**
     * Creates a new cue marker at the current playback position.
     *
     * @return true if successful, false otherwise
     */
    public boolean addCueMarkerAtPlaybackPosition() {
        logger.info("BitwigApiFacade: Creating cue marker at playback position");
        return arrangerFacade.addCueMarkerAtPlaybackPosition();
    }

    /**
     * Sets the playback position in beats.
     * If transport is playing, jumps to the new position immediately.
     *
     * @param beats The position in beats (0.0 = start of timeline)
     */
    public void setPlaybackPosition(double beats) {
        logger.info("BitwigApiFacade: Setting playback position to " + beats + " beats");
        transport.playStartPosition().set(beats);
        if (transport.isPlaying().get()) {
            transport.jumpToPlayStartPosition();
        }
    }

    /**
     * Gets whether cue markers are currently visible in the arranger.
     *
     * @return true if markers are visible
     */
    public boolean areCueMarkersVisible() {
        return arrangerFacade.areCueMarkersVisible();
    }

    /**
     * Sets the visibility of cue markers in the arranger.
     *
     * @param visible true to show markers, false to hide
     */
    public void setCueMarkersVisible(boolean visible) {
        arrangerFacade.setCueMarkersVisible(visible);
    }

    /**
     * Gets the maximum number of cue markers supported.
     *
     * @return The maximum number of cue markers
     */
    public int getMaxCueMarkers() {
        return arrangerFacade.getMaxCueMarkers();
    }

    /**
     * Creates a new scene at the specified index position.
     *
     * @param index The index where to create the scene
     * @return true if successful, false otherwise
     */
    public boolean createScene(int index) {
        logger.info("BitwigApiFacade: Creating scene at index " + index);
        return sceneBankFacade.createScene(index);
    }

    /**
     * Sets the name of a scene at the specified index.
     *
     * @param index The scene index
     * @param name The name to set
     * @return true if successful, false otherwise
     */
    public boolean setSceneName(int index, String name) {
        logger.info("BitwigApiFacade: Setting scene " + index + " name to '" + name + "'");
        return sceneBankFacade.setSceneName(index, name);
    }

    /**
     * Launches (triggers) a scene at the specified index.
     *
     * @param index The scene index to launch
     * @return true if successful, false otherwise
     */
    public boolean launchScene(int index) {
        logger.info("BitwigApiFacade: Launching scene at index " + index);
        return sceneBankFacade.launchScene(index);
    }
}
