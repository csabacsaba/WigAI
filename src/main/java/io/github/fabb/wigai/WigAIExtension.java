package io.github.fabb.wigai;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import io.github.fabb.wigai.config.PreferencesBackedConfigManager;
import io.github.fabb.wigai.config.ConfigChangeObserver;
import io.github.fabb.wigai.mcp.McpServerManager;
import io.github.fabb.wigai.server.JettyServerManager;

import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Main extension class for the WigAI extension.
 * Handles lifecycle events (init, exit) and owns the primary components.
 * Manages the Jetty server and servlet context for multiple servlets.
 */
public class WigAIExtension extends ControllerExtension implements ConfigChangeObserver {
    private static final String MCP_ENDPOINT_PATH = "/mcp";

    private Logger logger;
    private ConfigManager configManager;
    private McpServerManager mcpServerManager;
    private JettyServerManager jettyServerManager;

    /**
     * Creates a new WigAIExtension instance.
     *
     * @param definition The extension definition
     * @param host       The Bitwig ControllerHost
     */
    protected WigAIExtension(final WigAIExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    /**
     * Initialize the extension.
     * This is called when the extension is enabled in Bitwig Studio.
     */
    @Override
    public void init() {
        final ControllerHost host = getHost();

        // Initialize the logger
        logger = new Logger(host);

        // Initialize the config manager with Bitwig preferences integration
        configManager = new PreferencesBackedConfigManager(logger, host);

        // Initialize the Jetty server manager
        jettyServerManager = new JettyServerManager(logger, configManager, (WigAIExtensionDefinition)getExtensionDefinition(), host);

        // Initialize and start the MCP server
        mcpServerManager = new McpServerManager(logger, configManager, (WigAIExtensionDefinition)getExtensionDefinition(), host);

        // Register this extension as configuration change observers
        configManager.addObserver(this);

        // Start the Jetty server and MCP server
        startServer();

        // Run API introspection
        logger.info("DEBUG: About to run API introspection...");
        host.println("DEBUG: About to run API introspection...");
        try {
            runApiIntrospection();
            logger.info("DEBUG: API introspection completed successfully");
            host.println("DEBUG: API introspection completed successfully");
        } catch (Exception e) {
            logger.error("DEBUG: API introspection FAILED", e);
            host.println("DEBUG: API introspection FAILED with exception: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
        }

        // Log startup message
        logger.info(String.format("WigAI Extension Loaded - Version %s", getExtensionDefinition().getVersion()));
    }

    /**
     * Run API introspection to examine available Bitwig API methods.
     */
    private void runApiIntrospection() {
        final ControllerHost host = getHost();
        
        String separator = "================================================================================";
        host.println(separator);
        host.println("[API Introspector] Starting reflection test...");
        host.println(separator);

        try {
            // 1) Project + first track
            com.bitwig.extension.controller.api.Project project = host.getProject();
            com.bitwig.extension.controller.api.TrackBank tb = host.createTrackBank(1, 0, 8);
            com.bitwig.extension.controller.api.Track track = tb.getItemAt(0);
            track.selectInMixer();

            // 2) Basic API classes introspection
            printApiMethods(host, "ControllerHost", host.getClass());
            printApiMethods(host, "Project", project.getClass());
            printApiMethods(host, "Track", track.getClass());

            // 3) Device chain introspection
            com.bitwig.extension.controller.api.DeviceBank db = track.createDeviceBank(8);
            com.bitwig.extension.controller.api.Device device = db.getItemAt(0);
            printApiMethods(host, "Device", device.getClass());

            // 4) Check for InsertionPoint specifically
            host.println("");
            host.println(separator);
            host.println("[API Introspector] SEARCHING FOR INSERTION POINT METHODS:");
            host.println(separator);
            
            checkForInsertionPointMethods(host, track, "Track");
            checkForInsertionPointMethods(host, device, "Device");
            
            // 5) Get an actual InsertionPoint and introspect it
            try {
                java.lang.reflect.Method endOfChainMethod = track.getClass().getMethod("endOfDeviceChainInsertionPoint");
                Object insertionPoint = endOfChainMethod.invoke(track);
                if (insertionPoint != null) {
                    printApiMethods(host, "InsertionPoint", insertionPoint.getClass());
                } else {
                    host.println("InsertionPoint is null!");
                }
            } catch (Exception e) {
                host.println("Could not get InsertionPoint: " + e.getMessage());
            }
            
            // 6) Introspect Browser API for device scanning
            try {
                com.bitwig.extension.controller.api.PopupBrowser browser = host.createPopupBrowser();
                if (browser != null) {
                    printApiMethods(host, "PopupBrowser", browser.getClass());
                    browser.cancel(); // IMPORTANT: Close the browser to allow future PopupBrowser creation
                } else {
                    host.println("PopupBrowser is null!");
                }
            } catch (Exception e) {
                host.println("Could not get PopupBrowser: " + e.getMessage());
            }

        } catch (Exception e) {
            host.println("[API Introspector] ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }

        host.println("");
        host.println(separator);
        host.println("[API Introspector] Done. Check Bitwig log (Help → Show Log Files).");
        host.println(separator);
    }

    private void printApiMethods(ControllerHost host, String label, Class<?> clazz) {
        host.println("");
        host.println("---- " + label + " (" + clazz.getName() + ") ----");
        String list = java.util.Arrays.stream(clazz.getMethods())
            .map(java.lang.reflect.Method::getName)
            .distinct()
            .sorted(java.util.Comparator.naturalOrder())
            .collect(java.util.stream.Collectors.joining(", "));
        host.println(list);
    }

    private void checkForInsertionPointMethods(ControllerHost host, Object obj, String label) {
        host.println("");
        host.println("Checking " + label + " for insertion point methods:");
        
        java.lang.reflect.Method[] methods = obj.getClass().getMethods();
        boolean found = false;
        
        for (java.lang.reflect.Method method : methods) {
            String name = method.getName().toLowerCase();
            if (name.contains("insertion") || name.contains("insert") || name.contains("device")) {
                host.println("  - " + method.getName() + " : " + 
                             java.util.Arrays.toString(method.getParameterTypes()) + " -> " + 
                             method.getReturnType().getSimpleName());
                found = true;
            }
        }
        
        if (!found) {
            host.println("  (no insertion-related methods found)");
        }
    }

    /**
     * Starts the Jetty server and registers all servlets.
     */
    private void startServer() {
        try {
            // Create MCP servlet from the MCP server manager
            ServletHolder mcpServlet = mcpServerManager.createMcpServlet(MCP_ENDPOINT_PATH);

            // Start Jetty server with the MCP servlet
            jettyServerManager.startServer(mcpServlet, MCP_ENDPOINT_PATH);
        } catch (Exception e) {
            logger.error("Failed to create MCP servlet or start server", e);
        }
    }    /**
     * Stops the Jetty server and all servlets.
     */
    private void stopServer() {
        jettyServerManager.stopServer();
    }

    /**
     * Gracefully restarts the server with new configuration.
     */
    private void restartServer() {
        try {
            // Create MCP servlet from the MCP server manager
            ServletHolder mcpServlet = mcpServerManager.createMcpServlet(MCP_ENDPOINT_PATH);

            // Restart Jetty server with the MCP servlet
            jettyServerManager.restartServer(mcpServlet, MCP_ENDPOINT_PATH);
        } catch (Exception e) {
            logger.error("Failed to create MCP servlet or restart server", e);
        }
    }    /**
     * Called when the MCP server host changes.
     * Triggers a graceful restart of the entire server.
     *
     * @param oldHost The previous host value
     * @param newHost The new host value
     */
    @Override
    public void onHostChanged(String oldHost, String newHost) {
        logger.info("WigAI Extension: Host changed from '" + oldHost + "' to '" + newHost + "', restarting server");
        restartServer();
    }

    /**
     * Called when the MCP server port changes.
     * Triggers a graceful restart of the entire server.
     *
     * @param oldPort The previous port value
     * @param newPort The new port value
     */
    @Override
    public void onPortChanged(int oldPort, int newPort) {
        logger.info("WigAI Extension: Port changed from " + oldPort + " to " + newPort + ", restarting server");
        restartServer();
    }

    /**
     * Clean up when the extension is closed.
     * This is called when the extension is disabled in Bitwig Studio or when Bitwig
     * Studio is closed.
     */
    @Override
    public void exit() {
        if (logger != null) {
            logger.info("WigAI Extension shutting down");
        }

        // Stop the server (which includes MCP server)
        stopServer();
    }

    /**
     * Called when GUI updates should be performed.
     */
    @Override
    public void flush() {
        // No GUI updates needed for now
    }
}
