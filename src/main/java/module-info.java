/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
/** Beacon Garden application and project bootstrap. */
module io.github.glynch.beacon.garden {
    requires java.logging;
    requires io.github.glynch.jscene3d.project.runtime;
    requires io.github.glynch.jscene3d.project.spatial3d;
    requires static org.jspecify;

    provides io.github.glynch.jscene3d.project.runtime.extension.ComponentRuntimeExtension with
            io.github.glynch.beacongarden.BeaconGardenRuntimeExtension;
}
