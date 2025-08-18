package com.himanshu.securex.services;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * A service that automatically triggers a lock action after a period of inactivity.
 * This helps mitigate opportunistic attempts to obtain credentials using physical access.
 * TODO: add an option for the user to be able to choose the timeout period.
 */
public class AutoLockService {

    private final Timeline timeline;
    private final Runnable onTimeout;

    /**
     * Initializes the auto-lock service.
     * @param timeOutMinutes The number of minutes of inactivity before locking.
     * @param onTimeout      The action to perform when the timer expires.
     */
    public AutoLockService(long timeOutMinutes, Runnable onTimeout) {
        this.onTimeout = onTimeout;
        this.timeline = new Timeline(new KeyFrame(Duration.minutes(timeOutMinutes), e -> lock()));
        this.timeline.setCycleCount(1); // Run only once
    }

    /**
     * Starts the inactivity timer.
     */
    public void start() {
        timeline.playFromStart();
    }

    /**
     * Resets the inactivity timer. This should be called on any user interaction.
     */
    public void reset() {
        timeline.playFromStart();
    }

    /**
     * Stops the timer.
     */
    public void stop() {
        timeline.stop();
    }

    private void lock() {
        Platform.runLater(onTimeout);
    }
}