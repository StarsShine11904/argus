package com.argus.client.cit;

import com.argus.cit.CitRule;
import com.argus.cit.CitRuleSet;
import com.argus.resource.NamespaceId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fabric-resolved CIT snapshot for hot item-render lookup.
 *
 * <p>Purpose: converts shared {@link NamespaceId} item ids to actual
 * Minecraft {@link Item} instances once at reload, then exposes O(1)
 * identity lookups during rendering.
 *
 * <p>Threading: immutable after construction and published atomically by
 * {@link CitRuntime}.
 *
 * <p>Performance: HOT PATH via {@link #rulesFor(Item)}. It performs one
 * identity-map lookup and returns the immutable per-item array.
 */
public final class CitClientSnapshot {

    private static final CitRule[] EMPTY_RULES = new CitRule[0];
    private static final CitClientSnapshot EMPTY =
            new CitClientSnapshot(CitRuleSet.empty(), new IdentityHashMap<>());

    private final CitRuleSet ruleSet;
    private final IdentityHashMap<Item, CitRule[]> byItem;

    private CitClientSnapshot(CitRuleSet ruleSet,
                              IdentityHashMap<Item, CitRule[]> byItem) {
        this.ruleSet = Objects.requireNonNull(ruleSet, () ->
                Component.translatable("argus.error.cit.ruleset_null").getString());
        this.byItem = new IdentityHashMap<>(byItem);
    }

    public static CitClientSnapshot empty() {
        return EMPTY;
    }

    public static CitClientSnapshot from(CitRuleSet ruleSet) {
        Objects.requireNonNull(ruleSet, () ->
                Component.translatable("argus.error.cit.ruleset_null").getString());
        if (ruleSet.isEmpty()) {
            return EMPTY;
        }
        IdentityHashMap<Item, CitRule[]> index = new IdentityHashMap<>();
        for (Map.Entry<NamespaceId, CitRule[]> entry
                : ruleSet.byItem().entrySet()) {
            Identifier id = Identifier.fromNamespaceAndPath(
                    entry.getKey().namespace(), entry.getKey().path());
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != null) {
                index.put(item, entry.getValue());
            }
        }
        if (index.isEmpty()) {
            return EMPTY;
        }
        return new CitClientSnapshot(ruleSet, index);
    }

    public CitRuleSet ruleSet() {
        return ruleSet;
    }

    public boolean isEmpty() {
        return byItem.isEmpty();
    }

    public int size() {
        return byItem.size();
    }

    public CitRule[] rulesFor(Item item) {
        CitRule[] rules = byItem.get(item);
        return rules == null ? EMPTY_RULES : rules;
    }

    /**
     * 取得快照狀態的本地化摘要 Component（適合 Log 或除錯介面使用）。
     */
    public Component toComponent() {
        if (isEmpty()) {
            return Component.translatable("argus.info.cit.snapshot_empty");
        }
        return Component.translatable("argus.info.cit.snapshot_summary", byItem.size());
    }
}
