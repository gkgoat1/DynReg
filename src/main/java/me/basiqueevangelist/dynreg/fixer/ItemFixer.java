package me.basiqueevangelist.dynreg.fixer;

import me.basiqueevangelist.dynreg.debug.DebugContext;
import me.basiqueevangelist.dynreg.event.RegistryEntryDeletedCallback;
import me.basiqueevangelist.dynreg.mixin.fabric.ApiProviderHashMapAccessor;
import me.basiqueevangelist.dynreg.mixin.fabric.BlockApiLookupImplAccessor;
import me.basiqueevangelist.dynreg.mixin.fabric.ItemApiLookupImplAccessor;
import me.basiqueevangelist.dynreg.util.ApiLookupUtil;
import me.basiqueevangelist.dynreg.util.ClearUtils;
import me.basiqueevangelist.dynreg.util.VersionTracker;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.recipe.FireworkStarRecipe;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryEntry;

public final class ItemFixer {
    public static VersionTracker ITEMS_VERSION = new VersionTracker();

    private ItemFixer() {

    }

    public static void init() {
        RegistryEntryDeletedCallback.event(Registry.ITEM).register(ItemFixer::onItemDeleted);

        DebugContext.addSupplied("dynreg:item_version", () -> ITEMS_VERSION.getVersion());
    }

    private static void onItemDeleted(int rawId, RegistryEntry.Reference<Item> entry) {
        Item item = entry.value();

        ClearUtils.clearMapValues(item,
            Item.BLOCK_ITEMS,
            SpawnEggItem.SPAWN_EGGS);

        ClearUtils.clearMapKeys(item,
            DispenserBlock.BEHAVIORS,
            FireworkStarRecipe.TYPE_MODIFIER_MAP);

        CompostingChanceRegistry.INSTANCE.remove(item);
        FuelRegistry.INSTANCE.remove(item);

        for (var lookup : ItemApiLookupImplAccessor.getLOOKUPS()) {
            var apiProviderMap = ((ItemApiLookupImplAccessor) lookup).getProviderMap();
            var map = ApiLookupUtil.getActualMap(apiProviderMap);
            map.remove(item);
        }

        ITEMS_VERSION.bumpVersion();
    }
}
