package com.example.floatingtap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import java.util.List;

public class TapRepeaterService extends AccessibilityService {

    private static TapRepeaterService instance;
    private Handler handler;
    private Runnable tapRunnable;
    private Runnable patternRunnable;
    private boolean isRunning = false;
    private boolean isPatternPlaying = false;
    private List<OverlayService.TapMarker> markers;
    private OverlayService.TapPattern currentPattern;
    private int currentMarkerIndex = 0;
    private int tapInterval = 500;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        stopTapping();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTapping();
        instance = null;
    }

    public static void startTapping(List<OverlayService.TapMarker> markerList, int interval) {
        if (instance != null) {
            instance.markers = markerList;
            instance.tapInterval = interval;
            instance.startTappingInternal();
        }
    }

    public static void stopTapping() {
        if (instance != null) {
            instance.stopTappingInternal();
        }
    }

    public static void startPatternPlayback(OverlayService.TapPattern pattern) {
        if (instance != null) {
            instance.currentPattern = pattern;
            instance.startPatternPlaybackInternal();
        }
    }

    public static void stopPatternPlayback() {
        if (instance != null) {
            instance.stopPatternPlaybackInternal();
        }
    }

    private void startTappingInternal() {
        if (isRunning || markers == null || markers.isEmpty()) {
            return;
        }

        isRunning = true;
        currentMarkerIndex = 0;

        tapRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning && markers != null && !markers.isEmpty()) {
                    OverlayService.TapMarker marker = markers.get(currentMarkerIndex);
                    performTap(marker.x, marker.y);

                    currentMarkerIndex = (currentMarkerIndex + 1) % markers.size();
                    handler.postDelayed(this, tapInterval);
                }
            }
        };
        handler.post(tapRunnable);
    }

    private void stopTappingInternal() {
        isRunning = false;
        if (tapRunnable != null) {
            handler.removeCallbacks(tapRunnable);
        }
    }

    private void startPatternPlaybackInternal() {
        if (isPatternPlaying || currentPattern == null || currentPattern.taps.isEmpty()) {
            return;
        }

        isPatternPlaying = true;
        final long patternDuration = currentPattern.duration;

        // Function to play one cycle of the pattern
        Runnable playCycle = new Runnable() {
            @Override
            public void run() {
                if (!isPatternPlaying || currentPattern == null) {
                    return;
                }

                long cycleStartTime = System.currentTimeMillis();
                
                // Schedule each tap at its relative timestamp
                for (OverlayService.PatternTap tap : currentPattern.taps) {
                    final int x = tap.x;
                    final int y = tap.y;
                    long delay = tap.timestamp;
                    
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (isPatternPlaying) {
                                performTap(x, y);
                            }
                        }
                    }, delay);
                }

                // Schedule next cycle after pattern duration
                handler.postDelayed(this, patternDuration);
            }
        };

        // Start first cycle immediately
        handler.post(playCycle);
    }

    private void stopPatternPlaybackInternal() {
        isPatternPlaying = false;
        if (patternRunnable != null) {
            handler.removeCallbacks(patternRunnable);
        }
    }

    private void performTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 50);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGesture(builder.build(), null, null);
    }
}