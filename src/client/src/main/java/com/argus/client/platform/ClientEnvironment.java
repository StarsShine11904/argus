package com.argus.client.platform;

import com.argus.Constants;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Access point for loader facts used by shared client classes.
 *
 * <p>The holder resolves one {@link ClientRuntimePlatform} through
 * {@link ServiceLoader}. A fallback keeps tests and unusual development
 * launches non-crashing, but real loader modules should always register a
 * provider.
 *
 * <p>Threading: lock-free reads after class initialization.
 *
 * <p>Performance: lookup is cached in one atomic reference; hot-path calls are
 * a single read plus the provider method.
 */
public final class ClientEnvironment {

    private static final ClientRuntimePlatform FALLBACK =
            new ClientRuntimePlatform() {
                @Override
                public Path configDir() {
                    return Path.of("config");
                }

                @Override
                public boolean isModLoaded(String modId) {
                    return false;
                }

                @Override
                public String modVersion() {
                    return "dev";
                }
            };

    private static final AtomicReference<ClientRuntimePlatform> PLATFORM =
            new AtomicReference<>(loadFirst());

    private ClientEnvironment() {
    }

    /**
     * Returns the active loader bridge.
     */
    public static ClientRuntimePlatform get() {
        ClientRuntimePlatform platform = PLATFORM.get();
        return platform == null ? FALLBACK : platform;
    }

    /**
     * Convenience mod-presence check.
     */
    public static boolean isModLoaded(String modId) {
        return modId != null && get().isModLoaded(modId);
    }

    /**
     * Returns the display version used by external config UIs.
     */
    public static String modVersion() {
        String version = get().modVersion();
        return version == null || version.isBlank()
                ? Constants.MOD_NAME + " " + Component.translatable("argus.platform.version.dev").getString()
                : version;
    }

    private static ClientRuntimePlatform loadFirst() {
        try {
            for (ClientRuntimePlatform candidate
                    : ServiceLoader.load(ClientRuntimePlatform.class)) {
                return candidate;
            }
        } catch (ServiceConfigurationError err) {
            throw new IllegalStateException(
                    Component.translatable("argus.error.platform_load_failed").getString(), err);
        }
        return FALLBACK;
    }
}
