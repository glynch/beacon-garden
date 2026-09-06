/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.asset.AssetRef;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.entity.EntityDefinition;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.PreparedEntityDefinition;
import io.github.glynch.jscene3d.project.runtime.SpawnOperation;
import io.github.glynch.jscene3d.project.runtime.SpawnStatus;
import io.github.glynch.jscene3d.project.runtime.SpawnTarget;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import java.util.Objects;
import java.util.Optional;

/** Owns the garden's prepared pulse and requests one runtime instance during its first fixed update. */
final class GardenBehavior implements ComponentUpdateCallbacks {
    static final ComponentId COMPONENT_ID = ComponentId.from("fd201861-8733-41ba-b28e-bd234badd1f1");
    static final ComponentType TYPE = ComponentType.of(BeaconGardenRuntimeExtension.ID + "/garden-behavior", 1);

    private final SpawnTarget spawnTarget;
    private final AssetRef<EntityDefinition> pulseDefinition;
    private Optional<PreparedEntityDefinition> preparedPulse = Optional.empty();
    private Optional<SpawnOperation> pulseSpawn = Optional.empty();
    private Optional<SpawnStatus> pulseStatusAtRequest = Optional.empty();

    /** Stores the owner-scoped spawn capability supplied during construction. */
    GardenBehavior(SpawnTarget spawnTarget, AssetRef<EntityDefinition> pulseDefinition) {
        this.spawnTarget = Objects.requireNonNull(spawnTarget, "spawnTarget");
        this.pulseDefinition = Objects.requireNonNull(pulseDefinition, "pulseDefinition");
    }

    /** Returns the authored reusable definition dependency selected in project data. */
    AssetRef<EntityDefinition> pulseDefinition() {
        return pulseDefinition;
    }

    /** Supplies the definition prepared by the host before world activation. */
    void preparePulse(PreparedEntityDefinition prepared) {
        PreparedEntityDefinition validPrepared = Objects.requireNonNull(prepared, "prepared");
        if (validPrepared.world() != spawnTarget.owner().world()) {
            throw new IllegalArgumentException("prepared pulse belongs to another world");
        }
        preparedPulse = Optional.of(validPrepared);
    }

    /** Returns the operation created by the first fixed update. */
    SpawnOperation pulseSpawn() {
        return pulseSpawn.orElseThrow(() -> new IllegalStateException("Beacon Pulse has not been requested"));
    }

    /** Returns the operation status observed inside the requesting callback. */
    SpawnStatus pulseStatusAtRequest() {
        return pulseStatusAtRequest.orElseThrow(() -> new IllegalStateException("Beacon Pulse has not been requested"));
    }

    /** Requests one owned pulse during the first eligible update and leaves later updates unchanged. */
    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (pulseSpawn.isPresent()) {
            return;
        }
        PreparedEntityDefinition prepared = preparedPulse.orElseThrow(
                () -> new IllegalStateException("Beacon Pulse was not prepared before world activation"));
        SpawnOperation requested = spawnTarget.spawn(prepared);
        pulseSpawn = Optional.of(requested);
        pulseStatusAtRequest = Optional.of(requested.status());
    }
}
