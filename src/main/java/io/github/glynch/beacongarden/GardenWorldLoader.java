/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.asset.AssetCatalog;
import io.github.glynch.jscene3d.project.asset.AssetCatalogLoadResult;
import io.github.glynch.jscene3d.project.asset.AssetKind;
import io.github.glynch.jscene3d.project.asset.AssetMetadata;
import io.github.glynch.jscene3d.project.asset.AssetRef;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoadResult;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoader;
import io.github.glynch.jscene3d.project.extension.ExtensionDescriptor;
import io.github.glynch.jscene3d.project.extension.RegisteredTypeCatalog;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.runtime.RuntimeResourceLease;
import io.github.glynch.jscene3d.project.runtime.RuntimeResourceProvider;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.WorldComposer;
import io.github.glynch.jscene3d.project.runtime.WorldCompositionResult;
import io.github.glynch.jscene3d.project.runtime.WorldModuleBinding;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dAdapters;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dRuntimeExtension;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import io.github.glynch.jscene3d.project.world.WorldDefinition;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads the real Beacon Garden project data into one inactive headless world. */
final class GardenWorldLoader {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final RuntimeResourceProvider NO_RESOURCES = new RuntimeResourceProvider() {
        @Override
        public <T> RuntimeResourceLease<T> acquire(ResourceReference reference, Class<T> valueType) {
            throw new IllegalStateException("Beacon Garden's bootstrap world declares no runtime resources");
        }
    };

    /** Prevents construction of this stateless loader. */
    private GardenWorldLoader() {
        throw new AssertionError("GardenWorldLoader cannot be instantiated");
    }

    /** Loads the manifest, extension metadata, asset catalog, and startup world without activating it. */
    static GardenWorld load(Path projectRoot) {
        GameProject project = loadProject(projectRoot);
        RegisteredTypeCatalog types = loadTypes(project);
        AssetCatalog assets = loadAssets(project);
        AssetMetadata startup = startupAsset(project, assets);
        Spatial3dWorldModule spatial = Spatial3dAdapters.standard();
        WorldCompositionResult composition = WorldComposer.compose(
                assets,
                AssetRef.<WorldDefinition>to(startup.id()),
                types,
                List.of(new Spatial3dRuntimeExtension()),
                List.of(WorldModuleBinding.of(Spatial3dWorldModule.class, spatial)),
                NO_RESOURCES);
        if (!composition.isComposed()) {
            spatial.close();
            throw failure("startup world composition failed", composition.diagnostics());
        }
        World world = composition.world().orElseThrow();
        return new GardenWorld(project, assets, world, spatial);
    }

    /** Loads and validates the project manifest. */
    private static GameProject loadProject(Path projectRoot) {
        ProjectLoadResult result = new ProjectLoader(ENGINE_VERSION).load(projectRoot);
        return result.project().orElseThrow(() -> failure("project manifest loading failed", result.diagnostics()));
    }

    /** Discovers project metadata and adds host-selected built-in 3D metadata. */
    private static RegisteredTypeCatalog loadTypes(GameProject project) {
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, BeaconGardenApplication.class.getClassLoader());
        if (!result.isComplete()) {
            throw failure("application extension loading failed", result.diagnostics());
        }
        List<ExtensionDescriptor> descriptors = new ArrayList<>(result.catalog().extensions());
        descriptors.add(Spatial3dDescriptors.extensionDescriptor());
        return RegisteredTypeCatalog.of(descriptors);
    }

    /** Scans the project directory for stable-ID definition assets. */
    private static AssetCatalog loadAssets(GameProject project) {
        AssetCatalogLoadResult result = AssetCatalog.scan(project.root());
        return result.catalog().orElseThrow(() -> failure("asset catalog loading failed", result.diagnostics()));
    }

    /** Resolves the manifest entry path to its catalogued stable world identity. */
    private static AssetMetadata startupAsset(GameProject project, AssetCatalog assets) {
        AssetMetadata startup = assets.assets().stream()
                .filter(asset -> asset.path().equals(project.runtime().entryScene()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("startup world is absent from the asset catalog: "
                        + project.runtime().entryScene()));
        if (startup.kind() != AssetKind.WORLD_DEFINITION) {
            throw new IllegalStateException("startup asset is not a world definition: " + startup.path());
        }
        return startup;
    }

    /** Creates one terminal bootstrap failure retaining ordered structured diagnostics. */
    private static IllegalStateException failure(String message, List<?> diagnostics) {
        return new IllegalStateException(message + ": " + diagnostics);
    }
}
