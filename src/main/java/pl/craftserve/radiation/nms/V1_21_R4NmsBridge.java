package pl.craftserve.radiation.nms;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.MinecraftKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import pl.craftserve.radiation.LugolsIodinePotion;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class V1_21_R4NmsBridge implements RadiationNmsBridge {
    static final Logger logger = Logger.getLogger(V1_21_R4NmsBridge.class.getName());

    private final Class<?> iRegistryClass;
    private final Class<?> mobEffectClass;
    private final Class<?> potionRegistryClass;
    private final Class<?> holderClass;
    private final Class<?> recipeItemStackClass;
    private final Class<?> iMaterialClass;

    private final Field isRegistryMaterialsFrozen;

    private final Method getItem;
    private final Method newMinecraftKey;
    private final Method getPotion;
    private final Method minHeightMethod;

    private final Object potionRegistry;
    private final Object potionBrewer;

    private final Map<UUID, Integer> minWorldHeightMap = new HashMap<>();

    public V1_21_R4NmsBridge(String version) {
        try {
            this.iRegistryClass = Class.forName("net.minecraft.core.IRegistry"); // IRegistry -> Registry
            this.mobEffectClass = Class.forName("net.minecraft.world.effect.MobEffect"); // MobEffect -> MobEffectInstance
            this.potionRegistryClass = Class.forName("net.minecraft.world.item.alchemy.PotionRegistry"); // PotionRegistry -> Potion
            this.holderClass = Class.forName("net.minecraft.core.Holder");
            this.iMaterialClass = Class.forName("net.minecraft.world.level.IMaterial");
            this.recipeItemStackClass = Class.forName("net.minecraft.world.item.crafting.RecipeItemStack");

            Class<?> registryMaterialsClass = Class.forName("net.minecraft.core.RegistryMaterials"); // RegistryMaterials -> MappedRegistry
            this.isRegistryMaterialsFrozen = registryMaterialsClass.getDeclaredField("l"); // l -> frozen

            Class<?> potionBrewerClass = Class.forName("net.minecraft.world.item.alchemy.PotionBrewer"); // PotionBrewer -> PotionBrewing
            this.potionBrewer = potionBrewerClass.getDeclaredField("b").get(null); // Static instance of PotionBrewer

            Class<?> minecraftKey = Class.forName("net.minecraft.resources.MinecraftKey"); // MinecraftKey -> ResourceLocation
            this.newMinecraftKey = minecraftKey.getMethod("a", String.class); // a -> tryParse

            Class<?> builtInRegistries = Class.forName("net.minecraft.core.registries.BuiltInRegistries"); // RegistryGeneration -> BuiltInRegistries
            this.potionRegistry = builtInRegistries.getDeclaredField("h").get(null); // h -> POTION
            this.getPotion = this.potionRegistry.getClass().getMethod("a", minecraftKey); // a -> get

            Class<?> craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.v1_21_R4.util.CraftMagicNumbers");
            this.getItem = craftMagicNumbers.getMethod("getItem", Material.class);
            this.minHeightMethod = Class.forName("org.bukkit.generator.WorldInfo").getMethod("getMinHeight");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize 1.21.5 bridge", e);
        }
    }

    @Override
    public void registerLugolsIodinePotion(NamespacedKey potionKey, LugolsIodinePotion.Config.Recipe config) {
        Objects.requireNonNull(potionKey, "potionKey");
        Objects.requireNonNull(config, "config");

        try {
            String basePotionName = config.basePotion().name().toLowerCase(Locale.ROOT);

            String keyName = "minecraft:" + basePotionName;
            Object key = this.newMinecraftKey.invoke(null, keyName);
            Object basePotion = this.getPotion.invoke(this.potionRegistry, key);
            Objects.requireNonNull(basePotion, "basePotion not found");


            Object ingredient = this.getItem.invoke(null, config.ingredient());
            Objects.requireNonNull(ingredient, "ingredient not found");

            Object mobEffectArray = Array.newInstance(this.mobEffectClass, 0);
            Object newPotion = this.potionRegistryClass.getConstructor(basePotionName.getClass(), mobEffectArray.getClass()).newInstance(basePotionName, mobEffectArray);

            Method registerMethod = this.iRegistryClass.getDeclaredMethod("a", this.iRegistryClass, String.class, Object.class); // a -> register
            this.isRegistryMaterialsFrozen.setAccessible(true);
            this.isRegistryMaterialsFrozen.set(this.potionRegistry, false);
            Object potion = registerMethod.invoke(null, this.potionRegistry, potionKey.getKey(), newPotion);

            Method wrapAsHolder = this.iRegistryClass.getDeclaredMethod("e", Object.class); // e -> wrapAsHolder
            Object basePotionHolder = wrapAsHolder.invoke(this.potionRegistry, basePotion); // Get Potion Holder for base potion
            Object potionHolder = wrapAsHolder.invoke(this.potionRegistry, potion); // Get Potion Holder for new potion

            Field mixes = this.potionBrewer.getClass().getDeclaredField("d"); // d -> potionMixes
            mixes.setAccessible(true);

            List<?> originalList = (List<?>) mixes.get(this.potionBrewer); // Get mixes from potion brewer
            List<Object> modifiableList = new ArrayList<>(originalList); // Turn mixes into modifiable list

            Method of = this.recipeItemStackClass.getDeclaredMethod("a", this.iMaterialClass); // a -> of
            Object item =  of.invoke(null, ingredient); // Create RecipeItemStack

            Class<?> predicatedClass = Class.forName("net.minecraft.world.item.alchemy.PotionBrewer$PredicatedCombination");
            Constructor<?> constructor = predicatedClass.getDeclaredConstructor(this.holderClass, this.recipeItemStackClass, this.holderClass);
            constructor.setAccessible(true);

            Object newCombination = constructor.newInstance(basePotionHolder, item, potionHolder); // Create new PotionBrewer$PredicatedCombination

            modifiableList.add(newCombination); // Add new combination to the mixes

            mixes.set(this.potionBrewer, modifiableList); // Set new mix in PotionBrewer
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not handle reflective operation.", e);
        }
    }

    @Override
    public int getMinWorldHeight(World bukkitWorld) {
        Objects.requireNonNull(bukkitWorld, "bukkitWorld");

        return this.minWorldHeightMap.computeIfAbsent(bukkitWorld.getUID(), worldId -> {
            try {
                return (int) this.minHeightMethod.invoke(bukkitWorld);
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.log(Level.SEVERE, "Could not handle min world height on world '" + bukkitWorld.getName() + "' ('" + worldId + "').", e);
                return 0;
            }
        });
    }
}