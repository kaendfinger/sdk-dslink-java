package org.dsa.iot.dslink;

import org.dsa.iot.dslink.config.Configuration;
import org.dsa.iot.dslink.util.Objects;

/**
 * Top level API for handling the configuration of nodes and responses to
 * requests. Note that {@link #onRequesterInitialized} and
 * {@link #onResponderInitialized} can be each called for the same link ID.
 * This allows for the node managers to be completely isolated between a
 * a requester and responder.
 *
 * @author Samuel Grenier
 */
@SuppressWarnings("UnusedParameters")
public abstract class DSLinkHandler {

    private Configuration configuration;

    /**
     * The default setting from here is what is used in the
     * {@link Configuration} instance during an auto configuration. The result
     * must be strictly static.
     *
     * @return Whether this DSLink is a responder or not.
     */
    public boolean isResponder() {
        return false;
    }

    /**
     * The default setting from here is what is used in the
     * {@link Configuration} instance during an auto configuration. The result
     * must be strictly static.
     *
     * @return Whether this DSLink is a requester or not.
     */
    public boolean isRequester() {
        return false;
    }

    /**
     * Stops the entire DSLink. The DSLink is expected to close all resources
     * and stop all threads. This method is called when {@link DSLinkProvider}
     * is stopped. Ensure to call {@code super} after overriding.
     */
    public void stop() {
        Objects.getVertx().stop();
        Objects.getDaemonThreadPool().shutdownNow();
        Objects.getThreadPool().shutdownNow();
    }

    /**
     * Sets the configuration of the handler.
     *
     * @param configuration Configuration of the link
     */
    public void setConfig(Configuration configuration) {
        if (configuration == null) {
            throw new NullPointerException("configuration");
        }
        this.configuration = configuration;
    }

    /**
     * @return Configuration of the DSLink
     */
    public Configuration getConfig() {
        return configuration;
    }

    /**
     * Pre initializes the handler. If this link is a responder any actions
     * must be populated here.
     */
    public void preInit() {
    }

    /**
     * This method is asynchronously called. The link is not yet connected
     * to the server.
     *
     * @param link The link that needs to be initialized.
     */
    public void onRequesterInitialized(DSLink link) {
    }

    /**
     * This method is asynchronously called. The link is connected to the
     * server at this stage.
     *
     * @param link The link that has completed a connection.
     */
    public void onRequesterConnected(DSLink link) {
    }

    /**
     * This method is asynchronously called. The link is not yet connected
     * to the server.
     *
     * @param link The link that needs to be initialized.
     */
    public void onResponderInitialized(DSLink link) {
    }

    /**
     * This method is asynchronously called. The link is connected to the
     * server at this stage.
     *
     * @param link The link that has completed a connection.
     */
    public void onResponderConnected(DSLink link) {
    }
}
