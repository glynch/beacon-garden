/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.ProjectInput;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.asset.AssetKind;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.desktop.StandardProjectEnvironment;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.extension.RegisteredType;
import io.github.glynch.jscene3d.project.physics3d.CollisionShape3d;
import io.github.glynch.jscene3d.project.physics3d.CollisionShape3dResource;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectHostException;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeEntityId;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.SpawnStatus;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.spatial3d.DirectionalLight3d;
import io.github.glynch.jscene3d.project.spatial3d.Material3dResource;
import io.github.glynch.jscene3d.project.spatial3d.Mesh3dResource;
import io.github.glynch.jscene3d.project.spatial3d.MeshRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises Beacon Garden through the generic manifest-selected project-host boundary. */
final class ProjectHostIntegrationTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final AssetId BEACON_PULSE_ASSET = AssetId.from("d4c94a8e-678c-4438-bbef-2f92103f8149");
    private static final AssetId GARDEN_DEFINITION = AssetId.from("0dff6469-a27b-3b7e-b680-45ace4bd46b9");
    private static final EntityId GARDEN_BEHAVIOR_ENTITY = EntityId.from("d19ae6cf-a8cd-437b-a00a-27dffdd903dd");
    private static final EntityId GARDEN_PLACEMENT = EntityId.from("cf795ee1-fe86-4b46-bbc1-b98ca9107fe5");
    private static final EntityId BEACON_PLACEMENT = EntityId.from("e9f4ab33-c25f-4dc7-b08c-1de71a83175c");
    private static final EntityId SUN_ENTITY = EntityId.from("e30e1867-444a-45c8-93ce-c8aaad2a1813");
    private static final ComponentId SUN_LIGHT = ComponentId.from("224343e5-57fe-462f-9205-56f6ae1fd218");
    private static final ComponentId BEACON_LIGHT = ComponentId.from("8c594962-2f31-4917-ab5f-56caa132a688");
    private static final ComponentId SENSOR_BOX = ComponentId.from("4575ddab-ce91-4d79-84a1-14849cd6a792");
    private static final ComponentId SENSOR_SPHERE = ComponentId.from("6b7d9fd9-d036-40d9-8528-a179c3f08390");
    private static final ComponentId GARDEN_MESH_RENDERER = ComponentId.from("8b369046-b207-379f-850b-39d099ed2da8");
    private static final InputAction PULSE = new InputAction("pulse");
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("beaconGarden.projectRoot", "."))
            .toAbsolutePath()
            .normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Ensures direct IDE test execution has the same published inputs as the Maven lifecycle. */
    @BeforeAll
    static void publishProjectImports() {
        BeaconGardenContentPublisher.publish(PROJECT_ROOT, importCache(PROJECT_ROOT));
    }

    /** Loads, activates, and closes the authored startup project without application-specific loader code. */
    @Test
    void loadsAndActivatesStartupWorld() {
        HostedProject loaded = load(PROJECT_ROOT);
        Spatial3dWorldModule spatial = loaded.world().requireModule(Spatial3dWorldModule.class);
        Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
        InputWorldModule input = loaded.world().requireModule(InputWorldModule.class);

        assertThat(loaded.project().identity().id()).isEqualTo("io.github.glynch.beacon-garden");
        assertThat(loaded.assets().assets())
                .extracting(metadata -> metadata.kind())
                .containsExactly(AssetKind.ENTITY_DEFINITION, AssetKind.ENTITY_DEFINITION, AssetKind.WORLD_DEFINITION);
        assertThat(loaded.world().roots())
                .extracting(entity -> entity.name().orElseThrow())
                .containsExactly("Camera", "Sun", "Garden", "Garden Overlap Target", "Beacon A", "Garden Behavior");
        assertThat(loaded.world().isActive()).isFalse();
        assertThat(spatial.isReadyToRender()).isFalse();
        assertThat(physics.collisionObjectCount()).isEqualTo(2);
        assertThat(physics.collisionShapeCount()).isEqualTo(3);
        assertThat(input.snapshot()).isEqualTo(ActionSnapshot.empty());

        loaded.world().activate();

        assertThat(loaded.world().isActive()).isTrue();
        assertThat(spatial.isReadyToRender()).isTrue();
        loaded.close();
        assertThat(loaded.world().isClosed()).isTrue();
        assertThat(spatial.isClosed()).isTrue();
        assertThat(physics.isClosed()).isTrue();
    }

    /** Delivers both authored Beacon shape overlaps and changes the referenced light instance. */
    @Test
    void handlesAuthoredMultiShapeOverlap() {
        HostedProject loaded = load(PROJECT_ROOT);
        Entity behaviorEntity = authoredRoot(loaded, GARDEN_BEHAVIOR_ENTITY);
        GardenBehavior behavior = behaviorEntity
                .component(GardenBehavior.COMPONENT_ID, GardenBehavior.class)
                .orElseThrow();
        DirectionalLight3d indicator = authoredRoot(loaded, SUN_ENTITY)
                .component(SUN_LIGHT, DirectionalLight3d.class)
                .orElseThrow();
        Entity beacon = authoredRoot(loaded, BEACON_PLACEMENT);
        DirectionalLight3d beaconIndicator =
                beacon.component(BEACON_LIGHT, DirectionalLight3d.class).orElseThrow();
        BeaconResponse beaconResponse = beacon.component(BeaconResponse.COMPONENT_ID, BeaconResponse.class)
                .orElseThrow();
        CollisionShape3dResource sensorResource;
        try (loaded) {
            sensorResource = beacon.component(SENSOR_BOX, CollisionShape3d.class)
                    .orElseThrow()
                    .resource();
            assertThat(indicator.intensity()).isEqualTo(2.5F);
            assertThat(beaconIndicator.intensity()).isZero();
            assertThat(behavior.enteredOverlaps()).isEmpty();
            assertThat(beaconResponse.overlaps()).isEmpty();

            loaded.world().activate();
            loaded.world().advanceFixed(Duration.ofMillis(16L));

            assertThat(behavior.enteredOverlaps()).hasSize(2);
            assertThat(behavior.enteredSensorShapeIds()).containsExactlyInAnyOrder(SENSOR_BOX, SENSOR_SPHERE);
            assertThat(indicator.intensity()).isEqualTo(6.0F);
            assertThat(beaconResponse.overlaps()).hasSize(2);
            assertThat(beaconResponse.indicatorIntensity()).isEqualTo(1.5F);
            assertThat(sensorResource.isClosed()).isFalse();
        }
        assertThat(sensorResource.isClosed()).isTrue();
    }

    /** Publishes and composes the Garden glTF as project-native entities and owned runtime resources. */
    @Test
    void composesPublishedGardenDefinitionAndReleasesResources() throws IOException {
        copyProject();
        Path cache = importCache(temporaryDirectory);
        BeaconGardenContentPublisher.publish(temporaryDirectory, cache);
        BeaconGardenContentPublisher.publish(temporaryDirectory, cache);
        MeshRenderer3d renderer;
        Mesh3dResource mesh;
        Material3dResource material;

        try (HostedProject loaded = load(temporaryDirectory, cache)) {
            Entity garden = authoredRoot(loaded, GARDEN_PLACEMENT);
            Entity generatedRoot = garden.children().getFirst();
            Entity westBed = generatedRoot.children().getFirst();
            renderer = westBed.component(GARDEN_MESH_RENDERER, MeshRenderer3d.class)
                    .orElseThrow();
            mesh = renderer.mesh();
            material = renderer.material();

            assertThat(generatedRoot.authoredAsset()).isEqualTo(GARDEN_DEFINITION);
            assertThat(generatedRoot.name()).contains("Garden Beds");
            assertThat(generatedRoot.children())
                    .extracting(entity -> entity.name().orElseThrow())
                    .containsExactly("West Bed", "Central Bed", "East Bed");
            assertThat(renderer.isVisible()).isTrue();
            assertThat(mesh.isClosed()).isFalse();
            assertThat(material.isClosed()).isFalse();
        }

        assertThat(renderer.isClosed()).isTrue();
        assertThat(mesh.isClosed()).isTrue();
        assertThat(material.isClosed()).isTrue();
    }

    /** Rejects a payload whose runtime value violates the descriptor-declared overlap contract. */
    @Test
    void rejectsInvalidBeaconOverlapPayload() {
        BeaconResponse response = new BeaconResponse();
        CapturingComponentEndpoints endpoints = new CapturingComponentEndpoints();
        response.bindEndpoints(endpoints);
        RuntimePayload invalidPayload =
                new RuntimePayload(new RegisteredType("io.github.glynch.beacon-garden/invalid-payload", 1), "invalid");

        RuntimePayloadAction payloadAction = endpoints.payloadAction();

        assertThatThrownBy(() -> payloadAction.execute(invalidPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CollisionOverlap3d");
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
            assertThat(behavior.children()).isEmpty();
            publishPulse(loaded);
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
            publishPulse(loaded);
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
    void rejectsMissingHeadlessSmokeArgument() {
        String[] arguments = {};

        assertThatThrownBy(() -> BeaconGardenHeadlessSmoke.main(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project-directory and published-content-directory");
    }

    /** Rejects build-time publication without both explicit filesystem locations. */
    @Test
    void rejectsMissingPublisherArguments() {
        String[] arguments = {};

        assertThatThrownBy(() -> BeaconGardenContentPublisher.main(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("project-directory and import-cache");
    }

    /** Runs the scripted headless smoke entry point against the generic project host. */
    @Test
    void runsHeadlessSmoke() {
        String[] arguments = {PROJECT_ROOT.toString(), importCache(PROJECT_ROOT).toString()};

        BeaconGardenHeadlessSmoke.main(arguments);
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
        return load(projectRoot, importCache(projectRoot));
    }

    /** Creates a generic host with one explicit published-import cache. */
    private static HostedProject load(Path projectRoot, Path cacheRoot) {
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new StandardProjectEnvironment(cacheRoot));
        return host.load(projectRoot);
    }

    /** Returns the Maven-owned publication location used by the headless application. */
    private static Path importCache(Path projectRoot) {
        return projectRoot.resolve("target/import-cache");
    }

    /** Copies the authored project documents into the test's isolated directory. */
    private void copyProject() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("entities"));
        Files.createDirectories(temporaryDirectory.resolve("assets"));
        Files.createDirectories(temporaryDirectory.resolve("config"));
        Files.createDirectories(temporaryDirectory.resolve("imports"));
        Files.createDirectories(temporaryDirectory.resolve("resources"));
        Files.createDirectories(temporaryDirectory.resolve("worlds"));
        Files.copy(PROJECT_ROOT.resolve("project.json"), temporaryDirectory.resolve("project.json"));
        Files.copy(PROJECT_ROOT.resolve("config/input-map.json"), temporaryDirectory.resolve("config/input-map.json"));
        Files.copy(
                PROJECT_ROOT.resolve("entities/beacon.entity.json"),
                temporaryDirectory.resolve("entities/beacon.entity.json"));
        Files.copy(
                PROJECT_ROOT.resolve("entities/beacon-pulse.entity.json"),
                temporaryDirectory.resolve("entities/beacon-pulse.entity.json"));
        Files.copy(PROJECT_ROOT.resolve("assets/garden.gltf"), temporaryDirectory.resolve("assets/garden.gltf"));
        Files.copy(
                PROJECT_ROOT.resolve("imports/garden.import.json"),
                temporaryDirectory.resolve("imports/garden.import.json"));
        Files.copy(
                PROJECT_ROOT.resolve("resources/beacon-sensor-box.resource.json"),
                temporaryDirectory.resolve("resources/beacon-sensor-box.resource.json"));
        Files.copy(
                PROJECT_ROOT.resolve("resources/beacon-sensor-sphere.resource.json"),
                temporaryDirectory.resolve("resources/beacon-sensor-sphere.resource.json"));
        Files.copy(
                PROJECT_ROOT.resolve("resources/garden-overlap-target.resource.json"),
                temporaryDirectory.resolve("resources/garden-overlap-target.resource.json"));
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

    /** Publishes a deterministic semantic press through the same world input module used by gameplay. */
    private static void publishPulse(HostedProject loaded) {
        ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
        input.publish(ActionSnapshot.builder().pressed(PULSE).build());
    }

    /** Captures the payload-bearing endpoint installed by a component under test. */
    private static final class CapturingComponentEndpoints implements ComponentEndpoints {
        private Optional<RuntimePayloadAction> payloadAction = Optional.empty();

        private RuntimePayloadAction payloadAction() {
            return payloadAction.orElseThrow();
        }

        @Override
        public RuntimeSignal signal(EndpointId endpoint) {
            throw new UnsupportedOperationException("no signal expected");
        }

        @Override
        public void action(EndpointId endpoint, RuntimeAction action) {
            throw new UnsupportedOperationException("no payload-free action expected");
        }

        @Override
        public void action(EndpointId endpoint, RuntimePayloadAction action) {
            assertThat(endpoint).isEqualTo(BeaconResponse.RECEIVE_OVERLAP);
            payloadAction = Optional.of(action);
        }
    }
}
