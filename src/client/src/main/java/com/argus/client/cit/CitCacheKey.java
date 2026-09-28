package com.argus.client.cit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Content-based CIT cache key. No wall-clock state.
 *
 * <p>Purpose: invalidate naturally when the item facts relevant to CIT change.
 * Component hashing intentionally uses the stack's immutable component map
 * hash instead of traversing expensive NBT-like structures in the key path.
 */
public record CitCacheKey(Identifier itemId,
                          int count,
                          int damage,
                          int maxDamage,
                          int enchantHash,
                          int componentHash,
                          String hand) {

    public static CitCacheKey from(ItemStack stack, String hand) {
        return new CitCacheKey(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getCount(),
                stack.getDamageValue(),
                stack.getMaxDamage(),
                enchantHash(stack.getEnchantments()),
                stack.getComponents().hashCode(),
                hand);
    }

    /**
     * 取得手持狀態的可翻譯 Component。
     */
    public Component handDisplayName() {
        if (hand == null || hand.isEmpty()) {
            return Component.translatable("argus.cit.hand.none");
        }
        return switch (hand) {
            case "main_hand", "mainhand" -> Component.translatable("argus.cit.hand.main");
            case "off_hand", "offhand" -> Component.translatable("argus.cit.hand.off");
            default -> Component.translatable("argus.cit.hand.custom", hand);
        };
    }

    private static int enchantHash(ItemEnchantments enchantments) {
        int hash = 1;
        for (var entry : enchantments.entrySet()) {
            hash = 31 * hash
                    + entry.getKey().getRegisteredName().hashCode();
            hash = 31 * hash + entry.getIntValue();
        }
        return hash;
    }
}
