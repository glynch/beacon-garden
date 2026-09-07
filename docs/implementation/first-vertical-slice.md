# Beacon Garden first vertical slice

Status: implemented

Beacon Garden is the first consumer of JScene3D's accepted
[entity-component world architecture](../../../threejs-java/docs/design/entity-component-world-architecture.md).
Its purpose is to prove the smallest useful end-to-end path before Doomed
Corridors migrates. It must exercise the real authoring, validation,
composition, rendering, physics, behavior, and spawning interfaces rather than
introducing application-only substitutes.

This plan intentionally starts with data and runtime behavior. A visual editor
will consume the same definitions and descriptors later; an editor-specific
document model is not part of this slice.

## Outcome

The slice is complete when Beacon Garden can be launched from its project
directory and the engine can:

1. load and validate its project manifest and startup world;
2. instantiate locally authored entities and reusable entity definitions;
3. render a camera, light, meshes, and one imported glTF definition;
4. construct an ordinary Java behavior component from its registered type;
5. prepare and spawn a reusable entity definition during gameplay;
6. commit the spawn atomically between runtime phases;
7. show authored and runtime-created entities distinctly in a live diagnostic
   hierarchy;
8. detect an overlap through a collision sensor containing multiple shapes;
9. deliver a typed signal to behavior that changes visible instance state;
10. disable, re-enable, and destroy an owned entity subtree correctly;
11. close the world without leaking runtime resources or backend handles.

These acceptance points are implemented and exercised through focused engine
tests, Beacon Garden's headless project-host integration tests, its graphical
smoke path, and its relocated exported-application integration test.

Invalid fixtures must demonstrate stable diagnostics for missing assets,
definition cycles, component conflicts, unresolved required references,
invalid signal connections, and unavailable component types.

## Concrete acceptance world

The first world should be deliberately small but should use every important
seam:

```text
Garden World
├── Camera                         locally authored entity
├── Sun                            locally authored entity
├── Garden                         placed generated glTF EntityDefinition
├── Beacon A                       placed authored EntityDefinition
└── Garden behavior               locally authored entity
    └── spawns Beacon Pulse        runtime-created EntityDefinition instance
```

The authored `Beacon` definition should contain a 3D transform, visible mesh or
light presentation, a Java behavior component, and a collision sensor with two
independently transformed shapes. A signal from the sensor should invoke a
typed behavior action that produces an observable visual change.

The runtime-spawned `Beacon Pulse` is present as an asset and can be opened as a
definition. It is not placed in the authored world. Each spawned instance
appears in the live hierarchy only for its own lifetime.

The content and visual treatment can change without changing these acceptance
relationships.

## Implementation sequence

Each stage should leave its module compiling and its tests passing. Public Java
names should be selected by interface design and tests rather than copied
blindly from the conceptual names in the architecture document.

### 1. Definition and identity kernel

Implement in JScene3D:

- distinct `AssetId`, `EntityId`, `ComponentId`, component-type ID, and
  property/endpoint identity value types;
- typed asset references with optional diagnostic path hints;
- immutable `ProjectManifest`, `WorldDefinition`, `EntityDefinition`, local
  entity, placement, component-definition, and public-contract values;
- one representation for inline entity children and nested definition
  placements;
- stable placement identity without a wrapper entity;
- deterministic UTF-8 JSON readers and writers;
- an asset catalog that indexes headers by stable asset ID and rejects
  duplicates.

Start with in-memory definitions in unit tests, then prove that JSON loading
produces the same values. Do not involve rendering, physics, Maven extension
loading, or a game loop in this stage.

### 2. Descriptor registry and structural validation

Implement the registered component-type catalog and its validation seam:

- versioned property schemas and defaults;
- capability provision and requirements;
- multiplicity and conflict rules;
- spatial-domain requirements and single-primary-transform validation;
- lifecycle and schedule declarations;
- typed signals and actions;
- preservation of unavailable component records;
- asset-format and component-schema migration in memory only;
- definition inclusion cycle detection;
- ordered structured diagnostics.

Register only the types needed by the acceptance world. The descriptor must be
usable by the loader, runtime, tests, and future Inspector without duplicating
metadata.

### 3. World and live entity kernel

Introduce `World` as the explicit runtime composition root. The host supplies
its module implementations; the world does not construct native backends or
use static globals.

Implement:

- multiple root entities;
- entity ownership, enabled state, and stable live addresses;
- ordinary runtime components produced through the registered construction
  seam;
- creation, activation, deactivation, and destruction lifecycle;
- owner-first startup and child-first shutdown;
- effective descendant disablement with preservation of local enabled flags;
- explicit world module access through stable interfaces;
- complete reverse-order cleanup after both success and failed construction.

Use fake module adapters to test the world without graphics or native audio.
Do not introduce a dependency-injection framework. Keep component construction
behind one seam so a DI adapter can be evaluated later.

### 4. Composition and reference binding

Implement one transactional composer for both world loading and later runtime
spawning:

1. resolve the complete structural definition closure;
2. acquire immutable runtime-resource leases;
3. allocate inactive entities and components;
4. bind stable internal and public-contract references;
5. validate capabilities and transform authority;
6. connect typed signals and actions;
7. register participating components with world modules and schedules;
8. activate atomically or roll back everything.

Support world-local entities, reusable root placements, nested placements,
initial root spatial placement, exported parameters, and exported attachment
points. Do not implement definition inheritance, arbitrary internal overrides,
or imported-definition refinement.

### 5. Scheduling, signals, and safe mutation

Implement one logical simulation thread with the initial closed phase order:

```text
input acquisition
fixed before-physics behavior
physics
fixed after-physics behavior and physics signals
frame presentation update
render preparation/submission
```

Structural mutation commits between phases. A newly spawned entity never runs
in the requesting phase. An entity marked for destruction becomes effectively
inactive immediately and is cleaned up at the next commit.

Signals dispatch synchronously using a stable connection snapshot. Results
from any worker thread enter the world through a queue and are delivered only
at a defined simulation phase.

### 6. Minimal 3D realization

Provide built-in descriptors and runtime adapters for only:

- `Transform3d`;
- perspective camera;
- one directional or ambient light;
- mesh rendering with immutable mesh/material assets;
- the minimum instance-visible material or light value needed for the signal
  acceptance case.

The 3D adapter maps these components to existing JScene3D rendering objects.
Those renderer objects remain implementation details; `Entity` does not extend
`Object3D` and rendering does not traverse the ownership hierarchy as its
public scheduling model.

### 7. glTF publication

Implement glTF project import without routing generated project content through
the lower-level renderer `Scene` graph. A selected scene publishes:

- a generated read-only `EntityDefinition`;
- referenced immutable mesh, material, texture, skin, and animation assets as
  supported by the current glTF profile;
- stable source identity and provenance sufficient for conservative reimport.

Map glTF nodes to entities and supported features to components. Do not add a
public `Model3d`/`ModelInstance3d` path. The runtime may privately optimize the
result after correctness is proven.

### 8. Minimal collision realization

Replace the existing project/runtime physics architecture with a physics module
and component interfaces that provide:

- one static collision body where needed by the garden;
- one non-blocking sensor/area body;
- at least two shape records owned by that sensor;
- stable entity/component references from the collision object to its shapes;
- typed enter/exit or contact signals identifying object and shape;
- explicit transform authority and safe physics registration lifecycle.

No collision is inferred from rendered meshes. The behavior component decides
the result of the overlap signal.

### 9. Preparation and runtime spawning

Implement preparation separately from spawning:

- prepare the `Beacon Pulse` definition and its transitive resources before
  gameplay needs it;
- request an owned spawn during before-physics behavior;
- expose the provisional asynchronous `SpawnOperation` result;
- instantiate without blocking I/O;
- activate at the next phase boundary;
- inspect and then destroy the live instance;
- verify failure and cancellation release every acquired resource.

Spawning into a different owner requires an explicitly supplied scoped spawn
target. Do not expose unrestricted world mutation to every behavior component.

### 10. Beacon Garden project assembly

Only after the engine seams above exist, add Beacon Garden's real project
files and Java behavior module:

- project manifest;
- startup world definition;
- authored beacon and pulse definitions;
- imported garden source and import recipe;
- component descriptor and factory for garden behavior;
- launcher/host wiring that explicitly constructs the world modules;
- headless validation and lifecycle tests;
- graphical smoke test.

The application owns names, rules, and presentation specific to Beacon Garden.
No Beacon Garden term belongs in a generic JScene3D module.

## Verification at every stage

The implementation should maintain three test levels:

- focused module tests through each module's public interface;
- headless project fixtures proving loading, validation, composition,
  scheduling, and cleanup together;
- one graphical smoke test launched from the same project data used by the
  headless tests.

Tests should assert observable results and diagnostics, not internal collection
layouts. The runtime is free to change storage or compile definitions without
changing those tests.

## Deliberately excluded from this slice

- a complete visual editor;
- dependency-injection framework selection;
- definition inheritance or variants;
- arbitrary nested-definition overrides;
- save games or live-authoring persistence;
- networking and deterministic replay;
- generalized world streaming;
- full animation authoring;
- complete 2D and UI implementations;
- broad component catalogs;
- pooling and high-volume entity optimization;
- Doomed Corridors migration.

These exclusions keep the implementation small. The accepted architecture
still requires that none of the first-slice interfaces assume all entities are
3D, all worlds are graphical, rendered meshes own collision, runtime content is
authored in Java, or every spawned object must be represented by an editor
placement.

## Doomed Corridors compatibility checks

Before accepting a public interface introduced by this slice, test it mentally
against these later uses:

- a WAD-derived world with many generated static render and collision records;
- a player entity owning camera, input, character body, weapons, and audio;
- a gun referencing several independent projectile definitions;
- runtime bullets that report exact collision shapes and destroy themselves;
- pickup sensors with no rendered collision geometry;
- doors, lifts, switches, and teleports driven by typed signals/actions;
- enemies instantiated repeatedly with independent animation and physics state;
- imported models wrapped with game behavior and collision;
- HUD/UI ownership alongside a 3D world without a mixed-domain entity;
- effects whose internal particles are batched records rather than forced
  entities.

Passing this check means the interface does not block these cases. It does not
mean implementing them in Beacon Garden.

## First coding decision after documentation review

The first code change should be the definition and identity kernel in JScene3D,
with tests for immutable in-memory definitions and stable typed references. It
should not begin by rewriting the renderer, implementing the visual editor, or
adapting Doomed Corridors. Those consumers should be connected only after the
canonical model and validation seam exist.
