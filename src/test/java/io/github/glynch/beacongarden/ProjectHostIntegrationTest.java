/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.asset.AssetKind;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectHostException;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.runtime.RuntimeEntityId;
import io.github.glynch.jscene3d.project.runtime.SpawnStatus;
import io.github.glynch.jscene3d.project.spatial3d.HeadlessSpatial3dEnvironment;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises Beacon Garden through the generic manifest-selected project-host boundary. */
final class ProjectHostIntegrationTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final AssetId BEACON_PULSE_ASSET = AssetId.from("d4c94a8e-678c-4438-bbef-2f92103f8149");
    private static final EntityId GARDEN_BEHAVIOR_ENTITY = EntityId.from("d19ae6cf-a8cd-437b-a00a-27dffdd903dd");
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("beaconGarden.projectRoot", "."))
            .toAbsolutePath()
            .normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Loads, activates, and closes the authored startup project without application-specific loader code. */
    @Test
    void loadsAndActivatesStartupWorld() {
        HostedProject loaded = load(PROJECT_ROOT);
        Spatial3dWorldModule spatial = loaded.world().requireModule(Spatial3dWorldModule.class);

        assertThat(loaded.project().identity().id()).isEqualTo("io.github.glynch.beacon-garden");
        assertThat(loaded.assets().assets())
                .extracting(metadata -> metadata.kind())
                .containsExactly(AssetKind.ENTITY_DEFINITION, AssetKind.ENTITY_DEFINITION, AssetKind.WORLD_DEFINITION);
        assertThat(loaded.world().roots())
                .extracting(entity -> entity.name().orElseThrow())
                .containsExactly("Camera", "Sun", "Garden", "Beacon A", "Garden Behavior");
        assertThat(loaded.world().isActive()).isFalse();
        assertThat(spatial.isReadyToRender()).isFalse();

        loaded.world().activate();

        assertThat(loaded.world().isActive()).isTrue();
        assertThat(spatial.isReadyToRender()).isTrue();
        loaded.close();
        assertThat(loaded.world().isClosed()).isTrue();
        assertThat(spatial.isClosed()).isTrue();
    }

    /** Discovers the manifest-selected application provider and prepares its authored pulse dependency. */
    @Test
    void preparesAndSpawnsAuthoredPulseDefinition() {
        HostedProject loaded = load(PROJECT_ROOT);
        try (loaded) {
            Entity behavior = authoredRoot(loaded, GARDEN_BEHAVIOR_ENTITY);
            GardenBehavior gardenBehavior = behavior.component(GardenBehavior.COMPONENT_ID, GardenBehavior.class)
                    .orElseThrow();

            assertThat(gardenBehavior.pulseDefinition().id()).isEqualTo(BEACON_PULSE_ASSET);

            loaded.world().activate();
            assertThat(behavior.children()).isEmpty();
            loaded.world().advanceFixed(Duration.ofMillis(16L));

            assertThat(gardenBehavior.pulseStatusAtRequest()).isEqualTo(SpawnStatus.PENDING);
            assertThat(gardenBehavior.pulseSpawn().status()).isEqualTo(SpawnStatus.ACTIVE);
            assertThat(behavior.children()).singleElement().satisfies(pulse -> {
                assertThat(pulse.name()).contains("Beacon Pulse");
                assertThat(pulse.parent()).contains(behavior);
                assertThat(pulse.authoredAsset()).isEqualTo(BEACON_PULSE_ASSET);
                assertThat(pulse.isEnabled()).isTrue();
                assertThat(gardenBehavior.pulseSpawn().entity()).contains(pulse);
            });

            loaded.world().advanceFixed(Duration.ofMillis(16L));
            assertThat(behavior.children()).hasSize(1);
        }
    }

    /** Removes the runtime pulse from world lookup, ownership, and its spatial registration. */
    @Test
    void destroysRuntimePulseAndReleasesSpatialRegistration() {
        HostedProject loaded = load(PROJECT_ROOT);
        try (loaded) {
            Entity behavior = authoredRoot(loaded, GARDEN_BEHAVIOR_ENTITY);
            Spatial3dWorldModule spatial = loaded.world().requireModule(Spatial3dWorldModule.class);
            loaded.world().activate();
            loaded.world().advanceFixed(Duration.ofMillis(16L));
            Entity pulse = behavior.children().getFirst();
            RuntimeEntityId pulseId = pulse.id();

            assertThat(spatial.findTransform(pulse)).isPresent();

            loaded.world().destroy(pulse);

            assertThat(pulse.isDestroyed()).isTrue();
            assertThat(pulse.isEnabled()).isFalse();
            assertThat(behavior.children()).isEmpty();
            assertThat(loaded.world().find(pulseId)).isEmpty();
            assertThat(spatial.findTransform(pulse)).isEmpty();
        }
    }

    /** Rejects invocation without the required project path before touching project state. */
    @Test
    void rejectsMissingApplicationArgument() {
        String[] arguments = {};

        assertThatThrownBy(() -> BeaconGardenApplication.main(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one Beacon Garden project-directory path");
    }

    /** Runs the supported headless application entry point against the generic project host. */
    @Test
    void runsHeadlessApplication() {
        String[] arguments = {PROJECT_ROOT.toString()};

        BeaconGardenApplication.main(arguments);
    }

    /** Identifies an invalid project manifest before scanning or composing content. */
    @Test
    void rejectsInvalidManifest() throws IOException {
        Files.writeString(temporaryDirectory.resolve("project.json"), "{}\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("project manifest loading failed");
    }

    /** Identifies a declared application extension absent from the runtime class path. */
    @Test
    void rejectsMissingApplicationExtension() throws IOException {
        copyProject();
        replace(
                temporaryDirectory.resolve("project.json"),
                "io.github.glynch.beacon-garden",
                "io.github.glynch.missing-extension");

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("extension catalog loading failed");
    }

    /** Identifies duplicate stable identities while building the definition catalog. */
    @Test
    void rejectsInvalidAssetCatalog() throws IOException {
        copyProject();
        Files.copy(
                temporaryDirectory.resolve("entities/beacon.entity.json"),
                temporaryDirectory.resolve("entities/duplicate.entity.json"));

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("asset catalog loading failed");
    }

    /** Identifies a regular manifest entry file which the definition catalog cannot address. */
    @Test
    void rejectsUncataloguedStartupAsset() throws IOException {
        copyProject();
        Path uncatalogued = temporaryDirectory.resolve("worlds/uncatalogued.json");
        Files.writeString(uncatalogued, "{}\n", StandardCharsets.UTF_8);
        replace(temporaryDirectory.resolve("project.json"), "worlds/garden.world.json", "worlds/uncatalogued.json");

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("startup world is absent from the asset catalog");
    }

    /** Identifies an entity definition selected where a startup world is required. */
    @Test
    void rejectsEntityDefinitionAsStartupWorld() throws IOException {
        copyProject();
        replace(temporaryDirectory.resolve("project.json"), "worlds/garden.world.json", "entities/beacon.entity.json");

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("startup asset is not a world definition");
    }

    /** Preserves structured diagnostics when catalog-aware startup composition fails. */
    @Test
    void rejectsInvalidStartupWorld() throws IOException {
        copyProject();
        replace(
                temporaryDirectory.resolve("worlds/garden.world.json"),
                "io.github.glynch.jscene3d.spatial3d/transform-3d",
                "io.github.glynch.beacon-garden/missing-component");

        assertThatThrownBy(() -> load(temporaryDirectory))
                .isInstanceOf(ProjectHostException.class)
                .hasMessageContaining("startup world composition failed");
    }

    /** Creates the same generic host that an editor preview or exported launcher supplies. */
    private static HostedProject load(Path projectRoot) {
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION, ProjectHostIntegrationTest.class.getClassLoader(), new HeadlessSpatial3dEnvironment());
        return host.load(projectRoot);
    }

    /** Copies the authored project documents into the test's isolated directory. */
    private void copyProject() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("entities"));
        Files.createDirectories(temporaryDirectory.resolve("worlds"));
        Files.copy(PROJECT_ROOT.resolve("project.json"), temporaryDirectory.resolve("project.json"));
        Files.copy(
                PROJECT_ROOT.resolve("entities/beacon.entity.json"),
                temporaryDirectory.resolve("entities/beacon.entity.json"));
        Files.copy(
                PROJECT_ROOT.resolve("entities/beacon-pulse.entity.json"),
                temporaryDirectory.resolve("entities/beacon-pulse.entity.json"));
        Files.copy(
                PROJECT_ROOT.resolve("worlds/garden.world.json"),
                temporaryDirectory.resolve("worlds/garden.world.json"));
    }

    /** Replaces one exact authored fragment using deterministic UTF-8 I/O. */
    private static void replace(Path path, String target, String replacement) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).replace(target, replacement);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** Finds one authored world root by its stable local identity. */
    private static Entity authoredRoot(HostedProject loaded, EntityId authoredId) {
        return loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(authoredId))
                .findFirst()
                .orElseThrow();
    }
}
