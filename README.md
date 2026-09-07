# Beacon Garden

Beacon Garden is the first application built on JScene3D's hierarchical
entity-component world architecture. The current slice loads the real project
manifest and startup world, composes its authored camera, light, garden roots,
reusable beacon definition, independently authored collision resources, and
runtime behavior through headless adapters. Its Garden geometry is authored as
glTF and published at build time as a generated read-only entity definition
with immutable mesh and material resources.

JScene3D is currently consumed as a sibling source checkout. Install its current
snapshot before building this repository:

```shell
../threejs-java/mvnw -q -f ../threejs-java/pom.xml -DskipTests install
./mvnw clean verify
```

Run the build-time import publication followed by the headless startup path
with:

```shell
./mvnw -q -Prun-headless process-classes
```

The application should report six world roots, five live entities in the
generated Garden instance, an active primary camera, two collision objects
containing three independently authored shapes, and two entered overlaps
identifying the Beacon sensor's two exact shape components. It should also
report the garden indicator changing from intensity `2.5` to `6.0`, the
reusable Beacon instance's indicator changing from `0.0` to `1.5`, the pulse
changing from pending to active at the phase boundary, and both world adapters
closing.
