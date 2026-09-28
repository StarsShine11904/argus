package com.argus.client.customgui;

import com.argus.Constants;
import com.argus.customgui.CustomGuiParseResult;
import com.argus.customgui.CustomGuiRuleParser;
import com.argus.customgui.CustomGuiRuleSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client resource reload bridge for OptiFine Custom GUI rules.
 *
 * <p>Purpose: scans active resource packs for GUI properties files, parses
 * them in shared code, and publishes an immutable Fabric snapshot.
 *
 * <p>Threading: I/O and parsing run on the prepare executor; publication runs
 * after the reload barrier.
 */
public final class CustomGuiReloadListener implements PreparableReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Constants.MOD_ID + "/custom-gui-reload");
    private static final String OPTIFINE_GUI_CONTAINER =
            "optifine/gui/container";

    public static final Identifier ID =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID,
                    "custom_gui_reload");

    @Override
    public String getName() {
        return "Argus Custom GUI Reload Listener";
    }

    /**
     * 取得本地化顯示名稱，適合除錯 HUD 或介面顯示。
     */
    public Component getDisplayName() {
        return Component.translatable("argus.reload_listener.custom_gui");
    }

    @Override
    public CompletableFuture<Void> reload(
            SharedState currentReload,
            Executor taskExecutor,
            PreparationBarrier preparationBarrier,
            Executor reloadExecutor) {
        ResourceManager resourceManager = currentReload.resourceManager();
        return CompletableFuture
                .supplyAsync(() -> load(resourceManager), taskExecutor)
                .thenCompose(preparationBarrier::wait)
                .thenAcceptAsync(CustomGuiReloadListener::publish,
                        reloadExecutor);
    }

    private static CustomGuiRuleSet load(ResourceManager resourceManager) {
        ArrayList<CustomGuiRuleParser.RuleSource> sources = new ArrayList<>();
        for (Identifier loc : resourceManager
                .listResources(OPTIFINE_GUI_CONTAINER,
                        id -> id.getPath().endsWith(".properties"))
                .keySet()) {
            Optional<Resource> resource = resourceManager.getResource(loc);
            if (resource.isEmpty()) {
                continue;
            }
            try (var in = resource.get().open();
                 var reader = new InputStreamReader(
                         in, StandardCharsets.UTF_8)) {
                sources.add(new CustomGuiRuleParser.RuleSource(
                        readAll(reader), loc.toString()));
            } catch (Exception e) {
                LOGGER.warn("{}", Component.translatable(
                        "argus.warn.customgui.reload.read_failed",
                        Constants.MOD_NAME,
                        loc,
                        e.getMessage()
                ).getString());
            }
        }
        CustomGuiParseResult parsed = CustomGuiRuleParser.parseAll(sources);
        for (CustomGuiParseResult.Error error : parsed.errors()) {
            LOGGER.warn("{}", Component.translatable(
                    "argus.warn.customgui.reload.malformed_file",
                    Constants.MOD_NAME,
                    error.sourceFile(),
                    error.message()
            ).getString());
        }
        CustomGuiRuleSet ruleSet = CustomGuiRuleSet.of(parsed.rules());
        LOGGER.info("{}", Component.translatable(
                "argus.info.customgui.reload.summary",
                Constants.MOD_NAME,
                ruleSet.all().size(),
                sources.size()
        ).getString());
        return ruleSet;
    }

    private static void publish(CustomGuiRuleSet ruleSet) {
        CustomGuiClientSnapshot snapshot = CustomGuiClientSnapshot.from(ruleSet);
        CustomGuiRuntime.replace(snapshot);
        LOGGER.info("{}", Component.translatable(
                "argus.info.customgui.reload.installed",
                Constants.MOD_NAME,
                ruleSet.all().size(),
                !snapshot.isEmpty()
        ).getString());
    }

    private static String readAll(java.io.Reader reader)
            throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) > 0) {
            out.append(buf, 0, n);
        }
        return out.toString();
    }
}
