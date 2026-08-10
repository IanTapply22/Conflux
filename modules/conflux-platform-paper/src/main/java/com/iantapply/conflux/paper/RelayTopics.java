package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAppearanceFrame;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostMovementFrame;
import com.iantapply.relay.api.Codecs;
import com.iantapply.relay.api.Topic;

/** Relay topics used to exchange Conflux ghost state. */
final class RelayTopics {
    /** Topic carrying complete node-level player snapshots. */
    static final Topic<GhostFrame> FRAME = Topic.of("conflux.ghost.frame.v2", Codecs.json(GhostFrame.class));

    /** Topic carrying frequent lightweight movement snapshots. */
    static final Topic<GhostMovementFrame> MOVEMENT =
            Topic.of("conflux.ghost.movement.v2", Codecs.json(GhostMovementFrame.class));

    /** Topic carrying change-only player identity and equipment updates. */
    static final Topic<GhostAppearanceFrame> APPEARANCE =
            Topic.of("conflux.ghost.appearance.v2", Codecs.json(GhostAppearanceFrame.class));

    /** Topic carrying transient player animations. */
    static final Topic<GhostAnimation> ANIMATION =
            Topic.of("conflux.ghost.animation.v2", Codecs.json(GhostAnimation.class));

    /** Prevents construction of this topic container. */
    private RelayTopics() {}
}
