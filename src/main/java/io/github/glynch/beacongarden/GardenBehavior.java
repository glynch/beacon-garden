/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.asset.AssetRef;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.entity.EntityDefinition;
import io.github.glynch.jscene3d.project.physics3d.CollisionOverlap3d;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.PreparedEntityDefinition;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.SpawnOperation;
import io.github.glynch.jscene3d.project.runtime.SpawnStatus;
import io.github.glynch.jscene3d.project.runtime.SpawnTarget;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.DirectionalLight3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Spawns the garden pulse and converts authored Beacon overlap signals into visible light state. */
final class GardenBehavior implements ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks {
    static final ComponentId COMPONENT_ID = ComponentId.from("fd201861-8733-41ba-b28e-bd234badd1f1");
    static final ComponentType TYPE = ComponentType.of(BeaconGardenRuntimeExtension.ID + "/garden-behavior", 1);
    static final PropertyId INDICATOR_LIGHT = new PropertyId("indicator-light");
    static final EndpointId RECEIVE_OVERLAP = new EndpointId("receive-overlap");
    private static final float ACTIVE_INDICATOR_INTENSITY = 6.0F;

    private final SpawnTarget spawnTarget;
    private final AssetRef<EntityDefinition> pulseDefinition;
    private final InputWorldModule input;
    private final InputAction pulseAction;
    private final List<CollisionOverlap3d> enteredOverlaps = new ArrayList<>();
    private Optional<PreparedEntityDefinition> preparedPulse = Optional.empty();
    private Optional<SpawnOperation> pulseSpawn = Optional.empty();
    private Optional<SpawnStatus> pulseStatusAtRequest = Optional.empty();
    private Optional<DirectionalLight3d> indicatorLight = Optional.empty();

    /** Stores the owner-scoped spawn capability supplied during construction. */
    GardenBehavior(
            SpawnTarget spawnTarget,
            AssetRef<EntityDefinition> pulseDefinition,
            InputWorldModule input,
            InputAction pulseAction) {
        this.spawnTarget = Objects.requireNonNull(spawnTarget, "spawnTarget");
        this.pulseDefinition = Objects.requireNonNull(pulseDefinition, "pulseDefinition");
        this.input = Objects.requireNonNull(input, "input");
        this.pulseAction = Objects.requireNonNull(pulseAction, "pulseAction");
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

    /** Returns precise overlap transitions received from the authored Beacon sensor connection. */
    List<CollisionOverlap3d> enteredOverlaps() {
        return List.copyOf(enteredOverlaps);
    }

    /** Returns the exact Beacon sensor-shape identities which entered another collision object. */
    List<ComponentId> enteredSensorShapeIds() {
        return enteredOverlaps.stream()
                .map(overlap -> overlap.sensorShape().componentId())
                .toList();
    }

    /** Returns the current authored indicator-light intensity. */
    float indicatorIntensity() {
        return requiredIndicatorLight().intensity();
    }

    /** Resolves the stable authored light target without relying on hierarchy position. */
    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        indicatorLight = Optional.of(references.component(INDICATOR_LIGHT, DirectionalLight3d.class));
    }

    /** Implements the descriptor-declared overlap action. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        endpoints.action(RECEIVE_OVERLAP, this::receiveOverlap);
    }

    /** Requests one owned pulse on the first authored pulse-action press. */
    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (pulseSpawn.isPresent() || !input.snapshot().wasPressed(pulseAction)) {
            return;
        }
        PreparedEntityDefinition prepared = preparedPulse.orElseThrow(
                () -> new IllegalStateException("Beacon Pulse was not prepared before world activation"));
        SpawnOperation requested = spawnTarget.spawn(prepared);
        pulseSpawn = Optional.of(requested);
        pulseStatusAtRequest = Optional.of(requested.status());
    }

    /** Records one precise collision transition and applies its visible garden response. */
    private void receiveOverlap(RuntimePayload payload) {
        if (!(payload.value() instanceof CollisionOverlap3d overlap)) {
            throw new IllegalArgumentException("receive-overlap requires a CollisionOverlap3d payload");
        }
        enteredOverlaps.add(overlap);
        requiredIndicatorLight().setIntensity(ACTIVE_INDICATOR_INTENSITY);
    }

    /** Requires reference binding to have supplied the authored presentation target. */
    private DirectionalLight3d requiredIndicatorLight() {
        return indicatorLight.orElseThrow(() -> new IllegalStateException("indicator light has not been bound"));
    }
}
