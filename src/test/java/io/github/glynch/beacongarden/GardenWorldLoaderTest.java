/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.jscene3d.project.asset.AssetKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises Beacon Garden's real manifest-to-world bootstrap boundary. */
final class GardenWorldLoaderTest {
    private static final Path PROJECT_ROOT = Path.of(System.getProperty("beaconGarden.projectRoot", "."))
            .toAbsolutePath()
            .normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Loads, activates, and closes the authored startup project without native graphics. */
    @Test
    void loadsAndActivatesStartupWorld() {
        GardenWorld loaded = GardenWorldLoader.load(PROJECT_ROOT);

        assertThat(loaded.project().identity().id()).isEqualTo("io.github.glynch.beacon-garden");
        assertThat(loaded.assets().assets())
                .extracting(metadata -> metadata.kind())
                .containsExactly(AssetKind.ENTITY_DEFINITION, AssetKind.WORLD_DEFINITION);
        assertThat(loaded.world().roots())
                .extracting(entity -> entity.name().orElseThrow())
                .containsExactly("Camera", "Sun", "Garden", "Beacon A", "Garden Behavior");
        assertThat(loaded.world().isActive()).isFalse();
        assertThat(loaded.spatial().isReadyToRender()).isFalse();

        loaded.world().activate();

        assertThat(loaded.world().isActive()).isTrue();
        assertThat(loaded.spatial().isReadyToRender()).isTrue();
        loaded.close();
        assertThat(loaded.world().isClosed()).isTrue();
        assertThat(loaded.spatial().isClosed()).isTrue();
    }

    /** Rejects invocation without the required project path before touching project state. */
    @Test
    void rejectsMissingApplicationArgument() {
        String[] arguments = {};

        assertThatThrownBy(() -> BeaconGardenApplication.main(arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one Beacon Garden project-directory path");
    }

    /** Runs the supported headless application entry point against the authored project. */
    @Test
    void runsHeadlessApplication() {
        String[] arguments = {PROJECT_ROOT.toString()};

        BeaconGardenApplication.main(arguments);
    }

    /** Identifies an invalid project manifest before scanning or composing content. */
    @Test
    void rejectsInvalidManifest() throws IOException {
        Files.writeString(temporaryDirectory.resolve("project.json"), "{}\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
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

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("application extension loading failed");
    }

    /** Identifies duplicate stable identities while building the definition catalog. */
    @Test
    void rejectsInvalidAssetCatalog() throws IOException {
        copyProject();
        Files.copy(
                temporaryDirectory.resolve("entities/beacon.entity.json"),
                temporaryDirectory.resolve("entities/duplicate.entity.json"));

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asset catalog loading failed");
    }

    /** Identifies a regular manifest entry file which the definition catalog cannot address. */
    @Test
    void rejectsUncataloguedStartupAsset() throws IOException {
        copyProject();
        Path uncatalogued = temporaryDirectory.resolve("worlds/uncatalogued.json");
        Files.writeString(uncatalogued, "{}\n", StandardCharsets.UTF_8);
        replace(temporaryDirectory.resolve("project.json"), "worlds/garden.world.json", "worlds/uncatalogued.json");

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startup world is absent from the asset catalog");
    }

    /** Identifies an entity definition selected where a startup world is required. */
    @Test
    void rejectsEntityDefinitionAsStartupWorld() throws IOException {
        copyProject();
        replace(temporaryDirectory.resolve("project.json"), "worlds/garden.world.json", "entities/beacon.entity.json");

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
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

        assertThatThrownBy(() -> GardenWorldLoader.load(temporaryDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("startup world composition failed");
    }

    /** Copies the three authored project documents into the test's isolated directory. */
    private void copyProject() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("entities"));
        Files.createDirectories(temporaryDirectory.resolve("worlds"));
        Files.copy(PROJECT_ROOT.resolve("project.json"), temporaryDirectory.resolve("project.json"));
        Files.copy(
                PROJECT_ROOT.resolve("entities/beacon.entity.json"),
                temporaryDirectory.resolve("entities/beacon.entity.json"));
        Files.copy(
                PROJECT_ROOT.resolve("worlds/garden.world.json"),
                temporaryDirectory.resolve("worlds/garden.world.json"));
    }

    /** Replaces one exact authored fragment using deterministic UTF-8 I/O. */
    private static void replace(Path path, String target, String replacement) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).replace(target, replacement);
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
