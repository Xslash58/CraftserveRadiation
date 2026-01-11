/*
 * Copyright 2019 Aleksander Jagiełło <themolkapl@gmail.com>
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

import com.google.common.collect.Lists;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LugolsIodineDisplay implements Listener {
    static final Logger logger = Logger.getLogger(LugolsIodineDisplay.class.getName());

    private static final String DEFAULT_BAR_ID = "default";
    /**
     * Fallback config when a silly user removes the default one.
     */
    private static final BarConfig DEFAULT_BAR_CONFIG = new BarConfig(
            "Lugol's Iodine Effect",
            BossBar.Color.GREEN,
            BossBar.Overlay.NOTCHED_20,
            new HashSet<>());

    private final Map<UUID, Display> displayMap = new HashMap<>(128);
    private final Plugin plugin;
    private final LugolsIodineEffect effectHandler;
    private final Map<String, BarConfig> configs;

    private Task task;

    public LugolsIodineDisplay(Plugin plugin, LugolsIodineEffect effectHandler, Map<String, BarConfig> configs) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.effectHandler = Objects.requireNonNull(effectHandler, "effectHandler");
        this.configs = Objects.requireNonNull(configs, "configs");
    }

    public void enable() {
        this.task = new Task();
        this.task.runTaskTimer(this.plugin, 20L, 20L);

        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    public void disable() {
        HandlerList.unregisterAll(this);

        if (this.task != null) {
            this.task.cancel();
        }

        this.displayMap.values().forEach(Display::removeAll);
        this.displayMap.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Display display = this.displayMap.remove(playerId);

        if (display != null) {
            display.removeAll();
        }
    }

    class Display {
        private final Map<String, BossBar> bossBarMap = new LinkedHashMap<>();

        BossBar add(Player player, LugolsIodineEffect.Effect effect) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(effect, "effect");

            BossBar bossBar = this.bossBarMap.computeIfAbsent(effect.getId(), effectId -> this.createBossBar(effect));

            bossBar.progress((float) effect.getTimeLeft().toMillis() / effect.getInitialDuration().toMillis());
            bossBar.addViewer(player);
            return bossBar;
        }

        void remove(Player player, String effectId) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(effectId, "effectId");

            BossBar bossBar = this.bossBarMap.remove(effectId);
            if (bossBar != null) {
                bossBar.removeViewer(player);
            }
        }

        void removeAll() {
            this.bossBarMap.values().forEach(bossBar -> Lists.newArrayList(bossBar.viewers()).clear());
            this.bossBarMap.clear();
        }

        void update(Player player, List<LugolsIodineEffect.Effect> effectList) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(effectList, "effectList");

            existingLoop:
            for (Map.Entry<String, BossBar> entry : new LinkedHashSet<>(this.bossBarMap.entrySet())) {
                String effectId = entry.getKey();

                for (LugolsIodineEffect.Effect next : effectList) {
                    if (next.getId().equals(effectId)) {
                        // We found matching effect in effectList, retain it.
                        continue existingLoop;
                    }
                }

                // We couldn't find matching effect in effectList, remove it.
                this.remove(player, effectId);
            }

            for (LugolsIodineEffect.Effect newEffect : effectList) {
                this.add(player, newEffect);
            }
        }

        private BossBar createBossBar(LugolsIodineEffect.Effect effect) {
            Objects.requireNonNull(effect, "effect");

            BarConfig barConfig = configs.get(effect.getId());
            if (barConfig == null) {
                barConfig = configs.get(DEFAULT_BAR_ID);
                if (barConfig == null) {
                    barConfig = DEFAULT_BAR_CONFIG;
                }
            }

            return barConfig.create();
        }
    }

    class Task extends BukkitRunnable {
        @Override
        public void run() {
            plugin.getServer().getOnlinePlayers().forEach(player -> {
                List<LugolsIodineEffect.Effect> effectList;
                try {
                    effectList = effectHandler.getEffects(player);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Could not get lugol's iodine effects on '" + player.getName() + "'.", e);
                    return;
                }

                Display display = displayMap.computeIfAbsent(player.getUniqueId(), playerId -> new Display());
                display.update(player, effectList);
            });
        }
    }
}
