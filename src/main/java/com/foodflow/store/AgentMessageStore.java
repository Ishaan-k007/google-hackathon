package com.foodflow.store;

import com.foodflow.model.AgentMessage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the agent negotiation transcript for the CURRENT run only.
 * Each message carries a revealOffsetMs so the dashboard, when polling,
 * only sees messages whose offset has "elapsed" since the run started -
 * this produces the staged reveal effect during a live demo without
 * needing WebSockets or background threads.
 */
@Component
public class AgentMessageStore {

    private final List<AgentMessage> messages = new ArrayList<>();
    private final Object lock = new Object();
    private volatile Instant runStartedAt = Instant.now();

    public void startNewRun() {
        synchronized (lock) {
            messages.clear();
            runStartedAt = Instant.now();
        }
    }

    public void append(AgentMessage message) {
        synchronized (lock) {
            messages.add(message);
        }
    }

    public List<AgentMessage> findVisibleNow() {
        long elapsedMs = ChronoUnit.MILLIS.between(runStartedAt, Instant.now());
        synchronized (lock) {
            List<AgentMessage> visible = new ArrayList<>();
            for (AgentMessage m : messages) {
                if (m.getRevealOffsetMs() <= elapsedMs) {
                    visible.add(m);
                }
            }
            return visible;
        }
    }

    public List<AgentMessage> findAllRaw() {
        synchronized (lock) {
            return new ArrayList<>(messages);
        }
    }

    public boolean isRunComplete() {
        long elapsedMs = ChronoUnit.MILLIS.between(runStartedAt, Instant.now());
        synchronized (lock) {
            if (messages.isEmpty()) {
                return false;
            }
            long maxOffset = Collections.max(messages.stream().map(AgentMessage::getRevealOffsetMs).toList());
            return elapsedMs >= maxOffset;
        }
    }

    public void clear() {
        synchronized (lock) {
            messages.clear();
            runStartedAt = Instant.now();
        }
    }
}
