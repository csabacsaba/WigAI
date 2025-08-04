package io.github.fabb.wigai.server;

import com.bitwig.extension.controller.api.ControllerHost;
import io.github.fabb.wigai.common.Logger;
import io.github.fabb.wigai.config.ConfigManager;
import io.github.fabb.wigai.WigAIExtensionDefinition;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Manages the Jetty server lifecycle and configuration.
 * Handles starting, stopping, and restarting the Jetty server with proper error handling.
 */
public class JettyServerManager {
    private final Logger logger;
    private final ConfigManager configManager;
    private final WigAIExtensionDefinition extensionDefinition;
    private final ControllerHost host;

    // Jetty server management
    private Server jettyServer;
    private ServletContextHandler contextHandler;
    private String currentEndpointPath;

    /**
     * Creates a new JettyServerManager instance.
     *
     * @param logger The logger instance
     * @param configManager The configuration manager
     * @param extensionDefinition The extension definition for version info
     * @param host The Bitwig ControllerHost for popup notifications
     */
    public JettyServerManager(Logger logger, ConfigManager configManager, WigAIExtensionDefinition extensionDefinition, ControllerHost host) {
        this.logger = logger;
        this.configManager = configManager;
        this.extensionDefinition = extensionDefinition;
        this.host = host;
    }

    /**
     * Starts the Jetty server with the current configuration and registers the provided servlet.
     *
     * @param mcpServlet The MCP servlet to register, or null to start without servlet
     * @param endpointPath The endpoint path for the servlet, or null if no servlet provided
     * @throws Exception if the server fails to start
     */
    public void startServer(ServletHolder mcpServlet, String endpointPath) throws Exception {
        if (jettyServer != null && jettyServer.isRunning()) {
            logger.info("WigAI Server is already running");
            return;
        }

        // Create and configure Jetty server
        jettyServer = new Server();
        ServerConnector connector = new ServerConnector(jettyServer);
        connector.setHost(configManager.getMcpHost());
        connector.setPort(configManager.getMcpPort());
        jettyServer.addConnector(connector);

        // Create servlet context handler
        contextHandler = new ServletContextHandler();
        contextHandler.setContextPath("/");
        jettyServer.setHandler(contextHandler);

        // Register servlet if provided
        if (mcpServlet != null && endpointPath != null) {
            contextHandler.addServlet(mcpServlet, endpointPath);
            this.currentEndpointPath = endpointPath;
        }

        // Start the Jetty server
        jettyServer.start();

        notifyServerStarted();
    }

    /**
     * Stops the Jetty server and all servlets.
     */
    public void stopServer() {
        if (jettyServer == null || !jettyServer.isRunning()) {
            logger.info("WigAI Server is not running");
            return;
        }

        try {
            logger.info("Stopping Jetty server");
            jettyServer.stop();
            notifyServerStopped();
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            String fullStackTrace = stringWriter.toString();

            logger.error("Error stopping WigAI Server\n" + fullStackTrace);
        }
    }

    /**
     * Gracefully restarts the server with new configuration and registers the provided servlet.
     *
     * @param mcpServlet The MCP servlet to register, or null to restart without servlet
     * @param endpointPath The endpoint path for the servlet, or null if no servlet provided
     * @throws Exception if the server fails to restart
     */
    public void restartServer(ServletHolder mcpServlet, String endpointPath) throws Exception {
        logger.info("WigAI Extension: Beginning graceful server restart");

        // Stop the current server if running
        if (jettyServer != null && jettyServer.isRunning()) {
            logger.info("WigAI Extension: Stopping current server for restart");
            stopServer();
        }

        // Small delay to ensure clean shutdown
        Thread.sleep(500);

        // Start the server with new configuration
        logger.info("WigAI Extension: Starting server with updated configuration");
        startServer(mcpServlet, endpointPath);

        logger.info("WigAI Extension: Server restart completed successfully");
    }

    /**
     * Checks if the Jetty server is currently running.
     *
     * @return true if the server is running, false otherwise
     */
    public boolean isRunning() {
        return jettyServer != null && jettyServer.isRunning();
    }

    /**
     * Gets the current ServletContextHandler.
     *
     * @return The current ServletContextHandler, or null if server is not running
     */
    public ServletContextHandler getContextHandler() {
        return contextHandler;
    }

    /**
     * Notifies that the server started successfully.
     */
    private void notifyServerStarted() {
        String endpointPath = currentEndpointPath != null ? currentEndpointPath : "";
        String connectionUrl = String.format("http://%s:%d%s",
            configManager.getMcpHost(), configManager.getMcpPort(), endpointPath);
        String message = String.format("WigAI MCP Server v%s started. Connect AI agents to: %s",
            extensionDefinition.getVersion(), connectionUrl);
        logger.info(message);

        try {
            host.showPopupNotification(message);
        } catch (Exception e) {
            logger.error("WigAI Extension: Error showing startup notification", e);
        }
    }

    /**
     * Notifies that the server stopped.
     */
    private void notifyServerStopped() {
        String message = String.format("WigAI MCP Server v%s stopped", extensionDefinition.getVersion());
        logger.info(message);

        try {
            host.showPopupNotification(message);
        } catch (Exception e) {
            logger.error("WigAI Extension: Error showing stop notification", e);
        }
    }
}
