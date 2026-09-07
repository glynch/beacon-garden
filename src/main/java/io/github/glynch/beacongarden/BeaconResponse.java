/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.physics3d.CollisionOverlap3d;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.spatial3d.DirectionalLight3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Applies precise sensor overlap signals to one reusable Beacon instance's own presentation state. */
final class BeaconResponse implements ComponentReferenceBinder, ComponentEndpointBinder {
    static final ComponentId COMPONENT_ID = ComponentId.from("d41453ab-d987-4dd4-a8e6-6f3a8e2ee8d4");
    static final ComponentType TYPE = ComponentType.of(BeaconGardenRuntimeExtension.ID + "/beacon-response", 1);
    static final PropertyId INDICATOR_LIGHT = new PropertyId("indicator-light");
    static final EndpointId RECEIVE_OVERLAP = new EndpointId("receive-overlap");
    private static final float ACTIVE_INTENSITY = 1.5F;

    private final List<CollisionOverlap3d> overlaps = new ArrayList<>();
    private Optional<DirectionalLight3d> indicatorLight = Optional.empty();

    /** Resolves this definition instance's explicitly authored indicator light. */
    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        indicatorLight = Optional.of(references.component(INDICATOR_LIGHT, DirectionalLight3d.class));
    }

    /** Implements the descriptor-declared collision response action. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        endpoints.action(RECEIVE_OVERLAP, this::receiveOverlap);
    }

    /** Returns precise overlaps received by this Beacon instance. */
    List<CollisionOverlap3d> overlaps() {
        return List.copyOf(overlaps);
    }

    /** Returns this Beacon instance's current indicator intensity. */
    float indicatorIntensity() {
        return requiredIndicatorLight().intensity();
    }

    /** Records an overlap and makes this Beacon instance visibly active. */
    private void receiveOverlap(RuntimePayload payload) {
        if (!(payload.value() instanceof CollisionOverlap3d overlap)) {
            throw new IllegalArgumentException("receive-overlap requires a CollisionOverlap3d payload");
        }
        overlaps.add(overlap);
        requiredIndicatorLight().setIntensity(ACTIVE_INTENSITY);
    }

    /** Requires authored-reference binding to have completed. */
    private DirectionalLight3d requiredIndicatorLight() {
        return indicatorLight.orElseThrow(() -> new IllegalStateException("Beacon indicator light has not been bound"));
    }
}
