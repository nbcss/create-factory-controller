# Connection / Component Architecture Rework — Plan

Status: **draft** (design agreed; not yet implemented)
Last updated: 2026-06-24

## 1. Goal & non-goals

**Goal.** Replace the hardcoded, pairwise (`instanceof gauge/link`) connection validation + creation
— currently duplicated across the client check, the hover preview, and the server — with a generic,
data-driven model that:

- makes the **server** the single authoritative validator (client mirrors it exactly);
- scales to **new component / connection kinds** without editing an N×N pair matrix
  (near-term: a **logic tube** that takes a redstone input and emits a redstone output; later an
  **integer** type for a math unit);
- keeps validation logic split **per component type** (a gauge defines gauge rules, a link defines
  link rules) instead of one monolithic method.

**Non-goals (for this rework).**
- Redstone *multi-hop graph semantics* (tube chains, evaluation order, feedback) — flagged, scoped
  separately.
- New types beyond what is needed to prove the model (LOGISTICS, REDSTONE; INTEGER is sketched
  but not built).

## 2. What's wrong today

- `completeConnection` (client), `renderHoverTarget` (preview), and `FactoryControllerBlockEntity
  .addConnection` (server) each re-derive the same link/gauge rules with `instanceof`. Adding a third
  type explodes this into an O(N²) pair matrix in three places.
- The server's check is a thin guard inside `addConnection` (silent no-op on dup/cap) — it is **not**
  the same ruleset the client enforces, so the server is effectively more permissive than the UI.
- A connection's role assignment (who is source / sink / where it's stored) is hardcoded for the two
  current types. The redstone wire's direction is mode-dependent, which the current code special-cases.
- Connections are stored **per component** (`AbstractVirtualComponent.targetedBy`), so "who owns the
  wire" is implicit in *where it is stored* — which is exactly what tangles the gauge↔link case.

## 3. Target model

### 3.1 Types

A **type** is the category of a wire: *what flows, how it's stored, how it draws.* `Connection.Type`
is not a registry — only INTEGER is on the horizon; revisit if addons must add types.

```java
public abstract class Connection.Type {
    LOGISTICS(Uniqueness.DIRECTED),     // item/fluid ingredient flow (gauge → gauge)
    REDSTONE (Uniqueness.UNDIRECTED);   // boolean gating/state signal (gauge ↔ link, future tubes)
    // INTEGER(Uniqueness.DIRECTED)     // FUTURE: numeric value for a math unit

    public enum Uniqueness {            // nested in Connection.Type
        DIRECTED,    // A→B unique; B→A is a separate, allowed wire
        UNDIRECTED   // one wire per unordered pair (also blocks redstone feedback between tubes)
    }

    public final Uniqueness uniqueness;
    Connection.Type(Uniqueness u) { this.uniqueness = u; }
}
```

Each type also owns:
- a **payload type + NBT codec** — `LogisticsConnection{amount, bend}`, `RedstoneConnection
  {powered, bend}`, future `IntConnection{value, bend}` (these already exist as `VirtualPanelConnection`
  subclasses; only their dispatch changes);
- a **renderer** — *registered client-side only* (keep render classes off the server). The
  link-vs-gauge color/flow branching currently in `VirtualConnectionRenderer` moves here, one renderer
  per type.

> **Direction is NOT a type property** — it is resolved from the endpoints' live roles (§3.3).

### 3.2 Ports (per-component capability declaration)

A **port** is a component's declared participation in a type: a `(type, capability)` pair.
Components declare a list of ports; the resolver reads them instead of `instanceof`.

```java
public enum PortRole { SOURCE, SINK, BOTH }     // capability AND live-role use this type

public record Port(Connection.Type type, PortRole capability) {}
```

**Capability** is static and direction-independent — used for *validity*. A wire of type C is
structurally possible iff one end `canSource(C)` and the other `canSink(C)`.

| Component        | Declared ports                                             |
|------------------|------------------------------------------------------------|
| **Gauge**        | `(LOGISTICS, BOTH)`, `(REDSTONE, BOTH)`                     |
| **Redstone link**| `(REDSTONE, BOTH)`                                          |
| **Logic tube** *(future)* | `(REDSTONE, BOTH)`, `(INTEGER, BOTH)`             |
| **Math unit** *(future)*  | `(INTEGER, BOTH)`                                  |

### 3.3 Live role & direction resolution (no weight, no owner)

Separate from capability, each component reports a **live role** per type: a *decisive* `SOURCE`/
`SINK` when it knows its role, or `BOTH` when it defers. This resolves direction with **no priority
number**:

```java
PortRole liveRole(Connection.Type type);   // on the component
```

- Redstone link: `liveRole(REDSTONE) = receive ? SOURCE : SINK`  ← decisive (the mode)
- Gauge:         `liveRole(REDSTONE) = BOTH`, `liveRole(LOGISTICS) = BOTH`  ← defers
- Logic tube:    fixed per port (`SOURCE` on its out-port, `SINK` on its in-port)

**Resolution rule:** a decisive role beats `BOTH`; if both defer, use the stored creation order; if
both are decisive they must agree.

| Wire                       | A liveRole        | B liveRole       | resolved        |
|----------------------------|-------------------|------------------|-----------------|
| gauge ↔ RECEIVE link       | gauge `BOTH`      | link `SOURCE`    | link → gauge    |
| gauge ↔ SEND link          | gauge `BOTH`      | link `SINK`      | gauge → link    |
| gauge → gauge (logistics)  | `BOTH`            | `BOTH`           | stored order    |
| tube → tube (redstone)     | out `SOURCE`      | in `SINK`        | fixed           |

This is why **owner and weight are dropped** — the link dictating redstone direction falls out of
`liveRole`, not a priority value; and with central storage (§4) there is no "where it's stored" to own.

### 3.4 Validation contract — split `validateAsSource` / `validateAsSink`, `ValidationResult` (implemented)

Rules attach to a **role**. Each component implements two methods returning a {@code ValidationResult} (a lazy
message, so the preview never builds the {@code Component}); the resolver calls source-side then sink-side:

```java
record ValidationResult(boolean isSuccess, @Nullable Supplier<Component> message) {
    static final ValidationResult SIMPLE_SUCCESS = new ValidationResult(true);
    static ValidationResult fail(Supplier<Component> message) { ... }
}

List<Port>       ports();                                   // declared capabilities (no instanceof matrix)
PortRole         liveRole(Connection.Type type);            // decisive SOURCE/SINK or BOTH (direction)
ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink);
ValidationResult validateAsSink  (Connection.Type type, VirtualComponentBehaviour source);
```

| Component | ports | `validateAsSource` | `validateAsSink` |
|-----------|-------|--------------------|------------------|
| **Gauge** | `(LOGISTICS,BOTH)`, `(REDSTONE,BOTH)` | LOGISTICS: filter non-empty | LOGISTICS: `usedInputSlots < MAX_INGREDIENTS` |
| **Link**  | `(REDSTONE,BOTH)`, `liveRole = receive?SOURCE:SINK` | reject a link partner | reject a link partner |
| **Abstract (default)** | none | **FAIL-CLOSED** (reject) | **FAIL-CLOSED** (reject) |

- **Fail-closed default:** a component must explicitly permit a connection by overriding the `validateAs*` — forgetting
  rules never silently allows a wire. The `ports()` declaration gates which types are even possible; the
  `validateAs*` refine them.
- **Partner restrictions** (link rejects link) live on the link only — no duplication, because the resolver consults
  the relevant *role's* method.
- **Uniqueness** is the type's policy (LOGISTICS directed, REDSTONE undirected), checked centrally by the resolver
  against `targetedBy` (Phase 1) → central graph (Phase 2).
- The gauge's **slot cap** resolves sibling filters via `siblingAt` (server: controller; client: injected menu
  lookup) — blocker §6.1, done.

### 3.5 The resolver + call sites (implemented)

`ConnectionResolver.validate(a, b, creationSink) → Result(type, source, sink, ValidationResult)` — first shared
type → orient (decisive `liveRole`, else `creationSink`) → `validateAsSource` + `validateAsSink` → uniqueness; on
success it also attaches the green confirmation message. `Result.ok()` = type resolved && validation success. All
three sites call it with `(clicked, initiator, initiator)` and render `result.validation().message()` uniformly:
- **Hover preview** (`renderHoverTarget`): `ok()` → white, else red (message never built).
- **Client commit** (`completeConnection`): show `message.get()` for either outcome; on `ok()` send `AddConnectionPacket`.
- **Server apply** (`FactoryControllerBlockEntity.addConnection`): `!ok()` → deny; else store (Phase-1 owner swap) + sync.

*Status:* **implemented and compiles** — all three sites route through the resolver; the inline `instanceof` pair-logic
is gone from the screen and the BE (BE keeps only a storage-side swap, removed in Phase 2).

## 4. Data structure & persistence

### 4.1 Move connections to the controller (central edge list)

Replace per-component `targetedBy`/`targeting` with one edge list on the controller. Ownership becomes
*unnecessary* (no `owner` field — see §3.3); only the **stable creation order** `(a,b)` is kept, as the
direction fallback + record of intent.

```java
// on FactoryControllerBlockEntity
public record Connection(Connection.Type type,
                         VirtualPanelPosition a,        // creation-order endpoints (a = creation source)
                         VirtualPanelPosition b,
                         VirtualPanelConnection payload) {}

private final List<Connection> connections = new ArrayList<>();
// cached adjacency index, rebuilt on mutate, for O(1) tick/render access:
private transient Map<VirtualPanelPosition, List<Connection>> edgesByCell;
```

- `targetedBy()` / `targeting()` on components become **views** over the adjacency index (so existing
  call sites — tick, redstone gating, recipe screen, renderer — keep working), or are replaced by
  `controller.edgesAt(pos)` / `incoming(pos)` / `outgoing(pos)`.
- Removing a component drops every edge referencing it (graph sweep) — no owner bookkeeping.

### 4.2 Version key + migration  — **IMPLEMENTED (Phase 2)**

Version scheme: **absent (0) → 1** (no released data uses 1 yet, so the central format IS version 1; `DATA_VERSION`
stays 1, no bump). The new format writes `Ver: 1` plus a central `Connections` edge list; the **read discriminates by
the presence of `Connections`** (robust — also handles a Phase-0 dev world that wrote `Ver:1` with the old
per-component layout).

```java
public static final int DATA_VERSION = 1;
// write:  tag.putInt("Ver", DATA_VERSION); tag.put("Connections", connectionGraph.toNBT());
// read:   if (tag.contains("Connections")) connectionGraph.readNBT(...);   // new format
//         else for each component b.flushLegacyConnections();              // migrate old per-component
```

Migration (old/absent → central) is **mechanical and lossless** — the old per-component storage already encodes
everything (a wire stored on X ⇒ X owns it). Each component's `fromNBT` parks its old `TargetedBy` into a transient
buffer; the controller flushes those into the graph (`owner = that component`, type from the payload subclass).
- For each component, for each `targetedBy` entry `key → payload`:
  - `type` = from the payload subclass (`LogisticsConnection→LOGISTICS`, `RedstoneConnection→REDSTONE`).
  - `a` = `key` (the old map key is the source position), `b` = the component's own position.
  - emit `Connection(type, a, b, payload)`.
- Drop per-component connection NBT; rebuild the adjacency index + `targeting` views from the edge list.

## 5. Suggested package layout

Consolidate connection types under `content.connection`:
`Connection.Type`, `Uniqueness`, `PortRole`, `Port`, `ConnectionResolver`, and the existing
`VirtualPanelConnection` / `LogisticsConnection` / `RedstoneConnection` (currently split between
`content` and `content.component`). Optional but reduces churn later.

## 6. Known blockers

1. **Client sibling resolution (must fix first).** Client behaviours are built with `controller == null`
   (`FactoryControllerMenu` line 68), so the gauge slot-cap (`validateAsSink` LOGISTICS) can't resolve
   sibling filters. Inject a side-agnostic lookup: server uses `controller.components`, the client menu
   injects `componentsByPosition::get`. Without this the resolver can't run identically on the client.
2. **`targetedBy`/`targeting` call-site breadth.** Many places read these (tick, gating, recipe screen,
   renderer). Keep them as views over the central index in Phase 2 to avoid a big-bang rewrite.
3. **Renderer coupling.** `VirtualConnectionRenderer` hardcodes link/gauge color + flow + arrow flip.
   Generalize to per-type renderers reading `liveRole` for direction.
4. **Redstone multi-hop semantics** (tube chains, cycles, evaluation order) — out of scope here; the
   data model must merely not preclude arbitrary directed redstone edges.
5. **Migration safety.** Guard behind `Ver`; write a unit/manual test loading a pre-rework controller.

## 7. Phased implementation

- **Phase 0 — versioning (tiny, do immediately).** Add `"Ver": Integer` to controller NBT
  (`DATA_VERSION = 1`), read with a `0` fallback. No behaviour change. Establishes the migration anchor.
- **Phase 1 — generic validation (no storage change).** Add `Connection.Type`/`Port`/`PortRole`,
  `ports()` + `liveRole()` + `validateAsSource/Sink` on gauge & link, and `ConnectionResolver`. Inject
  the client sibling lookup (§6.1). Route the three call sites through the resolver. Server becomes
  authoritative; `instanceof` pair-logic deleted from the GUI/BE. Storage stays per-component (the
  resolver's uniqueness query reads `targetedBy`).
- **Phase 2 — central storage + migration. ✅ IMPLEMENTED (compiles; needs in-game testing).** Edges live in a
  controller-level `ConnectionGraph`; version absent→1 (no bump); read discriminated by `Connections` presence; old
  saves migrate via each component's parked legacy buffer; `targetedBy/targeting` are live views; `moveComponent`→
  `graph.rename`, batch→`graph.remap`; sync stays per-component on the wire, client rebuilds its graph. *(Original note
  below kept for reference; superseded on the version-bump detail.)* Move edges to the controller `Connection` list, bump
  `DATA_VERSION = 2`, add the migration, replace `targetedBy/targeting` with views, switch the
  resolver's uniqueness query to the central graph. Drop owner/weight entirely (already gone from the
  model).
- **Phase 3 — type-owned rendering + logic tube.** Move renderer branching onto types. Add the
  logic tube as: ports table row + (reused) `RedstoneConnection` + its component class. No resolver
  changes needed.
- **Phase 4 (separate) — redstone graph semantics.** Multi-hop evaluation, cycle handling.

## 8. Open questions

- `validateAsSource/Sink` return a fully-styled `Component`, or a `(reasonKey, style)` so callers
  control styling? (Leaning: return styled; callers just display.)
- Adjacency index: rebuild-on-mutate (simple) vs incremental maintenance (faster, more code). Leaning
  rebuild-on-mutate given board sizes.
- Where does the type→renderer registration live so the server never classloads render code?
  (Likely a client-init map keyed by `Connection.Type`.)
