# Beacon Garden

Beacon Garden is the first application built on JScene3D's hierarchical
entity-component world architecture. The current slice loads the real project
manifest and startup world, composes its authored camera, light, garden roots,
reusable beacon definition, independently authored collision resources, and
runtime behavior through the standard project environment. Its Garden geometry is authored as
glTF and published at build time as a generated read-only entity definition
with immutable mesh and material resources. The same project data can run
headlessly or through the generic native desktop host.

JScene3D is currently consumed as a sibling source checkout. Install its current
snapshot before building this repository:

```shell
../threejs-java/mvnw -q -f ../threejs-java/pom.xml -DskipTests install
./mvnw clean verify
```

Run the build-time import publication followed by Beacon Garden's scripted
headless integration smoke test with:

```shell
./mvnw -q -Prun-headless test-compile
```

The smoke test should report six world roots, five live entities in the
generated Garden instance, an active primary camera, two collision objects
containing three independently authored shapes, and two entered overlaps
identifying the Beacon sensor's two exact shape components. It should also
identify the Garden as an authored placement of its generated definition and
the Beacon Pulse as a runtime spawn of its authored definition. Finally, it should
report the garden indicator changing from intensity `2.5` to `6.0`, the
reusable Beacon instance's indicator changing from `0.0` to `1.5`, the pulse
changing from pending to active at the phase boundary, and both world adapters
closing.

Run the graphical project through the engine's generic desktop launcher with:

```shell
./mvnw -q -Prun-desktop process-classes
```

Close the native window normally. Press Space or the south face button on the
standard gamepad assigned to controller slot 0 to trigger the authored `pulse`
action. The initially muted-green garden should become brighter green after the
first pulse. The current pulse definition adds a persistent directional light,
so it has no visible mesh, does not fade, and is spawned only once.

Build and verify a relocatable application directory with:

```shell
./mvnw clean verify -Pexport-directory
```

The engine-owned project exporter produces the resulting
`target/export/beacon-garden` directory from the authored project, published
imports, and Maven-resolved runtime JARs. Maven does not define the image
layout or generate its launchers. The image contains the application JAR,
engine and third-party runtime JARs, host-selected LWJGL natives, runtime
project JSON, published import artifacts, and generic desktop launch scripts.
It deliberately contains no Java sources, tests, headless smoke launcher, raw
glTF source, Maven files, or import tooling dependencies. The export integration
test copies that image to a temporary directory outside the source checkout and
runs the project against only the relocated runtime files. This directory form
uses the host platform's native libraries and requires an installed Java 21
runtime; later native packaging can wrap the same engine-produced image with a
bundled runtime.
