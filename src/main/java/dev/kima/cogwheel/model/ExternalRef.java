package dev.kima.cogwheel.model;

import java.util.UUID;

/**
 * Cross-factory reference: a {@link SourceNode} of kind {@link SourceNode.Kind#EXTERNAL_FACTORY}
 * uses this to point at a specific {@link OutputNode} in another {@link Factory}.
 *
 * <p>Only the IDs are stored. Names are looked up at render time from the current
 * {@code FactoryStore}; if either ID has been deleted, the source renders as "missing".
 */
public record ExternalRef(UUID factoryId, UUID outputNodeId) {}
