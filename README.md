# Beacon Garden

Beacon Garden is the first application built on JScene3D's hierarchical
entity-component world architecture. The current slice loads the real project
manifest and startup world, composes its authored camera, light, garden roots,
and reusable beacon definition, then exercises activation and cleanup through
headless adapters.

JScene3D is currently consumed as a sibling source checkout. Install its current
snapshot before building this repository:

```shell
../threejs-java/mvnw -q -f ../threejs-java/pom.xml -DskipTests install
./mvnw clean verify
```

Run the headless startup path with:

```shell
./mvnw -q -Prun-headless compile
```

The application should report that it loaded five world roots, activated a
primary camera, and closed the spatial adapter.
