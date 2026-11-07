package net.minecraftforge.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Futo 兼容层的统一日志器。
 */
public final class FutoLog {
    public static final Logger LOGGER = LogManager.getLogger("touhou_little_maid");

    private FutoLog() {
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
