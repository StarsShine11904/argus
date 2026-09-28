package com.argus.client.mixin;

import com.argus.Constants;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Applies client mixin compatibility policy before Mixin transforms classes.
 *
 * <p>Sodium is Argus's required terrain-renderer foundation. The active
 * client mixin list contains only the Sodium terrain hook and atlas/resource
 * hooks. Sodium is a hard dependency for supported clients, so this plugin
 * must not silently disable Sodium hooks when a loader exposes Sodium classes
 * later than Mixin's plugin-load phase.
 *
 * <h2>Threading</h2>
 *
 * <p>Mixin calls this during client startup on the launch thread.
 *
 * <h2>Performance</h2>
 *
 * <p>Startup-only policy lookup. No render-path cost.
 */
public final class ArgusClientMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/mixin-plugin");

    private static final String SODIUM_BLOCK_RENDERER =
            "net.caffeinemc.mods.sodium.client.render.chunk.compile"
                    + ".pipeline.BlockRenderer";

    private static final Set<String> SODIUM_TERRAIN_MIXINS = Set.of(
            "com.argus.client.mixin.SodiumBlockRendererCtmMixin",
            "com.argus.client.mixin.SodiumLevelColorCacheCustomColorsMixin");

    private static boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumLoaded = isClassPresent(SODIUM_BLOCK_RENDERER);
        if (sodiumLoaded) {
            logSafe("argus.info.mixin.sodium_present",
                    "[%s] Sodium classes visible during mixin plugin load",
                    Constants.MOD_NAME);
        } else {
            logSafe("argus.info.mixin.sodium_missing_early",
                    "[%s] Sodium classes are not visible during mixin plugin load; "
                            + "Sodium mixins remain enabled because Sodium is a required dependency",
                    Constants.MOD_NAME);
        }
    }

    /**
     * 供遊戲啟動完成後（除錯 HUD、F3、錯誤診斷畫面）取得本地化狀態。
     */
    public static Component statusComponent() {
        if (sodiumLoaded) {
            return Component.translatable("argus.status.mixin.sodium_present");
        }
        return Component.translatable("argus.status.mixin.sodium_deferred");
    }

    public static boolean isSodiumLoaded() {
        return sodiumLoaded;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName,
                                    String mixinClassName) {
        if (SODIUM_TERRAIN_MIXINS.contains(mixinClassName)) {
            return true;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets,
                              Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName,
                         ClassNode targetClass,
                         String mixinClassName,
                         IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName,
                          ClassNode targetClass,
                          String mixinClassName,
                          IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return ArgusClientMixinPlugin.class.getClassLoader()
                .getResource(resourceName) != null;
    }

    /**
     * 安全日誌防護：若 Minecraft 早期啟動階段未載入 Component/語系，退回字串格式化輸出。
     */
    private static void logSafe(String key, String fallbackFormat, Object... args) {
        try {
            LOGGER.info("{}", Component.translatable(key, args).getString());
        } catch (Throwable ignored) {
            LOGGER.info(String.format(fallbackFormat, args));
        }
    }
}
