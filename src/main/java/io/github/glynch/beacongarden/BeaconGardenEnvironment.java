/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.asset.AssetCatalog;
import io.github.glynch.jscene3d.project.extension.ExtensionDescriptor;
import io.github.glynch.jscene3d.project.extension.RegisteredTypeCatalog;
import io.github.glynch.jscene3d.project.importing.ImportedRuntimeResources;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.physics3d.Physics3dAdapters;
import io.github.glynch.jscene3d.project.physics3d.Physics3dDescriptors;
import io.github.glynch.jscene3d.project.physics3d.Physics3dResourceLoaders;
import io.github.glynch.jscene3d.project.physics3d.Physics3dRuntimeExtension;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.ProjectContent;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeEnvironment;
import io.github.glynch.jscene3d.project.runtime.RuntimeResourceLoader;
import io.github.glynch.jscene3d.project.runtime.WorldModuleBinding;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentRuntimeExtension;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dAdapters;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dResourceLoaders;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dRuntimeExtension;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.util.ArrayList;
import java.util.List;

/** Headless Beacon Garden environment combining standard 3D presentation and collision facilities. */
final class BeaconGardenEnvironment implements ProjectRuntimeEnvironment {
    @Override
    public List<ExtensionDescriptor> descriptors() {
        return List.of(Spatial3dDescriptors.extensionDescriptor(), Physics3dDescriptors.extensionDescriptor());
    }

    @Override
    public List<ComponentRuntimeExtension> runtimeExtensions() {
        return List.of(new Spatial3dRuntimeExtension(), new Physics3dRuntimeExtension());
    }

    @Override
    public List<WorldModuleBinding<?>> createWorldModules() {
        return List.of(
                WorldModuleBinding.of(Spatial3dWorldModule.class, Spatial3dAdapters.standard()),
                WorldModuleBinding.of(Physics3dWorldModule.class, Physics3dAdapters.standard()));
    }

    @Override
    public ProjectContent loadContent(GameProject project, RegisteredTypeCatalog types, AssetCatalog authored) {
        List<RuntimeResourceLoader<?>> loaders = new ArrayList<>();
        loaders.addAll(Spatial3dResourceLoaders.all());
        loaders.addAll(Physics3dResourceLoaders.all());
        return new ProjectContent(authored, ImportedRuntimeResources.create(project, types, loaders));
    }
}
