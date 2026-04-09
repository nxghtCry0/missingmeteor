/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc.swarm;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Util;

public class Swarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgUSS = settings.createGroup("State Sync");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("What type of client to run.")
        .defaultValue(Mode.Host)
        .build()
    );

    private final Setting<String> ipAddress = sgGeneral.add(new StringSetting.Builder()
        .name("ip")
        .description("The IP address of the host server.")
        .defaultValue("localhost")
        .visible(() -> mode.get() == Mode.Worker)
        .build()
    );

    private final Setting<Integer> serverPort = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("The port used for connections.")
        .defaultValue(6969)
        .range(1, 65535)
        .noSlider()
        .build()
    );

    // USS Settings

    private final Setting<Boolean> ussEnabled = sgUSS.add(new BoolSetting.Builder()
        .name("uss-enabled")
        .description("Enable Universal State Synchronization (position + block claims).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> ussProximityAvoid = sgUSS.add(new DoubleSetting.Builder()
        .name("proximity-avoid")
        .description("How close workers must be before avoiding each other's targets.")
        .defaultValue(5.0)
        .min(0)
        .sliderMax(16)
        .visible(ussEnabled::get)
        .build()
    );

    private final Setting<Integer> ussSyncRate = sgUSS.add(new IntSetting.Builder()
        .name("sync-rate")
        .description("Ticks between full claim table broadcasts.")
        .defaultValue(10)
        .min(1)
        .sliderMax(100)
        .visible(ussEnabled::get)
        .build()
    );

    // Inventory Settings

    private final Setting<Boolean> invSyncEnabled = sgInventory.add(new BoolSetting.Builder()
        .name("inv-sync")
        .description("Enable inventory synchronization across the swarm.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> invSyncRate = sgInventory.add(new IntSetting.Builder()
        .name("inv-sync-rate")
        .description("Ticks between inventory syncs.")
        .defaultValue(100)
        .min(20)
        .sliderMax(600)
        .visible(invSyncEnabled::get)
        .build()
    );

    private final Setting<Boolean> invHotbarOnly = sgInventory.add(new BoolSetting.Builder()
        .name("hotbar-only")
        .description("Only sync hotbar slots to save bandwidth.")
        .defaultValue(true)
        .visible(invSyncEnabled::get)
        .build()
    );

    // Runtime state
    public SwarmHost host;
    public SwarmWorker worker;

    public Swarm() {
        super(Categories.Misc, "swarm", "HiveMind — control multiple instances with state sync, groups, and more.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WHorizontalList b = list.add(theme.horizontalList()).expandX().widget();

        WButton start = b.add(theme.button("Start")).expandX().widget();
        start.action = () -> {
            if (!isActive()) return;
            close();
            if (mode.get() == Mode.Host) {
                host = new SwarmHost(serverPort.get(), this);
                applySettingsToHost();
            } else {
                worker = new SwarmWorker(ipAddress.get(), serverPort.get());
                applySettingsToWorker();
            }
        };

        WButton stop = b.add(theme.button("Stop")).expandX().widget();
        stop.action = this::close;

        WButton guide = list.add(theme.button("Guide")).expandX().widget();
        guide.action = () -> Util.getOperatingSystem().open("https://github.com/nxghtCry0/missingmeteor/wiki/HiveMind-Guide");

        return list;
    }

    @Override
    public String getInfoString() {
        if (isHost()) return String.format("Host | %d workers", host.getConnectionCount());
        if (isWorker()) return String.format("#%d %s [%s]", worker.workerId,
            MeteorClient.mc.player != null ? MeteorClient.mc.player.getName().getString() : "?",
            worker.group);
        return mode.get().name();
    }

    @Override
    public void onActivate() {
        close();
    }

    @Override
    public void onDeactivate() {
        close();
    }

    public void close() {
        try {
            if (host != null) {
                host.disconnect();
                host = null;
            }
            if (worker != null) {
                worker.disconnect();
                worker = null;
            }
        } catch (Exception ignored) {}
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        toggle();
    }

    @Override
    public void toggle() {
        close();
        super.toggle();
    }

    public boolean isHost() {
        return mode.get() == Mode.Host && host != null && !host.isInterrupted();
    }

    public boolean isWorker() {
        return mode.get() == Mode.Worker && worker != null && worker.connected;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (isWorker()) {
            worker.tick();
        }
        if (isHost()) {
            host.tick();
        }
    }

    private void applySettingsToHost() {
        if (host == null) return;
        host.ussEnabled = ussEnabled.get();
        host.ussSyncRate = ussSyncRate.get();
        host.ussProximityAvoid = ussProximityAvoid.get();
        host.invSyncEnabled = invSyncEnabled.get();
        host.invSyncRate = invSyncRate.get();
    }

    public void applySettingsToWorker() {
        if (worker == null) return;
        worker.ussEnabled = ussEnabled.get();
        worker.ussSyncRate = ussSyncRate.get();
        worker.invSyncEnabled = invSyncEnabled.get();
        worker.invSyncRate = invSyncRate.get();
        worker.invHotbarOnly = invHotbarOnly.get();
    }

    public enum Mode {
        Host,
        Worker
    }
}
