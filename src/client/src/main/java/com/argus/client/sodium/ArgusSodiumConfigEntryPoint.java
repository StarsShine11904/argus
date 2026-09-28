package com.argus.client.sodium;

import com.argus.Constants;
import com.argus.client.config.ArgusClientConfigLoader;
import com.argus.client.platform.ClientEnvironment;
import com.argus.config.ArgusConfig;
import com.argus.config.ArgusConfigDefaults;
import com.argus.config.ArgusConfigHolder;
import com.argus.config.BetterGrassMode;
import com.argus.config.FullscreenMode;
import com.argus.config.OverlayCorner;
import com.argus.config.TextContrast;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Registers Argus's Sodium settings pages.
 *
 * <p>Threading: Sodium calls this on the client configuration UI thread. The
 * storage publishes immutable {@link ArgusConfig} snapshots through
 * {@link ArgusConfigHolder}.
 *
 * <p>Performance: not in render hot paths.
 */
public final class ArgusSodiumConfigEntryPoint implements ConfigEntryPoint {

    private static final Identifier CONFIG_ICON =
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/config_icon.png");
    private static final String ETF_MOD_ID = "entity_texture_features";

    private final OptionStorage storage = new OptionStorage();
    private final StorageEventHandler storageHandler = this.storage::flush;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setName(Constants.MOD_NAME)
                .setVersion(argusVersion())
                .setIcon(CONFIG_ICON)
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable("argus.config.page.general"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.translatable("argus.config.group.renderer"))
                                .addOption(booleanOption(builder, "enabled",
                                        ArgusConfigDefaults.ENABLED,
                                        this.storage::setEnabled,
                                        this.storage::getEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "safe_mode",
                                        ArgusConfigDefaults.SAFE_MODE,
                                        this.storage::setSafeMode,
                                        this.storage::getSafeMode))
                                .addOption(booleanOption(builder, "verify_mode",
                                        ArgusConfigDefaults.VERIFY_MODE,
                                        this.storage::setVerifyMode,
                                        this.storage::getVerifyMode))
                                .addOption(booleanOption(builder, "duplicate_translucent_backfaces",
                                        ArgusConfigDefaults.DUPLICATE_TRANSLUCENT_BACKFACES,
                                        this.storage::setDuplicateTranslucentBackfaces,
                                        this.storage::getDuplicateTranslucentBackfaces,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))))
                .addPage(builder.createOptionPage()
                        .setName(Component.translatable("argus.config.page.resource_packs"))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.translatable("argus.config.group.optifine_features"))
                                .addOption(booleanOption(builder, "ctm_enabled",
                                        ArgusConfigDefaults.CTM_ENABLED,
                                        this.storage::setCtmEnabled,
                                        this.storage::getCtmEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "cit_enabled",
                                        ArgusConfigDefaults.CIT_ENABLED,
                                        this.storage::setCitEnabled,
                                        this.storage::getCitEnabled))
                                .addOption(booleanOption(builder, "custom_gui_enabled",
                                        ArgusConfigDefaults.CUSTOM_GUI_ENABLED,
                                        this.storage::setCustomGuiEnabled,
                                        this.storage::getCustomGuiEnabled))
                                .addOption(booleanOption(builder, "custom_colors_enabled",
                                        ArgusConfigDefaults.CUSTOM_COLORS_ENABLED,
                                        this.storage::setCustomColorsEnabled,
                                        this.storage::getCustomColorsEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "custom_sky_enabled",
                                        ArgusConfigDefaults.CUSTOM_SKY_ENABLED,
                                        this.storage::setCustomSkyEnabled,
                                        this.storage::getCustomSkyEnabled))
                                .addOption(booleanOption(builder, "custom_animations_enabled",
                                        ArgusConfigDefaults.CUSTOM_ANIMATIONS_ENABLED,
                                        this.storage::setCustomAnimationsEnabled,
                                        this.storage::getCustomAnimationsEnabled))
                                .addOption(booleanOption(builder, "custom_entity_models_enabled",
                                        ArgusConfigDefaults.CUSTOM_ENTITY_MODELS_ENABLED,
                                        this.storage::setCustomEntityModelsEnabled,
                                        this.storage::getCustomEntityModelsEnabled))
                                .addOption(booleanOption(builder, "natural_textures_enabled",
                                        ArgusConfigDefaults.NATURAL_TEXTURES_ENABLED,
                                        this.storage::setNaturalTexturesEnabled,
                                        this.storage::getNaturalTexturesEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "better_snow_enabled",
                                        ArgusConfigDefaults.BETTER_SNOW_ENABLED,
                                        this.storage::setBetterSnowEnabled,
                                        this.storage::getBetterSnowEnabled,
                                        OptionFlag.REQUIRES_RENDERER_RELOAD))
                                .addOption(booleanOption(builder, "ctm_debug_logging",
                                        ArgusConfigDefaults.CTM_DEBUG_LOGGING,
                                        this.storage::setCtmDebugLogging,
                                        this.storage::getCtmDebugLogging)))
                        .addOptionGroup(builder.createOptionGroup()
                                .setName(Component.translatable("argus.config.group.entity_textures"))
                                .addOption(entityTextureOption(builder, "entity_textures_enabled",
                                        ArgusConfigDefaults.ENTITY_TEXTURES_ENABLED,
                                        this.storage::setEntityTexturesEnabled,
                                        this.storage::getEntityTexturesEnabled))
                                .addOption(entityTextureOption(builder, "random_entities_enabled",
                                        ArgusConfigDefaults.RANDOM_ENTITIES_ENABLED,
                                        this.storage::setRandomEntitiesEnabled,
                                        this.storage::getRandomEntitiesEnabled))
                                .addOption(entityTextureOption(builder, "random_block_entity_textures",
                                        ArgusConfigDefaults.RANDOM_BLOCK_ENTITY_TEXTURES,
                                        this.storage::setRandomBlockEntityTextures,
                                        this.storage::getRandomBlockEntityTextures))
                                .addOption(entityTextureOption(builder, "entity_emissive_textures",
                                        ArgusConfigDefaults.ENTITY_EMISSIVE_TEXTURES,
                                        this.storage::setEntityEmissiveTextures,
                                        this.storage::getEntityEmissiveTextures))
                                .addOption(entityTextureOption(builder, "entity_texture_debug",
                                        ArgusConfigDefaults.ENTITY_TEXTURE_DEBUG,
                                        this.storage::setEntityTextureDebug,
                                        this.storage::getEntityTextureDebug))))
                .addPage(detailsPage(builder))
                .addPage(animationsPage(builder))
                .addPage(particlesPage(builder))
                .addPage(entitiesPage(builder))
                .addPage(hudPage(builder))
                .addPage(extrasPage(builder))
                .addPage(betterGrassPage(builder));
    }

    private OptionPageBuilder detailsPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.details"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.sky_and_weather"))
                        .addOption(booleanOption(builder, "details_sky_enabled",
                                ArgusConfigDefaults.DETAILS_SKY_ENABLED,
                                this.storage::setDetailsSkyEnabled,
                                this.storage::getDetailsSkyEnabled))
                        .addOption(booleanOption(builder, "details_sun_enabled",
                                ArgusConfigDefaults.DETAILS_SUN_ENABLED,
                                this.storage::setDetailsSunEnabled,
                                this.storage::getDetailsSunEnabled))
                        .addOption(booleanOption(builder, "details_moon_enabled",
                                ArgusConfigDefaults.DETAILS_MOON_ENABLED,
                                this.storage::setDetailsMoonEnabled,
                                this.storage::getDetailsMoonEnabled))
                        .addOption(booleanOption(builder, "details_stars_enabled",
                                ArgusConfigDefaults.DETAILS_STARS_ENABLED,
                                this.storage::setDetailsStarsEnabled,
                                this.storage::getDetailsStarsEnabled))
                        .addOption(integerOption(builder, "details_cloud_height",
                                ArgusConfigDefaults.DETAILS_CLOUD_HEIGHT,
                                0, 512, 1,
                                this.storage::setDetailsCloudHeight,
                                this.storage::getDetailsCloudHeight))
                        .addOption(booleanOption(builder, "details_rain_snow_enabled",
                                ArgusConfigDefaults.DETAILS_RAIN_SNOW_ENABLED,
                                this.storage::setDetailsRainSnowEnabled,
                                this.storage::getDetailsRainSnowEnabled))
                        .addOption(booleanOption(builder, "colors_biome_enabled",
                                ArgusConfigDefaults.BIOME_COLORS_ENABLED,
                                this.storage::setBiomeColorsEnabled,
                                this.storage::getBiomeColorsEnabled,
                                OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(booleanOption(builder, "colors_sky_enabled",
                                ArgusConfigDefaults.SKY_COLORS_ENABLED,
                                this.storage::setSkyColorsEnabled,
                                this.storage::getSkyColorsEnabled)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.fog"))
                        .addOption(booleanOption(builder, "fog_enabled",
                                ArgusConfigDefaults.FOG_ENABLED,
                                this.storage::setFogEnabled,
                                this.storage::getFogEnabled))
                        .addOption(booleanOption(builder, "fog_air",
                                ArgusConfigDefaults.FOG_AIR,
                                this.storage::setFogAir,
                                this.storage::getFogAir))
                        .addOption(booleanOption(builder, "fog_water",
                                ArgusConfigDefaults.FOG_WATER,
                                this.storage::setFogWater,
                                this.storage::getFogWater))
                        .addOption(booleanOption(builder, "fog_lava",
                                ArgusConfigDefaults.FOG_LAVA,
                                this.storage::setFogLava,
                                this.storage::getFogLava))
                        .addOption(booleanOption(builder, "fog_powder_snow",
                                ArgusConfigDefaults.FOG_POWDER_SNOW,
                                this.storage::setFogPowderSnow,
                                this.storage::getFogPowderSnow)));
    }

    private OptionPageBuilder animationsPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.animations"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.texture_animations"))
                        .addOption(booleanOption(builder, "animations_enabled",
                                ArgusConfigDefaults.ANIMATIONS_ENABLED,
                                this.storage::setAnimationsEnabled,
                                this.storage::getAnimationsEnabled))
                        .addOption(booleanOption(builder, "animation_water",
                                ArgusConfigDefaults.ANIMATION_WATER,
                                this.storage::setAnimationWater,
                                this.storage::getAnimationWater))
                        .addOption(booleanOption(builder, "animation_lava",
                                ArgusConfigDefaults.ANIMATION_LAVA,
                                this.storage::setAnimationLava,
                                this.storage::getAnimationLava))
                        .addOption(booleanOption(builder, "animation_fire",
                                ArgusConfigDefaults.ANIMATION_FIRE,
                                this.storage::setAnimationFire,
                                this.storage::getAnimationFire))
                        .addOption(booleanOption(builder, "animation_portal",
                                ArgusConfigDefaults.ANIMATION_PORTAL,
                                this.storage::setAnimationPortal,
                                this.storage::getAnimationPortal))
                        .addOption(booleanOption(builder, "animation_sculk_sensor",
                                ArgusConfigDefaults.ANIMATION_SCULK_SENSOR,
                                this.storage::setAnimationSculkSensor,
                                this.storage::getAnimationSculkSensor))
                        .addOption(booleanOption(builder, "animation_blocks",
                                ArgusConfigDefaults.ANIMATION_BLOCKS,
                                this.storage::setAnimationBlocks,
                                this.storage::getAnimationBlocks))
                        .addOption(integerOption(builder, "custom_animations_mipmap_distance",
                                ArgusConfigDefaults.CUSTOM_ANIMATION_MIPMAP_DISTANCE,
                                0, 4, 1,
                                this.storage::setCustomAnimationMipmapDistance,
                                this.storage::getCustomAnimationMipmapDistance)));
    }

    private OptionPageBuilder particlesPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.particles"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.particle_spawning"))
                        .addOption(booleanOption(builder, "particle_rain_splash",
                                ArgusConfigDefaults.PARTICLE_RAIN_SPLASH,
                                this.storage::setParticleRainSplash,
                                this.storage::getParticleRainSplash))
                        .addOption(booleanOption(builder, "particle_block_break",
                                ArgusConfigDefaults.PARTICLE_BLOCK_BREAK,
                                this.storage::setParticleBlockBreak,
                                this.storage::getParticleBlockBreak))
                        .addOption(booleanOption(builder, "particle_block_breaking",
                                ArgusConfigDefaults.PARTICLE_BLOCK_BREAKING,
                                this.storage::setParticleBlockBreaking,
                                this.storage::getParticleBlockBreaking))
                        .addOption(booleanOption(builder, "particle_explosion",
                                ArgusConfigDefaults.PARTICLE_EXPLOSION,
                                this.storage::setParticleExplosion,
                                this.storage::getParticleExplosion))
                        .addOption(booleanOption(builder, "particle_water",
                                ArgusConfigDefaults.PARTICLE_WATER,
                                this.storage::setParticleWater,
                                this.storage::getParticleWater))
                        .addOption(booleanOption(builder, "particle_smoke",
                                ArgusConfigDefaults.PARTICLE_SMOKE,
                                this.storage::setParticleSmoke,
                                this.storage::getParticleSmoke))
                        .addOption(booleanOption(builder, "particle_potion",
                                ArgusConfigDefaults.PARTICLE_POTION,
                                this.storage::setParticlePotion,
                                this.storage::getParticlePotion))
                        .addOption(booleanOption(builder, "particle_portal",
                                ArgusConfigDefaults.PARTICLE_PORTAL,
                                this.storage::setParticlePortal,
                                this.storage::getParticlePortal))
                        .addOption(booleanOption(builder, "particle_flame",
                                ArgusConfigDefaults.PARTICLE_FLAME,
                                this.storage::setParticleFlame,
                                this.storage::getParticleFlame))
                        .addOption(booleanOption(builder, "particle_redstone",
                                ArgusConfigDefaults.PARTICLE_REDSTONE,
                                this.storage::setParticleRedstone,
                                this.storage::getParticleRedstone))
                        .addOption(booleanOption(builder, "particle_dripping",
                                ArgusConfigDefaults.PARTICLE_DRIPPING,
                                this.storage::setParticleDripping,
                                this.storage::getParticleDripping))
                        .addOption(booleanOption(builder, "particle_firework",
                                ArgusConfigDefaults.PARTICLE_FIREWORK,
                                this.storage::setParticleFirework,
                                this.storage::getParticleFirework)));
    }

    private OptionPageBuilder entitiesPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.entities"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.entity_rendering"))
                        .addOption(booleanOption(builder, "entity_name_tags_enabled",
                                ArgusConfigDefaults.ENTITY_NAME_TAGS_ENABLED,
                                this.storage::setEntityNameTagsEnabled,
                                this.storage::getEntityNameTagsEnabled))
                        .addOption(booleanOption(builder, "entity_player_name_tags",
                                ArgusConfigDefaults.ENTITY_PLAYER_NAME_TAGS,
                                this.storage::setEntityPlayerNameTags,
                                this.storage::getEntityPlayerNameTags))
                        .addOption(booleanOption(builder, "entity_item_frames",
                                ArgusConfigDefaults.ENTITY_ITEM_FRAMES,
                                this.storage::setEntityItemFrames,
                                this.storage::getEntityItemFrames))
                        .addOption(booleanOption(builder, "entity_paintings",
                                ArgusConfigDefaults.ENTITY_PAINTINGS,
                                this.storage::setEntityPaintings,
                                this.storage::getEntityPaintings))
                        .addOption(booleanOption(builder, "entity_piston_animations",
                                ArgusConfigDefaults.ENTITY_PISTON_ANIMATIONS,
                                this.storage::setEntityPistonAnimations,
                                this.storage::getEntityPistonAnimations))
                        .addOption(booleanOption(builder, "entity_beacon_beam",
                                ArgusConfigDefaults.ENTITY_BEACON_BEAM,
                                this.storage::setEntityBeaconBeam,
                                this.storage::getEntityBeaconBeam))
                        .addOption(booleanOption(builder, "entity_enchanting_table_book",
                                ArgusConfigDefaults.ENTITY_ENCHANTING_TABLE_BOOK,
                                this.storage::setEntityEnchantingTableBook,
                                this.storage::getEntityEnchantingTableBook)));
    }

    private OptionPageBuilder hudPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.hud"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.overlay"))
                        .addOption(booleanOption(builder, "hud_fps",
                                ArgusConfigDefaults.SHOW_FPS,
                                this.storage::setShowFps,
                                this.storage::getShowFps))
                        .addOption(booleanOption(builder, "hud_fps_extended",
                                ArgusConfigDefaults.SHOW_FPS_EXTENDED,
                                this.storage::setShowFpsExtended,
                                this.storage::getShowFpsExtended))
                        .addOption(booleanOption(builder, "hud_coords",
                                ArgusConfigDefaults.SHOW_COORDS,
                                this.storage::setShowCoords,
                                this.storage::getShowCoords))
                        .addOption(enumOption(builder, "hud_corner",
                                OverlayCorner.class,
                                ArgusConfigDefaults.OVERLAY_CORNER,
                                this.storage::setOverlayCorner,
                                this.storage::getOverlayCorner))
                        .addOption(enumOption(builder, "hud_text_contrast",
                                TextContrast.class,
                                ArgusConfigDefaults.TEXT_CONTRAST,
                                this.storage::setTextContrast,
                                this.storage::getTextContrast)));
    }

    private OptionPageBuilder extrasPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.extras"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.toasts"))
                        .addOption(booleanOption(builder, "toast_advancement",
                                ArgusConfigDefaults.TOAST_ADVANCEMENT,
                                this.storage::setToastAdvancement,
                                this.storage::getToastAdvancement))
                        .addOption(booleanOption(builder, "toast_recipe",
                                ArgusConfigDefaults.TOAST_RECIPE,
                                this.storage::setToastRecipe,
                                this.storage::getToastRecipe))
                        .addOption(booleanOption(builder, "toast_system",
                                ArgusConfigDefaults.TOAST_SYSTEM,
                                this.storage::setToastSystem,
                                this.storage::getToastSystem))
                        .addOption(booleanOption(builder, "toast_tutorial",
                                ArgusConfigDefaults.TOAST_TUTORIAL,
                                this.storage::setToastTutorial,
                                this.storage::getToastTutorial)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.movement"))
                        .addOption(booleanOption(builder, "instant_sneak",
                                ArgusConfigDefaults.INSTANT_SNEAK,
                                this.storage::setInstantSneak,
                                this.storage::getInstantSneak)));
    }

    private OptionPageBuilder betterGrassPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable("argus.config.page.better_grass"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable("argus.config.group.grass_sides"))
                        .addOption(builder.createEnumOption(
                                        id("better_grass_mode"),
                                        BetterGrassMode.class)
                                .setName(Component.translatable("argus.config.option.better_grass_mode"))
                                .setTooltip(Component.translatable("argus.config.option.better_grass_mode.desc"))
                                .setElementNameProvider(mode -> Component.translatable(
                                        "argus.config.enum.better_grass_mode." + mode.name().toLowerCase(Locale.ROOT)))
                                .setStorageHandler(this.storageHandler)
                                .setBinding(this.storage::setBetterGrassMode,
                                        this.storage::getBetterGrassMode)
                                .setDefaultValue(ArgusConfigDefaults.BETTER_GRASS_MODE)
                                .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(booleanOption(builder, "better_grass_ignore_resource_pack",
                                ArgusConfigDefaults.BETTER_GRASS_IGNORE_RESOURCE_PACK,
                                this.storage::setBetterGrassIgnoreResourcePack,
                                this.storage::getBetterGrassIgnoreResourcePack,
                                OptionFlag.REQUIRES_RENDERER_RELOAD))
                        .addOption(betterGrassBlockOption(builder, "better_grass_grass_block",
                                ArgusConfigDefaults.BETTER_GRASS_GRASS_BLOCK,
                                this.storage::setBetterGrassGrassBlock,
                                this.storage::getBetterGrassGrassBlock))
                        .addOption(betterGrassBlockOption(builder, "better_grass_snowy_grass_block",
                                ArgusConfigDefaults.BETTER_GRASS_SNOWY_GRASS_BLOCK,
                                this.storage::setBetterGrassSnowyGrassBlock,
                                this.storage::getBetterGrassSnowyGrassBlock))
                        .addOption(betterGrassBlockOption(builder, "better_grass_dirt_path",
                                ArgusConfigDefaults.BETTER_GRASS_DIRT_PATH,
                                this.storage::setBetterGrassDirtPath,
                                this.storage::getBetterGrassDirtPath))
                        .addOption(betterGrassBlockOption(builder, "better_grass_farmland",
                                ArgusConfigDefaults.BETTER_GRASS_FARMLAND,
                                this.storage::setBetterGrassFarmland,
                                this.storage::getBetterGrassFarmland))
                        .addOption(betterGrassBlockOption(builder, "better_grass_mycelium",
                                ArgusConfigDefaults.BETTER_GRASS_MYCELIUM,
                                this.storage::setBetterGrassMycelium,
                                this.storage::getBetterGrassMycelium))
                        .addOption(betterGrassBlockOption(builder, "better_grass_podzol",
                                ArgusConfigDefaults.BETTER_GRASS_PODZOL,
                                this.storage::setBetterGrassPodzol,
                                this.storage::getBetterGrassPodzol))
                        .addOption(betterGrassBlockOption(builder, "better_grass_crimson_nylium",
                                ArgusConfigDefaults.BETTER_GRASS_CRIMSON_NYLIUM,
                                this.storage::setBetterGrassCrimsonNylium,
                                this.storage::getBetterGrassCrimsonNylium))
                        .addOption(betterGrassBlockOption(builder, "better_grass_warped_nylium",
                                ArgusConfigDefaults.BETTER_GRASS_WARPED_NYLIUM,
                                this.storage::setBetterGrassWarpedNylium,
                                this.storage::getBetterGrassWarpedNylium)));
    }

    private BooleanOptionBuilder betterGrassBlockOption(ConfigBuilder builder,
                                                        String path,
                                                        boolean defaultValue,
                                                        Consumer<Boolean> setter,
                                                        Supplier<Boolean> getter) {
        return booleanOption(builder, path, defaultValue, setter, getter,
                OptionFlag.REQUIRES_RENDERER_RELOAD);
    }

    private BooleanOptionBuilder booleanOption(ConfigBuilder builder,
                                               String path,
                                               boolean defaultValue,
                                               Consumer<Boolean> setter,
                                               Supplier<Boolean> getter,
                                               OptionFlag... flags) {
        return builder.createBooleanOption(id(path))
                .setName(Component.translatable("argus.config.option." + path))
                .setTooltip(Component.translatable("argus.config.option." + path + ".desc"))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setFlags(flags);
    }

    private BooleanOptionBuilder entityTextureOption(
            ConfigBuilder builder,
            String path,
            boolean defaultValue,
            Consumer<Boolean> setter,
            Supplier<Boolean> getter,
            OptionFlag... flags) {
        boolean etfLoaded = ClientEnvironment.isModLoaded(ETF_MOD_ID);
        Component tooltip = etfLoaded
                ? Component.translatable("argus.config.option." + path + ".desc")
                        .append(" ")
                        .append(Component.translatable("argus.config.compat.etf_installed"))
                : Component.translatable("argus.config.option." + path + ".desc");

        return builder.createBooleanOption(id(path))
                .setName(Component.translatable("argus.config.option." + path))
                .setTooltip(tooltip)
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setFlags(flags)
                .setEnabled(!etfLoaded);
    }

    private IntegerOptionBuilder integerOption(ConfigBuilder builder,
                                               String path,
                                               int defaultValue,
                                               int min,
                                               int max,
                                               int step,
                                               Consumer<Integer> setter,
                                               Supplier<Integer> getter,
                                               OptionFlag... flags) {
        return builder.createIntegerOption(id(path))
                .setName(Component.translatable("argus.config.option." + path))
                .setTooltip(Component.translatable("argus.config.option." + path + ".desc"))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setRange(min, max, step)
                .setValueFormatter(value -> Component.literal(Integer.toString(value)))
                .setFlags(flags);
    }

    private <T extends Enum<T>> EnumOptionBuilder<T> enumOption(
            ConfigBuilder builder,
            String path,
            Class<T> type,
            T defaultValue,
            Consumer<T> setter,
            Supplier<T> getter,
            OptionFlag... flags) {
        String enumPrefix = "argus.config.enum." + type.getSimpleName().toLowerCase(Locale.ROOT) + ".";
        return builder.createEnumOption(id(path), type)
                .setName(Component.translatable("argus.config.option." + path))
                .setTooltip(Component.translatable("argus.config.option." + path + ".desc"))
                .setElementNameProvider(value -> Component.translatable(
                        enumPrefix + value.name().toLowerCase(Locale.ROOT)))
                .setStorageHandler(this.storageHandler)
                .setBinding(setter, getter)
                .setDefaultValue(defaultValue)
                .setFlags(flags);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
    }

    private static String argusVersion() {
        return ClientEnvironment.modVersion();
    }

    private static final class OptionStorage {
        private final Path configDir = ClientEnvironment.get().configDir();

        void flush() {
            ArgusClientConfigLoader.save(this.configDir, ArgusConfigHolder.get());
        }

        private void update(Consumer<ArgusConfig.Builder> updater) {
            ArgusConfig.Builder builder = ArgusConfigHolder.get().toBuilder();
            updater.accept(builder);
            ArgusConfigHolder.replace(builder.build());
        }

        boolean getEnabled() { return ArgusConfigHolder.get().enabled(); }
        void setEnabled(boolean value) { update(b -> b.enabled(value)); }
        boolean getSafeMode() { return ArgusConfigHolder.get().safeMode(); }
        void setSafeMode(boolean value) { update(b -> b.safeMode(value)); }
        boolean getVerifyMode() { return ArgusConfigHolder.get().verifyMode(); }
        void setVerifyMode(boolean value) { update(b -> b.verifyMode(value)); }
        boolean getCtmEnabled() { return ArgusConfigHolder.get().ctmEnabled(); }
        void setCtmEnabled(boolean value) { update(b -> b.ctmEnabled(value)); }
        boolean getCtmDebugLogging() { return ArgusConfigHolder.get().ctmDebugLogging(); }
        void setCtmDebugLogging(boolean value) { update(b -> b.ctmDebugLogging(value)); }
        boolean getDuplicateTranslucentBackfaces() { return ArgusConfigHolder.get().duplicateTranslucentBackfaces(); }
        void setDuplicateTranslucentBackfaces(boolean value) { update(b -> b.duplicateTranslucentBackfaces(value)); }
        boolean getCitEnabled() { return ArgusConfigHolder.get().citEnabled(); }
        void setCitEnabled(boolean value) { update(b -> b.citEnabled(value)); }
        boolean getCustomGuiEnabled() { return ArgusConfigHolder.get().customGuiEnabled(); }
        void setCustomGuiEnabled(boolean value) { update(b -> b.customGuiEnabled(value)); }
        boolean getCustomColorsEnabled() { return ArgusConfigHolder.get().customColorsEnabled(); }
        void setCustomColorsEnabled(boolean value) { update(b -> b.customColorsEnabled(value)); }
        boolean getCustomSkyEnabled() { return ArgusConfigHolder.get().customSkyEnabled(); }
        void setCustomSkyEnabled(boolean value) { update(b -> b.customSkyEnabled(value)); }
        boolean getNaturalTexturesEnabled() { return ArgusConfigHolder.get().naturalTexturesEnabled(); }
        void setNaturalTexturesEnabled(boolean value) { update(b -> b.naturalTexturesEnabled(value)); }
        boolean getBetterSnowEnabled() { return ArgusConfigHolder.get().betterSnowEnabled(); }
        void setBetterSnowEnabled(boolean value) { update(b -> b.betterSnowEnabled(value)); }
        boolean getCustomAnimationsEnabled() { return ArgusConfigHolder.get().customAnimationsEnabled(); }
        void setCustomAnimationsEnabled(boolean value) { update(b -> b.customAnimationsEnabled(value)); }
        boolean getRandomEntitiesEnabled() { return ArgusConfigHolder.get().randomEntitiesEnabled(); }
        void setRandomEntitiesEnabled(boolean value) { update(b -> b.randomEntitiesEnabled(value)); }
        boolean getEntityTexturesEnabled() { return ArgusConfigHolder.get().entityTexturesEnabled(); }
        void setEntityTexturesEnabled(boolean value) { update(b -> b.entityTexturesEnabled(value)); }
        boolean getRandomBlockEntityTextures() { return ArgusConfigHolder.get().randomBlockEntityTextures(); }
        void setRandomBlockEntityTextures(boolean value) { update(b -> b.randomBlockEntityTextures(value)); }
        boolean getEntityEmissiveTextures() { return ArgusConfigHolder.get().entityEmissiveTextures(); }
        void setEntityEmissiveTextures(boolean value) { update(b -> b.entityEmissiveTextures(value)); }
        boolean getBlockEntityEmissiveTextures() { return ArgusConfigHolder.get().blockEntityEmissiveTextures(); }
        void setBlockEntityEmissiveTextures(boolean value) { update(b -> b.blockEntityEmissiveTextures(value)); }
        boolean getEntityTextureDebug() { return ArgusConfigHolder.get().entityTextureDebug(); }
        void setEntityTextureDebug(boolean value) { update(b -> b.entityTextureDebug(value)); }
        boolean getCustomEntityModelsEnabled() { return ArgusConfigHolder.get().customEntityModelsEnabled(); }
        void setCustomEntityModelsEnabled(boolean value) { update(b -> b.customEntityModelsEnabled(value)); }
        int getCustomAnimationMipmapDistance() { return ArgusConfigHolder.get().customAnimationMipmapDistance(); }
        void setCustomAnimationMipmapDistance(int value) { update(b -> b.customAnimationMipmapDistance(value)); }
        boolean getDetailsSkyEnabled() { return ArgusConfigHolder.get().detailsSkyEnabled(); }
        void setDetailsSkyEnabled(boolean value) { update(b -> b.detailsSkyEnabled(value)); }
        boolean getDetailsSunEnabled() { return ArgusConfigHolder.get().detailsSunEnabled(); }
        void setDetailsSunEnabled(boolean value) { update(b -> b.detailsSunEnabled(value)); }
        boolean getDetailsMoonEnabled() { return ArgusConfigHolder.get().detailsMoonEnabled(); }
        void setDetailsMoonEnabled(boolean value) { update(b -> b.detailsMoonEnabled(value)); }
        boolean getDetailsStarsEnabled() { return ArgusConfigHolder.get().detailsStarsEnabled(); }
        void setDetailsStarsEnabled(boolean value) { update(b -> b.detailsStarsEnabled(value)); }
        boolean getDetailsCloudsEnabled() { return ArgusConfigHolder.get().detailsCloudsEnabled(); }
        void setDetailsCloudsEnabled(boolean value) { update(b -> b.detailsCloudsEnabled(value)); }
        int getDetailsCloudHeight() { return ArgusConfigHolder.get().detailsCloudHeight(); }
        void setDetailsCloudHeight(int value) { update(b -> b.detailsCloudHeight(value)); }
        boolean getDetailsRainSnowEnabled() { return ArgusConfigHolder.get().detailsRainSnowEnabled(); }
        void setDetailsRainSnowEnabled(boolean value) { update(b -> b.detailsRainSnowEnabled(value)); }
        boolean getDetailsVignetteEnabled() { return ArgusConfigHolder.get().detailsVignetteEnabled(); }
        void setDetailsVignetteEnabled(boolean value) { update(b -> b.detailsVignetteEnabled(value)); }
        boolean getAnimationsEnabled() { return ArgusConfigHolder.get().animationsEnabled(); }
        void setAnimationsEnabled(boolean value) { update(b -> b.animationsEnabled(value)); }
        boolean getAnimationWater() { return ArgusConfigHolder.get().animationWater(); }
        void setAnimationWater(boolean value) { update(b -> b.animationWater(value)); }
        boolean getAnimationLava() { return ArgusConfigHolder.get().animationLava(); }
        void setAnimationLava(boolean value) { update(b -> b.animationLava(value)); }
        boolean getAnimationFire() { return ArgusConfigHolder.get().animationFire(); }
        void setAnimationFire(boolean value) { update(b -> b.animationFire(value)); }
        boolean getAnimationPortal() { return ArgusConfigHolder.get().animationPortal(); }
        void setAnimationPortal(boolean value) { update(b -> b.animationPortal(value)); }
        boolean getAnimationSculkSensor() { return ArgusConfigHolder.get().animationSculkSensor(); }
        void setAnimationSculkSensor(boolean value) { update(b -> b.animationSculkSensor(value)); }
        boolean getAnimationBlocks() { return ArgusConfigHolder.get().animationBlocks(); }
        void setAnimationBlocks(boolean value) { update(b -> b.animationBlocks(value)); }
        boolean getParticlesEnabled() { return ArgusConfigHolder.get().particlesEnabled(); }
        void setParticlesEnabled(boolean value) { update(b -> b.particlesEnabled(value)); }
        boolean getParticleRainSplash() { return ArgusConfigHolder.get().particleRainSplash(); }
        void setParticleRainSplash(boolean value) { update(b -> b.particleRainSplash(value)); }
        boolean getParticleBlockBreak() { return ArgusConfigHolder.get().particleBlockBreak(); }
        void setParticleBlockBreak(boolean value) { update(b -> b.particleBlockBreak(value)); }
        boolean getParticleBlockBreaking() { return ArgusConfigHolder.get().particleBlockBreaking(); }
        void setParticleBlockBreaking(boolean value) { update(b -> b.particleBlockBreaking(value)); }
        boolean getParticleExplosion() { return ArgusConfigHolder.get().particleExplosion(); }
        void setParticleExplosion(boolean value) { update(b -> b.particleExplosion(value)); }
        boolean getParticleWater() { return ArgusConfigHolder.get().particleWater(); }
        void setParticleWater(boolean value) { update(b -> b.particleWater(value)); }
        boolean getParticleSmoke() { return ArgusConfigHolder.get().particleSmoke(); }
        void setParticleSmoke(boolean value) { update(b -> b.particleSmoke(value)); }
        boolean getParticlePotion() { return ArgusConfigHolder.get().particlePotion(); }
        void setParticlePotion(boolean value) { update(b -> b.particlePotion(value)); }
        boolean getParticlePortal() { return ArgusConfigHolder.get().particlePortal(); }
        void setParticlePortal(boolean value) { update(b -> b.particlePortal(value)); }
        boolean getParticleFlame() { return ArgusConfigHolder.get().particleFlame(); }
        void setParticleFlame(boolean value) { update(b -> b.particleFlame(value)); }
        boolean getParticleRedstone() { return ArgusConfigHolder.get().particleRedstone(); }
        void setParticleRedstone(boolean value) { update(b -> b.particleRedstone(value)); }
        boolean getParticleDripping() { return ArgusConfigHolder.get().particleDripping(); }
        void setParticleDripping(boolean value) { update(b -> b.particleDripping(value)); }
        boolean getParticleFirework() { return ArgusConfigHolder.get().particleFirework(); }
        void setParticleFirework(boolean value) { update(b -> b.particleFirework(value)); }
        boolean getFogEnabled() { return ArgusConfigHolder.get().fogEnabled(); }
        void setFogEnabled(boolean value) { update(b -> b.fogEnabled(value)); }
        boolean getFogWater() { return ArgusConfigHolder.get().fogWater(); }
        void setFogWater(boolean value) { update(b -> b.fogWater(value)); }
        boolean getFogLava() { return ArgusConfigHolder.get().fogLava(); }
        void setFogLava(boolean value) { update(b -> b.fogLava(value)); }
        boolean getFogPowderSnow() { return ArgusConfigHolder.get().fogPowderSnow(); }
        void setFogPowderSnow(boolean value) { update(b -> b.fogPowderSnow(value)); }
        boolean getFogAir() { return ArgusConfigHolder.get().fogAir(); }
        void setFogAir(boolean value) { update(b -> b.fogAir(value)); }
        boolean getEntityShadowsEnabled() { return ArgusConfigHolder.get().entityShadowsEnabled(); }
        void setEntityShadowsEnabled(boolean value) { update(b -> b.entityShadowsEnabled(value)); }
        boolean getEntityNameTagsEnabled() { return ArgusConfigHolder.get().entityNameTagsEnabled(); }
        void setEntityNameTagsEnabled(boolean value) { update(b -> b.entityNameTagsEnabled(value)); }
        boolean getEntityPlayerNameTags() { return ArgusConfigHolder.get().entityPlayerNameTags(); }
        void setEntityPlayerNameTags(boolean value) { update(b -> b.entityPlayerNameTags(value)); }
        boolean getEntityItemFrames() { return ArgusConfigHolder.get().entityItemFrames(); }
        void setEntityItemFrames(boolean value) { update(b -> b.entityItemFrames(value)); }
        boolean getEntityPaintings() { return ArgusConfigHolder.get().entityPaintings(); }
        void setEntityPaintings(boolean value) { update(b -> b.entityPaintings(value)); }
        boolean getEntityPistonAnimations() { return ArgusConfigHolder.get().entityPistonAnimations(); }
        void setEntityPistonAnimations(boolean value) { update(b -> b.entityPistonAnimations(value)); }
        boolean getEntityBeaconBeam() { return ArgusConfigHolder.get().entityBeaconBeam(); }
        void setEntityBeaconBeam(boolean value) { update(b -> b.entityBeaconBeam(value)); }
        boolean getEntityLimitBeaconBeamHeight() { return ArgusConfigHolder.get().entityLimitBeaconBeamHeight(); }
        void setEntityLimitBeaconBeamHeight(boolean value) { update(b -> b.entityLimitBeaconBeamHeight(value)); }
        boolean getEntityEnchantingTableBook() { return ArgusConfigHolder.get().entityEnchantingTableBook(); }
        void setEntityEnchantingTableBook(boolean value) { update(b -> b.entityEnchantingTableBook(value)); }
        boolean getShowFps() { return ArgusConfigHolder.get().showFps(); }
        void setShowFps(boolean value) { update(b -> b.showFps(value)); }
        boolean getShowFpsExtended() { return ArgusConfigHolder.get().showFpsExtended(); }
        void setShowFpsExtended(boolean value) { update(b -> b.showFpsExtended(value)); }
        boolean getShowCoords() { return ArgusConfigHolder.get().showCoords(); }
        void setShowCoords(boolean value) { update(b -> b.showCoords(value)); }
        OverlayCorner getOverlayCorner() { return ArgusConfigHolder.get().overlayCorner(); }
        void setOverlayCorner(OverlayCorner value) { update(b -> b.overlayCorner(value)); }
        TextContrast getTextContrast() { return ArgusConfigHolder.get().textContrast(); }
        void setTextContrast(TextContrast value) { update(b -> b.textContrast(value)); }
        boolean getSteadyDebugHud() { return ArgusConfigHolder.get().steadyDebugHud(); }
        void setSteadyDebugHud(boolean value) { update(b -> b.steadyDebugHud(value)); }
        int getSteadyDebugHudRefreshInterval() { return ArgusConfigHolder.get().steadyDebugHudRefreshInterval(); }
        void setSteadyDebugHudRefreshInterval(int value) { update(b -> b.steadyDebugHudRefreshInterval(value)); }
        boolean getToastAdvancement() { return ArgusConfigHolder.get().toastAdvancement(); }
        void setToastAdvancement(boolean value) { update(b -> b.toastAdvancement(value)); }
        boolean getToastRecipe() { return ArgusConfigHolder.get().toastRecipe(); }
        void setToastRecipe(boolean value) { update(b -> b.toastRecipe(value)); }
        boolean getToastSystem() { return ArgusConfigHolder.get().toastSystem(); }
        void setToastSystem(boolean value) { update(b -> b.toastSystem(value)); }
        boolean getToastTutorial() { return ArgusConfigHolder.get().toastTutorial(); }
        void setToastTutorial(boolean value) { update(b -> b.toastTutorial(value)); }
        boolean getInstantSneak() { return ArgusConfigHolder.get().instantSneak(); }
        void setInstantSneak(boolean value) { update(b -> b.instantSneak(value)); }
        FullscreenMode getFullscreenMode() { return ArgusConfigHolder.get().fullscreenMode(); }
        void setFullscreenMode(FullscreenMode value) { update(b -> b.fullscreenMode(value)); }
        boolean getBiomeColorsEnabled() { return ArgusConfigHolder.get().biomeColorsEnabled(); }
        void setBiomeColorsEnabled(boolean value) { update(b -> b.biomeColorsEnabled(value)); }
        boolean getSkyColorsEnabled() { return ArgusConfigHolder.get().skyColorsEnabled(); }
        void setSkyColorsEnabled(boolean value) { update(b -> b.skyColorsEnabled(value)); }
        BetterGrassMode getBetterGrassMode() { return ArgusConfigHolder.get().betterGrassMode(); }
        void setBetterGrassMode(BetterGrassMode value) { update(b -> b.betterGrassMode(value)); }
        boolean getBetterGrassIgnoreResourcePack() { return ArgusConfigHolder.get().betterGrassIgnoreResourcePack(); }
        void setBetterGrassIgnoreResourcePack(boolean value) { update(b -> b.betterGrassIgnoreResourcePack(value)); }
        boolean getBetterGrassGrassBlock() { return ArgusConfigHolder.get().betterGrassGrassBlock(); }
        void setBetterGrassGrassBlock(boolean value) { update(b -> b.betterGrassGrassBlock(value)); }
        boolean getBetterGrassSnowyGrassBlock() { return ArgusConfigHolder.get().betterGrassSnowyGrassBlock(); }
        void setBetterGrassSnowyGrassBlock(boolean value) { update(b -> b.betterGrassSnowyGrassBlock(value)); }
        boolean getBetterGrassDirtPath() { return ArgusConfigHolder.get().betterGrassDirtPath(); }
        void setBetterGrassDirtPath(boolean value) { update(b -> b.betterGrassDirtPath(value)); }
        boolean getBetterGrassFarmland() { return ArgusConfigHolder.get().betterGrassFarmland(); }
        void setBetterGrassFarmland(boolean value) { update(b -> b.betterGrassFarmland(value)); }
        boolean getBetterGrassMycelium() { return ArgusConfigHolder.get().betterGrassMycelium(); }
        void setBetterGrassMycelium(boolean value) { update(b -> b.betterGrassMycelium(value)); }
        boolean getBetterGrassPodzol() { return ArgusConfigHolder.get().betterGrassPodzol(); }
        void setBetterGrassPodzol(boolean value) { update(b -> b.betterGrassPodzol(value)); }
        boolean getBetterGrassCrimsonNylium() { return ArgusConfigHolder.get().betterGrassCrimsonNylium(); }
        void setBetterGrassCrimsonNylium(boolean value) { update(b -> b.betterGrassCrimsonNylium(value)); }
        boolean getBetterGrassWarpedNylium() { return ArgusConfigHolder.get().betterGrassWarpedNylium(); }
        void setBetterGrassWarpedNylium(boolean value) { update(b -> b.betterGrassWarpedNylium(value)); }
    }
}
