package carpet.mixins;

import carpet.CarpetSettings;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import carpet.helpers.HopperCounter;
import carpet.utils.WoolTool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntity_counterMixin extends RandomizableContainerBlockEntity
{
    protected HopperBlockEntity_counterMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState) {
        super(blockEntityType, blockPos, blockState);
    }

    @Shadow private Direction facing;

    @Inject(method = "ejectItems", at = @At("HEAD"), cancellable = true)
    private static void onInsert(Level world, BlockPos blockPos, HopperBlockEntity hopperBlockEntity, CallbackInfoReturnable<Boolean> cir)
    {
        if (CarpetSettings.hopperCounters) {
            Direction hopperFacing = world.getBlockState(blockPos).getValue(HopperBlock.FACING);
            DyeColor woolColor = WoolTool.getWoolColorAtPosition(
                    world,
                    blockPos.relative(hopperFacing));
            if (woolColor != null)
            {
                Container inventory = HopperBlockEntity.getContainerAt(world, blockPos);
                for (int i = 0; i < inventory.getContainerSize(); ++i)
                {
                    if (!inventory.getItem(i).isEmpty())
                    {
                        ItemStack itemstack = inventory.getItem(i);
                        HopperCounter.getCounter(woolColor).add(world.getServer(), itemstack);
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                }
                cir.setReturnValue(true);
            }
        }
    }
}
