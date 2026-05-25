package com.yision.fluidlogistics.compat.jade;

 
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
 
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.yision.fluidlogistics.config.Config;
import com.yision.fluidlogistics.item.CompressedTankItem;

 
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ViewGroup;

@WailaPlugin
public class FluidLogisticsJadePlugin implements IWailaPlugin {

 
    public static final ResourceLocation PACKAGE_ITEM_STORAGE_UID = ResourceLocation.parse("fluidlogistics:package_item_storage");
    public static final ResourceLocation PACKAGE_FLUID_STORAGE_UID = ResourceLocation.parse("fluidlogistics:package_fluid_storage");
 
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerItemStorage(PackageItemStorageProvider.INSTANCE, PackageEntity.class);
        registration.registerFluidStorage(PackageFluidStorageProvider.INSTANCE, PackageEntity.class);
        registration.registerFluidStorage(MultiFluidTankProvider.INSTANCE, Block.class);
        registration.registerFluidStorage(ContraptionMultiFluidTankProvider.INSTANCE, AbstractContraptionEntity.class);
    }
 
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerItemStorageClient(PackageItemStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(PackageFluidStorageProvider.INSTANCE);
        registration.registerFluidStorageClient(MultiFluidTankProvider.INSTANCE);
        registration.registerFluidStorageClient(ContraptionMultiFluidTankProvider.INSTANCE);
    }
 
    public enum PackageItemStorageProvider implements IServerExtensionProvider<ItemStack>, IClientExtensionProvider<ItemStack, ItemView> {
        INSTANCE;
 
        @Override
        public ResourceLocation getUid() {
            return PACKAGE_ITEM_STORAGE_UID;
        }
 
        @Override
        public @Nullable List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof EntityAccessor entityAccessor)) {
                return null;
            }
 
            if (!(entityAccessor.getEntity() instanceof PackageEntity packageEntity)) {
                return null;
            }
 
            ItemStack box = packageEntity.getBox();
            if (box.isEmpty() || !PackageItem.isPackage(box)) {
                return null;
            }
 
            ItemStackHandler contents = PackageItem.getContents(box);
            List<ItemStack> displayItems = new ArrayList<>();
 
            for (int i = 0; i < contents.getSlots(); i++) {
                ItemStack slotStack = contents.getStackInSlot(i);
                if (slotStack.isEmpty()) continue;
 
                if (slotStack.getItem() instanceof CompressedTankItem) {
                    continue;
                }
                displayItems.add(slotStack);
            }
 
            if (displayItems.isEmpty()) {
                return List.of();
            }
 
            return List.of(new ViewGroup<>(displayItems));
        }
 
        @Override
        public List<ClientViewGroup<ItemView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ItemStack>> groups) {
            return ClientViewGroup.map(groups, ItemView::new, null);
        }
 
        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return accessor instanceof EntityAccessor entityAccessor &&
                    entityAccessor.getEntity() instanceof PackageEntity;
        }
 
        @Override
        public int getDefaultPriority() {
            return -5000;
        }
    }
 
    public enum PackageFluidStorageProvider implements IServerExtensionProvider<CompoundTag>, IClientExtensionProvider<CompoundTag, FluidView> {
        INSTANCE;
 
        @Override
        public ResourceLocation getUid() {
            return PACKAGE_FLUID_STORAGE_UID;
        }
 
        @Override
        public @Nullable List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof EntityAccessor entityAccessor)) {
                return null;
            }
 
            if (!(entityAccessor.getEntity() instanceof PackageEntity packageEntity)) {
                return null;
            }
 
            ItemStack box = packageEntity.getBox();
            if (box.isEmpty() || !PackageItem.isPackage(box)) {
                return null;
            }
 
            ItemStackHandler contents = PackageItem.getContents(box);
            List<FluidStack> fluids = new ArrayList<>();
            int capacity = Config.getFluidPerPackage();
 
            for (int i = 0; i < contents.getSlots(); i++) {
                ItemStack slotStack = contents.getStackInSlot(i);
                if (!slotStack.isEmpty() && slotStack.getItem() instanceof CompressedTankItem) {
                    FluidStack fluid = CompressedTankItem.getFluid(slotStack);
                    int totalAmount = fluid.getAmount() * slotStack.getCount();
                    mergeFluid(fluids, fluid, totalAmount);
                }
            }
 
            if (fluids.isEmpty()) {
                return List.of();
            }
 
            List<CompoundTag> views = new ArrayList<>();
            for (FluidStack fluid : fluids) {
                JadeFluidObject fluidObject = JadeFluidObject.of(
                    fluid.getFluid(),
                    fluid.getAmount(),
                    fluid.getComponentsPatch()
                );
                views.add(FluidView.writeDefault(fluidObject, capacity));
            }
 
            return List.of(new ViewGroup<>(views));
        }
 
        private void mergeFluid(List<FluidStack> fluids, FluidStack newFluid, int totalAmount) {
            for (FluidStack existing : fluids) {
                if (FluidStack.isSameFluidSameComponents(existing, newFluid)) {
                    existing.grow(totalAmount);
                    return;
                }
            }
            FluidStack copy = newFluid.copy();
            copy.setAmount(totalAmount);
            fluids.add(copy);
        }
 
        @Override
        public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
            return ClientViewGroup.map(groups, FluidView::readDefault, null);
        }
 
        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return accessor instanceof EntityAccessor entityAccessor &&
                    entityAccessor.getEntity() instanceof PackageEntity;
        }
 
        @Override
        public int getDefaultPriority() {
            return -5000;
        }
    }
}
