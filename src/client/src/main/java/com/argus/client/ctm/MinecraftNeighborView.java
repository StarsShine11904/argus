package com.argus.client.ctm;

import com.argus.Constants;
import com.argus.ctm.CtmMaterialTable;
import com.argus.ctm.CtmRegistry;
import com.argus.ctm.CtmRule;
import com.argus.ctm.CtmRuleParser;
import com.argus.ctm.CtmRuleSet;
import com.argus.ctm.CtmTileAtlas;
import com.argus.ctm.CtmTileAtlasEntry;
import com.argus.ctm.CtmTileResolver;
import com.argus.platform.Platforms;
import com.argus.resource.NamespaceId;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Discovers OptiFine / Continuity CTM {@code .properties} files
 * across all loaded resource packs and feeds them into the
 * loader-agnostic {@link CtmRegistry}.
 *
 * <p>Resource paths scanned:
 * <ul>
 *   <li>{@code assets/&lt;ns&gt;/optifine/ctm/*.properties}</li>
 *   <li>{@code assets/&lt;ns&gt;/continuity/ctm/*.properties}</li>
 * </ul>
 *
 * <p>This is the only file in the project that knows about the
 * Minecraft reload-listener API; everything else about CTM lives in
 * the loader-agnostic {@code shared} module.
 *
 * <h2>Threading</h2>
 *
 * <p>The reload is split into two phases by Mojang's reload API: the
 * {@code prepare} executor does the file I/O and parsing, the
 * {@code apply} executor (render thread) does the atomic swap into
 * the registry. Both are called by the engine; we do not need to
 * synchronise beyond what the engine already provides.
 *
 * <h2>Performance</h2>
 *
 * <p>Resource discovery is O(n_files). Parsing each file is
 * O(file size). The final rule set is built in
 * {@link CtmRuleSet.Builder} which keeps an insertion-sort
 * priority; total cost is O(n log n) for n rules across all packs.
 */
public final class CtmReloadListener implements PreparableReloadListener {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/ctm-reload");

    private static final String OPTIFINE_CTM = "optifine/ctm";
    private static final String CONTINUITY_CTM = "continuity/ctm";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ctm_reload");

    @Override
    public String getName() {
        return Component.translatable("argus.reload_listener.ctm").getString();
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor) {

        ResourceManager resourceManager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> collectRules(resourceManager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(
                        rules -> applyRules(rules, resourceManager),
                        reloadExecutor);
    }

    private List<CtmRuleParser.RuleSource> collectRules(ResourceManager resourceManager) {
        List<CtmRuleParser.RuleSource> out = new ArrayList<>();
        scan(resourceManager, OPTIFINE_CTM, out);
        scan(resourceManager, CONTINUITY_CTM, out);
        LOGGER.info("{}", Component.translatable(
                "argus.info.ctm.reload.discovered_files",
                Constants.MOD_NAME,
                out.size()
        ).getString());
        return out;
    }

    private void scan(ResourceManager resourceManager, String folder,
                      List<CtmRuleParser.RuleSource> out) {
        for (Identifier loc : resourceManager
                .listResources(folder, p -> p.getPath().endsWith(".properties"))
                .keySet()) {
            try {
                Resource res = resourceManager.getResource(loc).orElse(null);
                if (res == null) {
                    continue;
                }
                String body;
                try (var in = res.open();
                     var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    body = readAll(reader);
                }
                String sourceLabel = loc.toString();
                out.add(new CtmRuleParser.RuleSource(body, sourceLabel));
            } catch (IOException e) {
                LOGGER.warn("{}", Component.translatable(
                        "argus.warn.ctm.reload.read_file_failed",
                        Constants.MOD_NAME,
                        loc,
                        e.getMessage()
                ).getString());
            }
        }
    }

    private void applyRules(List<CtmRuleParser.RuleSource> sources,
                            ResourceManager resourceManager) {
        NamespaceId parent = new NamespaceId(Constants.MOD_ID, OPTIFINE_CTM);
        CtmRuleSet ruleSet = CtmRuleParser.buildRuleSet(sources, parent,
                (label, message, cause) -> {
                    if (cause != null && cause.getMessage() != null) {
                        LOGGER.warn("{}", Component.translatable(
                                "argus.warn.ctm.reload.malformed_rule_file_with_cause",
                                Constants.MOD_NAME,
                                label,
                                message,
                                cause.getMessage()
                        ).getString());
                    } else {
                        LOGGER.warn("{}", Component.translatable(
                                "argus.warn.ctm.reload.malformed_rule_file",
                                Constants.MOD_NAME,
                                label,
                                message
                        ).getString());
                    }
                });
        CtmRegistry reg = Platforms.get().ctmRegistry();
        reg.replace(ruleSet);
        LOGGER.info("{}", Component.translatable(
                "argus.info.ctm.reload.ruleset_installed",
                Constants.MOD_NAME,
                ruleSet.all().size(),
                sources.size()
        ).getString());

        buildAndPublishTileAtlas(ruleSet, sources, resourceManager);
    }

    private void buildAndPublishTileAtlas(
            CtmRuleSet ruleSet,
            List<CtmRuleParser.RuleSource> sources,
            ResourceManager resourceManager) {
        java.util.HashMap<String, String> labelByPath =
                new java.util.HashMap<>();
        for (CtmRuleParser.RuleSource s : sources) {
            labelByPath.put(s.sourceLabel(), s.sourceLabel());
        }
        java.util.ArrayList<CtmTileAtlasEntry> entries =
                new java.util.ArrayList<>();
        int numericTiles = 0;
        int namedTiles = 0;
        int generatedFallbackTiles = 0;
        for (CtmRule rule : ruleSet.all()) {
            String sourcePath = rule.sourceFile().orElse(null);
            if (sourcePath == null) {
                continue;
            }
            if (!labelByPath.containsKey(sourcePath)) {
                labelByPath.put(sourcePath, sourcePath);
            }
            String dirPath = CtmTileResolver.propertiesDirectoryPath(sourcePath);
            int dirColon = dirPath.indexOf(':');
            String dirNs = dirColon < 0
                    ? com.argus.resource.NamespaceId.DEFAULT_NAMESPACE
                    : dirPath.substring(0, dirColon);
            String dirSlash = dirColon < 0
                    ? dirPath
                    : dirPath.substring(dirColon + 1);
            java.util.function.IntPredicate tileExists = n -> {
                String tilePath = dirSlash + "/" + n + ".png";
                Identifier tileId = Identifier.fromNamespaceAndPath(
                        dirNs, tilePath);
                return resourceManager.getResource(tileId).isPresent();
            };
            try {
                java.util.List<CtmTileResolver.Resolution> resolutions =
                        CtmTileResolver.resolve(
                                rule, sourcePath, tileExists);
                entries.add(new CtmTileAtlasEntry(rule, resolutions));
                for (CtmTileResolver.Resolution r : resolutions) {
                    if (r.needsInjection()) {
                        numericTiles++;
                        if (r.resourcePath() == null
                                && r.fallbackSourceSprite() != null) {
                            generatedFallbackTiles++;
                        }
                    } else if (r.isConcrete()) {
                        namedTiles++;
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warn("{}", Component.translatable(
                        "argus.warn.ctm.reload.tile_resolution_failed",
                        Constants.MOD_NAME,
                        sourcePath,
                        e.getMessage()
                ).getString());
            }
        }
        CtmTileAtlas atlas = CtmTileAtlas.of(entries);
        CtmTileAtlas.replace(atlas);
        CtmMaterialTable materialTable = CtmMaterialTable.of(atlas);
        CtmMaterialTable.replace(materialTable);
        LOGGER.info("{}", Component.translatable(
                "argus.info.ctm.reload.tile_atlas_installed",
                Constants.MOD_NAME,
                entries.size(),
                numericTiles,
                generatedFallbackTiles,
                namedTiles,
                materialTable.size()
        ).getString());
        requestTerrainRebuild();
    }

    private void requestTerrainRebuild() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.levelRenderer.invalidateCompiledGeometry(
                minecraft.level,
                minecraft.options,
                minecraft.gameRenderer.mainCamera(),
                minecraft.getBlockColors());
        LOGGER.info("{}", Component.translatable(
                "argus.info.ctm.reload.terrain_rebuild_requested",
                Constants.MOD_NAME
        ).getString());
    }

    private static String readAll(java.io.Reader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = r.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
