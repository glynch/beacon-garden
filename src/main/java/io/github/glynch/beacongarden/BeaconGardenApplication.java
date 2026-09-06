/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.runtime.SpawnOperation;
import io.github.glynch.jscene3d.project.spatial3d.HeadlessSpatial3dEnvironment;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/** Headless entry point proving Beacon Garden's manifest-to-live-world startup path. */
public final class BeaconGardenApplication {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final Logger LOGGER = Logger.getLogger(BeaconGardenApplication.class.getName());

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
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION, BeaconGardenApplication.class.getClassLoader(), new HeadlessSpatial3dEnvironment());
        HostedProject loaded = host.load(projectRoot);
        try (loaded) {
            Spatial3dWorldModule spatial = loaded.world().requireModule(Spatial3dWorldModule.class);
            GardenBehavior behavior = gardenBehavior(loaded);
            loaded.world().activate();
            LOGGER.info(() -> "Loaded project = " + loaded.project().identity().name());
            LOGGER.info(() -> "World roots = " + loaded.world().roots().size());
            LOGGER.info(() -> "Primary camera active = " + spatial.isReadyToRender());
            loaded.world().advanceFixed(Duration.ofMillis(16L));
            SpawnOperation pulseSpawn = behavior.pulseSpawn();
            Entity pulse = pulseSpawn.entity().orElseThrow();
            Entity pulseOwner = pulse.parent().orElseThrow();
            LOGGER.info(() -> "Pulse status at request = " + behavior.pulseStatusAtRequest());
            LOGGER.info(() -> "Pulse status after boundary = " + pulseSpawn.status());
            LOGGER.info(
                    () -> "Runtime pulse children = " + pulseOwner.children().size());
            loaded.world().destroy(pulse);
            LOGGER.info(() -> "Pulse destroyed = " + pulse.isDestroyed() + ", runtime pulse children = "
                    + pulseOwner.children().size());
            loaded.close();
            LOGGER.info(() -> "Spatial adapter closed = " + spatial.isClosed());
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
}
