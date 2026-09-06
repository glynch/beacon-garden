/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.asset.AssetCatalog;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.util.Objects;

/** Loaded project metadata, assets, live world, and its headless spatial adapter. */
record GardenWorld(GameProject project, AssetCatalog assets, World world, Spatial3dWorldModule spatial)
        implements AutoCloseable {
    /** Validates the complete loaded-world aggregate. */
    GardenWorld {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(spatial, "spatial");
    }

    /** Closes the world and every adapter whose ownership transferred to it. */
    @Override
    public void close() {
        world.close();
    }
}
