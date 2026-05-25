package com.yision.fluidlogistics.mixin.client;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.item.FluidPackageItem;
import com.yision.fluidlogistics.render.FluidVisual;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.transform.Translate;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChainConveyorVisual.class)
public class ChainConveyorVisualMixin {

    @Unique
    private FluidVisual fluidlogistics$fluidVisual;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void fluidlogistics$ctor(VisualizationContext context, ChainConveyorBlockEntity blockEntity, float partialTick, CallbackInfo ci) {
        fluidlogistics$fluidVisual = new FluidVisual(context, false, true);
    }

    @Inject(
            method = "beginFrame",
            at = @At("HEAD")
    )
    private void fluidlogistics$begin(DynamicVisual.Context ctx, CallbackInfo ci) {
        fluidlogistics$fluidVisual.begin();
    }

    @Inject(
            method = "_delete",
            at = @At("RETURN")
    )
    private void fluidlogistics$delete(CallbackInfo ci) {
        fluidlogistics$fluidVisual.delete();
    }

    @Inject(
            method = "beginFrame",
            at = @At("RETURN")
    )
    private void fluidlogistics$end(DynamicVisual.Context ctx, CallbackInfo ci) {
        fluidlogistics$fluidVisual.end();
    }

    @Definition(id = "TransformedInstance", type = TransformedInstance.class)
    @Expression("new TransformedInstance[]{?,?}")
    @ModifyExpressionValue(
            method = "setupBoxVisual",
            at = @At("MIXINEXTRAS:EXPRESSION")
    )
    private TransformedInstance[] fluidlogistics$setupFluidBuffers(TransformedInstance[] original,
                                                                   @Local(argsOnly = true) ChainConveyorPackage box,
                                                                   @Share("fluid") LocalRef<FluidStack> fluid) {
        if (!(box.item.getItem() instanceof FluidPackageItem)) return original;

        fluid.set(fluidlogistics$getPrimaryFluid(box.item));

        if (fluid.get().isEmpty()) return original;

        TransformedInstance[] buffers = fluidlogistics$fluidVisual.setupBuffers(fluid.get(), original.length);
        System.arraycopy(original, 0, buffers, 0, original.length);

        return buffers;
    }

    @SuppressWarnings("rawtypes")
    @WrapOperation(
            method = "setupBoxVisual",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/lib/instance/TransformedInstance;uncenter()Ldev/engine_room/flywheel/lib/transform/Translate;"
            )
    )
    private Translate fluidlogistics$setupFluidVisual(TransformedInstance instance, Operation<Translate> original,
                                                      @Local(ordinal = 0) TransformedInstance rigBuffer,
                                                      @Local(ordinal = 1) TransformedInstance boxBuffer,
                                                      @Local(ordinal = 2) TransformedInstance buf,
                                                      @Share("fluidBufferIndex") LocalIntRef fluidBufferIndex,
                                                      @Share("fluid") LocalRef<FluidStack> fluid) {
        if (buf == rigBuffer || buf == boxBuffer) return original.call(instance);

        fluidlogistics$fluidVisual.setupBuffer(fluid.get(), Config.getFluidPerPackage(), buf, fluidBufferIndex.get(), 12f / 16, 10f / 16);
        fluidBufferIndex.set(fluidBufferIndex.get() + 1);

        return instance;
    }

    @Unique
    private FluidStack fluidlogistics$getPrimaryFluid(ItemStack box) {
        if (!PackageItem.isPackage(box)) return FluidStack.EMPTY;

        ItemStackHandler contents = PackageItem.getContents(box);
        List<FluidStack> fluids = new ArrayList<>();

        for (int i = 0; i < contents.getSlots(); i++) {
            var slotStack = contents.getStackInSlot(i);
            if (!slotStack.isEmpty() && slotStack.getItem() instanceof CompressedTankItem) {
                FluidStack fluid = CompressedTankItem.getFluid(slotStack);
                fluidlogistics$mergeFluid(fluids, fluid);
            }
        }

        return fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0);
    }

    @Unique
    private void fluidlogistics$mergeFluid(List<FluidStack> fluids, FluidStack newFluid) {
        for (FluidStack existing : fluids) {
            if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
                existing.grow(newFluid.getAmount());
                return;
            }
        }
        fluids.add(newFluid.copy());
    }
}
