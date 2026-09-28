package com.argus.client.benchmark;

import com.argus.ctm.CtmOverlayDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Dev-only in-game benchmark driver for repeatable loader comparisons.
 *
 * <p>Purpose: when explicitly enabled with
 * {@code -Dargus.benchmark.autopilot=true}, opens a local world, closes menus,
 * rotates the camera, and holds movement/use/attack keys through a deterministic
 * route. Before each run it recreates one dedicated benchmark world from
 * scratch with a stable seed, so Fabric and NeoForge compare the same fresh
 * world-generation and render workload instead of inheriting previous save
 * state. By default it also closes the client after the last sample, so Gradle
 * benchmark runs return without manual cleanup.
 *
 * <p>Threading: called from the client tick event on the client thread only.
 *
 * <p>Performance: disabled cost is one static-final boolean branch. This class
 * is intended for development runs, not release benchmarking.
 */
public final class ArgusBenchmarkDriver {

    private static final boolean ENABLED =
            Boolean.getBoolean("argus.benchmark.autopilot");
    private static final Logger LOGGER =
            LoggerFactory.getLogger("argus/benchmark-driver");
    private static final int MAX_TICKS = Integer.getInteger(
            "argus.benchmark.autopilotTicks", 900);
    private static final boolean CLOSE_ON_COMPLETE = Boolean.parseBoolean(
            System.getProperty("argus.benchmark.closeOnComplete", "true"));
    private static final int CLOSE_DELAY_TICKS = Integer.getInteger(
            "argus.benchmark.closeDelayTicks", 40);
    private static final int SETTLE_TICKS = Integer.getInteger(
            "argus.benchmark.settleTicks", 100);
    private static final double TARGET_X = Double.parseDouble(
            System.getProperty("argus.benchmark.targetX", "0.0"));
    private static final double TARGET_Y = Double.parseDouble(
            System.getProperty("argus.benchmark.targetY", "125.0"));
    private static final double TARGET_Z = Double.parseDouble(
            System.getProperty("argus.benchmark.targetZ", "0.0"));
    private static final int LOG_INTERVAL_TICKS = 100;
    private static final int LOW_FPS_THRESHOLD = Integer.getInteger(
            "argus.benchmark.lowFpsThreshold", 120);
    private static final int LOW_FPS_SAMPLE_LIMIT = Integer.getInteger(
            "argus.benchmark.lowFpsSampleLimit", 24);
    private static final String DEFAULT_WORLD = "ArgusBenchmark";
    private static final long DEFAULT_SEED = 329562103L;
    private static final String METHODOLOGY =
            "Argus dev autopilot recreates a fixed-seed local world, "
                    + "enters it, teleports the player to configured benchmark "
                    + "coordinates, waits for the settle window so the player "
                    + "lands and nearby chunks can build, resets benchmark "
                    + "buckets, then drives deterministic camera and movement "
                    + "input for the configured tick count while Argus "
                    + "renderer buckets and per-tick FPS are sampled.";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    private static boolean openAttempted;
    private static boolean started;
    private static boolean teleported;
    private static boolean complete;
    private static boolean reportWritten;
    private static boolean closing;
    private static int closeDelayTicks;
    private static int settleTicks;
    private static int ticks;
    private static int fpsMin = Integer.MAX_VALUE;
    private static int fpsMax;
    private static long fpsSum;
    private static int fpsSamples;
    private static int[] fpsValues = new int[Math.max(1, MAX_TICKS)];
    private static int lowFpsSamples;
    private static int lowFpsSegments;
    private static boolean inLowFpsSegment;
    private static int currentLowFpsSegmentStart;
    private static int worstFpsTick;
    private static int worstFps = Integer.MAX_VALUE;
    private static int[] lowFpsTicks = new int[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static int[] lowFpsValues = new int[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static String[] lowFpsPositions = new String[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsProcessQuadMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsCtmMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static double[] lowFpsResolveMs = new double[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static long[] lowFpsGcCount = new long[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static long[] lowFpsGcMillis = new long[Math.max(1, LOW_FPS_SAMPLE_LIMIT)];
    private static int lowFpsStoredSamples;
    private static long startGcCount;
    private static long startGcMillis;
    private static long startHeapUsed;
    private static long peakHeapUsed;

    private ArgusBenchmarkDriver() {
    }

    /**
     * Advances the benchmark driver by one client tick.
     *
     * @param minecraft active client instance
     */
    public static void tick(Minecraft minecraft) {
        if (!ENABLED || minecraft == null) {
            return;
        }
        if (complete) {
            closeAfterCompletion(minecraft);
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            openWorldIfNeeded(minecraft);
            return;
        }

        if (!teleported) {
            teleportToBenchmarkPosition(minecraft);
            return;
        }
        if (settleTicks > 0) {
            settleBeforeBenchmark(minecraft);
            return;
        }

        if (!started) {
            started = true;
            ticks = 0;
            resetFpsSamples();
            ArgusBenchmark.resetTotals();
            CtmCandidateAnalysis.reset();
            CtmOverlayDiagnostics.reset();
            captureRuntimeBaselines();
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.driver_started",
                    worldLabel(minecraft),
                    positionLabel(minecraft.player)
            ).getString());
        }

        ticks++;
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }

        driveCamera(minecraft.player, ticks);
        driveMovement(minecraft, ticks);
        sampleFps(minecraft);

        if (ticks % LOG_INTERVAL_TICKS == 0) {
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.driver_tick",
                    ticks,
                    minecraft.getFps(),
                    fpsMin == Integer.MAX_VALUE ? 0 : fpsMin,
                    fpsSamples == 0 ? 0 : fpsSum / fpsSamples,
                    fpsMax,
                    positionLabel(minecraft.player)
            ).getString());
        }

        if (ticks >= MAX_TICKS) {
            releaseKeys(minecraft);
            complete = true;
            closeDelayTicks = CLOSE_DELAY_TICKS;
            writeReport(minecraft);
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.driver_complete",
                    ticks,
                    fpsMin == Integer.MAX_VALUE ? 0 : fpsMin,
                    fpsSamples == 0 ? 0 : fpsSum / fpsSamples,
                    fpsMax,
                    fpsSamples,
                    CLOSE_ON_COMPLETE
            ).getString());
        }
    }

    private static void closeAfterCompletion(Minecraft minecraft) {
        if (!CLOSE_ON_COMPLETE || closing) {
            return;
        }
        if (closeDelayTicks > 0) {
            closeDelayTicks--;
            return;
        }
        closing = true;
        releaseKeys(minecraft);
        LOGGER.info("{}", Component.translatable(
                "argus.info.benchmark.requesting_stop"
        ).getString());
        minecraft.stop();
    }

    private static void openWorldIfNeeded(Minecraft minecraft) {
        if (openAttempted) {
            return;
        }
        String world = configuredWorld();
        if (world == null || world.isBlank()) {
            openAttempted = true;
            LOGGER.warn("{}", Component.translatable(
                    "argus.warn.benchmark.no_world_configured"
            ).getString());
            return;
        }
        openAttempted = true;
        recreateBenchmarkWorld(minecraft, world);
    }

    private static void recreateBenchmarkWorld(Minecraft minecraft,
                                                String world) {
        Path saves = minecraft.getLevelSource()
                .getBaseDir()
                .toAbsolutePath()
                .normalize();
        Path target = saves.resolve(world).toAbsolutePath().normalize();
        if (!target.startsWith(saves)) {
            LOGGER.warn("{}", Component.translatable(
                    "argus.warn.benchmark.unsafe_world_id",
                    world
            ).getString());
            return;
        }
        try {
            deleteDirectory(target);
        } catch (IOException e) {
            LOGGER.warn("{}", Component.translatable(
                    "argus.warn.benchmark.world_reset_failed",
                    world,
                    target,
                    e.getMessage()
            ).getString());
            return;
        }

        long seed = configuredSeed();
        LevelSettings settings = new LevelSettings(
                world,
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(
                        Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(seed, true, false);
        LOGGER.info("{}", Component.translatable(
                "argus.info.benchmark.creating_world",
                world,
                seed,
                target
        ).getString());
        minecraft.createWorldOpenFlows()
                .createFreshLevel(world, settings, options,
                        WorldPresets::createNormalWorldDimensions,
                        new TitleScreen());
    }

    private static void teleportToBenchmarkPosition(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        teleported = true;
        settleTicks = SETTLE_TICKS;
        releaseKeys(minecraft);
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
        String command = String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f",
                TARGET_X, TARGET_Y, TARGET_Z);
        minecraft.player.connection.sendCommand(command);
        LOGGER.info("{}", Component.translatable(
                "argus.info.benchmark.teleport",
                command,
                settleTicks
        ).getString());
    }

    private static void settleBeforeBenchmark(Minecraft minecraft) {
        releaseKeys(minecraft);
        if (minecraft.gui.screen() != null) {
            minecraft.gui.setScreen(null);
        }
        settleTicks--;
        if (settleTicks == 0 && minecraft.player != null) {
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.settle_complete",
                    positionLabel(minecraft.player)
            ).getString());
        }
    }

    private static String configuredWorld() {
        String configured = System.getProperty("argus.benchmark.world", "")
                .trim();
        if (configured.isEmpty()) {
            configured = DEFAULT_WORLD;
        }
        return configured.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static long configuredSeed() {
        String configured = System.getProperty("argus.benchmark.seed", "")
                .trim();
        if (configured.isEmpty()) {
            return DEFAULT_SEED;
        }
        return WorldOptions.parseSeed(configured).orElse(DEFAULT_SEED);
    }

    private static void deleteDirectory(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(target)) {
            Path[] ordered = paths
                    .sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void driveCamera(LocalPlayer player, int tick) {
        float yaw = player.getYRot() + 2.0F;
        float pitch = -8.0F + (float) Math.sin(tick / 35.0D) * 18.0F;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    private static void driveMovement(Minecraft minecraft, int tick) {
        boolean forward = tick < MAX_TICKS - 80;
        boolean right = tick % 240 < 120;
        boolean left = !right && tick < MAX_TICKS - 80;
        boolean jump = tick % 90 < 8;
        boolean use = tick > 100 && tick % 70 < 5;
        boolean attack = tick > 140 && tick % 110 < 5;

        minecraft.options.keyUp.setDown(forward);
        minecraft.options.keyRight.setDown(right);
        minecraft.options.keyLeft.setDown(left);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyJump.setDown(jump);
        minecraft.options.keyUse.setDown(use);
        minecraft.options.keyAttack.setDown(attack);
    }

    private static void releaseKeys(Minecraft minecraft) {
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyAttack.setDown(false);
    }

    private static void resetFpsSamples() {
        fpsMin = Integer.MAX_VALUE;
        fpsMax = 0;
        fpsSum = 0L;
        fpsSamples = 0;
        lowFpsSamples = 0;
        lowFpsSegments = 0;
        inLowFpsSegment = false;
        currentLowFpsSegmentStart = 0;
        worstFpsTick = 0;
        worstFps = Integer.MAX_VALUE;
        lowFpsStoredSamples = 0;
        if (fpsValues.length < Math.max(1, MAX_TICKS)) {
            fpsValues = new int[Math.max(1, MAX_TICKS)];
        }
        int lowLimit = Math.max(1, LOW_FPS_SAMPLE_LIMIT);
        if (lowFpsTicks.length < lowLimit) {
            lowFpsTicks = new int[lowLimit];
            lowFpsValues = new int[lowLimit];
            lowFpsPositions = new String[lowLimit];
            lowFpsProcessQuadMs = new double[lowLimit];
            lowFpsCtmMs = new double[lowLimit];
            lowFpsResolveMs = new double[lowLimit];
            lowFpsGcCount = new long[lowLimit];
            lowFpsGcMillis = new long[lowLimit];
        }
    }

    private static void sampleFps(Minecraft minecraft) {
        int fps = minecraft.getFps();
        if (fps <= 0) {
            return;
        }
        long heapUsed = usedHeapBytes();
        peakHeapUsed = Math.max(peakHeapUsed, heapUsed);
        if (fpsSamples == fpsValues.length) {
            fpsValues = Arrays.copyOf(fpsValues, fpsValues.length * 2);
        }
        fpsValues[fpsSamples] = fps;
        fpsMin = Math.min(fpsMin, fps);
        fpsMax = Math.max(fpsMax, fps);
        fpsSum += fps;
        fpsSamples++;
        if (fps < worstFps) {
            worstFps = fps;
            worstFpsTick = ticks;
        }
        if (fps <= LOW_FPS_THRESHOLD) {
            lowFpsSamples++;
            if (!inLowFpsSegment) {
                inLowFpsSegment = true;
                currentLowFpsSegmentStart = ticks;
                lowFpsSegments++;
            }
            rememberLowFpsSample(minecraft, fps);
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.low_fps_sample",
                    ticks,
                    fps,
                    LOW_FPS_THRESHOLD,
                    minecraft.player == null ? "none" : positionLabel(minecraft.player),
                    bucketSummary(),
                    gcSummary(),
                    heapUsed / (1024L * 1024L)
            ).getString());
        } else if (inLowFpsSegment) {
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.low_fps_segment_end",
                    currentLowFpsSegmentStart,
                    ticks - 1,
                    LOW_FPS_THRESHOLD
            ).getString());
            inLowFpsSegment = false;
        }
    }

    private static void captureRuntimeBaselines() {
        startGcCount = gcCollectionCount();
        startGcMillis = gcCollectionMillis();
        startHeapUsed = usedHeapBytes();
        peakHeapUsed = startHeapUsed;
    }

    private static void rememberLowFpsSample(Minecraft minecraft, int fps) {
        int slot = -1;
        if (lowFpsStoredSamples < lowFpsTicks.length) {
            slot = lowFpsStoredSamples++;
        } else {
            int weakestSlot = 0;
            int weakestFps = lowFpsValues[0];
            for (int i = 1; i < lowFpsValues.length; i++) {
                if (lowFpsValues[i] > weakestFps) {
                    weakestFps = lowFpsValues[i];
                    weakestSlot = i;
                }
            }
            if (fps < weakestFps) {
                slot = weakestSlot;
            }
        }
        if (slot < 0) {
            return;
        }
        ArgusBenchmark.BucketSnapshot[] buckets = ArgusBenchmark
                .snapshotTotals();
        lowFpsTicks[slot] = ticks;
        lowFpsValues[slot] = fps;
        lowFpsPositions[slot] = minecraft.player == null
                ? ""
                : positionLabel(minecraft.player);
        lowFpsProcessQuadMs[slot] = bucketMillis(buckets,
                "sodium.process_quad");
        lowFpsCtmMs[slot] = bucketMillis(buckets, "sodium.ctm");
        lowFpsResolveMs[slot] = bucketMillis(buckets, "ctm.resolve");
        lowFpsGcCount[slot] = gcCollectionCount();
        lowFpsGcMillis[slot] = gcCollectionMillis();
        sortLowFpsSamples();
    }

    private static void sortLowFpsSamples() {
        for (int i = 1; i < lowFpsStoredSamples; i++) {
            int tick = lowFpsTicks[i];
            int fps = lowFpsValues[i];
            String position = lowFpsPositions[i];
            double process = lowFpsProcessQuadMs[i];
            double ctm = lowFpsCtmMs[i];
            double resolve = lowFpsResolveMs[i];
            long gcCount = lowFpsGcCount[i];
            long gcMillis = lowFpsGcMillis[i];
            int j = i - 1;
            while (j >= 0 && lowFpsValues[j] > fps) {
                lowFpsTicks[j + 1] = lowFpsTicks[j];
                lowFpsValues[j + 1] = lowFpsValues[j];
                lowFpsPositions[j + 1] = lowFpsPositions[j];
                lowFpsProcessQuadMs[j + 1] = lowFpsProcessQuadMs[j];
                lowFpsCtmMs[j + 1] = lowFpsCtmMs[j];
                lowFpsResolveMs[j + 1] = lowFpsResolveMs[j];
                lowFpsGcCount[j + 1] = lowFpsGcCount[j];
                lowFpsGcMillis[j + 1] = lowFpsGcMillis[j];
                j--;
            }
            lowFpsTicks[j + 1] = tick;
            lowFpsValues[j + 1] = fps;
            lowFpsPositions[j + 1] = position;
            lowFpsProcessQuadMs[j + 1] = process;
            lowFpsCtmMs[j + 1] = ctm;
            lowFpsResolveMs[j + 1] = resolve;
            lowFpsGcCount[j + 1] = gcCount;
            lowFpsGcMillis[j + 1] = gcMillis;
        }
    }

    private static void writeReport(Minecraft minecraft) {
        if (reportWritten) {
            return;
        }
        reportWritten = true;
        try {
            Path reportsDir = Path.of("build", "reports", "benchmarks");
            Files.createDirectories(reportsDir);
            String timestamp = FILE_TIMESTAMP.format(Instant.now());
            Path reportFile = reportsDir.resolve(
                    "benchmark-driver-" + timestamp + ".md");
            StringBuilder report = new StringBuilder();
            report.append("# Argus Benchmark Run\n\n");
            report.append("- **World:** ").append(worldLabel(minecraft)).append('\n');
            report.append("- **Ticks:** ").append(ticks).append('\n');
            report.append("- **FPS (Min / Avg / Max):** ")
                    .append(fpsMin == Integer.MAX_VALUE ? 0 : fpsMin).append(" / ")
                    .append(fpsSamples == 0 ? 0 : fpsSum / fpsSamples).append(" / ")
                    .append(fpsMax).append('\n');
            report.append("- **Worst Tick:** ").append(worstFpsTick)
                    .append(" (").append(worstFps == Integer.MAX_VALUE ? 0 : worstFps).append(" FPS)\n");
            report.append("- **Heap Peak:** ")
                    .append(peakHeapUsed / (1024L * 1024L)).append(" MiB\n");
            report.append("\n## Methodology\n").append(METHODOLOGY).append("\n");
            Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
            LOGGER.info("{}", Component.translatable(
                    "argus.info.benchmark.report_saved",
                    reportFile.toAbsolutePath()
            ).getString());
        } catch (IOException e) {
            LOGGER.warn("{}", Component.translatable(
                    "argus.warn.benchmark.report_failed",
                    e.getMessage()
            ).getString());
        }
    }

    private static String worldLabel(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "none";
        }
        return minecraft.level.dimension().location().toString();
    }

    private static String positionLabel(LocalPlayer player) {
        if (player == null) {
            return "none";
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f",
                player.getX(), player.getY(), player.getZ());
    }

    private static long usedHeapBytes() {
        MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
        return bean.getHeapMemoryUsage().getUsed();
    }

    private static long gcCollectionCount() {
        long count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            if (c > 0) {
                count += c;
            }
        }
        return count;
    }

    private static long gcCollectionMillis() {
        long time = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long t = bean.getCollectionTime();
            if (t > 0) {
                time += t;
            }
        }
        return time;
    }

    private static String gcSummary() {
        long dCount = gcCollectionCount() - startGcCount;
        long dMillis = gcCollectionMillis() - startGcMillis;
        return "count=" + dCount + ",timeMs=" + dMillis;
    }

    private static double bucketMillis(ArgusBenchmark.BucketSnapshot[] buckets,
                                       String name) {
        for (ArgusBenchmark.BucketSnapshot b : buckets) {
            if (b.name().equals(name)) {
                return b.totalMillis();
            }
        }
        return 0.0D;
    }

    private static String bucketSummary() {
        ArgusBenchmark.BucketSnapshot[] buckets = ArgusBenchmark.snapshotTotals();
        double processQuad = bucketMillis(buckets, "sodium.process_quad");
        double ctm = bucketMillis(buckets, "sodium.ctm");
        double resolve = bucketMillis(buckets, "ctm.resolve");
        return String.format(Locale.ROOT,
                "processQuad=%.2fms,ctm=%.2fms,resolve=%.2fms",
                processQuad, ctm, resolve);
    }
}
