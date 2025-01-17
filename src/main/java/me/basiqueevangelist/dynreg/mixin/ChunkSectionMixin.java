package me.basiqueevangelist.dynreg.mixin;

import me.basiqueevangelist.dynreg.access.ExtendedIdListPalette;
import me.basiqueevangelist.dynreg.debug.DebugContext;
import me.basiqueevangelist.dynreg.fixer.BlockFixer;
import me.basiqueevangelist.dynreg.util.AdaptUtil;
import me.basiqueevangelist.dynreg.util.DefaultedIndexIterable;
import me.basiqueevangelist.dynreg.util.PaletteUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IdListPalette;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin {
    @Shadow
    public abstract boolean hasAny(Predicate<BlockState> predicate);

    @Shadow
    public abstract BlockState getBlockState(int x, int y, int z);

    @Shadow
    public abstract BlockState setBlockState(int x, int y, int z, BlockState state);

    @Shadow
    public abstract PalettedContainer<BlockState> getBlockStateContainer();

    private int dynreg$blocksVersion = BlockFixer.BLOCKS_VERSION.getVersion();

    @ModifyArg(method = "<init>(ILnet/minecraft/registry/Registry;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/PalettedContainer;<init>(Lnet/minecraft/util/collection/IndexedIterable;Ljava/lang/Object;Lnet/minecraft/world/chunk/PalettedContainer$PaletteProvider;)V", ordinal = 0))
    private IndexedIterable<BlockState> wrapIterable(IndexedIterable<BlockState> idList) {
        return new DefaultedIndexIterable<>(idList, Blocks.AIR.getDefaultState());
    }

    @Inject(method = "getBlockState", at = @At("HEAD"))
    private void getBlockStateHook(int unusedX, int unusedY, int unusedZ, CallbackInfoReturnable<BlockState> cir) {
        checkIfRefreshNeeded();
    }

    @Inject(method = "setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;", at = @At("HEAD"))
    private void setBlockStateHook(int x, int y, int z, BlockState state, boolean lock, CallbackInfoReturnable<BlockState> cir) {
        checkIfRefreshNeeded();
    }

    @Inject(method = "getBlockStateContainer", at = @At("HEAD"))
    private void saveHook(CallbackInfoReturnable<PalettedContainer<BlockState>> cir) {
        checkIfRefreshNeeded();
    }

    @Inject(method = "toPacket", at = @At("HEAD"))
    private void sendHook(PacketByteBuf buf, CallbackInfo ci) {
        checkIfRefreshNeeded();
        DebugContext.addData("dynreg:chunk_section_version", dynreg$blocksVersion);
    }

    @Inject(method = "toPacket", at = @At("RETURN"))
    private void leaveSend(PacketByteBuf buf, CallbackInfo ci) {
        DebugContext.removeData("dynreg:chunk_section_version");
    }

    @Unique
    private void checkIfRefreshNeeded() {
        int currentVersion = BlockFixer.BLOCKS_VERSION.getVersion();

        if (dynreg$blocksVersion != currentVersion) {
            dynreg$blocksVersion = currentVersion;

            if (!hasAny(state -> state.getBlock().wasDeleted())) return;

            var palette = getBlockStateContainer().data.palette();

            if (!(palette instanceof IdListPalette<BlockState>)) {
                PaletteUtils.tryClean(palette, AdaptUtil::adaptState);
            }

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState oldState = getBlockState(x, y, z);

                        if (oldState.getBlock().wasDeleted()) {
                            // TODO: FlashFreeze compat.

                            setBlockState(x, y, z, AdaptUtil.adaptState(oldState));
                        }
                    }
                }
            }

            if (palette instanceof ExtendedIdListPalette ext) {
                ext.dynreg$updateVersion();
            }
        }
    }
}
