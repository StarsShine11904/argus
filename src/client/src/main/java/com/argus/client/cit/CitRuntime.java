package com.argus.client.cit;

import com.argus.Constants;
import com.argus.cit.CitRule;
import com.argus.client.platform.ClientEnvironment;
import com.argus.config.ArgusConfigHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic holder and front-door resolver for Fabric CIT.
 *
 * <p>Purpose: keeps item-render hooks tiny and safe. The hook performs only
 * cheap global checks before delegating to the resolver with a snapshot-local
 * cache.
 *
 * <p>Threading: snapshot replacement is atomic; resolver cache is owned by
 * the published snapshot wrapper and contains no weak references.
 */
public final class CitRuntime {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/cit-runtime");
    private static final AtomicReference<CitResolver> RESOLVER =
            new AtomicReference<>(new CitResolver(CitClientSnapshot.empty()));
    private static volatile boolean warnedCitResewn;

    private CitRuntime() {
    }

    public static void replace(CitClientSnapshot snapshot) {
        RESOLVER.set(new CitResolver(snapshot));
    }

    public static CitClientSnapshot snapshot() {
        return RESOLVER.get().snapshot();
    }

    /**
     * Performance: HOT PATH. Checks config, compatibility, empty stack, and
     * item-prefilter before building any condition context.
     */
    public static CitRule select(ItemStack stack, String hand) {
        if (!ArgusConfigHolder.get().citActive()) {
            return null;
        }
        if (ClientEnvironment.isModLoaded("citresewn")) {
            if (!warnedCitResewn) {
                warnedCitResewn = true;
                LOGGER.warn("{}", Component.translatable(
                        "argus.warn.cit.runtime.citresewn_conflict",
                        Constants.MOD_NAME
                ).getString());
            }
            return null;
        }
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return RESOLVER.get().select(stack, hand);
    }

    /**
     * 取得目前 CIT 執行階段狀態的可翻譯描述（適合除錯介面或診斷日誌使用）。
     */
    public static Component statusComponent() {
        if (!ArgusConfigHolder.get().citActive()) {
            return Component.translatable("argus.status.cit.disabled_config");
        }
        if (ClientEnvironment.isModLoaded("citresewn")) {
            return Component.translatable("argus.status.cit.disabled_conflict");
        }
        return Component.translatable("argus.status.cit.active", snapshot().size());
    }
}
