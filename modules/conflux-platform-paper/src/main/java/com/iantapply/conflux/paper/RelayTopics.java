package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.relay.api.Codecs;
import com.iantapply.relay.api.Topic;

final class RelayTopics {
    static final Topic<GhostFrame> FRAME = Topic.of("conflux.ghost.frame.v1", Codecs.json(GhostFrame.class));
    static final Topic<GhostAnimation> ANIMATION =
            Topic.of("conflux.ghost.animation.v1", Codecs.json(GhostAnimation.class));

    private RelayTopics() {}
}
