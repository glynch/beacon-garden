/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.ProjectInput;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.runtime.SpawnOperation;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/** Headless entry point proving Beacon Garden's manifest-to-live-world startup path. */
public final class BeaconGardenApplication {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final Logger LOGGER = Logger.getLogger(BeaconGardenApplication.class.getName());
    private static final EntityId GARDEN_PLACEMENT = EntityId.from("cf795ee1-fe86-4b46-bbc1-b98ca9107fe5");
    private static final InputAction PULSE = new InputAction("pulse");

    /** Prevents construction of this application entry point. */
    private BeaconGardenApplication() {
        throw new AssertionError("BeaconGardenApplication cannot be instantiated");
    }

    /**
     * Loads, activates, reports, and closes Beacon Garden from its project directory.
     *
     * @param arguments exactly one project-directory path
     */
    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected one Beacon Garden project-directory path");
        }
        Path projectRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path publishedImports = projectRoot.resolve("target/import-cache");
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                BeaconGardenApplication.class.getClassLoader(),
                new BeaconGardenEnvironment(publishedImports));
        HostedProject loaded = host.load(projectRoot);
        try (loaded) {
            Spatial3dWorldModule spatial = loaded.world().requireModule(Spatial3dWorldModule.class);
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            GardenBehavior behavior = gardenBehavior(loaded);
            BeaconResponse beaconResponse = beaconResponse(loaded);
            Entity garden = authoredRoot(loaded, GARDEN_PLACEMENT);
            loaded.world().activate();
            LOGGER.info(() -> "Loaded project = " + loaded.project().identity().name());
            LOGGER.info(() -> "World roots = " + loaded.world().roots().size());
            LOGGER.info(() -> "Garden generated definition = "
                    + garden.children().getFirst().authoredAsset() + ", live entities = " + entityCount(garden));
            LOGGER.info(() -> "Primary camera active = " + spatial.isReadyToRender());
            LOGGER.info(() -> "Collision objects = " + physics.collisionObjectCount() + ", shapes = "
                    + physics.collisionShapeCount());
            float indicatorBeforeOverlap = behavior.indicatorIntensity();
            float beaconIndicatorBeforeOverlap = beaconResponse.indicatorIntensity();
            input.publish(ActionSnapshot.builder().pressed(PULSE).build());
            LOGGER.info(
                    () -> "Authored pulse action pressed = " + input.snapshot().wasPressed(PULSE));
            loaded.world().advanceFixed(Duration.ofMillis(16L));
            SpawnOperation pulseSpawn = behavior.pulseSpawn();
            Entity pulse = pulseSpawn.entity().orElseThrow();
            Entity pulseOwner = pulse.parent().orElseThrow();
            LOGGER.info(() -> "Pulse status at request = " + behavior.pulseStatusAtRequest());
            LOGGER.info(() -> "Pulse status after boundary = " + pulseSpawn.status());
            LOGGER.info(
                    () -> "Runtime pulse children = " + pulseOwner.children().size());
            LOGGER.info(() -> "Entered overlaps = " + behavior.enteredOverlaps().size() + ", sensor shapes = "
                    + behavior.enteredSensorShapeIds());
            LOGGER.info(
                    () -> "Indicator intensity = " + indicatorBeforeOverlap + " -> " + behavior.indicatorIntensity());
            LOGGER.info(() -> "Beacon indicator intensity = " + beaconIndicatorBeforeOverlap + " -> "
                    + beaconResponse.indicatorIntensity());
            loaded.world().destroy(pulse);
            LOGGER.info(() -> "Pulse destroyed = " + pulse.isDestroyed() + ", runtime pulse children = "
                    + pulseOwner.children().size());
            loaded.close();
            LOGGER.info(() -> "Physics adapter closed = " + physics.isClosed() + ", spatial adapter closed = "
                    + spatial.isClosed());
        }
    }

    /** Finds the application component prepared by the manifest-selected extension. */
    private static GardenBehavior gardenBehavior(HostedProject loaded) {
        return loaded.world().roots().stream()
                .map(entity -> entity.component(GardenBehavior.COMPONENT_ID, GardenBehavior.class))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("startup world has no Garden Behavior component"));
    }

    /** Finds the response component inside the placed reusable Beacon definition. */
    private static BeaconResponse beaconResponse(HostedProject loaded) {
        return loaded.world().roots().stream()
                .map(entity -> entity.component(BeaconResponse.COMPONENT_ID, BeaconResponse.class))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("startup world has no Beacon Response component"));
    }

    /** Finds one authored world root by stable placement or local identity. */
    private static Entity authoredRoot(HostedProject loaded, EntityId authoredId) {
        return loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(authoredId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("startup world has no entity " + authoredId));
    }

    /** Counts one live hierarchy without depending on generated definition internals. */
    private static int entityCount(Entity root) {
        return 1
                + root.children().stream()
                        .mapToInt(BeaconGardenApplication::entityCount)
                        .sum();
    }
}
