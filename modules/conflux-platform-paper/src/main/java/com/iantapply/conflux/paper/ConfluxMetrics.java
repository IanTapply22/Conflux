package com.iantapply.conflux.paper;

import java.util.concurrent.atomic.AtomicLong;

/** Lightweight counters used by the status command and operational diagnostics. */
final class ConfluxMetrics {
    private final AtomicLong receivedMessages = new AtomicLong();
    private final AtomicLong droppedMessages = new AtomicLong();
    private final AtomicLong publishedMessages = new AtomicLong();
    private final AtomicLong publishFailures = new AtomicLong();
    private final AtomicLong movementPlayers = new AtomicLong();
    private final AtomicLong appearanceCharacters = new AtomicLong();
    private final AtomicLong estimatedPayloadBytes = new AtomicLong();
    private final AtomicLong selectionNanos = new AtomicLong();
    private final AtomicLong selectionRuns = new AtomicLong();

    /** Records one received protocol message. */
    void received() {
        receivedMessages.incrementAndGet();
    }

    /** Records one rejected or overflowed protocol message. */
    void dropped() {
        droppedMessages.incrementAndGet();
    }

    /** Records one successfully published protocol message. */
    void published() {
        publishedMessages.incrementAndGet();
    }

    /** Records one failed protocol publication. */
    void publishFailed() {
        publishFailures.incrementAndGet();
    }

    /**
     * Adds players included in an outgoing movement frame.
     *
     * @param count published player count
     */
    void movementPlayers(int count) {
        movementPlayers.addAndGet(count);
    }

    /**
     * Adds variable appearance characters sent over the wire.
     *
     * @param count encoded character count
     */
    void appearanceCharacters(long count) {
        appearanceCharacters.addAndGet(count);
    }

    /**
     * Adds an estimated encoded payload size.
     *
     * @param count estimated byte count
     */
    void estimatedPayloadBytes(long count) {
        estimatedPayloadBytes.addAndGet(count);
    }

    /**
     * Records the duration of one viewer selection pass.
     *
     * @param nanos elapsed selection time in nanoseconds
     */
    void selection(long nanos) {
        selectionNanos.addAndGet(nanos);
        selectionRuns.incrementAndGet();
    }

    /**
     * Produces a compact status-command representation of all counters.
     *
     * @return human-readable metrics summary
     */
    String summary() {
        long runs = selectionRuns.get();
        long averageMicros = runs == 0 ? 0 : selectionNanos.get() / runs / 1_000;
        return "rx=" + receivedMessages.get() + ", dropped=" + droppedMessages.get() + ", tx="
                + publishedMessages.get() + ", txFailed=" + publishFailures.get() + ", moved="
                + movementPlayers.get() + ", appearanceChars=" + appearanceCharacters.get() + ", selectAvgUs="
                + averageMicros + ", estimatedBytes=" + estimatedPayloadBytes.get();
    }
}
