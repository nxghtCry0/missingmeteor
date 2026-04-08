/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

public class AutoSign extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("The tick delay between sign update packets.")
        .defaultValue(10)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<Boolean> randomText = sgGeneral.add(new BoolSetting.Builder()
        .name("random-text")
        .description("Fills each sign line with random characters instead of using the captured text.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> randomLength = sgGeneral.add(new IntSetting.Builder()
        .name("random-length")
        .description("The number of random characters per sign line.")
        .defaultValue(15)
        .range(1, 15)
        .sliderRange(1, 15)
        .visible(randomText::get)
        .build()
    );

    private final Setting<Boolean> randomUnicode = sgGeneral.add(new BoolSetting.Builder()
        .name("random-unicode")
        .description("Include unicode characters in the random text for more variety.")
        .defaultValue(false)
        .visible(randomText::get)
        .build()
    );

    private final Random random = new Random();
    private String[] text;
    private static final String ASCII_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:',.<>?/~`";
    private static final String UNICODE_CHARS = "\u4e00\u4e01\u4e02\u4e03\u4e04\u4e05\u4e06\u4e07\u4e08\u4e09\u4e0a\u4e0b\u4e0c\u4e0d\u4e0e\u4e0f\u4e10\u4e11\u4e12\u4e13\u4e14\u4e15\u4e16\u4e17\u4e18\u4e19\u4e1a\u4e1b\u4e1c\u4e1d\u4e1e\u4e1f\u4e20\u4e21\u4e22\u4e23\u4e24\u4e25\u4e26\u4e27\u4e28\u4e29\u4e2a\u4e2b\u4e2c\u4e2d\u4e2e\u4e2f\u4e30\u4e31\u4e32\u4e33\u4e34\u4e35\u4e36\u4e37\u4e38\u4e39\u4e3a\u4e3b\u4e3c\u4e3d\u4e3e\u4e3f\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff";

    // Some servers (e.g., 2b2t) don't like the sign packet being sent too soon after the swing or block click packets, so queue them.
    // Delaying by sleeping in the event handler may be fine for a single sign, but would visibly lag the UI at a larger scale.
    private final Queue<UpdateSignC2SPacket> queue = new ArrayDeque<>();
    private int timer = 0;

    public AutoSign() {
        super(Categories.World, "auto-sign", "Automatically writes signs. The first sign's text will be used.");
    }

    @Override
    public void onDeactivate() {
        text = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Adding a new packet with the timer close to the threshold could lead to it being sent too fast relative to
        // the swing/click packets, but only if there isn't another packet ahead of it to reset the timer, so always
        // keep it reset if the queue is empty.
        if (mc.player == null || queue.peek() == null) {
            timer = 0;
            return;
        }

        if (timer < delay.get()) {
            timer++;
            return;
        }

        mc.player.networkHandler.sendPacket(queue.poll());

        timer = 0;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (!(event.packet instanceof UpdateSignC2SPacket)) return;

        text = ((UpdateSignC2SPacket) event.packet).getText();
    }

    private String[] generateRandomText() {
        String[] lines = new String[4];
        String charset = randomUnicode.get() ? ASCII_CHARS + UNICODE_CHARS : ASCII_CHARS;
        int length = randomLength.get();
        for (int i = 0; i < 4; i++) {
            StringBuilder sb = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                sb.append(charset.charAt(random.nextInt(charset.length())));
            }
            lines[i] = sb.toString();
        }
        return lines;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof AbstractSignEditScreen)) return;

        if (randomText.get()) {
            text = generateRandomText();
        }

        if (text == null) return;

        SignBlockEntity sign = ((AbstractSignEditScreenAccessor) event.screen).meteor$getSign();

        queue.add(new UpdateSignC2SPacket(sign.getPos(), true, text[0], text[1], text[2], text[3]));

        event.cancel();
    }
}
