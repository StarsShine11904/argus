package com.argus.client.quad;

import com.argus.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the live block {@link TextureAtlas} and keeps
 * {@link BlockAtlasProvider} in sync. The block atlas is
 * recreated by the renderer on every resource reload, so we
 * re-publish the reference every client tick.
 *
 * <p>Performance: HOT PATH (every client tick).
 * Allocation policy: none in the steady-state path.
 */
public final class BlockAtlasTracker {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/atlas-tracker");

    private static TextureAtlas lastPublished;

    private BlockAtlasTracker() {
    }

    public static void tick(Minecraft client) {
        if (client == null) {
            return;
        }
        if (client.getAtlasManager() == null) {
            return;
        }
        TextureAtlas atlas;
        try {
            atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Atlas not stitched yet (mid-reload) or the
            // id has been renamed in a future mapping.
            return;
        }
        if (atlas == lastPublished) {
            return;
        }
        lastPublished = atlas;
        BlockAtlasProvider.setBlockAtlas(atlas);
        LOGGER.debug("[{}] {}",
                Constants.MOD_NAME,
                Component.translatable("argus.log.atlas.block_atlas_published",
                        atlas.location()).getString());
    }
}
