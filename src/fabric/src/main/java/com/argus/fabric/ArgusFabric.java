package com.argus.fabric;

import com.argus.Constants;
import com.argus.platform.Platforms;
import net.fabricmc.api.ModInitializer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common-side (logical + physical) entrypoint. Runs on every environment
 * the mod is loaded into. Phase 0 only logs a startup line; feature work
 * will hang off the registered {@link com.argus.platform.Platform}.
 */
public final class ArgusFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] {}",
                Constants.MOD_NAME,
                Component.translatable("argus.log.common.initialized").getString());

        // Phase 4.6: the mod is client-only functional; the
        // dedicated server has no Platform service registered.
        // We log the absence and continue rather than crash.
        Platforms.tryGet().ifPresentOrElse(
                p -> LOGGER.info("[{}] {}",
                        Constants.MOD_NAME,
                        Component.translatable("argus.log.common.active_loader", p.id()).getString()),
                () -> LOGGER.info("[{}] {}",
                        Constants.MOD_NAME,
                        Component.translatable("argus.log.common.no_platform").getString()));
    }
}
