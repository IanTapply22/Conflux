package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.relay.api.Codecs;
import com.iantapply.relay.api.Topic;

/** Relay topics used to exchange Conflux ghost state. */
final class RelayTopics {
    /** Topic carrying complete node-level player snapshots. */
    static final Topic<GhostFrame> FRAME = Topic.of("conflux.ghost.frame.v1", Codecs.json(GhostFrame.class));

    /** Topic carrying transient player animations. */
    static final Topic<GhostAnimation> ANIMATION =
            Topic.of("conflux.ghost.animation.v1", Codecs.json(GhostAnimation.class));

    /** Prevents construction of this topic container. */
    private RelayTopics() {}
}
