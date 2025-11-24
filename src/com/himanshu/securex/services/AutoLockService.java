package com.himanshu.securex.services;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * A service that automatically triggers a lock action after a period of inactivity.
 * This helps mitigate opportunistic attempts to obtain credentials using physical access.
 */
public class AutoLockService {

    private Timeline timeline;
    private final Runnable onTimeout;
    private int timeoutMinutes;
    private boolean isEnabled;

    /**
     * Initializes the auto-lock service.
     * @param timeoutMinutes The number of minutes of inactivity before locking, or -1 to disable.
     * @param onTimeout      The action to perform when the timer expires.
     */
    public AutoLockService(int timeoutMinutes, Runnable onTimeout) {
        this.onTimeout = onTimeout;
        updateTimeout(timeoutMinutes);
    }

    /**
     * Updates the timeout period and recreates the timeline if necessary.
     * @param timeoutMinutes The new timeout in minutes, or -1 to disable auto-lock.
     */
    public void updateTimeout(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
        this.isEnabled = timeoutMinutes > 0;

        // Stop existing timeline
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }

        // Create new timeline only if auto-lock is enabled
        if (isEnabled) {
            this.timeline = new Timeline(new KeyFrame(Duration.minutes(timeoutMinutes), e -> lock()));
            this.timeline.setCycleCount(1); // Run only once
        }
    }

    public void start() {
        if (isEnabled && timeline != null) {
            timeline.playFromStart();
        }
    }

    public void reset() {
        if (isEnabled && timeline != null) {
            timeline.playFromStart();
        }
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    private void lock() {
        Platform.runLater(onTimeout);
    }
}