package io.github.fabb.wigai.features;

import io.github.fabb.wigai.bitwig.BitwigApiFacade;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.common.error.BitwigApiException;
import io.github.fabb.wigai.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for clip and scene launching operations in Bitwig Studio.
 * Handles the business logic for session control including clip launching,
 * track finding, and validation of clip operations.
 */
public class ClipSceneController {

    private final BitwigApiFacade bitwigApiFacade;
    private final Logger logger;

    /**
     * Constructs a ClipSceneController with required dependencies.
     *
     * @param bitwigApiFacade The facade for Bitwig API interactions
     * @param logger The logger service for operation logging
     */
    public ClipSceneController(BitwigApiFacade bitwigApiFacade, Logger logger) {
        this.bitwigApiFacade = bitwigApiFacade;
        this.logger = logger;
    }

    /**
     * Launches all clips in the specified scene index across all tracks.
     *
     * @param sceneIndex The zero-based index of the scene to launch
     * @return SceneLaunchResult indicating success/failure and any error details
     */
    public SceneLaunchResult launchSceneByIndex(int sceneIndex) {
        try {
            logger.info("Attempting to launch scene at index: " + sceneIndex);

            // Validate scene index
            if (sceneIndex < 0) {
                logger.warn("Scene index " + sceneIndex + " is negative");
                return SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene index must be non-negative");
            }

            int trackCount = bitwigApiFacade.getTrackBankSize();
            int launchedCount = 0;
            boolean anyTrack = false;

            for (int trackIdx = 0; trackIdx < trackCount; trackIdx++) {
                try {
                    String trackName = bitwigApiFacade.getTrackNameByIndex(trackIdx);
                    anyTrack = true;
                    int clipCount = bitwigApiFacade.getTrackClipCount(trackName);
                    if (sceneIndex < clipCount) {
                        bitwigApiFacade.launchClip(trackName, sceneIndex);
                        launchedCount++;
                    }
                } catch (BitwigApiException e) {
                    // Track doesn't exist or clip launch failed, continue with next track
                    logger.warn("Failed to launch clip on track " + trackIdx + ": " + e.getMessage());
                }
            }

            if (!anyTrack) {
                logger.warn("No tracks found in Bitwig session");
                return SceneLaunchResult.error("SCENE_NOT_FOUND", "No tracks found in Bitwig session");
            }

            if (launchedCount > 0) {
                String msg = "Scene " + sceneIndex + " launched on " + launchedCount + " track(s).";
                logger.info(msg);
                return SceneLaunchResult.success(msg);
            } else {
                logger.warn("Scene index " + sceneIndex + " out of bounds for all tracks");
                return SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene index " + sceneIndex + " is out of bounds for all tracks");
            }
        } catch (Exception e) {
            logger.error("Unexpected error launching scene: " + e.getMessage(), e);
            return SceneLaunchResult.error("BITWIG_ERROR", "Internal error occurred while launching scene: " + e.getMessage());
        }
    }

    /**
     * Launches all clips in the scene with the given name (case-sensitive, first match wins).
     *
     * @param sceneName The name of the scene to launch
     * @return SceneLaunchResult indicating success/failure and any error details
     */
    public SceneLaunchResult launchSceneByName(String sceneName) {
        logger.info("Received request to launch scene by name: '" + sceneName + "'");
        if (sceneName == null || sceneName.trim().isEmpty()) {
            logger.warn("Scene name is empty or null");
            return SceneLaunchResult.error("SCENE_NOT_FOUND", "scene_name must be a non-empty string");
        }
        int sceneIndex = bitwigApiFacade.findSceneByName(sceneName);
        logger.info("Searching for scene '" + sceneName + "' (case-sensitive)");
        if (sceneIndex < 0) {
            logger.warn("Scene not found: '" + sceneName + "'");
            return SceneLaunchResult.error("SCENE_NOT_FOUND", "Scene '" + sceneName + "' not found");
        }
        logger.info("Found scene '" + sceneName + "' at index " + sceneIndex + ". Launching...");
        SceneLaunchResult result = launchSceneByIndex(sceneIndex);
        if (result.isSuccess()) {
            String msg = "Scene '" + sceneName + "' launched.";
            logger.info(msg);
            return SceneLaunchResult.success(msg + " (index: " + sceneIndex + ")");
        } else {
            logger.error("Failed to launch scene by name: '" + sceneName + "' - " + result.getMessage());
            return result;
        }
    }

    public BitwigApiFacade getBitwigApiFacade() {
        return bitwigApiFacade;
    }

    /**
     * Gets detailed information for all clips within a specific scene.
     *
     * @param sceneIndex The zero-based index of the scene (optional if sceneName provided)
     * @param sceneName The name of the scene (optional if sceneIndex provided)
     * @return List of clip slot objects for the specified scene
     */
    public Object getClipsInScene(Integer sceneIndex, String sceneName) {
        try {
            logger.info("Getting clips in scene - Index: " + sceneIndex + ", Name: '" + sceneName + "'");

            // Determine target scene index
            int targetSceneIndex;
            if (sceneName != null && !sceneName.trim().isEmpty()) {
                // Scene name takes precedence - case-insensitive comparison
                String normalizedName = sceneName.trim();
                targetSceneIndex = findSceneByNameCaseInsensitive(normalizedName);
                if (targetSceneIndex < 0) {
                    throw new BitwigApiException(
                        ErrorCode.SCENE_NOT_FOUND,
                        "get_clips_in_scene",
                        "Scene not found: " + sceneName,
                        Map.of("scene_name", sceneName)
                    );
                }
                logger.info("Found scene '" + sceneName + "' at index " + targetSceneIndex);
            } else if (sceneIndex != null) {
                targetSceneIndex = sceneIndex;
                // Validate scene index exists by checking if any track has clips at this index
                if (!isSceneIndexValid(targetSceneIndex)) {
                    throw new BitwigApiException(
                        ErrorCode.SCENE_NOT_FOUND,
                        "get_clips_in_scene",
                        "Scene not found: " + targetSceneIndex,
                        Map.of("scene_index", targetSceneIndex)
                    );
                }
                logger.info("Using scene index " + targetSceneIndex);
            } else {
                throw new BitwigApiException(
                    ErrorCode.INVALID_PARAMETER,
                    "get_clips_in_scene",
                    "At least one of scene_index or scene_name must be provided",
                    Map.of()
                );
            }

            // Get clips from all tracks at the target scene index
            List<Map<String, Object>> clipSlots = new ArrayList<>();
            int trackCount = bitwigApiFacade.getTrackBankSize();

            for (int trackIndex = 0; trackIndex < trackCount; trackIndex++) {
                try {
                    String trackName = bitwigApiFacade.getTrackNameByIndex(trackIndex);
                    if (trackName == null || trackName.trim().isEmpty()) {
                        continue; // Skip tracks that don't exist
                    }

                    Map<String, Object> clipSlot = bitwigApiFacade.getClipSlotDetails(trackIndex, trackName, targetSceneIndex);
                    if (clipSlot != null) {
                        clipSlots.add(clipSlot);
                    } else {
                        // Create default empty slot entry for tracks that don't have this scene index
                        Map<String, Object> emptySlot = new LinkedHashMap<>();
                        emptySlot.put("track_index", trackIndex);
                        emptySlot.put("track_name", trackName);
                        emptySlot.put("has_content", false);
                        emptySlot.put("clip_name", null);
                        emptySlot.put("clip_color", null);
                        emptySlot.put("is_playing", false);
                        emptySlot.put("is_recording", false);
                        emptySlot.put("is_playback_queued", false);
                        emptySlot.put("is_recording_queued", false);
                        emptySlot.put("is_stop_queued", false);
                        clipSlots.add(emptySlot);
                    }
                } catch (Exception e) {
                    logger.warn("Error getting clip info for track " + trackIndex + " at scene " + targetSceneIndex + ": " + e.getMessage());
                    // Continue with next track
                }
            }

            logger.info("Retrieved " + clipSlots.size() + " clip slots for scene " + targetSceneIndex);
            return clipSlots;

        } catch (BitwigApiException e) {
            logger.error("Failed to get clips in scene: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error getting clips in scene: " + e.getMessage(), e);
            throw new BitwigApiException(
                ErrorCode.INTERNAL_ERROR,
                "get_clips_in_scene",
                "Internal error occurred while getting clips in scene: " + e.getMessage(),
                Map.of()
            );
        }
    }

    /**
     * Finds a scene by name using case-insensitive comparison.
     * Returns the first matching scene index, or -1 if not found.
     */
    private int findSceneByNameCaseInsensitive(String sceneName) {
        int sceneCount = bitwigApiFacade.getSceneCount();
        for (int i = 0; i < sceneCount; i++) {
            String currentSceneName = bitwigApiFacade.getSceneName(i);
            if (currentSceneName != null && currentSceneName.trim().equalsIgnoreCase(sceneName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Validates that a scene index is valid by checking if the scene bank contains it.
     */
    private boolean isSceneIndexValid(int sceneIndex) {
        if (sceneIndex < 0) {
            return false;
        }
        int sceneCount = bitwigApiFacade.getSceneCount();
        return sceneIndex < sceneCount;
    }

    /**
     * Launches a clip at the specified track and clip index.
     *
     * @param trackName The name of the track containing the clip (case-sensitive)
     * @param clipIndex The zero-based index of the clip slot to launch
     * @return ClipLaunchResult indicating success/failure and any error details
     */
    public ClipLaunchResult launchClip(String trackName, int clipIndex) {
        try {
            logger.info("Attempting to launch clip - Track: '" + trackName + "', Index: " + clipIndex);

            // Find the track by name (case-sensitive) and launch the clip
            try {
                bitwigApiFacade.findTrackIndexByName(trackName); // This will throw BitwigApiException if track not found

                // Check if clip index is within bounds
                int trackClipCount = bitwigApiFacade.getTrackClipCount(trackName);
                if (clipIndex < 0 || clipIndex >= trackClipCount) {
                    logger.warn("Clip index " + clipIndex + " out of bounds for track '" + trackName + "' (valid range: 0-" + (trackClipCount - 1) + ")");
                    return ClipLaunchResult.error("CLIP_INDEX_OUT_OF_BOUNDS",
                        "Clip index " + clipIndex + " is out of bounds for track '" + trackName + "'");
                }

                // Launch the clip
                bitwigApiFacade.launchClip(trackName, clipIndex);
                logger.info("Successfully launched clip at " + trackName + "[" + clipIndex + "]");
                return ClipLaunchResult.success("Clip at " + trackName + "[" + clipIndex + "] launched.");

            } catch (BitwigApiException e) {
                if (e.getErrorCode() == ErrorCode.TRACK_NOT_FOUND) {
                    logger.warn("Track not found: '" + trackName + "'");
                    return ClipLaunchResult.error("TRACK_NOT_FOUND", "Track '" + trackName + "' not found");
                } else {
                    logger.error("Failed to launch clip at " + trackName + "[" + clipIndex + "]: " + e.getMessage());
                    return ClipLaunchResult.error("BITWIG_ERROR", "Failed to launch clip: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("Unexpected error launching clip: " + e.getMessage(), e);
            return ClipLaunchResult.error("BITWIG_ERROR", "Internal error occurred while launching clip: " + e.getMessage());
        }
    }

    /**
     * Result class for clip launch operations.
     */
    public static class ClipLaunchResult {
        private final boolean success;
        private final String errorCode;
        private final String message;

        private ClipLaunchResult(boolean success, String errorCode, String message) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
        }

        /**
         * Creates a successful result.
         *
         * @param message Success message
         * @return Successful ClipLaunchResult
         */
        public static ClipLaunchResult success(String message) {
            return new ClipLaunchResult(true, null, message);
        }

        /**
         * Creates an error result.
         *
         * @param errorCode Error code for the failure
         * @param message Error message
         * @return Error ClipLaunchResult
         */
        public static ClipLaunchResult error(String errorCode, String message) {
            return new ClipLaunchResult(false, errorCode, message);
        }

        /**
         * Returns whether the operation was successful.
         *
         * @return true if successful, false otherwise
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * Returns the error code if the operation failed.
         *
         * @return error code or null if successful
         */
        public String getErrorCode() {
            return errorCode;
        }

        /**
         * Returns the result message.
         *
         * @return success or error message
         */
        public String getMessage() {
            return message;
        }
    }

    /**
     * Result class for scene launch operations.
     */
    public static class SceneLaunchResult {
        private final boolean success;
        private final String errorCode;
        private final String message;

        private SceneLaunchResult(boolean success, String errorCode, String message) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
        }

        public static SceneLaunchResult success(String message) {
            return new SceneLaunchResult(true, null, message);
        }

        public static SceneLaunchResult error(String errorCode, String message) {
            return new SceneLaunchResult(false, errorCode, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }
}
