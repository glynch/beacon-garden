/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.gltf.GltfProjectImportExtension;
import io.github.glynch.jscene3d.project.extension.RegisteredTypeCatalog;
import io.github.glynch.jscene3d.project.importing.ImportManager;
import io.github.glynch.jscene3d.project.importing.ImportState;
import io.github.glynch.jscene3d.project.importing.PreparedImport;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.imports.ImportLoadResult;
import io.github.glynch.jscene3d.project.imports.ImportLoader;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import java.nio.file.Path;
import java.util.List;

/** Build-time entry point which publishes Beacon Garden's declared source imports. */
public final class BeaconGardenContentPublisher {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";

    /** Prevents construction of this stateless build entry point. */
    private BeaconGardenContentPublisher() {
        throw new AssertionError("BeaconGardenContentPublisher cannot be instantiated");
    }

    /**
     * Publishes stale or missing imports into the explicitly supplied build cache.
     *
     * @param arguments project root followed by import-cache root
     */
    public static void main(String[] arguments) {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("expected project-directory and import-cache paths");
        }
        publish(Path.of(arguments[0]), Path.of(arguments[1]));
    }

    /** Publishes every declared import requiring a new complete generation. */
    static void publish(Path projectRoot, Path cacheRoot) {
        ProjectLoadResult loaded = new ProjectLoader(ENGINE_VERSION).load(projectRoot);
        GameProject project = loaded.project()
                .orElseThrow(
                        () -> new IllegalStateException("project manifest loading failed: " + loaded.diagnostics()));
        GltfProjectImportExtension gltf = new GltfProjectImportExtension();
        RegisteredTypeCatalog importTypes = RegisteredTypeCatalog.of(List.of(GltfProjectImportExtension.descriptor()));
        ImportManager imports = ImportManager.create(
                project, importTypes, cacheRoot.toAbsolutePath().normalize(), List.of(gltf));
        ImportLoader definitionLoader = new ImportLoader();
        for (Path definitionPath : project.imports()) {
            ImportLoadResult result = definitionLoader.load(project, definitionPath);
            ImportDefinition definition = result.definition()
                    .orElseThrow(() ->
                            new IllegalStateException("import definition loading failed: " + result.diagnostics()));
            publishWhenRequired(imports, definition);
        }
    }

    /** Atomically publishes one import unless its active generation already matches all inputs. */
    private static void publishWhenRequired(ImportManager imports, ImportDefinition definition) {
        if (imports.status(definition).state() == ImportState.CURRENT) {
            return;
        }
        try (PreparedImport prepared = imports.prepare(definition)) {
            if (!prepared.preview().isValid()) {
                throw new IllegalStateException(
                        "import preparation failed: " + prepared.preview().diagnostics());
            }
            prepared.commit();
        }
    }
}
