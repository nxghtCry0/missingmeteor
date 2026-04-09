/*
 * This file is part of the MissingMeteor distribution (https://github.com/nxghtCry0/missingmeteor).
 * Copyright (c) nxghtCry0.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.Swarm;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.SwarmWorkerInfo;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * 2D radar/visualizer HUD element for HiveMind swarm.
 * Shows all connected worker positions as colored blips on a top-down radar.
 */
public class SwarmRadarHud extends HudElement {
    public static final HudElementInfo<SwarmRadarHud> INFO = new HudElementInfo<>(Hud.GROUP, "swarm-radar",
        "Displays a 2D radar of all HiveMind swarm worker positions.", SwarmRadarHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("Scale");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    // General

    private final Setting<Double> radarSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("size")
        .description("Size of the radar in pixels.")
        .defaultValue(120)
        .min(50)
        .sliderRange(50, 300)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Blocks per pixel on the radar.")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(2.0)
        .build()
    );

    private final Setting<Boolean> showNames = sgGeneral.add(new BoolSetting.Builder()
        .name("show-names")
        .description("Show worker ID labels on the radar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showGroups = sgGeneral.add(new BoolSetting.Builder()
        .name("show-groups")
        .description("Color dots by group assignment.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showNorth = sgGeneral.add(new BoolSetting.Builder()
        .name("show-north")
        .description("Show north direction indicator.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("show-player")
        .description("Show your own position on the radar.")
        .defaultValue(true)
        .build()
    );

    // Colors

    private final Setting<SettingColor> playerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("player-color")
        .description("Color of the center player dot.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> dotColor = sgGeneral.add(new ColorSetting.Builder()
        .name("dot-color")
        .description("Default color for worker dots (when group colors are off).")
        .defaultValue(new SettingColor(0, 200, 255, 255))
        .build()
    );

    private final Setting<SettingColor> ringColor = sgGeneral.add(new ColorSetting.Builder()
        .name("ring-color")
        .description("Color of the radar ring outline.")
        .defaultValue(new SettingColor(100, 100, 100, 150))
        .build()
    );

    private final Setting<SettingColor> northColor = sgGeneral.add(new ColorSetting.Builder()
        .name("north-color")
        .description("Color of the north indicator.")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .build()
    );

    // Scale

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
        .name("custom-scale")
        .description("Applies a custom scale to this hud element.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> hudScale = sgScale.add(new DoubleSetting.Builder()
        .name("hud-scale")
        .description("Custom HUD scale.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays a background behind the radar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(0, 0, 0, 120))
        .build()
    );

    // Group color palette
    private static final SettingColor[] GROUP_COLORS = {
        new SettingColor(0, 200, 255, 255),   // Blue
        new SettingColor(255, 100, 100, 255),  // Red
        new SettingColor(100, 255, 100, 255),  // Green
        new SettingColor(255, 255, 0, 255),    // Yellow
        new SettingColor(255, 150, 0, 255),    // Orange
        new SettingColor(200, 100, 255, 255),  // Purple
        new SettingColor(255, 100, 255, 255),  // Pink
        new SettingColor(100, 255, 255, 255),  // Cyan
    };

    private final Map<String, SettingColor> groupColorCache = new LinkedHashMap<>();

    public SwarmRadarHud() {
        super(INFO);
        calculateSize();
    }

    private void calculateSize() {
        double size = radarSize.get();
        setSize(size, size);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;

        Swarm swarm = Modules.get().get(Swarm.class);
        boolean isHost = swarm.isHost();

        double size = radarSize.get();
        double centerX = x + size / 2;
        double centerY = y + size / 2;
        double radius = size / 2;
        double scaleValue = scale.get();

        // Background
        if (background.get()) {
            renderer.quad(x, y, size, size, backgroundColor.get());
        }

        // Outer ring
        renderer.quad(centerX - 1, y, 2, size, ringColor.get());
        renderer.quad(centerX - radius, centerY - 1, size, 2, ringColor.get());

        // Cross hairs (faint)
        Color crosshairColor = new SettingColor(60, 60, 60, 80);
        renderer.quad(centerX, y, 0.5, size, crosshairColor);
        renderer.quad(x, centerY, size, 0.5, crosshairColor);

        // North indicator
        if (showNorth.get()) {
            double northX = centerX;
            double northY = y + 4;
            renderer.text("N", northX - 3, northY, northColor.get(), true);
        }

        // Center player dot
        if (showPlayer.get()) {
            renderer.quad(centerX - 2, centerY - 2, 4, 4, playerColor.get());
        }

        // Worker dots
        if (isHost && swarm.host != null) {
            Collection<SwarmWorkerInfo> workers = swarm.host.getWorkers();
            double playerYaw = mc.player.getYaw();
            double playerX = mc.player.getX();
            double playerZ = mc.player.getZ();

            for (SwarmWorkerInfo info : workers) {
                if (!info.isAlive()) continue;

                double dx = info.x - playerX;
                double dz = info.z - playerZ;

                // Rotate relative to player facing
                double radians = Math.toRadians(-playerYaw);
                double rotX = (dx * Math.cos(radians)) - (dz * Math.sin(radians));
                double rotZ = (dx * Math.sin(radians)) + (dz * Math.cos(radians));

                // Scale and offset to radar position
                double dotX = centerX + (rotX / scaleValue);
                double dotY = centerY + (rotZ / scaleValue);

                // Check if within radar bounds
                if (dotX < x || dotX > x + size || dotY < y || dotY > y + size) continue;

                // Get color
                Color dotCol = showGroups.get() ? getGroupColor(info.group) : dotColor.get();
                double dotSize = 4;

                // Draw the dot
                renderer.quad(dotX - dotSize / 2, dotY - dotSize / 2, dotSize, dotSize, dotCol);

                // Draw name label
                if (showNames.get()) {
                    renderer.text(info.playerName, dotX + 4, dotY - 3, dotCol, true);
                }
            }
        }
    }

    private Color getGroupColor(String group) {
        // Check cache first
        if (groupColorCache.containsKey(group)) {
            return groupColorCache.get(group);
        }

        // Generate a stable color based on group name hash
        int hash = group.toLowerCase().hashCode();
        int colorIndex = Math.abs(hash) % GROUP_COLORS.length;
        SettingColor color = GROUP_COLORS[colorIndex];
        groupColorCache.put(group, color);
        return color;
    }

    @Override
    public void tick(HudRenderer renderer) {
        calculateSize();
    }

    private double getScale() {
        return customScale.get() ? hudScale.get() : Hud.get().getTextScale();
    }
}
