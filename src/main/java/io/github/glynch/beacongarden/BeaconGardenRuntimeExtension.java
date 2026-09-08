/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.beacongarden;

import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.asset.AssetRef;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.entity.EntityDefinition;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.extension.ApplicationRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryRegistry;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;
import java.util.Optional;

/** Supplies Beacon Garden's executable component factories independently of its safe descriptor metadata. */
public final class BeaconGardenRuntimeExtension implements ApplicationRuntimeExtension {
    static final String ID = "io.github.glynch.beacon-garden";
    private static final PropertyId PULSE_DEFINITION = new PropertyId("pulse-definition");
    private static final PropertyId PULSE_ACTION = new PropertyId("pulse-action");

    /** Creates the stateless provider used by standard Java service discovery. */
    public BeaconGardenRuntimeExtension() {
        // Public construction is required by ServiceLoader on the class path and module path.
    }

    /** Returns the extension identity shared with the application descriptor. */
    @Override
    public String id() {
        return ID;
    }

    /** Registers the application behavior factory under its exact descriptor-backed component type. */
    @Override
    public void register(ComponentFactoryRegistry registry) {
        ComponentFactoryRegistry validRegistry = Objects.requireNonNull(registry, "registry");
        validRegistry.register(
                GardenBehavior.TYPE,
                context -> new GardenBehavior(
                        context.spawnTarget(),
                        pulseDefinition(context.properties().resourceReference(PULSE_DEFINITION)),
                        context.world().requireModule(InputWorldModule.class),
                        new InputAction(context.properties().text(PULSE_ACTION))));
        validRegistry.register(BeaconResponse.TYPE, context -> new BeaconResponse());
    }

    @Override
    public void prepare(HostedProject project) {
        HostedProject validProject = Objects.requireNonNull(project, "project");
        validProject.world().roots().stream()
                .map(entity -> entity.component(GardenBehavior.COMPONENT_ID, GardenBehavior.class))
                .flatMap(Optional::stream)
                .forEach(behavior -> behavior.preparePulse(validProject.world().prepare(behavior.pulseDefinition())));
    }

    /** Converts one descriptor-validated asset reference into a typed entity-definition reference. */
    private static AssetRef<EntityDefinition> pulseDefinition(ResourceReference reference) {
        if (reference.kind() != ResourceReference.Kind.ASSET) {
            throw new IllegalArgumentException("pulse-definition must be an asset reference");
        }
        return AssetRef.to(AssetId.from(reference.locator()));
    }
}
