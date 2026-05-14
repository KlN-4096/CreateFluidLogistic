# CFL (CreateFluidLogistics) vs Fluid 2.0.0 Source Similarity Analysis Report


## 1. Conclusion Summary

Fluid 2.0.0 and CFL show systematic structural equivalence in their fluid packaging functionality. The evidence is concentrated in three main areas:

1. The main fluid packaging path: from fluid inventory scanning, virtual fluid request items, fluid rendering in Redstone Requester and Factory Panel ghost slots, fluid entry rendering in the StockKeeper request list, and request-based fluid extraction, to generating packages with address and order metadata.
2. The clipboard address system: `#`-prefixed address parsing, network packet handling, sign-address protection, address writing, and feedback effects.
3. Factory Panel and StockKeeper UI integration: mixin injections around the same Create methods to modify fluid amount settings, display, restocking, and StockKeeper rendering.

This document is organized as "Evidence overview -> itemized code comparison -> supplemental fluid packaging evidence -> architecture comparison -> overall conclusion". All code comparisons use a side-by-side format.

---

## 2. File Correspondence

| No. | Fluid 2.0.0 File | CFL Corresponding File | Strength | Notes |
|------|------------------|------------------------|----------|-------|
| 1 | `util/ClipboardAddressUtil.java` | `util/ClipboardAddressUtil.java` | Very high | Clipboard `#` address parsing |
| 2 | `packet/ClipboardSetAddressPacket.java` | `network/ClipboardSetAddressPacket.java` | Very high | Clipboard address network packet |
| 3 | `mixin/InventorySummaryMixin.java` | `mixin/logistics/InventorySummaryMixin.java` | High | Virtual fluid item counting |
| 4 | `mixin/FactoryPanelBehaviourMixin.java` | `mixin/logistics/FactoryPanelBehaviourMixin.java` | High | Factory Panel behavior extension |
| 5 | `mixin/CanFillerBlockEntityMixin.java` | `mixin/logistics/PackagerBlockEntityMixin.java` | High | Sign address reading |
| 6 | `mixin/FactoryPanelBlockEntityMixin.java` | `mixin/logistics/FactoryPanelBlockEntityMixin.java` | High | Adjacent block recognition for panels |
| 7 | `goggle/CanFillerGoggleInfo.java` | `goggle/PackagerGoggleInfo.java` | High | Goggle information display |
| 8 | `util/ICanFillerData.java` | `util/IPackagerOverrideData.java` | High | Address attachment interface |
| 9 | `mixin/StockKeeperRequestScreenMixin.java` | `mixin/client/StockKeeperRequestScreenMixin.java` | High | StockKeeper fluid amount rendering |
| 10 | `mixin/FactoryPanelScreenMixin.java` | `mixin/client/FactoryPanelScreenMixin.java` | Medium | Factory Panel tooltip and amount display |
| 11 | `client/FluidAmountHelper.java` | `util/FluidAmountHelper.java` | Medium | Fluid amount formatting |
| 12 | `client/FluidValueBoxRenderer.java` | `mixin/client/ValueBoxRendererMixin.java` | Medium | ValueBox fluid rendering |

---

## 3. Detailed Evidence

### Evidence 1: ClipboardAddressUtil

**Severity: Very high**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `util/ClipboardAddressUtil.java` | `util/ClipboardAddressUtil.java` |
| Method | Helper methods including `extractFirstAddress` | `extractFirstAddress` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public static String extractFirstAddress(ItemStack item) { public static String extractFirstAddress(ItemStack item) {
    if (item != null && !item.isEmpty()) {                  if (item == null || item.isEmpty())
        List pages = ClipboardEntry.readAll(item);              return null;
        return pages.isEmpty() ? null                       List<List<ClipboardEntry>> pages =
            : ClipboardAddressUtil.findFirstAddress(pages);     ClipboardEntry.readAll(item);
    }                                                       if (pages.isEmpty())
    return null;                                                return null;
}                                                           for (List<ClipboardEntry> page : pages) {
                                                                for (ClipboardEntry entry : page) {
private static String findFirstAddress(                             String text = entry.text.getString();
        List<List<ClipboardEntry>> pages) {                         if (text != null && text.startsWith("#")
    for (List<ClipboardEntry> page : pages) {                               && text.length() > 1) {
        for (ClipboardEntry entry : page) {                             String candidate =
            String text = entry.text.getString();                           text.substring(1).stripLeading();
            if (!isValidAddress(text)) continue;                        if (!candidate.isBlank())
            return stripAddressPrefix(text);                                return candidate;
        }                                                               }
    }                                                               }
    return null;                                                }
}                                                               return null;
                                                            }
private static boolean isValidAddress(String text) {
    return text != null && text.startsWith("#")
        && !text.substring(1).isBlank();
}

private static String stripAddressPrefix(String text) {
    return text.substring(1).stripLeading();
}
```

Comparison conclusion: Fluid splits CFL's inline implementation into `findFirstAddress`, `isValidAddress`, and `stripAddressPrefix`, but the conditions and return logic remain `readAll` -> iterate pages and entries -> `startsWith("#")` -> `substring(1).stripLeading()` -> return when non-empty.

---

### Evidence 2: ClipboardSetAddressPacket

**Severity: Very high**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `packet/ClipboardSetAddressPacket.java` | `network/ClipboardSetAddressPacket.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
Player p = context.player();                             public void handle(ServerPlayer player) {
if (!(p instanceof ServerPlayer player) || !player.mayBuild())    if (!player.mayBuild())
    return;                                                         return;

Level level = player.level();                              Level level = player.level();
if (!level.isLoaded(this.pos)) return;                      if (!level.isLoaded(pos)) return;

BlockEntity be = level.getBlockEntity(this.pos);            if (player.distanceToSqr(pos.getX() + 0.5D,
if (!(be instanceof PackagerBlockEntity packager)) return;          pos.getY() + 0.5D,
                                                                     pos.getZ() + 0.5D) > 64.0D)
if (!ClipboardAddressHandler.isClipboardTarget(                     return;
        level.getBlockState(this.pos))) return;

if (player.distanceToSqr(this.pos.getX() + 0.5,             ItemStack heldItem = player.getMainHandItem();
        this.pos.getY() + 0.5,                              if (!AllBlocks.CLIPBOARD.isIn(heldItem)) return;
        this.pos.getZ() + 0.5) > 64.0)
    return;

ItemStack clipboard =                                      BlockEntity blockEntity = level.getBlockEntity(pos);
    ClipboardAddressHandler.findClipboard(player);          if (!(blockEntity instanceof IPackagerOverrideData data))
if (clipboard == null) return;                                  return;

String resolvedAddress =                                   String address =
    this.address == null ? "" : this.address.strip();           ClipboardAddressUtil.extractFirstAddress(heldItem);
if (resolvedAddress.isEmpty()                              if (address == null) {
        && (resolvedAddress = ClipboardAddressHandler           fluidlogistics$sendFeedback(level, pos, player, false);
            .extractFirstAddress(clipboard)) == null)           return;
    resolvedAddress = "";                                  }

if (resolvedAddress.isEmpty()) {                            if (fluidlogistics$hasSignAddress(level, pos)) {
    PacketDistributor.sendToPlayer(player,                       fluidlogistics$sendFeedback(level, pos, player, false);
        new ClipboardAddressParticlePacket(this.pos));           return;
    return;                                                 }
}

ClipboardSetAddressPacket.applyAddressToPackager(           data.fluidlogistics$setClipboardAddress(address);
    packager, resolvedAddress, level, this.pos, player);     // Then writes into PackagerBlockEntity or FluidPackagerBlockEntity
                                                        }
```

```java
// Fluid 2.0.0                                          // CFL git HEAD
private static void applyAddressToPackager(...) {        if (fluidlogistics$hasSignAddress(level, pos)) {
    if (!(packager instanceof ICanFillerData)) return;        player.displayClientMessage(...);
    ICanFillerData data = (ICanFillerData) packager;          fluidlogistics$sendFeedback(level, pos, player, false);
                                                              return;
    if (checkHasSignAddress(level, pos)) {                }
        player.displayClientMessage(...);
        PacketDistributor.sendToPlayer(player,            data.fluidlogistics$setClipboardAddress(address);
            new ClipboardAddressParticlePacket(pos));     if (blockEntity instanceof PackagerBlockEntity packager) {
        return;                                               packager.signBasedAddress = address;
    }                                                         packager.setChanged();
                                                              packager.notifyUpdate();
    data.setClipboardAddress(address);                    } else if (blockEntity instanceof FluidPackagerBlockEntity fp) {
    packager.signBasedAddress = address;                      fp.signBasedAddress = address;
    packager.notifyUpdate();                                  fp.setChanged();
    PacketDistributor.sendToPlayer(player,                    fp.notifyUpdate();
        new ClipboardAddressParticlePacket(pos));         }
}                                                         fluidlogistics$sendFeedback(level, pos, player, true);
```

Comparison conclusion: Both sides perform build-permission checks, chunk-loaded checks, `64.0` distance checks, clipboard address extraction, empty-address failure handling, sign-address protection, address writing, and feedback effects. Fluid 2.0.0 centralizes clipboard lookup and parsing in `ClipboardAddressHandler`; CFL uses held-item checks and `ClipboardAddressUtil` separately.

---

### Evidence 3: InventorySummaryMixin

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/InventorySummaryMixin.java` | `mixin/logistics/InventorySummaryMixin.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
private void fluid$addFluidManifest(ItemStack stack,    private void fluidlogistics$addCompressedTank(ItemStack stack,
        int count, CallbackInfo ci) {                           int count, CallbackInfo ci) {
    if (count == 0 || stack.isEmpty()) return;              if (count == 0 || stack.isEmpty()) return;
    if (!(stack.getItem() instanceof FluidManifestItem))     if (!(stack.getItem() instanceof CompressedTankItem))
        return;                                                  return;

    FluidManifestContent content = stack.get(...);           FluidTankContent content = stack.get(...);
    if (content == null || content.fluid().isEmpty())        if (content == null || content.fluid().isEmpty())
        return;                                                  return;

    for (BigItemStack entry : this.stacks) {                 for (BigItemStack entry : this.stacks) {
        if (!matchesSameFluid(entry.stack, stack)) continue;     if (!matchesVirtualFluid(entry.stack, fluid)) continue;
        entry.count = Math.min(1000000000,                       entry.count = Math.min(BigItemStack.INF,
            entry.count + count);                                    entry.count + count);
        ci.cancel();                                             ci.cancel();
        return;                                                  return;
    }                                                        }
    this.stacks.add(new BigItemStack(stack, count));         this.stacks.add(new BigItemStack(stack, count));
    ci.cancel();                                             ci.cancel();
}                                                        }
```

Comparison conclusion: Both use a mixin to intercept `InventorySummary`, merge virtual fluid containers as inventory summary entries, and use the same upper-limit semantics: Fluid writes `1000000000` directly, while CFL uses `BigItemStack.INF`.

---

### Evidence 4: FactoryPanelBehaviourMixin

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/FactoryPanelBehaviourMixin.java` | `mixin/logistics/FactoryPanelBehaviourMixin.java` |

| Injected Method | Fluid | CFL |
|----------|-------|-----|
| `tryRestock` | `@Inject` | `@Inject` |
| `createBoard` | `@Inject` | `@Inject` |
| `formatValue` | `@Inject` | `@Inject` |
| `setValueSettings` | `@Inject` | `@Inject` + `@ModifyExpressionValue` |
| `getValueSettings` | `@Inject` | `@Inject` |
| `getCountLabelForValueBox` | `@Inject` | `@Inject` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
@Inject(method={"tryRestock"}, at=@At("HEAD"),          @Inject(method = "tryRestock", at = @At("HEAD"),
        cancellable=true, remap=false)                          cancellable = true, remap = false)
private void fluid$tryRestockFromCanFiller(CallbackInfo ci) {private void fluidlogistics$tryFluidRestock(CallbackInfo ci) {
    FactoryPanelBehaviour behaviour =                           FactoryPanelBehaviour self =
        (FactoryPanelBehaviour) this;                                (FactoryPanelBehaviour) (Object) this;
    ItemStack item = fluid$normalizeManifest(                    IFluidPackager fluidPackager =
        behaviour.getFilter());                                      FluidGaugeHelper.getFluidPackager(self.panelBE());
    if (!(item.getItem() instanceof FluidManifestItem)) return;   if (fluidPackager == null) return;
    PackagerBlockEntity packager = behaviour.panelBE()            ItemStack item = self.getFilter();
        .getRestockedPackager();
    if (!(packager instanceof CanFillerBlockEntity)) return;

    int inStorage = behaviour.getLevelInStorage();                int inStorage = self.getLevelInStorage();
    int promised = behaviour.getPromised();                       int promised = self.getPromised();
    int demand = behaviour.getAmount();                           int demand = FluidGaugeHelper.getRestockDemand(self);
    int amountToOrder =                                           int shortage = demand - promised - inStorage;
        Math.max(demand - promised - inStorage, 0);

    int availableOnNetwork = LogisticsManager.getStockOf(         IdentifiedInventory inv =
        behaviour.network, item, null);                               fluidPackager.getIdentifiedInventory();
                                                                  int availableOnNetwork =
                                                                      LogisticsManager.getStockOf(network, item, inv);
                                                                  int amountToOrder = Math.min(shortage, availableOnNetwork);

    BigItemStack orderedItem =                                    BigItemStack orderedItem =
        new BigItemStack(item, Math.min(amountToOrder,                new BigItemStack(item, amountToOrder);
            availableOnNetwork));
    PackageOrderWithCrafts order =                                PackageOrderWithCrafts order =
        PackageOrderWithCrafts.simple(List.of(orderedItem));          PackageOrderWithCrafts.simple(List.of(orderedItem));

    LogisticsManager.broadcastPackageRequest(                     LogisticsManager.broadcastPackageRequest(
        behaviour.network, RequestType.RESTOCK, order,                network, RequestType.RESTOCK, order, inv, recipeAddress);
        null, behaviour.recipeAddress);
    behaviour.restockerPromises.add(new RequestPromise(orderedItem));restockerPromises.add(new RequestPromise(orderedItem));
    ci.cancel();                                                  ci.cancel();
}                                                             }
```

```java
// Fluid 2.0.0                                          // CFL git HEAD
@Inject(method={"createBoard"}, at=@At("HEAD"),          @Inject(method = "createBoard", at = @At("RETURN"),
        cancellable=true, remap=false)                          cancellable = true, remap = false)
fluid$createBucketBoard(...) {                           fluidlogistics$modifyBoardLabels(...) {
    new ValueSettingsBoard(                                   new ValueSettingsBoard(
        translate("factory_panel.target_amount"),                 translate("factory_panel.target_amount"),
        100, 10,                                                  100, 10,
        List.of(literal("mB"), literal("B")),                     List.of(text("mB"), text("B")),
        formatter -> behaviour.formatValue(formatter));           original.formatter());
}                                                           }

@Inject(method={"formatValue"}, at=@At("HEAD"),            @Inject(method = "formatValue", at = @At("HEAD"),
        cancellable=true, remap=false)                             cancellable = true, remap = false)
fluid$formatValue(value, cir) {                            fluidlogistics$formatFluidValue(value, cir) {
    if (value.value() == 0) inactive;                         String formatted =
    else if (value.row() == 1) value.value() + "B";               FluidAmountHelper.formatFactoryGaugeValueSetting(
    else value.value() * 10 + "mB";                                   value.row(), value.value());
}                                                               formatted == null ? inactive : literal(formatted);
                                                            }

@Inject(method={"setValueSettings"}, at=@At("HEAD"),       @Inject(method = "setValueSettings", at = @At("HEAD"))
        cancellable=true, remap=false)                     fluidlogistics$beforeSetValueSettings(..., settings, ...) {
fluid$setBucketValueSettings(..., settings, ..., ci) {         needsConversion = true;
    int storedAmountMb = fluid$boardValueToMb(settings);       useBucketsMode = settings.row() == 1;
    behaviour.count = storedAmountMb;                      }
    behaviour.upTo = true;                                 @ModifyExpressionValue(method = "setValueSettings", ...)
    behaviour.blockEntity.sendData();                      fluidlogistics$modifySettingsValue(original) {
    ci.cancel();                                               return FluidAmountHelper.toFactoryGaugeAmount(row, original);
}                                                           }

@Inject(method={"getValueSettings"}, at=@At("HEAD"),       @Inject(method = "getValueSettings", at = @At("HEAD"),
        cancellable=true, remap=false)                             cancellable = true, remap = false)
fluid$getBucketValueSettings(cir) {                        fluidlogistics$onGetValueSettings(cir) {
    cir.setReturnValue(fluid$mbToBoardSettings(count));         cir.setReturnValue(new ValueSettings(row, displayValue));
}                                                           }

@Inject(method={"getCountLabelForValueBox"}, at=@At("HEAD"),@Inject(method = "getCountLabelForValueBox", at = @At("HEAD"),
        cancellable=true, remap=false)                             cancellable = true, remap = false)
fluid$getBucketCountLabel(cir) {                           fluidlogistics$onGetCountLabelForValueBox(cir) {
    text(inStorageText).add("/").add(targetText);               text(format(levelInStorage)).add("/").add(format(count));
}                                                           }
```

Comparison conclusion: Both extend the same `FactoryPanelBehaviour` to handle fluid restocking, amount settings, and display. In `tryRestock`, the structures around `inStorage/promised/demand` calculation, broadcasting `PackageOrderWithCrafts`, and writing `restockerPromises` correspond clearly.

---

### Evidence 5: Sign Address Reading

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/CanFillerBlockEntityMixin.java` | `mixin/logistics/PackagerBlockEntityMixin.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
BlockEntity object = level.getBlockEntity(               BlockEntity blockEntity = level.getBlockEntity(
    packager.getBlockPos().relative(side));                   packager.getBlockPos().relative(side));
if (!(object instanceof SignBlockEntity))                 if (!(blockEntity instanceof SignBlockEntity sign))
    return "";                                                return "";
SignBlockEntity sign = (SignBlockEntity) object;

for (boolean front : new boolean[]{true, false}) {        for (boolean front : Iterate.trueAndFalse) {
    SignText text = sign.getText(front);                      SignText text = sign.getText(front);
    String address = "";                                      StringBuilder address = new StringBuilder();
    for (Component component : text.getMessages(false)) {     for (Component component : text.getMessages(false)) {
        String string = component.getString();                    String string = component.getString();
        if (string.isBlank()) continue;                          if (!string.isBlank())
        address += string.trim() + " ";                              address.append(string.trim()).append(' ');
    }                                                           }
    if (address.isBlank()) continue;                            if (address.length() > 0)
    return address.trim();                                          return address.toString().trim();
}                                                           }
return "";                                                  return "";
```

Comparison conclusion: Both read sign addresses in the same order: adjacent block -> `SignBlockEntity` -> front and back text -> text components -> trim and concatenate -> return address.

---

### Evidence 6: FactoryPanelBlockEntityMixin

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/FactoryPanelBlockEntityMixin.java` | `mixin/logistics/FactoryPanelBlockEntityMixin.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
@Inject(method={"getRestockedPackager"},                 private boolean fluidlogistics$modifyRestockerCheck(
        at=@At("HEAD"), cancellable=true, remap=false)           boolean original) {
private void fluid$getCanFillerAsRestockedPackager(cir) {    if (original) return true;
    FactoryPanelBlockEntity be =                             FactoryPanelBlockEntity self =
        (FactoryPanelBlockEntity) this;                           (FactoryPanelBlockEntity) (Object) this;
    if (!fluid$isFactoryGauge(be)) return;                    if (self.getLevel() == null) return false;

    BlockEntity attached = fluid$getAttachedBlockEntity(be);  BlockState state = self.getBlockState();
    if (!(attached instanceof CanFillerBlockEntity)) return;  BlockPos connectedPos = self.getBlockPos().relative(
    cir.setReturnValue((CanFillerBlockEntity) attached);          FactoryPanelBlock.connectedDirection(state).getOpposite());
}                                                           if (!self.getLevel().isLoaded(connectedPos)) return false;

private static BlockEntity fluid$getAttachedBlockEntity(     BlockEntity be = self.getLevel().getBlockEntity(connectedPos);
        FactoryPanelBlockEntity be) {                        return be instanceof IFluidPackager;
    BlockState state = be.getBlockState();                }
    BlockPos attachedPos = be.getBlockPos().relative(
        FactoryPanelBlock.connectedDirection(state).getOpposite());
    if (!be.getLevel().isLoaded(attachedPos)) return null;
    return be.getLevel().getBlockEntity(attachedPos);
}
```

Comparison conclusion: Both use `FactoryPanelBlock.connectedDirection(state).getOpposite()` to calculate the adjacent block behind the panel and use that block entity to determine whether to enter fluid packager logic.

---

### Evidence 7: Goggle Information Display

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `goggle/CanFillerGoggleInfo.java` | `goggle/PackagerGoggleInfo.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
translate("goggles.repackager_title")                   translate("goggles.repackager_title")
    .style(ChatFormatting.WHITE).forGoggles(tooltip);        .style(ChatFormatting.WHITE).forGoggles(tooltip);
translate("goggles.address_label")                      translate("goggles.address_label")
    .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);      .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
text(address)                                           text(address)
    .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);      .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
translate("goggles.packager_title")                     translate("goggles.packager_title")
    .style(ChatFormatting.WHITE).forGoggles(tooltip);        .style(ChatFormatting.WHITE).forGoggles(tooltip);
translate("goggles.no_address")                         translate("goggles.no_address")
    .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1); .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
```

Comparison conclusion: The translation keys, color combinations, and `forGoggles(..., 1)` indentation pattern are consistent.

---

### Evidence 8: Address Attachment Interface

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `util/ICanFillerData.java` | `util/IPackagerOverrideData.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public interface ICanFillerData {                       public interface IPackagerOverrideData {
    String getClipboardAddress();                            boolean fluidlogistics$isManualOverrideLocked();
    void setClipboardAddress(String address);                 void fluidlogistics$setManualOverrideLocked(boolean locked);
}                                                            String fluidlogistics$getClipboardAddress();
                                                             void fluidlogistics$setClipboardAddress(String address);
                                                         }
```

Comparison conclusion: Fluid preserves the two core address read/write interface methods, while CFL additionally includes manual override lock state.

---

### Evidence 9: StockKeeperRequestScreenMixin

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/StockKeeperRequestScreenMixin.java` | `mixin/client/StockKeeperRequestScreenMixin.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
@Inject(method={"renderItemEntry(...)"}, at=@At("HEAD"))@Inject(method = "renderItemEntry", at = @At("HEAD"),
fluid$onRenderItemEntryHead(graphics, pt, entry) {              remap = false, cancellable = true)
    fluid$isFluidManifest = false;                      fluidlogistics$onRenderItemEntryHead(graphics, scale, entry) {
    fluid$fluidAmount = 0;                                  fluidlogistics$isCompressedTank = false;
    fluid$cachedFluid = FluidStack.EMPTY;
    fluid$cachedGraphics = graphics;                        ItemStack stack = entry.stack;
                                                            if (stack.getItem() instanceof CompressedTankItem
    if (entry.stack.getItem() instanceof FluidManifestItem       && CompressedTankItem.isVirtual(stack)) {
            && !FluidManifestItem.read(entry.stack).isEmpty()) { FluidStack fluid = CompressedTankItem.getFluid(stack);
        fluid$isFluidManifest = true;                          if (!fluid.isEmpty())
        fluid$fluidAmount = entry.count;                           fluidlogistics$isCompressedTank = true;
        fluid$cachedFluid = fluid;                             }
    }                                                      }
}

@Redirect(method={"renderItemEntry"},                    @Redirect(method = "renderItemEntry",
        target="StockKeeperRequestScreen.drawItemCount")          target="StockKeeperRequestScreen.drawItemCount")
fluid$redirectDrawItemCount(instance, graphics, count, custom) {fluidlogistics$redirectDrawItemCount(instance, graphics, count, custom) {
    if (fluid$isFluidManifest) {                            if (fluidlogistics$isCompressedTank)
        if (fluid$fluidAmount > 1)                              return;
            FluidSlotAmountRenderer.renderInStockKeeper(    drawItemCount(graphics, count, custom);
                graphics, fluid$fluidAmount);           }
        return;
    }                                                      @Redirect(method = "renderItemEntry",
    callDrawItemCount(graphics, count, custom);                    target="GuiGraphics.renderItemDecorations")
}                                                         fluidlogistics$redirectRenderItemDecorations(..., customCount) {
                                                              if (fluidlogistics$isVirtualCompressedTank(stack)
                                                                      && customCount > 0)
                                                                  FluidSlotAmountRenderer.renderInStockKeeper(graphics, customCount);
                                                          }
```

Comparison conclusion: Both identify virtual fluid entries at the start of `renderItemEntry` and intercept `drawItemCount` to take over StockKeeper amount display. Fluid uses `FluidManifestItem`; CFL uses virtual `CompressedTankItem`. Both ultimately call `FluidSlotAmountRenderer.renderInStockKeeper`.

---

### Evidence 10: FactoryPanelScreenMixin

**Severity: Medium**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `mixin/FactoryPanelScreenMixin.java` | `mixin/client/FactoryPanelScreenMixin.java` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
@Redirect(method={"renderInputItem"},                    @Redirect(method = "renderInputItem",
        target="GuiGraphics.renderComponentTooltip")             target="GuiGraphics.renderComponentTooltip")
fluid$renderInputFluidTooltip(..., BigItemStack item) {  fluidlogistics$redirectInputTooltip(..., BigItemStack item) {
    if (restocker) {                                         if (restocker) {
        translate("gui.factory_panel.sending_item", fluid);      translate("gui.factory_panel.sending_item", fluid);
        translate("gui.factory_panel.sending_item_tip");         translate("gui.factory_panel.sending_item_tip");
        translate("gui.factory_panel.sending_item_tip_1");       translate("gui.factory_panel.sending_item_tip_1");
    } else {                                                } else {
        translate("gui.factory_panel.sending_item",             translate("gui.factory_panel.sending_item",
            fluid + " x" + amountText);                             fluid + " x" + amountText);
        translate("gui.factory_panel.scroll_to_change_amount"); translate("gui.factory_panel.scroll_to_change_amount");
        translate("gui.factory_panel.left_click_disconnect");   translate("gui.factory_panel.left_click_disconnect");
    }                                                        }
    graphics.renderComponentTooltip(font, newTooltips, x, y); graphics.renderComponentTooltip(font, newTooltips, x, y);
}                                                        }
```

Comparison conclusion: Both intercept tooltip rendering in `renderInputItem` and reuse the same Create translation keys and color/italic style combinations.

---

### Evidence 11: FluidAmountHelper

**Severity: Medium**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `client/FluidAmountHelper.java` | `util/FluidAmountHelper.java` |
| Topic | mB/B amount formatting | mB/B/KB amount formatting and Factory Panel value conversion |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public static String format(int amountMB) {             public static String formatPrecise(int amount) {
    String text = String.valueOf(                           if (amount < MB_PER_BUCKET)
        (float) amountMB / 1000.0f);                            return amount + "mB";
    if (text.endsWith(".0"))                               return BigDecimal.valueOf(amount, 3)
        text = text.substring(0, text.length() - 2);            .stripTrailingZeros()
    return text;                                                .toPlainString() + "B";
}                                                           }

public static String formatWithUnit(int amountMB) {      public static String formatFactoryGaugeValueSetting(
    int clamped = Math.max(0, amountMB);                         int row, int value) {
    if (clamped < 1000)                                     if (value == 0)
        return clamped + "mb";                                  return null;
    if (clamped % 1000 == 0)                                if (row == 1)
        return clamped / 1000 + "b";                            return Math.clamp(value, 1, 100) + "B";
    return FluidAmountHelper.format(clamped) + "b";         return Math.max(0, value) * 10 + "mB";
}                                                           }

                                                            public static int toFactoryGaugeAmount(int row, int value) {
                                                                if (value <= 0) return 0;
                                                                if (row == 1)
                                                                    return Math.clamp(value, 1, 100) * MB_PER_BUCKET;
                                                                return Math.max(0, value) * 10;
                                                            }
```

Comparison conclusion: Both perform display conversion around the same `1000 mB = 1 B` unit system. Fluid keeps simplified `format/formatWithUnit` helpers, while CFL expands this into StockKeeper display, detailed display, and Factory Panel scroll value conversion. The core rule remains: show small mB values directly, convert whole buckets to B, and display non-whole buckets as decimals with trailing zeros removed.

---

### Evidence 12: FluidValueBoxRenderer / ValueBoxRendererMixin

**Severity: Medium**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `client/FluidValueBoxRenderer.java` | `mixin/client/ValueBoxRendererMixin.java` |
| Topic | Replacing item rendering with fluid texture in ValueBox | Intercepting ValueBoxRenderer and rendering virtual fluids |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public static void renderFluidIntoValueBox(             @Inject(method = "renderItemIntoValueBox",
        FluidStack stack, PoseStack ms,                         at = @At("HEAD"), cancellable = true)
        MultiBufferSource buffer, int light) {          private static void fluidlogistics$renderFluidIntoValueBox(
    if (stack.isEmpty() || stack.getFluid() == Fluids.EMPTY)     ItemStack filter, PoseStack ms,
        return;                                                  MultiBufferSource buffer, int light,
    FluidStack renderStack = stack.getAmount() == 0              int overlay, CallbackInfo ci) {
        ? stack.copyWithAmount(1) : stack;                   FluidStack fluid = VirtualFluidDisplayHelper.getDisplayFluid(filter);
                                                            if (!fluid.isEmpty()) {
    IClientFluidTypeExtensions clientFluid =                     FluidSlotRenderer.renderFluidInWorld(fluid, ms, buffer, light);
        IClientFluidTypeExtensions.of(renderStack.getFluid());   ci.cancel();
    TextureAtlasSprite sprite = ...;                         }
    int color = clientFluid.getTintColor(renderStack);       }
    if ((color >> 24 & 0xFF) == 0) a = 1.0f;
                                                        @Inject(method = "renderFlatItemIntoValueBox",
    ms.pushPose();                                             at = @At("HEAD"), cancellable = true)
    ms.scale(0.5f, 0.5f, 0.5f);                    private static void fluidlogistics$renderFlatFluidIntoValueBox(...) {
    ms.translate(-0.5f, -0.5f, -0.14f);                     FluidStack fluid = VirtualFluidDisplayHelper.getDisplayFluid(filter);
    VertexConsumer builder =                                if (fluid.isEmpty()) return;
        buffer.getBuffer(RenderType.translucent());         TransformStack.of(ms).translate(...).rotateYDegrees(180);
    putVertex(builder, pose, ...);                          squashedMS.scale(.5f, .5f, 1 / 1024f);
    ms.popPose();                                           FluidSlotRenderer.renderFluidItemIcon(fluid, squashedMS, buffer, itemLight);
}                                                           ci.cancel();
                                                        }
```

Comparison conclusion: Fluid extracts the rendering logic into a standalone `FluidValueBoxRenderer`, while CFL injects into Create's `ValueBoxRenderer` through mixins. The functional location is the same: when the filter represents a fluid, default item rendering is skipped and replaced with a fluid texture or fluid quad; both use `copyWithAmount(1)` for `amount == 0` fluids to ensure visibility.

---

## 4. Supplemental Fluid Packaging Evidence

### Evidence 13: Extracting Fluid by Request and Generating Packages

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `block/CanFiller/CanFillerBlockEntity.java` | `block/FluidPackager/FluidPackagerBlockEntity.java` |
| Method | `attemptToSendFluid` | `attemptToSendFluidRequest` |

```java
// Fluid 2.0.0                                          // CFL git HEAD
PackagingRequest nextRequest = queuedRequests.get(0);   PackagingRequest nextRequest = queuedRequests.get(0);
FluidRequestKey requestedKey =                          ItemStack requestedStack = nextRequest.item();
    FluidManifestItem.readKey(nextRequest.item());      if (!(requestedStack.getItem() instanceof CompressedTankItem)) {
if (requestedKey == null) {                                 queuedRequests.remove(0);
    queuedRequests.remove(0);                               return;
    return;                                             }
}
int requestedMb = nextRequest.getCount();               FluidStack requestedFluid = CompressedTankItem.getFluid(requestedStack);
FluidStack extracted = executePlan(requestedKey, requestedMb);if (requestedFluid.isEmpty()) {
if (extracted.isEmpty()) {                                  queuedRequests.remove(0);
    queuedRequests.remove(0);                               return;
    return;                                             }
}
                                                        int remainingCount = nextRequest.getCount();
ItemStack fluidPackage = CopperCanItem.create(          int toExtract = Math.min(remainingCount, Config.getFluidPerPackage());
    extracted, CFCommonConfig.getFluidPerPackage());    FluidStack extractedFluid = extractSpecificFluidFromTank(
PackageItem.clearAddress(fluidPackage);                     fluidHandler, requestedFluid, toExtract);
String address = nextRequest.address();                 ItemStack fluidPackage = createFluidPackage(extractedFluid);
if (address != null && !address.isBlank())              PackageItem.clearAddress(fluidPackage);
    PackageItem.addAddress(fluidPackage, address);      if (fixedAddress != null)
                                                            PackageItem.addAddress(fluidPackage, fixedAddress);
PackageItem.setOrder(fluidPackage,                      PackageItem.setOrder(fluidPackage,
    nextRequest.orderId(), nextRequest.linkIndex(),         fixedOrderId, linkIndexInOrder,
    nextRequest.finalLink().booleanValue(),                 finalLinkInOrder,
    nextRequest.packageCounter().getAndIncrement(),         packageIndexAtLink, finalPackageAtLink,
    nextRequest.isEmpty(), nextRequest.context());          orderContext);
nextRequest.subtract(extracted.getAmount());            nextRequest.subtract(extractedFluid.getAmount());

if (!heldBox.isEmpty() || animationTicks != 0) {        if (!heldBox.isEmpty() || animationTicks != 0) {
    queuedExitingPackages.add(new BigItemStack(fluidPackage, 1));queuedExitingPackages.add(new BigItemStack(fluidPackage, 1));
    return;                                                 return;
}                                                       }
heldBox = fluidPackage;                                 heldBox = fluidPackage;
animationInward = false;                                animationInward = false;
animationTicks = 20;                                    animationTicks = CYCLE;
triggerStockCheck();                                    triggerStockCheck();
notifyUpdate();                                         notifyUpdate();
```

Comparison conclusion: This is not the previously removed arrival-commit logic, but the actual fluid packaging path. Both sides take the target fluid from `PackagingRequest`, extract fluid by the requested amount, generate a fluid package, clear and write addresses, write order metadata, subtract from the request count, and then either eject immediately or enqueue into `queuedExitingPackages` depending on `heldBox/animationTicks`.

---

### Evidence 14: Refilling the Target Fluid Container After Unpacking

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `block/CanFiller/CanFillerBlockEntity.java` | `block/FluidPackager/FluidPackagerBlockEntity.java` |
| Method | `unwrapCopperCan` | `unwrapBox` + `tick` pending insert |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public boolean unwrapBox(ItemStack box, boolean simulate) {public boolean unwrapBox(ItemStack box, boolean simulate) {
    return CopperCanItem.isCopperCan(box)                    if (animationTicks > 0)
        && unwrapCopperCan(box, simulate);                       return false;
}                                                           if (!FluidPackageItem.isFluidPackage(box))
                                                                return false;
private boolean unwrapCopperCan(ItemStack box, boolean simulate) {
    if (animationTicks > 0) return false;                   IFluidHandler fluidHandler = fluidTarget.getInventory();
    IFluidHandler fluidHandler = getFluidHandler();         if (fluidHandler == null) return false;
    if (fluidHandler == null) return false;
                                                            List<FluidStack> packageFluids = collectPackageFluids(items);
    FluidStack fluid = CopperCanItem.getFluid(box);         if (!packageFluids.isEmpty()
    if (fluid.isEmpty()) return true;                           && !FluidInsertionHelper.canAcceptAll(targetBE, fluidHandler, packageFluids))
    int filled = fluidHandler.fill(fluid, SIMULATE);            return false;
    if (filled < fluid.getAmount()) return false;
    if (simulate) return true;                              if (simulate) return true;

    fluidHandler.fill(fluid, EXECUTE);                      pendingFluidsToInsert.clear();
    previouslyUnwrapped = box.copyWithCount(1);             pendingFluidsToInsert.addAll(packageFluids);
    animationInward = true;                                 previouslyUnwrapped = box.copyWithCount(1);
    animationTicks = 20;                                    animationInward = true;
    triggerStockCheck();                                    animationTicks = CYCLE;
    notifyUpdate();                                         notifyUpdate();
    return true;                                            return true;
}                                                       }

                                                        if (animationTicks == 0 && animationInward
                                                                && !pendingFluidsToInsert.isEmpty()) {
                                                            for (FluidStack fluid : pendingFluidsToInsert)
                                                                fluidHandler.fill(fluid.copy(), EXECUTE);
                                                            pendingFluidsToInsert.clear();
                                                            triggerStockCheck();
                                                        }
```

Comparison conclusion: Fluid directly simulates and executes `fluidHandler.fill` for a single `CopperCanItem`; CFL first collects multiple `FluidStack` values inside the package and then fills them after the animation through `pendingFluidsToInsert`. The implementation granularity differs, but the state machine is consistent: unpacking checks, capacity simulation, recording `previouslyUnwrapped`, inward animation, stock refresh, and synchronization.

---

### Supplemental Evidence 16: Data Structure for Virtual Fluid Request Items

**Severity: Medium**

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| File | `item/FluidManifestItem.java` | `item/CompressedTankItem.java` |
| Topic | Uses a lightweight item to represent a request for "some fluid" | Uses a virtual compressed tank to represent a request for "some fluid" |

```java
// Fluid 2.0.0                                          // CFL git HEAD
public static ItemStack of(FluidStack fluid, int amount) {public static void setFluidVirtual(ItemStack stack, FluidStack fluid) {
    ItemStack stack = new ItemStack(CFItems.FLUID_MANIFEST.get());stack.set(AllDataComponents.FLUID_TANK_CONTENT,
    stack.set(CFDataComponents.FLUID_MANIFEST.get(),              new FluidTankContent(fluid.copy(), true));
        new FluidManifestContent(                             }
            BuiltInRegistries.FLUID.getKey(fluid.getFluid()),
            fluid.isEmpty() ? 0 : 1));                    public static boolean isVirtual(ItemStack stack) {
    return stack;                                             FluidTankContent content = stack.get(AllDataComponents.FLUID_TANK_CONTENT);
}                                                           return content != null && content.virtual();
                                                            }

public static FluidStack read(ItemStack stack) {
    FluidManifestContent content = stack.get(...);       public static FluidStack getFluid(ItemStack stack) {
    if (content == null || content.fluidId() == null)        FluidTankContent content = stack.get(AllDataComponents.FLUID_TANK_CONTENT);
        return FluidStack.EMPTY;                            return content != null ? content.fluid() : FluidStack.EMPTY;
    Fluid fluid = BuiltInRegistries.FLUID.get(content.fluidId());}
    return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, 1);
}

public static FluidRequestKey readKey(ItemStack stack) {private ItemStack createFluidDisplayItem(FluidStack fluid) {
    FluidManifestContent content = stack.get(...);           ItemStack tankStack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
    if (content == null || content.fluidId() == null)        CompressedTankItem.setFluidVirtual(tankStack, fluid.copyWithAmount(1));
        return null;                                        return tankStack;
    return new FluidRequestKey(content.fluidId());       }
}
```

Comparison conclusion: Fluid uses `FluidManifestItem` to store `fluidId`; CFL uses a `CompressedTankItem` carrying `FluidStack` with `virtual=true`. Neither is actual container inventory. Both are fluid request/display placeholder items in the logistics system, and both use `copyWithAmount(1)` or an equivalent one-unit fluid representation to denote type.

---

### Supplemental Evidence 17: Fluid Rendering Inside Redstone Requester Slots

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL Old Version |
| --- | --- | --- |
| File | `mixin/client/RedstoneRequesterScreenMixin.java` | `mixin/client/RedstoneRequesterScreenMixin.java` |
| Evidence version | `F:\mcmod\fluid\fluid-2.0.0-decompiled` | `36e9bd6` (initial commit) |
| Topic | Rendering fluid icons in Redstone Requester ghost slots | Rendering fluid icons from virtual compressed tanks in Redstone Requester ghost slots |

**Code comparison:**

```java
// Fluid 2.0.0
protected void renderSlot(GuiGraphics graphics, Slot slot) {
    FluidStack fluid;
    ItemStack ghostStack;
    SlotItemHandler handlerSlot;
    int slotIndex;
    if (slot instanceof SlotItemHandler
            && (slotIndex = (handlerSlot = (SlotItemHandler)slot).getSlotIndex()) >= 0
            && slotIndex < ((RedstoneRequesterMenu)this.menu).ghostInventory.getSlots()
            && (ghostStack = ((RedstoneRequesterMenu)this.menu).ghostInventory.getStackInSlot(slotIndex))
                    .getItem() instanceof FluidManifestItem
            && !(fluid = FluidManifestItem.read(ghostStack)).isEmpty()) {
        FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid);
        return;
    }
    super.renderSlot(graphics, slot);
}
```

```java
// CFL old version 36e9bd6
@Override
protected void renderSlot(GuiGraphics graphics, Slot slot) {
    if (slot instanceof SlotItemHandler) {
        int slotIndex = slot.getSlotIndex();
        ItemStack itemStack = menu.ghostInventory.getStackInSlot(slotIndex);
        if (itemStack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(itemStack)) {
            FluidStack fluid = CompressedTankItem.getFluid(itemStack);
            if (!fluid.isEmpty()) {
                FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid);
                return;
            }
        }
    }
    super.renderSlot(graphics, slot);
}
```

**Similarities:** Both override the same `renderSlot(GuiGraphics, Slot)` rendering entry point, first check whether the slot is a `SlotItemHandler`, then read `menu.ghostInventory` by the same slot index, parse a fluid from the ghost item, call the identically named `FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid)` when non-empty, and return early; otherwise both fall back to `super.renderSlot(...)`. Fluid 2.0.0 replaces CFL's old `CompressedTankItem + isVirtual + getFluid` with `FluidManifestItem + read`, but the slot checks, ghost inventory access, non-empty fluid condition, and rendering call chain are basically the same.

### Supplemental Evidence 18: Fluid Rendering Inside Factory Panel Set-Item Screen Slots

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL Old Version |
| --- | --- | --- |
| File | `mixin/client/FactoryPanelSetItemScreenMixin.java` | `mixin/client/FactoryPanelSetItemScreenMixin.java` |
| Evidence version | `F:\mcmod\fluid\fluid-2.0.0-decompiled` | `36e9bd6` (initial commit) |
| Topic | Rendering fluid icons in ghost slots in the Factory Panel set-item screen | Rendering virtual fluid icons in ghost slots in the Factory Panel set-item screen |

**Code comparison:**

```java
// Fluid 2.0.0
protected void renderSlot(GuiGraphics graphics, Slot slot) {
    FluidStack fluid;
    ItemStack ghostStack;
    SlotItemHandler handlerSlot;
    int slotIndex;
    if (slot instanceof SlotItemHandler
            && (slotIndex = (handlerSlot = (SlotItemHandler)slot).getSlotIndex()) >= 0
            && slotIndex < ((FactoryPanelSetItemMenu)this.menu).ghostInventory.getSlots()
            && (ghostStack = ((FactoryPanelSetItemMenu)this.menu).ghostInventory.getStackInSlot(slotIndex))
                    .getItem() instanceof FluidManifestItem
            && !(fluid = FluidManifestItem.read(ghostStack)).isEmpty()) {
        FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid);
        return;
    }
    super.renderSlot(graphics, slot);
}
```

```java
// CFL old version 36e9bd6
@Override
protected void renderSlot(GuiGraphics graphics, Slot slot) {
    if (slot instanceof SlotItemHandler) {
        int slotIndex = slot.getSlotIndex();
        ItemStack itemStack = menu.ghostInventory.getStackInSlot(slotIndex);
        if (itemStack.getItem() instanceof CompressedTankItem
                && CompressedTankItem.isVirtual(itemStack)) {
            FluidStack fluid = CompressedTankItem.getFluid(itemStack);
            if (!fluid.isEmpty()) {
                FluidSlotRenderer.renderFluidSlot(graphics, slot.x, slot.y, fluid);
                return;
            }
        }
    }
    super.renderSlot(graphics, slot);
}
```

**Similarities:** Both implement the same goal in the same-named mixin for `FactoryPanelSetItemScreen`: showing a fluid icon in the ghost slot used to set the filter item. The code path is the same as the Redstone Requester evidence: `SlotItemHandler -> ghostInventory.getStackInSlot(slotIndex) -> read fluid -> FluidSlotRenderer.renderFluidSlot(...) -> return -> super.renderSlot(...)`. Fluid 2.0.0 only replaces CFL's old virtual compressed tank carrier with `FluidManifestItem`; the rest of the control flow and rendering destination remain highly consistent.

### Supplemental Evidence 19: Replacing Item Rendering with Fluid Rendering in StockKeeper Request List Entries

**Severity: High**

| Dimension | Fluid 2.0.0 | CFL Old Version |
| --- | --- | --- |
| File | `mixin/StockKeeperRequestScreenMixin.java` | `mixin/client/StockKeeperRequestScreenMixin.java` |
| Evidence version | `F:\mcmod\fluid\fluid-2.0.0-decompiled` | `36e9bd6` (initial commit) |
| Topic | Redirecting `GuiGameElement.of(ItemStack)` in `renderItemEntry` to replace item icons with fluid icons | Replacing item icons with fluids from virtual compressed tanks in the same rendering entry point |

**Code comparison:**

```java
// Fluid 2.0.0
@Redirect(
    method = {"renderItemEntry"},
    at = @At(
        value = "INVOKE",
        target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;",
        remap = false
    ),
    remap = false
)
private GuiGameElement.GuiRenderBuilder fluid$redirectGuiGameElementOf(ItemStack itemStack) {
    if (this.fluid$isFluidManifest && !this.fluid$cachedFluid.isEmpty()) {
        FluidSlotRenderer.renderFluidSlot(this.fluid$cachedGraphics, 0, 0, this.fluid$cachedFluid);
        return GuiGameElement.of((ItemStack)Blocks.AIR.asItem().getDefaultInstance());
    }
    return GuiGameElement.of((ItemStack)itemStack);
}
```

```java
// CFL old version 36e9bd6
@Redirect(
    method = "renderItemEntry",
    at = @At(
        value = "INVOKE",
        target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;",
        remap = false
    ),
    remap = false
)
private GuiGameElement.GuiRenderBuilder fluidlogistics$redirectGuiGameElementOf(
        ItemStack itemStack,
        @Local(argsOnly = true) GuiGraphics graphics) {
    if (fluidlogistics$isCompressedTank && fluidlogistics$cachedFluid != null) {
        FluidSlotRenderer.renderFluidSlot(graphics, 0, 0, fluidlogistics$cachedFluid);
        return GuiGameElement.of(Blocks.AIR.asItem().getDefaultInstance());
    }
    return GuiGameElement.of(itemStack);
}
```

**Similarities:** Both intercept the exact same `GuiGameElement.of(ItemStack)` call in `StockKeeperRequestScreen.renderItemEntry`; when a fluid entry is matched, both first draw the fluid with `FluidSlotRenderer.renderFluidSlot(...)` at `(0, 0)`, then return `GuiGameElement.of(Blocks.AIR.asItem().getDefaultInstance())` to replace the original item icon; otherwise both fall back to the original `GuiGameElement.of(itemStack)`. Fluid 2.0.0 replaces CFL old version's `fluidlogistics$isCompressedTank / fluidlogistics$cachedFluid` with `fluid$isFluidManifest / fluid$cachedFluid` and stores `GuiGraphics` in a field, but the intercepted target, control branch, fluid rendering call, and AIR placeholder replacement strategy are basically the same.

## 5. Architecture-Level Comparison

| Dimension | Fluid 2.0.0 | CFL |
|------|-------------|-----|
| Core block entity | `CanFillerBlockEntity` extends `PackagerBlockEntity` | `FluidPackagerBlockEntity` is independently implemented |
| Virtual item | `FluidManifestItem` + `FluidManifestContent` | `CompressedTankItem` + `FluidTankContent` |
| Logistics interface | `IFluidLogisticsPackager` | `IFluidPackager` |
| Goggle information | `CanFillerGoggleInfo` | `PackagerGoggleInfo` |
| Mixin prefix | `fluid$` | `fluidlogistics$` |

Fluid 2.0.0's main reproduction approaches still include:

1. Method-splitting copy: splitting CFL's inline logic into multiple private helper methods.
2. Rename-based replacement: replacing `CompressedTankItem` with `FluidManifestItem`, and replacing the `fluidlogistics$` prefix with `fluid$`.
3. Inheritance-based alternate implementation: simplifying the structure by extending Create's `PackagerBlockEntity` while retaining the core logistics process.
4. Simplified reproduction: preserving only the core paths related to CanFiller and Factory Panel interaction.

---

## 6. Overall Judgment

| Strength | Count | Representative Files |
|------|------|----------|
| Very high | 2 | `ClipboardAddressUtil`, `ClipboardSetAddressPacket` |
| High | 13 | `InventorySummaryMixin`, `FactoryPanelBehaviourMixin`, `CanFillerBlockEntityMixin`, `FactoryPanelBlockEntityMixin`, `CanFillerGoggleInfo`, `ICanFillerData`, `StockKeeperRequestScreenMixin`, `CanFillerBlockEntity` packaging/unpacking, `FluidPackagerBlockEntity` packaging/unpacking, `RedstoneRequesterScreenMixin` slot rendering, `FactoryPanelSetItemScreenMixin` slot rendering, `StockKeeperRequestScreenMixin` entry icon replacement |
| Medium | 4 | `FactoryPanelScreenMixin`, `FluidAmountHelper`, `FluidValueBoxRenderer`, virtual fluid request items |

Final judgment: In key features including fluid packaging/unpacking, virtual fluid request items, fluid rendering in Redstone Requester and Factory Panel ghost slots, fluid entry replacement in the StockKeeper request list, clipboard addresses, Factory Panel restocking, and ValueBox fluid rendering, Fluid 2.0.0 shows systematic code-structure similarity with CFL. The similarities are not limited to a single utility method or UI constant; they form a continuous implementation path across block entities, network packets, mixin injections, UI rendering, and address interfaces.
