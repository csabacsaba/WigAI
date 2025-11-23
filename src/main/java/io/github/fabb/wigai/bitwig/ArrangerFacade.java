package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CueMarker;
import com.bitwig.extension.controller.api.CueMarkerBank;
import com.bitwig.extension.controller.api.Transport;
import io.github.fabb.wigai.common.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade for Bitwig Arranger operations (cue markers, navigation).
 */
public class ArrangerFacade {
    private static final int MAX_CUE_MARKERS = 128; // Support up to 128 cue markers
    
    private final Arranger arranger;
    private final CueMarkerBank cueMarkerBank;
    private final Transport transport;
    private final Logger logger;

    public ArrangerFacade(ControllerHost host, Logger logger) {
        this.logger = logger;
        this.arranger = host.createArranger();
        this.cueMarkerBank = arranger.createCueMarkerBank(MAX_CUE_MARKERS);
        this.transport = host.createTransport();

        // Mark interest in all cue markers to enable value access
        for (int i = 0; i < MAX_CUE_MARKERS; i++) {
            CueMarker marker = cueMarkerBank.getItemAt(i);
            marker.exists().markInterested();
            marker.name().markInterested();
            marker.getColor().markInterested();
            marker.position().markInterested();
        }

        // Mark interest in arranger properties
        arranger.areCueMarkersVisible().markInterested();
        arranger.isPlaybackFollowEnabled().markInterested();
    }

    /**
     * Gets all cue markers in the project with their details.
     *
     * @return A list of cue marker information maps
     */
    public List<Map<String, Object>> getAllCueMarkersInfo() {
        logger.info("ArrangerFacade: Getting all cue markers info");
        List<Map<String, Object>> markersInfo = new ArrayList<>();

        try {
            for (int i = 0; i < MAX_CUE_MARKERS; i++) {
                CueMarker marker = cueMarkerBank.getItemAt(i);
                if (!marker.exists().get()) {
                    continue; // Skip non-existent markers
                }

                Map<String, Object> markerInfo = new LinkedHashMap<>();
                markerInfo.put("index", i);

                String markerName = marker.name().get();
                markerInfo.put("name", markerName);

                // Get marker position in beats
                double positionBeats = marker.position().get();
                markerInfo.put("position_beats", positionBeats);

                // Convert to bars (assuming 4 beats per bar)
                int bars = (int) (positionBeats / 4);
                markerInfo.put("position_bars", bars);

                // Format position as time string (bars:beats)
                int beats = (int) (positionBeats % 4);
                String timeString = String.format("%d:%d", bars, beats + 1);
                markerInfo.put("position_time", timeString);

                // Get marker color and format as RGB string
                String colorString = formatMarkerColor(marker.getColor().get());
                markerInfo.put("color", colorString);

                markersInfo.add(markerInfo);
            }

            logger.info("ArrangerFacade: Retrieved " + markersInfo.size() + " cue markers");
        } catch (Exception e) {
            logger.warn("ArrangerFacade: Error getting cue markers info: " + e.getMessage());
        }

        return markersInfo;
    }

    /**
     * Finds the first cue marker index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findCueMarkerByName(String markerName) {
        for (int i = 0; i < MAX_CUE_MARKERS; i++) {
            CueMarker marker = cueMarkerBank.getItemAt(i);
            if (marker.exists().get() && markerName.equals(marker.name().get())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Launches (jumps to) a cue marker by index.
     *
     * @param index The marker index to launch
     * @return true if successful, false otherwise
     */
    public boolean launchCueMarker(int index) {
        if (index < 0 || index >= MAX_CUE_MARKERS) {
            logger.warn("ArrangerFacade: Invalid marker index: " + index);
            return false;
        }

        CueMarker marker = cueMarkerBank.getItemAt(index);
        if (!marker.exists().get()) {
            logger.warn("ArrangerFacade: Marker at index " + index + " does not exist");
            return false;
        }

        try {
            marker.launch(false); // false = don't launch quantized
            logger.info("ArrangerFacade: Launched marker " + index + " (" + marker.name().get() + ")");
            return true;
        } catch (Exception e) {
            logger.warn("ArrangerFacade: Error launching marker: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets whether cue markers are currently visible in the arranger.
     *
     * @return true if markers are visible
     */
    public boolean areCueMarkersVisible() {
        return arranger.areCueMarkersVisible().get();
    }

    /**
     * Sets the visibility of cue markers in the arranger.
     *
     * @param visible true to show markers, false to hide
     */
    public void setCueMarkersVisible(boolean visible) {
        arranger.areCueMarkersVisible().set(visible);
        logger.info("ArrangerFacade: Set cue markers visible: " + visible);
    }

    /**
     * Formats a Color object into an RGB string format, or returns null if color is not available.
     *
     * @param color The Color object from Bitwig API
     * @return RGB string in format "rgb(r,g,b)" or null if color not available
     */
    private String formatMarkerColor(Color color) {
        try {
            if (color == null) {
                return null;
            }

            // Get color values with fallback to default gray if API calls fail
            double red = 0.5;   // Default gray
            double green = 0.5;
            double blue = 0.5;

            try {
                red = color.getRed();
                green = color.getGreen();
                blue = color.getBlue();
            } catch (Exception e) {
                // Use defaults if color API calls fail
                logger.info("ArrangerFacade: Using default color values due to API access issue");
            }

            return String.format("rgb(%d,%d,%d)",
                (int) (red * 255),
                (int) (green * 255),
                (int) (blue * 255));

        } catch (Exception e) {
            logger.warn("ArrangerFacade: Error formatting marker color: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the maximum number of cue markers supported.
     *
     * @return The maximum number of cue markers
     */
    public int getMaxCueMarkers() {
        return MAX_CUE_MARKERS;
    }

    /**
     * Creates a new cue marker at the current playback position.
     *
     * @return true if successful, false otherwise
     */
    public boolean addCueMarkerAtPlaybackPosition() {
        try {
            transport.addCueMarkerAtPlaybackPosition();
            logger.info("ArrangerFacade: Created cue marker at playback position");
            return true;
        } catch (Exception e) {
            logger.warn("ArrangerFacade: Error creating cue marker: " + e.getMessage());
            return false;
        }
    }
}
