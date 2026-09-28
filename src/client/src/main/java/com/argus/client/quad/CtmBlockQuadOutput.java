package com.argus.client.quad;

import com.argus.Constants;
import com.argus.client.ctm.MinecraftNeighborView;
import com.argus.quad.QuadContext;
import com.argus.quad.QuadDecorators;
import com.argus.quad.QuadRef;
import com.argus.resource.NamespaceId;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric-side adapter that wraps a vanilla {@link BlockQuadOutput} and runs every
 * quad through Argus's {@link QuadDecorators} pipeline before the original output is invoked.
 */
public final class CtmBlockQuadOutput implements BlockQuadOutput {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/ctm-output");

    private final BlockQuadOutput delegate;
    private final MinecraftNeighborView view;
    private final BlockPos pos;
    private final String blockId;

    private CtmBlockQuadOutput(BlockQuadOutput delegate,
                               MinecraftNeighborView view,
                               BlockPos pos,
                               String blockId) {
        this.delegate = delegate;
        this.view = view;
        this.pos = pos;
        this.blockId = blockId;
    }

    /**
     * Wraps a vanilla {@link BlockQuadOutput} for one block. The neighbour view
     * is populated for the 26 surrounding blocks once, then reused for the 6 faces.
     */
    public static BlockQuadOutput wrap(BlockQuadOutput delegate,
                                       BlockAndTintGetter level,
                                       BlockPos pos,
                                       BlockState blockState) {
        MinecraftNeighborView view = new MinecraftNeighborView(level);
        view.reset(pos);
        view.fillNeighbours();
        Identifier id = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
        String blockId = id == null ? "" : id.toString();
        return new CtmBlockQuadOutput(delegate, view, pos, blockId);
    }

    @Override
    public void put(float x, float y, float z,
                    BakedQuad quad, QuadInstance instance) {
        NamespaceId spriteId = new NamespaceId(
                quad.materialInfo().sprite().contents().name().getNamespace(),
                quad.materialInfo().sprite().contents().name().getPath());
        QuadContext ctx = new QuadContext(
                pos.getX(), pos.getY(), pos.getZ(),
                quad.direction().get3DDataValue(),
                blockId,
                spriteId,
                view);
        BakedQuadRef ref = new BakedQuadRef(quad);
        QuadRef result = QuadDecorators.apply(ref, ctx);
        if (result == ref) {
            // Hot path: no decorator replaced the quad.
            delegate.put(x, y, z, quad, instance);
            return;
        }
        if (result instanceof BakedQuadRef.PendingSwap pending) {
            TextureAtlas atlas = BlockAtlasProvider.blockAtlas();
            if (atlas == null) {
                delegate.put(x, y, z, quad, instance);
                return;
            }
            BakedQuad remapped = QuadRefSpriteSwapper.swap(
                    pending.original.quad(),
                    pending.newSprite, atlas);
            if (remapped == null) {
                delegate.put(x, y, z, quad, instance);
                return;
            }
            delegate.put(x, y, z, remapped, instance);
            return;
        }
        if (result instanceof BakedQuadRef swapped) {
            delegate.put(x, y, z, swapped.quad(), instance);
            return;
        }

        // A third-party decorator returned an unsupported QuadRef implementation.
        LOGGER.debug("[{}] {}",
                Constants.MOD_NAME,
                Component.translatable("argus.log.ctm.unsupported_quad_ref").getString());
        delegate.put(x, y, z, quad, instance);
    }
}
