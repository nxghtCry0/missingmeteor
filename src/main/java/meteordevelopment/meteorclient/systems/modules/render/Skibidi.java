/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public class Skibidi extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> opacity = sgGeneral.add(new IntSetting.Builder()
        .name("opacity")
        .description("The opacity of the blue overlay.")
        .defaultValue(80)
        .min(0)
        .max(255)
        .sliderRange(0, 255)
        .build()
    );

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the overlay.")
        .defaultValue(new SettingColor(0, 100, 255, 80))
        .build()
    );

    public Skibidi() {
        super(Categories.Render, "skibidi", "Adds a blue overlay to your game screen.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        Color c = color.get();
        Color overlayColor = new Color(c.r, c.g, c.b, opacity.get());

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(0, 0, event.screenWidth, event.screenHeight, overlayColor);
        Renderer2D.COLOR.render();
    }
}
