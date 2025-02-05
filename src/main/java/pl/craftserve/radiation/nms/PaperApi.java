package pl.craftserve.radiation.nms;

import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionBrewer;
import pl.craftserve.radiation.LugolsIodinePotion;

public class PaperApi implements RadiationNmsBridge {

    private final PotionBrewer brewer;

    public PaperApi() {
        this.brewer = Bukkit.getPotionBrewer();
    }

    @Override
    public void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config, ItemStack potion) {

        ItemStack basePotion = new ItemStack(Material.POTION);

        PotionMeta basePotionMeta = (PotionMeta) basePotion.getItemMeta();
        basePotionMeta.setBasePotionType(config.basePotion());

        basePotion.setItemMeta(basePotionMeta);

        PotionMix mix = new PotionMix(potionKey, potion, new RecipeChoice.ExactChoice(basePotion), new RecipeChoice.MaterialChoice(config.ingredient()));
        this.brewer.addPotionMix(mix);
    }

    @Override
    public void unregisterLugolsIodinePotion(NamespacedKey potionKey) {
        this.brewer.removePotionMix(potionKey);
    }

    @Override
    public int getMinWorldHeight(World bukkitWorld) {
        return bukkitWorld.getMinHeight();
    }
}
