package com.argus.client.customgui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable texture override table for the currently open screen.
 *
 * <p>Purpose: lets the GUI blit hook do a single map lookup without evaluating
 * resource-pack conditions.
 *
 * <p>Threading: immutable and published through {@link CustomGuiRuntime}.
 *
 * <p>Performance: HOT PATH via {@link #override(Identifier)}; no allocations.
 */
final class CustomGuiScreenOverrides {

    static final CustomGuiScreenOverrides EMPTY =
            new CustomGuiScreenOverrides(Map.of());

    private final Map<Identifier, Identifier> overrides;

    CustomGuiScreenOverrides(Map<Identifier, Identifier> overrides) {
        this.overrides = Map.copyOf(Objects.requireNonNull(
                overrides, () -> Component.translatable("argus.error.customgui.overrides_null").getString()));
    }

    boolean isEmpty() {
        return overrides.isEmpty();
    }

    int size() {
        return overrides.size();
    }

    Identifier override(Identifier original) {
        Identifier replacement = overrides.get(original);
        return replacement != null ? replacement : original;
    }

    /**
     * 取得目前畫面紋理覆蓋狀態的本地化摘要 Component（適合除錯 HUD 或診斷日誌使用）。
     */
    Component toComponent() {
        if (isEmpty()) {
            return Component.translatable("argus.info.customgui.overrides_empty");
        }
        return Component.translatable("argus.info.customgui.overrides_count", overrides.size());
    }
}
