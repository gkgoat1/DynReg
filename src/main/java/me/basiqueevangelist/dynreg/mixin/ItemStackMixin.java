package me.basiqueevangelist.dynreg.mixin;

import me.basiqueevangelist.dynreg.fixer.ItemFixer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Shadow
    @Mutable
    @Final
    private Item item;

    private int dynreg$itemsVersion = ItemFixer.ITEMS_VERSION.getVersion();

    @Shadow
    public abstract void setCount(int count);

    @Shadow
    private @Nullable NbtCompound nbt;

    @Inject(method = {"getItem", "getCount", "getName", "getCount", "getNbt", "getOrCreateNbt", "getOrCreateSubNbt", "isEmpty"}, at = @At("HEAD"))
    private void itemHook(CallbackInfoReturnable<Integer> cir) {
        checkItem();
    }

    @Unique
    private void checkItem() {
        int currentVersion = ItemFixer.ITEMS_VERSION.getVersion();

        if (dynreg$itemsVersion != currentVersion) {
            dynreg$itemsVersion = currentVersion;

            if (item != null && item.wasDeleted()) {
                @SuppressWarnings("deprecation") Item newItem = Registries.ITEM.getOrEmpty(item.getRegistryEntry().registryKey()).orElse(null);

                item = newItem;

                if (item == null) {
                    setCount(0);
                    nbt = null;
                }
            }
        }
    }
}
