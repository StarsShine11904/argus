package com.argus.client.customgui;

import com.argus.Constants;
import com.argus.client.platform.ClientEnvironment;
import com.argus.config.ArgusConfigHolder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Atomic holder and render-hook front door for Fabric Custom GUI.
 *
 * <p>Purpose: keeps reload, screen-open matching, and GUI blit replacement
 * separated. Resource-pack conditions are evaluated once per screen, not per
 * blit.
 *
 * <p>Threading: snapshots and screen override tables are atomically replaced.
 * Screen matching is called from the client render thread.
 *
 * <p>Performance: HOT PATH in {@link #override(Identifier)}. It performs only
 * config/compat checks and one immutable map lookup.
 */
public final class CustomGuiRuntime {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/custom-gui-runtime");

    private static final AtomicReference<CustomGuiClientSnapshot> SNAPSHOT =
            new AtomicReference<>(CustomGuiClientSnapshot.empty());
    private static final AtomicReference<CustomGuiScreenOverrides> ACTIVE =
            new AtomicReference<>(CustomGuiScreenOverrides.EMPTY);
    private static final AtomicReference<String> PENDING_SHULKER_COLOR =
            new AtomicReference<>();
    private static volatile boolean warnedOptiGui;

    private CustomGuiRuntime() {
    }

    public static void replace(CustomGuiClientSnapshot snapshot) {
        SNAPSHOT.set(snapshot != null ? snapshot : CustomGuiClientSnapshot.empty());
        ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
    }

    public static CustomGuiClientSnapshot snapshot() {
        return SNAPSHOT.get();
    }

    /**
     * Stores the color of a shulker box that is about to open a screen.
     *
     * <p>Threading: called on the client thread from the interaction hook.
     * The value is atomically consumed during the next screen classification.
     *
     * <p>Performance: one atomic write outside render hot paths.
     */
    public static void rememberPendingShulkerColor(String color) {
        PENDING_SHULKER_COLOR.set(color);
    }

    /**
     * Clears stale shulker context when an interaction does not open a shulker
     * screen.
     *
     * <p>Threading: client-thread interaction/screen lifecycle helper.
     *
     * <p>Performance: one atomic write outside render hot paths.
     */
    public static void clearPendingShulkerColor() {
        PENDING_SHULKER_COLOR.set(null);
    }

    /**
     * Consumes the remembered shulker color for the current screen match.
     *
     * <p>Threading: called on the client render thread during screen changes.
     *
     * <p>Performance: one atomic get-and-clear per shulker screen open.
     */
    static String consumePendingShulkerColor() {
        return PENDING_SHULKER_COLOR.getAndSet(null);
    }

    public static void screenChanged(Screen screen) {
        if (!enabled()) {
            ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
            clearPendingShulkerColor();
            return;
        }
        CustomGuiClientSnapshot snapshot = SNAPSHOT.get();
        if (snapshot.isEmpty()) {
            ACTIVE.set(CustomGuiScreenOverrides.EMPTY);
            clearPendingShulkerColor();
            return;
        }
        CustomGuiScreenContext context =
                CustomGuiScreenClassifier.classify(screen);
        if (context == null) {
            clearPendingShulkerColor();
        }
        ACTIVE.set(snapshot.resolve(context));
    }

    public static Identifier override(Identifier original) {
        if (original == null || !enabled()) {
            return original;
        }
        return ACTIVE.get().override(original);
    }

    /**
     * 取得目前自訂介面（Custom GUI）執行階段狀態的可翻譯描述（適合除錯介面或診斷日誌使用）。
     */
    public static Component statusComponent() {
        if (!ArgusConfigHolder.get().customGuiActive()) {
            return Component.translatable("argus.status.customgui.disabled_config");
        }
        if (ClientEnvironment.isModLoaded("optigui")) {
            return Component.translatable("argus.status.customgui.disabled_conflict");
        }
        return Component.translatable("argus.status.customgui.active", snapshot().ruleSet().all().size());
    }

    private static boolean enabled() {
        if (!ArgusConfigHolder.get().customGuiActive()) {
            return false;
        }
        if (ClientEnvironment.isModLoaded("optigui")) {
            if (!warnedOptiGui) {
                warnedOptiGui = true;
                LOGGER.warn("{}", Component.translatable(
                        "argus.warn.customgui.runtime.optigui_conflict",
                        Constants.MOD_NAME
                ).getString());
            }
            return false;
        }
        return true;
    }
}
