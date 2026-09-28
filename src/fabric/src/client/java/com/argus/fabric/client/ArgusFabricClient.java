package com.argus.fabric.client;

import com.argus.Constants;
import com.argus.client.command.ArgusClientCommands;
import com.argus.client.config.ArgusClientConfigLoader;
import com.argus.platform.Platforms;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Client-only entrypoint. Renderer hooks and any client-resource reload
 * listeners will be registered from here in later phases.
 */
public final class ArgusFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Constants.MOD_ID + "/client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] {}",
                Constants.MOD_NAME,
                Component.translatable("argus.log.client.initialized", Platforms.get().id()).getString());

        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            LOGGER.info("[{}] {}",
                    Constants.MOD_NAME,
                    Component.translatable("argus.log.client.sodium_detected").getString());
        }

        ArgusBuiltinResourcePacks.register();

        // Phase 5: load the config file (config/argus.properties) into the shared holder.
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            ArgusClientConfigLoader.loadAndInstall(configDir);
        } catch (RuntimeException e) {
            LOGGER.warn("[{}] {}",
                    Constants.MOD_NAME,
                    Component.translatable("argus.log.client.config_load_failed", e.getMessage()).getString());
        }

        FabricClientReloadBridge.register();
        FabricClientTickBridge.register();
        ArgusClientCommands.register();
    }
}
