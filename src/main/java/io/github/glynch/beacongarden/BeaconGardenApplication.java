/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import java.nio.file.Path;
import java.util.logging.Logger;

/** Headless entry point proving Beacon Garden's manifest-to-live-world startup path. */
public final class BeaconGardenApplication {
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
        GardenWorld loaded = GardenWorldLoader.load(projectRoot);
        try (loaded) {
            loaded.world().activate();
            LOGGER.info(() -> "Loaded project = " + loaded.project().identity().name());
            LOGGER.info(() -> "World roots = " + loaded.world().roots().size());
            LOGGER.info(() -> "Primary camera active = " + loaded.spatial().isReadyToRender());
        }
        LOGGER.info(() -> "Spatial adapter closed = " + loaded.spatial().isClosed());
    }
}
