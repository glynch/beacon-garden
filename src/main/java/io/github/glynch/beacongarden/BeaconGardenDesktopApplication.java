/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.desktop.DesktopProjectRunner;
import java.nio.file.Path;

/** Native desktop entry point which delegates complete project execution to the engine host. */
public final class BeaconGardenDesktopApplication {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";

    /** Prevents construction of this application entry point. */
    private BeaconGardenDesktopApplication() {
        throw new AssertionError("BeaconGardenDesktopApplication cannot be instantiated");
    }

    /** Runs Beacon Garden from one explicit project directory.
     *
     * @param arguments exactly one project-directory path
     */
    public static void main(String[] arguments) {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected one Beacon Garden project-directory path");
        }
        Path projectRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        DesktopProjectRunner runner = new DesktopProjectRunner(
                ENGINE_VERSION,
                BeaconGardenDesktopApplication.class.getClassLoader(),
                projectRoot.resolve("target/import-cache"));
        runner.run(projectRoot);
    }
}
