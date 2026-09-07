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

Build a relocatable application directory with:

```shell
./mvnw clean package -Pexport-directory
```

The engine-owned project exporter produces the resulting
`target/export/beacon-garden` directory from the authored project, published
imports, and Maven-resolved runtime JARs. Maven does not define the directory
layout or generate its launchers. The application directory contains the
application JAR, engine and third-party runtime JARs, host-selected LWJGL
natives, runtime project JSON, published import artifacts, and generic desktop
launch scripts. It deliberately contains no Java sources, tests, headless smoke
launcher, raw glTF source, Maven files, or import tooling dependencies. This
directory form uses the host platform's native libraries and requires an
installed Java 21 runtime.

Build a macOS application image with:

```shell
./mvnw clean package -Pexport-directory,export-macos-app
```

The engine-owned native exporter consumes the completed application directory
and produces `target/export-native/Beacon Garden.app`. The image retains the
generic `DesktopProjectLauncher`, packages the authored project and published
content, uses the `.icns` application icon declared by `identity.icon`, and
includes a Java 21 runtime, so the destination Mac does not need a separate
Java installation. This first native format is built for the current macOS
host and does not request a signing identity or notarization. Launch the generated
image with:

```shell
open "target/export-native/Beacon Garden.app"
```

Build a macOS disk image from that completed application image with:

```shell
./mvnw clean package -Pexport-directory,export-macos-app,export-macos-dmg
```

The engine-owned disk-image exporter reads the application name and native
version embedded in the `.app`; the build does not repeat or reconstruct that
identity. It stages the completed application image and an `/Applications`
symbolic link, creates the compressed image directly with `hdiutil`, then
transactionally installs `target/distributions/Beacon Garden-1.0.0.dmg`. DMG
assembly does not invoke Finder or AppleScript, so it uses the same
non-interactive path locally and on a macOS CI runner. Signing and notarization
remain outside this slice. Open the generated disk image with:

```shell
open "target/distributions/Beacon Garden-1.0.0.dmg"
```

Export profiles only assemble artifacts; they never deliberately start the
game. Structurally verify every exported form, including a read-only DMG mount,
with the following non-graphical command, which is intended for CI:

```shell
./mvnw clean verify -Pexport-directory,export-macos-app,export-macos-dmg,verify-export-directory,verify-macos-app,verify-macos-dmg
```

The directory verification runs a headless runtime probe from a relocated
copy. The macOS verifications inspect a relocated `.app`, verify the DMG, mount
it with `-nobrowse`, and inspect its packaged runtime and project content. They
do not start a graphical application. A developer or suitably configured CI
runner can explicitly exercise both native launch paths with:

```shell
./mvnw verify -Pexport-directory,export-macos-app,export-macos-dmg,verify-macos-launch
```

That last profile starts and closes the application image once, then starts and
closes the application once more from the read-only mounted DMG.
