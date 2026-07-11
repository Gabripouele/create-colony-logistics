# 2026-05-31 rc.12 FactoryPanelBehaviourMixin Loading Failure

## Summary

Runtime failure:

```text
InvalidMixinException:
@Shadow method getFilter in FactoryPanelBehaviourMixin was not located in target class FactoryPanelBehaviour.
```

Cause: `FactoryPanelBehaviourMixin` shadowed `getFilter()` as if it were declared directly on `FactoryPanelBehaviour`. In Create 6.0.6 runtime bytecode, `FactoryPanelBehaviour` does not declare `getFilter()`. The method is inherited from superclass `FilteringBehaviour`.

Fix: remove the invalid `@Shadow getFilter()` and call the inherited public API through a `FilteringBehaviour` cast:

```java
ItemStack filter = ((FilteringBehaviour) (Object) this).getFilter();
```

No snapshot architecture, routing, dispatch, package behavior, or performance strategy was changed.

## Runtime Class Inspected

Class:

```text
com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour
```

Artifact:

```text
Gradle cache artifact: maven.modrinth:create:tS7ygzAE
```

Command:

```text
javap -classpath <create jar> -p -s com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour
```

## FactoryPanelBehaviour Fields

Public/static:

- `public static final BehaviourType<FactoryPanelBehaviour> TOP_LEFT`
- `public static final BehaviourType<FactoryPanelBehaviour> TOP_RIGHT`
- `public static final BehaviourType<FactoryPanelBehaviour> BOTTOM_LEFT`
- `public static final BehaviourType<FactoryPanelBehaviour> BOTTOM_RIGHT`

Public instance:

- `public Map<FactoryPanelPosition, FactoryPanelConnection> targetedBy`
- `public Map<BlockPos, FactoryPanelConnection> targetedByLinks`
- `public Set<FactoryPanelPosition> targeting`
- `public List<ItemStack> activeCraftingArrangement`
- `public boolean satisfied`
- `public boolean promisedSatisfied`
- `public boolean waitingForNetwork`
- `public String recipeAddress`
- `public int recipeOutput`
- `public LerpedFloat bulb`
- `public FactoryPanelBlock.PanelSlot slot`
- `public int promiseClearingInterval`
- `public boolean forceClearPromises`
- `public UUID network`
- `public boolean active`
- `public boolean redstonePowered`
- `public RequestPromiseQueue restockerPromises`

Private instance:

- `private boolean promisePrimedForMarkDirty`
- `private int lastReportedUnloadedLinks`
- `private int lastReportedLevelInStorage`
- `private int lastReportedPromises`
- `private int timer`

## FactoryPanelBehaviour Methods

Constructor:

- `public FactoryPanelBehaviour(FactoryPanelBlockEntity, FactoryPanelBlock.PanelSlot)`

Public/static methods:

- `public static FactoryPanelBehaviour at(BlockAndTintGetter, FactoryPanelConnection)`
- `public static FactoryPanelBehaviour at(BlockAndTintGetter, FactoryPanelPosition)`
- `public static FactoryPanelSupportBehaviour linkAt(BlockAndTintGetter, FactoryPanelConnection)`
- `public static FactoryPanelSupportBehaviour linkAt(BlockAndTintGetter, FactoryPanelPosition)`
- `public static BehaviourType<?> getTypeForSlot(FactoryPanelBlock.PanelSlot)`

Public instance methods:

- `public void setNetwork(UUID)`
- `public void moveTo(FactoryPanelPosition, ServerPlayer)`
- `public void initialize()`
- `public void tick()`
- `public void lazyTick()`
- `public void checkForRedstoneInput()`
- `public void addConnection(FactoryPanelPosition)`
- `public FactoryPanelPosition getPanelPosition()`
- `public FactoryPanelBlockEntity panelBE()`
- `public void onShortInteract(Player, InteractionHand, Direction, BlockHitResult)`
- `public void enable()`
- `public void disable()`
- `public boolean isActive()`
- `public boolean isMissingAddress()`
- `public void destroy()`
- `public void disconnectAll()`
- `public void disconnectAllLinks()`
- `public int getUnloadedLinks()`
- `public int getLevelInStorage()`
- `public int getPromised()`
- `public void resetTimer()`
- `public void resetTimerSlightly()`
- `public void writeSafe(CompoundTag, HolderLookup.Provider)`
- `public void write(CompoundTag, HolderLookup.Provider, boolean)`
- `public void read(CompoundTag, HolderLookup.Provider, boolean)`
- `public float getRenderDistance()`
- `public MutableComponent formatValue(ValueSettings)`
- `public boolean setFilter(ItemStack)`
- `public void setValueSettings(Player, ValueSettings, boolean)`
- `public ValueSettingsBoard createBoard(Player, BlockHitResult)`
- `public MutableComponent getLabel()`
- `public ValueSettings getValueSettings()`
- `public MutableComponent getTip()`
- `public MutableComponent getAmountTip()`
- `public MutableComponent getCountLabelForValueBox()`
- `public int netId()`
- `public boolean isCountVisible()`
- `public BehaviourType<?> getType()`
- `public void displayScreen(Player)`
- `public int getIngredientStatusColor()`
- `public ItemRequirement getRequiredItems()`
- `public boolean canShortInteract(ItemStack)`
- `public boolean readFromClipboard(HolderLookup.Provider, CompoundTag, Player, Direction, boolean)`
- `public boolean writeToClipboard(HolderLookup.Provider, CompoundTag, Direction)`
- `public AbstractContainerMenu createMenu(int, Inventory, Player)`
- `public Component getDisplayName()`
- `public String getFrogAddress()`

Private instance methods:

- `private void moveToSlot(FactoryPanelBlock.PanelSlot)`
- `private void notifyRedstoneOutputs()`
- `private void tickStorageMonitor()`
- `private void tickRequests()`
- `private void tryRestock()`
- `private void sendEffect(FactoryPanelPosition, boolean)`
- `private InventorySummary getRelevantSummary()`
- `private int getConfigRequestIntervalInTicks()`
- `private int getPromiseExpiryTimeInTicks()`
- `private void tickOutline()`
- private lambda helpers generated by the compiler

## Superclass Member Check

Class:

```text
com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour
```

Relevant methods:

- `public ItemStack getFilter(Direction)`
- `public ItemStack getFilter()`

Therefore `getFilter()` exists in the runtime hierarchy, but not as a declared member of `FactoryPanelBehaviour`.

## Mixin Comparison

Before fix, `FactoryPanelBehaviourMixin` had:

```java
@Shadow(remap = false)
public abstract ItemStack getFilter();
```

That target is invalid because `FactoryPanelBehaviour` does not declare `getFilter()`.

Other targets:

- `@Inject(method = "getLevelInStorage", ...)` is valid: `FactoryPanelBehaviour` declares `public int getLevelInStorage()`.
- `@Shadow panelBE()` is valid: `FactoryPanelBehaviour` declares `public FactoryPanelBlockEntity panelBE()`.
- Reflection target `restocker` is valid on `FactoryPanelBlockEntity` based on prior rc.12 use and compile-time field access.
- Reflection target `getRestockedPackager` is intentionally reflective to avoid compile-time dependency on Create/Ponder virtual block entity types.

## Name/Mapping Analysis

The method did not change name and is not synthetic. It exists as:

```text
FilteringBehaviour.getFilter() : ItemStack
```

It is not declared on the target class. This is not a remapping issue. Field access is not needed because the public superclass method is available.

## Implemented Fix

Changed only `src/main/java/com/createcolonylogistics/mixin/FactoryPanelBehaviourMixin.java`:

- Removed invalid `@Shadow getFilter()`.
- Imported `FilteringBehaviour`.
- Replaced `getFilter()` call with `((FilteringBehaviour) (Object) this).getFilter()`.
- Kept the `getLevelInStorage` injection, snapshot lookup, routing/dispatch fallback behavior, and snapshot architecture unchanged.
- Matched the valid `panelBE()` shadow as `public abstract FactoryPanelBlockEntity panelBE()`.

## Validation

Build:

```text
$env:JAVA_HOME='<local Java 21 JDK>'; .\gradlew.bat build
```

Result: passed.

Server startup check:

```text
$env:JAVA_HOME='<local Java 21 JDK>'; .\gradlew.bat runServer
```

Result:

- Mixin subsystem initialized.
- The previous `InvalidMixinException` for `FactoryPanelBehaviourMixin.getFilter` did not appear.
- Startup did not reach a world because the dev run configuration is missing MineColonies runtime dependencies:
  - `domum_ornamentum`
  - `blockui`
  - `structurize`

Because of the missing dependencies, full server startup and in-world factory panel loading could not be verified in this local run. The mixin-loading failure under investigation was not reproduced after the fix.

## Scope Confirmation

No performance work was done in this pass.

No changes were made to:

- snapshot architecture
- routing logic
- dispatch logic
- package creation logic
- package fulfillment logic
- base Create behavior
- Smart Clipboard logic
- Smart Info tooltip formatting
- UI assets or screens
