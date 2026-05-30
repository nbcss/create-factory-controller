# Create Factory Controller — Implementation Plan

> Regenerated from commit `f311bce` by re-reading the actual source. Line numbers and
> behaviour below were verified against the committed code, not the previous plan text.

## Project Overview

A NeoForge 1.21.1 addon for the Create mod. The **Factory Controller** is a block that hosts a
virtual panel of "gauge" slots (validated against Deployer's `DeployerRegistries.PANEL`). Each
gauge monitors a Create logistics network for a specific item/amount and reports a
satisfied / promised / waiting status. Gauges can be connected to one another to represent
dependencies. The screen shows a 5-column board with vertical scrolling, a network selector,
per-cell status indicators, and a configure overlay.

---

## Resolved (post-f311bce working tree — compiles clean)

- [x] **BUG-1** — `FactoryControllerBlock.useWithoutItem` now opens via the consumer overload,
      `sp.openMenu(be, buf -> FactoryControllerMenu.writeExtraData(be, buf))`. Fixes the
      `IndexOutOfBoundsException` on interact.
- [x] **BUG-2** — `VirtualPanelBehaviour` no longer binds `controller::setChanged` when
      `controller == null` (client); uses a no-op `Runnable` instead. No more client-ctor NPE.
- [x] **BUG-3** — `FactoryControllerBlock` registers `FACING` (`createBlockStateDefinition` +
      `getStateForPlacement` + default state), so the `facing=*` blockstate variants resolve.
- [x] **BUG-4** — amount `EditBox` is created in `init()`, shown/seeded when a gauge is selected,
      receives click/key/char input, and is hidden via a shared `closeConfigureOverlay()`.
      `parseAmount()` now returns a real value → `ConfigureGaugePacket` sends the typed amount.
- [x] **GAP-1** — connection amount threaded through
      `drawConnection → addConnection(pos, amount)`; the screen sends the source gauge's amount
      instead of a hardcoded 1.
- [x] **FEAT-6** — block item added to **Create's** `BASE_CREATIVE_TAB` via
      `BuildCreativeModeTabContentsEvent`.

Still open: GAP-2 (remove-connection UI), GAP-3 (arrow bend), GAP-4 (horizontal scroll),
GAP-5 (live menu sync), FEAT-3/4/5 (restocking + recipes), FEAT-7 (lang cleanup), FEAT-8 (model).
Note: the client stock bar still calls `getLevelInStorage()` live; it is crash-safe
(`getSummaryOfNetwork` returns `EMPTY` on the client) but only accurate until GAP-5 lands.

---

## Current State (verified at commit f311bce, 2026-05-29)

### Implemented
- [x] Build setup: NeoForge 1.21.1, Create 6.0.11-292, Deployer 0.1.2 (`gradle.properties`)
- [x] `FactoryControllerBlock` — `HorizontalDirectionalBlock` + `IBE`, opens menu in `useWithoutItem`
- [x] `FactoryControllerBlockEntity` — `SmartBlockEntity` + `MenuProvider`; gauges map, known
      networks, selected network, full NBT (incl. `saveToItem`), per-tick gauge ticking
- [x] `VirtualPanelBehaviour` — filter/amount/upTo, `targetedBy`/`targeting` graph,
      satisfied/promisedSatisfied/waitingForNetwork status via `tickStorageMonitor`, NBT
- [x] `VirtualPanelConnection` (from, amount, arrowBendMode, success) + NBT
- [x] `VirtualPanelPosition` record(col, row) + NBT
- [x] `GaugeHelper` + `compat/DeployerGaugeHelper` — `DeployerRegistries.PANEL.containsKey` check
- [x] 6 play-to-server packets, all registered in `registerPayloads` and handled
      (network selection is client-only — no `SelectNetworkPacket`)
- [x] `FactoryControllerMenu` — server + client constructors, `writeExtraData`, player slots
- [x] `FactoryControllerScreen` — board render, network selector, configure overlay, connection
      draw mode, arrow rendering, keyboard handler
- [x] Block/item models, blockstate JSON, loot table, `en_us.json`

### NOT actually implemented (despite stubs)
- Storage **monitoring only** — nothing ever places a `PackageOrder`. `restockerPromises`,
  `recipeAddress`, `recipeOutput`, `promiseClearingInterval`, `forceClearPromises` are stored and
  serialized but never drive an order. `getPromised()` reads the queue but nothing enqueues.
- No creative tab → the block is unobtainable in survival.
- No play-to-client sync → the open screen never updates after open.

---

## Critical Bugs (block first run — fix before anything else)

### BUG-1: Menu open writes only the BlockPos → client constructor reads past end (CONFIRMED CRASH)
**File:** `FactoryControllerBlock.java` (`useWithoutItem` → `sp.openMenu(be, pos)`)
The `openMenu(MenuProvider, BlockPos)` overload writes **only** the `BlockPos`. The client
constructor reads the pos, then `buf.readVarInt()` for the gauge count and the rest — reading off
the end of the buffer. `FactoryControllerMenu.writeExtraData(...)` exists but is never wired up.

**Observed at runtime** (interacting with the placed controller):
```
Failed to open a screen with advanced data
IndexOutOfBoundsException: renderIndex(8) + length(1) exceeds writerIndex(8):
  UnpooledHeapByteBuf(ridx: 8, widx: 8, cap: 8/8)
```
`writerIndex(8)` = the 8 bytes of the `BlockPos` long and nothing else; the read index reaches 8
(pos consumed) and the next `readVarInt()` for the gauge count runs off the end. This is the
direct manifestation of writing only the pos.

**Fix:** use the consumer overload; the supplied buffer is already a `RegistryFriendlyByteBuf`,
so do **not** wrap it again:
```java
withBlockEntityDo(level, pos, be ->
    sp.openMenu(be, b -> FactoryControllerMenu.writeExtraData(be, b)));
```

### BUG-2: Client menu constructor NPEs on construction when any gauge exists
**File:** `VirtualPanelBehaviour` constructor, via `FactoryControllerMenu` client ctor →
`VirtualPanelBehaviour.fromNBT(null, ...)`.
The constructor runs `this.restockerPromises = new RequestPromiseQueue(controller::setChanged);`.
With `controller == null`, the bound method reference `controller::setChanged` is evaluated
eagerly and throws **NPE immediately at construction** — not "if ever called". So once BUG-1 is
fixed, opening the screen on a controller that has gauges crashes here.

**Fix:** make `restockerPromises` lazy or client-safe — e.g.
`new RequestPromiseQueue(controller == null ? () -> {} : controller::setChanged)`, or skip
allocating it client-side. Add a client-safe `fromNBT` path that never touches the controller.

### BUG-3: Block has no `FACING` property but the blockstate declares `facing=*` variants
**Files:** `FactoryControllerBlock.java` + `blockstates/factory_controller.json`
`HorizontalDirectionalBlock` declares the `FACING` constant but does **not** auto-register it.
`FactoryControllerBlock` overrides neither `createBlockStateDefinition` nor `getStateForPlacement`,
so the block state has no `facing` property — yet the blockstate JSON has `facing=north/…/east`
variants. State→model mapping fails (missing-model render) and placement never sets a direction.

**Fix:**
```java
@Override
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
    b.add(FACING);
}
@Override
public BlockState getStateForPlacement(BlockPlaceContext ctx) {
    return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
}
```

### BUG-4: Amount `EditBox` never created → amount is always 1
**File:** `FactoryControllerScreen.java` — `amountBox` is declared but `init()` only calls
`super.init()`. The overlay draws an "Amount:" label but no input. `parseAmount()` returns 1 when
`amountBox == null`, so `ConfigureGaugePacket` always sends `amount = 1`.

**Fix:** create `amountBox` in `init()`, render it in `renderConfigureOverlay`, route
`charTyped`/`keyPressed` to it while the overlay is open, and seed its value from the gauge.

---

## Functional Gaps (work, but incompletely wired)

### GAP-1: Connection amount is dropped twice, and the UI hardcodes 1
- `FactoryControllerBlockEntity.drawConnection(from, to, amount)` calls
  `gauges.get(to).addConnection(from)` — drops `amount`.
- `VirtualPanelBehaviour.addConnection(from)` hardcodes `new VirtualPanelConnection(from, 1)`.
- `FactoryControllerScreen.handleBoardCellClick` sends `new DrawConnectionPacket(..., 1)` — the
  UI never collects an amount.
**Fix:** thread `amount` through `addConnection(VirtualPanelPosition, int)` ← `drawConnection`,
and add an amount input to connection draw mode (depends on BUG-4's EditBox work).

### GAP-2: No way to remove a connection from the UI
`RemoveConnectionPacket` is registered and handled (`be.removeConnection`), but the screen never
sends it. There is no delete-link interaction. **Fix:** add a remove gesture (e.g. shift-click a
target cell in connect mode, or a delete button in the overlay).

### GAP-3: Arrow bend is non-functional end to end
- `cycleArrowBend` only ever produces modes `0–3` (`(mode + 1) % 4`); the `-1` "auto" value from
  the `VirtualPanelConnection` default is never reachable via cycling.
- `drawArrow` ignores `arrowBendMode` entirely — it always draws a horizontal-then-vertical
  L-line. **Fix:** make `drawArrow` respect `arrowBendMode`; decide whether `-1` is selectable.

### GAP-4: Horizontal scroll never happens
`mouseScrolled` only mutates `scrollRow`; `scrollCol` is always 0. The board is effectively a
fixed 5 columns with unbounded vertical scroll. Either document this as intended or add column
scrolling / a scrollbar (the layout reserves `+18` px for one that isn't drawn).

### GAP-5: Open menu goes stale (no play-to-client sync)
The client menu holds a **snapshot** deserialized at open time. When `sendData()` fires server-side
(status changes, config edits), the open screen is not refreshed. **Fix:** add a play-to-client
`SyncPanelStatePacket` sent after server-side mutations; the screen rebuilds `menu.gauges` from it.
(Server-side the menu does hold live `VirtualPanelBehaviour` references, but the screen is client.)

---

## Features To Implement (in order)

### FEAT-1: Land the four critical bugs + GAP-1
Nothing renders or opens correctly until BUG-1..4 are fixed; GAP-1 unblocks meaningful links.

### FEAT-2: Live menu sync (`SyncPanelStatePacket`) — see GAP-5
Play-to-client packet registered in `CreateFactoryController`, sent after BE state changes,
handled by the screen to refresh its gauge list.

### FEAT-3: Active restocking via `PackageOrder`
`tickStorageMonitor` currently only computes status. Add a restock path:
- Add a per-gauge "restock mode" toggle.
- When `!promisedSatisfied && restockMode`, place a `PackageOrder` /
  `PackageOrderWithCrafts` on `networkId` for the missing `demand - inStorage - promised`, via
  `LogisticsManager` / `Create.LOGISTICS`.
- Track the resulting `RequestPromise` in `restockerPromises`; wire `forceClearPromises` /
  `promiseClearingInterval` (currently dead) into the expiry logic.
- Gate work with the existing `timer` field, which is decremented but currently gates nothing.

### FEAT-4: Connection-driven restocking
When source A links to target B and B is unsatisfied, A triggers its restock. Set
`VirtualPanelConnection.success` so the arrow renders green vs. amber (rendering already keys off
`conn.success`).

### FEAT-5: Recipe trigger (`recipeAddress` / `recipeOutput`)
Expose a text field in the configure overlay; use it to request crafting on the network when stock
is low. Build on FEAT-3.

### FEAT-6: Add the block item to Create's creative tab
The controller item should appear in **Create's existing creative tab**, not a new one. Subscribe
to `BuildCreativeModeTabContentsEvent` on the mod event bus and, when
`event.getTabKey()` matches Create's tab, `event.accept(FACTORY_CONTROLLER_ITEM)`:
```java
modEventBus.addListener(this::addToCreativeTab);
// ...
private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
        event.accept(FACTORY_CONTROLLER_ITEM);
}
```
(Confirm the exact Create tab handle — `AllCreativeModeTabs.BASE_CREATIVE_TAB` / `.PALETTES_CREATIVE_TAB`
— against the Create version in `gradle.properties`; pick the main blocks/logistics tab.) Without
this the block is unobtainable in survival. The leftover `itemGroup.createfactorycontroller` lang
key can then be dropped as part of FEAT-7 since no own tab is created.

### FEAT-7: `en_us.json` cleanup
Remove leftover template keys: `block...example_block`, `item...example_item`, and the entire
`createfactorycontroller.configuration.*` block (no config screen exists). Add real keys for any
new UI strings (the screen currently hardcodes English in `drawString`).

### FEAT-8: Proper block model / texture
Current model is a placeholder cube. Design a directional front-panel texture; the `FACING`
rotation in the blockstate already exists (and is fixed by BUG-3).

---

## Architecture Notes

- **Gauge validation:** `GaugeHelper.isValidGauge` → `DeployerGaugeHelper` →
  `DeployerRegistries.PANEL.containsKey(itemId)`. Any registered panel item is accepted; no
  hardcoded list. Tuning is not required.
- **Network assignment (`attachComponent`):** a tuned gauge (`LogisticallyLinkedBlockItem.isTuned`)
  contributes its `networkFromStack` UUID to `knownNetworks`. An untuned gauge uses the selected
  network, which is a **client-only GUI choice** sent on `AttachComponentPacket` (the server no
  longer tracks a `selectedNetwork`). The server validates the supplied network is in
  `knownNetworks`, else rejects with a red action-bar message. Removed gauges refund an
  **untuned** item (no UUID).
- **Connection direction:** `targetedBy` on the *destination* stores incoming
  `VirtualPanelConnection`s keyed by source position; `targeting` on the *source* stores outgoing
  destination positions. `addConnection` / `removeConnection` / `disconnectAll` keep both sides in
  sync (caps incoming links at 9).
- **Status model:** `demand = amount * (upTo ? 1 : maxStackSize)`; `satisfied = inStorage >= demand`;
  `promisedSatisfied = inStorage + promised >= demand`; `waitingForNetwork = unloadedLinks > 0`.
- **Sync model:** server owns state. Client gets a one-shot snapshot via `writeExtraData` at open.
  All mutations go client → play-to-server packet → BE method → `setChanged()` + `sendData()`. A
  play-to-client refresh is missing (GAP-5 / FEAT-2).
- **Ticking:** `setLazyTickRate(20)` is set, but `tick()` (every tick) drives `gauge.tick()`, so
  monitoring runs every server tick regardless. `disconnectAll` also calls `sendData()` inside its
  loops — minor inefficiencies to revisit.

---

## File Map

| File | Role |
|------|------|
| `CreateFactoryController.java` | Deferred registries, payload registration, screen binding |
| `FactoryControllerBlock.java` | Block; opens menu on right-click (needs `FACING` — BUG-3) |
| `FactoryControllerBlockEntity.java` | Server state, tick, gauge attach/remove/configure, NBT |
| `VirtualPanelBehaviour.java` | Per-gauge config, status monitor, connection graph, restock stubs |
| `VirtualPanelConnection.java` | (from, amount, arrowBendMode, success) + NBT |
| `VirtualPanelPosition.java` | (col, row) record + NBT |
| `GaugeHelper.java` | Thin wrapper over `DeployerGaugeHelper` |
| `compat/DeployerGaugeHelper.java` | `DeployerRegistries.PANEL` membership check |
| `packet/*.java` | 6 play-to-server packets (`RemoveConnectionPacket` unused by UI — GAP-2) |
| `FactoryControllerMenu.java` | Snapshot sync + `writeExtraData`; client ctor NPE risk (BUG-2) |
| `FactoryControllerScreen.java` | Client UI; `amountBox` uninitialized (BUG-4) |
