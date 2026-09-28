package com.argus.client.natural;

import com.argus.Constants;
import com.argus.natural.NaturalTextureParseResult;
import com.argus.natural.NaturalTextureProperties;
import com.argus.natural.NaturalTextureRuleSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric reload bridge for OptiFine Natural Textures.
 *
 * <p>Threading: parse on prepare executor, publish immutable snapshot after
 * reload barrier.
 */
public final class NaturalTexturesReloadListener implements PreparableReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/natural-reload");
    private static final Identifier NATURAL_PROPERTIES =
            Identifier.fromNamespaceAndPath("minecraft", "optifine/natural.properties");

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "natural_textures_reload");

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor) {
        ResourceManager manager = currentReload.resourceManager();
        return CompletableFuture.supplyAsync(() -> load(manager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(NaturalTexturesRuntime::replace, reloadExecutor);
    }

    private static NaturalTextureRuleSet load(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(NATURAL_PROPERTIES);
        if (resource.isEmpty()) {
            LOGGER.info("[{}] {}",
                    Constants.MOD_NAME,
                    Component.translatable("argus.log.natural_textures.not_found",
                            NATURAL_PROPERTIES).getString());
            return NaturalTextureRuleSet.empty();
        }
        try (var in = resource.get().open();
             var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            NaturalTextureParseResult result = NaturalTextureProperties.parse(
                    readAll(reader), NATURAL_PROPERTIES.toString());
            for (NaturalTextureParseResult.Error error : result.errors()) {
                LOGGER.warn("[{}] {}",
                        Constants.MOD_NAME,
                        Component.translatable("argus.log.natural_textures.malformed_key",
                                error.key(), error.sourceFile(), error.value(),
                                error.message()).getString());
            }
            NaturalTextureRuleSet ruleSet = NaturalTextureRuleSet.of(result.rules());
            LOGGER.info("[{}] {}",
                    Constants.MOD_NAME,
                    Component.translatable("argus.log.natural_textures.loaded",
                            ruleSet.size()).getString());
            return ruleSet;
        } catch (Exception e) {
            LOGGER.warn("[{}] {}",
                    Constants.MOD_NAME,
                    Component.translatable("argus.log.natural_textures.read_failed",
                            NATURAL_PROPERTIES, e.getMessage()).getString());
            return NaturalTextureRuleSet.empty();
        }
    }

    private static String readAll(java.io.Reader reader) throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) > 0) {
            out.append(buf, 0, n);
        }
        return out.toString();
    }
}
