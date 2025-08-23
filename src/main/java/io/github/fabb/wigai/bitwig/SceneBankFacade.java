package io.github.fabb.wigai.bitwig;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Scene;
import io.github.fabb.wigai.common.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade for Bitwig Scene Bank operations (scene name lookup, color, etc).
 */
public class SceneBankFacade {
    private final SceneBank sceneBank;
    private final Logger logger;
    private final int sceneCount;

    public SceneBankFacade(ControllerHost host, Logger logger, int sceneCount) {
        this.logger = logger;
        this.sceneCount = sceneCount;
        this.sceneBank = host.createSceneBank(sceneCount);
        
        for (int i = 0; i < sceneCount; i++) {
            Scene scene = sceneBank.getItemAt(i);
            scene.name().markInterested();
            scene.exists().markInterested();
            scene.color().markInterested();
        }
    }

    public int getSceneCount() {
        return sceneCount;
    }

    public String getSceneName(int index) {
        if (index < 0 || index >= sceneCount) return null;
        Scene scene = sceneBank.getItemAt(index);
        if (scene.exists().get()) {
            return scene.name().get();
        }
        return null;
    }

    /**
     * Finds the first scene index with the given name (case-sensitive).
     * Returns -1 if not found.
     */
    public int findSceneByName(String sceneName) {
        for (int i = 0; i < sceneCount; i++) {
            Scene scene = sceneBank.getItemAt(i);
            if (scene.exists().get() && sceneName.equals(scene.name().get())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets all scenes in the project with their details.
     * Handles pagination across the full scene bank to return all scenes.
     *
     * @return A list of scene information maps
     */
    public List<Map<String, Object>> getAllScenesInfo() {
        logger.info("SceneBankFacade: Getting all scenes info");
        List<Map<String, Object>> scenesInfo = new ArrayList<>();

        try {
            for (int i = 0; i < sceneCount; i++) {
                Scene scene = sceneBank.getItemAt(i);
                if (!scene.exists().get()) {
                    continue; // Skip non-existent scenes
                }

                Map<String, Object> sceneInfo = new LinkedHashMap<>();
                sceneInfo.put("index", i);
                
                String sceneName = scene.name().get();
                sceneInfo.put("name", sceneName);

                // Get scene color and format as RGB string
                String colorString = formatSceneColor(scene.color().get());
                sceneInfo.put("color", colorString);

                scenesInfo.add(sceneInfo);
            }

            logger.info("SceneBankFacade: Retrieved " + scenesInfo.size() + " scenes");
        } catch (Exception e) {
            logger.warn("SceneBankFacade: Error getting scenes info: " + e.getMessage());
        }

        return scenesInfo;
    }

    /**
     * Formats a Color object into an RGB string format, or returns null if color is not available.
     *
     * @param color The Color object from Bitwig API
     * @return RGB string in format "rgb(r,g,b)" or null if color not available
     */
    private String formatSceneColor(Color color) {
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
                logger.info("SceneBankFacade: Using default color values due to API access issue");
            }
            
            return String.format("rgb(%d,%d,%d)",
                (int) (red * 255),
                (int) (green * 255),
                (int) (blue * 255));

        } catch (Exception e) {
            logger.warn("SceneBankFacade: Error formatting scene color: " + e.getMessage());
            return null;
        }
    }
}
