# Create Factory Controller — Implementation Plan

## Project Overview

A NeoForge 1.21.1 addon for the Create mod. The **Factory Controller** is a block that hosts a 
virtual panel of "gauge" slots (backed by Deployer's gauge registry). Each gauge monitors a 
Create logistics network for a specific item/amount. Gauges can be connected to each other to 
represent dependencies or trigger restocking. The screen shows a scrollable 5×4 grid with 
live status indicators, a network selector, and a configure overlay.

---

## Current State (as of 2026-05-29)

### Done
- [x] Build setup: NeoForge 1.21.1, Create 6.0.11-292, Deployer 0.1.2 (see `gradle.properties`)
- [x] `FactoryControllerBlock` — HorizontalDirectionalBlock, opens menu on right-click
- [x] `FactoryControllerBlockEntity` — SmartBlockEntity with gauges map, known networks, NBT, tick delegation
- [x] `VirtualPanelBehaviour` — per-gauge: filter/amount/upTo config, connection graph, satisfied/promisedSatisfied/waitingForNetwork status, storage monitoring tick
- [x] `VirtualPanelConnection` — from, amount, arrowBendMode, success fields + NBT
- [x] `VirtualPanelPosition` — record(col, row) + NBT
- [x] `GaugeHelper` + `DeployerGaugeHelper` — Deployer registry validation
- [x] All 7 play-to-server packets registered and handled
- [x] `FactoryControllerMenu` — server + client constructors, player inventory slots
- [x] `FactoryControllerScreen` — 5×4 scrollable grid, network selector, configure overlay skeleton, connection draw mode, arrow rendering, keyboard handler
- [x] Block/item models, blockstate JSON, loot table, en_us.json skeleton

---

## Known Bugs (must fix before first run)

### BUG-1: Menu extra data not written on open (CRASH)
**File:** `FactoryControllerBlock.java:31`  
`sp.openMenu(be, pos)` only serializes the `BlockPos` as extra data.  
The client constructor `FactoryControllerMenu(int, Inventory, RegistryFriendlyByteBuf)` reads 
`buf.readBlockPos()` and then expects gauge + network data — it will crash immediately.

**Fix:** Change the block's `useWithoutItem` to write full extra data:
```java
sp.openMenu(be, buf -> {
    RegistryFriendlyByteBuf rbuf = new RegistryFriendlyByteBuf(buf, sp.level().registryAccess());
    FactoryControllerMenu.writeExtraData(be, rbuf);
});
```

### BUG-2: Amount EditBox never initialized (silent failure)
**File:** `FactoryControllerScreen.java` — `amountBox` is declared but `init()` never creates it.  
`parseAmount()` always returns 1. `ConfigureGaugePacket` always sends `amount=1`.

**Fix:** Add `amountBox` creation in `init()` and render it in `renderConfigureOverlay`.

### BUG-3: `drawConnection` ignores amount parameter
**File:** `VirtualPanelBehaviour.java:155`  
`addConnection` hardcodes `new VirtualPanelConnection(fromPos, 1)` ignoring the `amount` arg 
passed from `DrawConnectionPacket`.

**Fix:** Pass the amount through: `new VirtualPanelConnection(fromPos, amount)` where the 
amount comes from a parameter added to `addConnection(VirtualPanelPosition, int)`.

### BUG-4: Menu state goes stale after open
`FactoryControllerMenu.gauges` is a snapshot taken at open time. When `sendData()` fires on 
the BE (e.g., satisfied status changes), the open menu is not updated.

**Fix (option A — simplest):** Add a play-to-client `SyncPanelStatePacket` sent when BE state 
changes, which the screen handles to refresh its menu's gauge list.  
**Fix (option B):** Override `broadcastChanges()` in the menu and use `ContainerData` for 
per-gauge status booleans.  
→ Option A preferred; it keeps the existing NBT sync path.

### BUG-5: `VirtualPanelBehaviour` constructed with null controller on client
`VirtualPanelBehaviour.fromNBT(null, ...)` is called in the client menu constructor.  
`restockerPromises` is initialized with `controller::setChanged` which would NPE if ever called 
client-side.

**Fix:** Guard with `controller != null` in the `RequestPromiseQueue` constructor call, or add 
a client-safe factory method.

---

## Incomplete Features (implement in order)

### FEAT-1: Fix BUG-1 + BUG-2 + BUG-3 first
These are blocking — nothing works without them.

### FEAT-2: Live menu sync (SyncPanelStatePacket)
- Add `SyncPanelStatePacket` (play-to-client), registered in `CreateFactoryController`
- Sent from BE after `sendData()` whenever gauge states change
- Screen handles it by refreshing `menu.gauges` from the packet's NBT payload

### FEAT-3: Amount input in configure overlay
- In `FactoryControllerScreen.init()`: create `amountBox = new EditBox(...)` positioned inside 
  the overlay area (ox+28, oy+20, width=60)
- In `renderConfigureOverlay`: call `amountBox.render(gfx, mouseX, mouseY, partialTick)`
- Wire `charTyped` and `keyPressed` to `amountBox` when overlay is open
- `parseAmount()` already reads from `amountBox` correctly once it's non-null

### FEAT-4: Active restocking via PackageOrder
`VirtualPanelBehaviour` has `recipeAddress`, `recipeOutput`, and `restockerPromises` stubs.  
The intended behavior: when `satisfied == false`, the controller should send a `PackageOrder` 
(or `PackageOrderWithCrafts`) to the logistics network to top up the missing items.

Steps:
- Add a "restock mode" toggle to `VirtualPanelBehaviour` (on/off)
- In `tickStorageMonitor`: if `!satisfied && restockMode`, call 
  `LogisticsManager` / `Create.LOGISTICS` to place an order for `(amount - inStorage)` units
- Track the resulting `RequestPromise` in `restockerPromises`
- Set `VirtualPanelConnection.success = true` on connected gauges when the promise resolves

### FEAT-5: Connection-driven restocking
When a source gauge (A) connects to a target gauge (B):
- If B is not satisfied, A should trigger its restock action
- Arrow color: green (`success=true`) when B has been restocked since last check, amber otherwise

### FEAT-6: `recipeAddress` — crafting trigger
- Expose a text field in the configure overlay for `recipeAddress`
- Used to look up a crafting recipe on the Create network and request crafting when stock is low
- Wire into `tickStorageMonitor` after FEAT-4 is stable

### FEAT-7: Creative tab
Register an `ItemDisplayParameters`-based creative tab in `CreateFactoryController` containing 
`FACTORY_CONTROLLER_ITEM`. Add translation key (already present in `en_us.json`).

### FEAT-8: `en_us.json` cleanup
Remove leftover template keys: `example_block`, `example_item`, `logDirtBlock`, `magicNumber*`, 
`configuration.*` (unless a real config screen is planned).

### FEAT-9: Block model / textures
Current model is a placeholder cube. Design and add a proper factory controller texture 
(front face with a display panel aesthetic). Update `factory_controller.json` blockstate/model 
to use `FACING` property for rotation.

---

## Architecture Notes

- **Gauge validation** goes through `DeployerGaugeHelper → DeployerRegistries.PANEL`. Any item 
  registered in Deployer's panel registry is accepted. No hardcoded list.
- **Network ID** comes from `LogisticallyLinkedBlockItem.networkFromStack()` when a tuned gauge 
  is placed; untuned gauges inherit `selectedNetwork`.
- **Connection direction**: `targetedBy` on the *destination* gauge stores incoming connections. 
  `targeting` on the *source* gauge stores outgoing positions. Both must stay in sync.
- **Arrow bend**: `arrowBendMode` in `VirtualPanelConnection` is 0–3 (fixed cardinal) or -1 
  (auto). Currently only cycled via `R` key on the source cell; rendering is a simple L-shaped 
  line in `drawArrow` — needs to respect the bend mode.
- **Menu sync model**: Server owns all state. Client gets a snapshot at open time. All mutations 
  go through play-to-server packets → BE method → `setChanged()` + `sendData()` + 
  `SyncPanelStatePacket` (after FEAT-2).

---

## File Map

| File | Role |
|------|------|
| `CreateFactoryController.java` | Deferred registries, packet registration, screen binding |
| `FactoryControllerBlock.java` | Block: opens menu on right-click |
| `FactoryControllerBlockEntity.java` | All server-side state + tick |
| `VirtualPanelBehaviour.java` | Per-gauge logic, storage monitoring, connection graph |
| `VirtualPanelConnection.java` | Connection metadata (from, arrowBendMode, success) |
| `VirtualPanelPosition.java` | (col, row) record |
| `GaugeHelper.java` | Thin wrapper delegating to DeployerGaugeHelper |
| `compat/DeployerGaugeHelper.java` | Deployer registry check |
| `packet/*.java` | 7 play-to-server packets |
| `FactoryControllerMenu.java` | Menu: snapshot sync + extra data serialization |
| `FactoryControllerScreen.java` | Full client UI |
