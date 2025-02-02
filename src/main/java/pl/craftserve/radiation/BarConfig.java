/*
 * Copyright 2020 Aleksander Jagiełło <themolkapl@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.craftserve.radiation;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class BarConfig {
    private final TextComponent title;
    private final BossBar.Color color;
    private final BossBar.Overlay overlay;
    private final Set<BossBar.Flag> flags;

    public BarConfig(@NotNull String title, @NotNull BossBar.Color color, @NotNull BossBar.Overlay overlay, @NotNull Set<BossBar.Flag> flags) {
        this.title = Component.text(title);
        this.color = color;
        this.overlay = overlay;
        this.flags = flags;
    }

    public BarConfig(ConfigurationSection section) throws InvalidConfigurationException {
        if (section == null) {
            section = new MemoryConfiguration();
        }

        this.title = Objects.requireNonNull(RadiationPlugin.colorizeComponent(section.getString("title", "")));

        String color = section.getString("color", BossBar.Color.WHITE.name());

        try {
            this.color = Objects.requireNonNull(BossBar.Color.valueOf(color.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Unknown bar color: " + color);
        }

        String overlay = section.getString("style", BossBar.Overlay.PROGRESS.name());

        try {
            this.overlay = Objects.requireNonNull(BossBar.Overlay.valueOf(overlay.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new InvalidConfigurationException("Unknown bar style: " + overlay);
        }

        Set<BossBar.Flag> flags = new HashSet<>();
        for (String flagName : section.getStringList("flags")) {
            try {
                flags.add(BossBar.Flag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new InvalidConfigurationException("Unknown bar flag: " + flagName);
            }
        }
        this.flags = flags;
    }

    public TextComponent title() {
        return this.title;
    }

    public BossBar.Color color() {
        return this.color;
    }

    public BossBar.Overlay overlay() {
        return this.overlay;
    }

    public Set<BossBar.Flag> flags() {
        return this.flags;
    }

    public BossBar create() {

        return BossBar.bossBar(this.title(), BossBar.MAX_PROGRESS, this.color(), this.overlay(), this.flags);
    }
}
