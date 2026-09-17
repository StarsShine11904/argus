package com.argus.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the baked quad groups held by one item render layer. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemStackRenderStateLayerAccessor {

    @Accessor("quads")
    ItemQuads argus$quads();

    @Accessor("quads")
    void argus$setQuads(ItemQuads quads);
}
