package org.dsa.iot.dslink.serializer;

import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.util.FileUtils;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles automatic serialization and deserialization.
 *
 * @author Samuel Grenier
 */
public class SerializationManager {

    private static final Logger LOGGER;

    private final File file;
    private final File backup;

    private final Deserializer deserializer;
    private final Serializer serializer;
    private ScheduledFuture<?> future;

    private final AtomicBoolean changed = new AtomicBoolean(false);

    /**
     * Handles serialization based on the file path.
     *
     * @param file Path that holds the data
     * @param manager Manager to deserialize/serialize
     */
    public SerializationManager(File file, NodeManager manager) {
        this.file = file;
        this.backup = new File(file.getPath() + ".bak");
        this.deserializer = new Deserializer(manager);
        this.serializer = new Serializer(manager);
    }

    public void markChanged() {
        changed.set(true);
    }

    public void markChangedOverride(boolean bool) {
        changed.set(bool);
    }

    public synchronized void start() {
        stop();
        ScheduledThreadPoolExecutor daemon = Objects.getDaemonThreadPool();
        future = daemon.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                boolean c = changed.getAndSet(false);
                if (c) {
                    serialize();
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /**
     * Serializes the data from the node manager into the file based on the
     * path. Manually calling this is redundant as a timer will automatically
     * handle serialization.
     */
    public void serialize() {
        JsonObject json = serializer.serialize();
        try {
            if (file.exists()) {
                if (backup.exists() && !backup.delete()) {
                    LOGGER.error("Failed to remove backup data");
                }

                if (!file.renameTo(backup)) {
                    LOGGER.error("Failed to create backup data");
                }
                LOGGER.debug("Copying serialized data to a backup");
            }
            byte[] bytes = json.encodePrettily();
            FileUtils.write(file, bytes);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Wrote serialized data: {}", json);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save serialized data", e);
        }
    }

    /**
     * Deserializes the data into the node manager based on the path.
     *
     * @throws Exception An error has occurred deserializing the nodes.
     */
    public void deserialize() throws Exception {
        deserialize(true);
    }

    private void deserialize(boolean cont) throws Exception {
        byte[] bytes = null;
        if (file.exists()) {
            try {
                bytes = FileUtils.readAllBytes(file);
            } catch (Exception ignored) {
            }
        } else if (backup.exists()) {
            bytes = FileUtils.readAllBytes(backup);
            FileUtils.write(file, bytes);
            LOGGER.warn("Restored backup data");
        }

        boolean tryAgain = false;
        if (bytes != null) {
            try {
                handle(bytes);
            } catch (Exception e) {
                if (!cont) {
                    throw e;
                }
                tryAgain = true;
            }
        } else {
            tryAgain = true;
        }

        if (tryAgain && cont) {
            // Force reading from the backup
            if (!file.delete()) {
                LOGGER.debug("Failed to delete original file");
            }
            deserialize(false);
        }
    }

    private void handle(byte[] bytes) throws Exception {
        String in = new String(bytes, "UTF-8");
        JsonObject obj = new JsonObject(in);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Read serialized data: " + obj);
        }
        deserializer.deserialize(obj);
    }

    static {
        LOGGER = LoggerFactory.getLogger(SerializationManager.class);
    }
}
