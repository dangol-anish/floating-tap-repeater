package com.example.floatingtap;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private View controlPanel;
    private View markerOverlay;
    private View stopRecordingButton;
    private boolean isPanelVisible = false;
    private boolean isAddingMarkers = false;
    private boolean isRunning = false;
    private boolean isRecording = false;
    private boolean isPlayingPattern = false;
    private List<TapMarker> markers = new ArrayList<>();
    private int frequency = 2;
    private TapPattern currentPattern = null;
    private long recordingStartTime = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingBubble();
    }

    private void createFloatingBubble() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(floatingView, params);

        setupBubbleListeners(params);
    }

    private void setupBubbleListeners(final WindowManager.LayoutParams params) {
        final View bubble = floatingView.findViewById(R.id.floating_bubble);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;
            private boolean hasMoved = false;
            private Runnable longPressRunnable;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        hasMoved = false;
                        
                        // Set up long-press handler for emergency stop
                        longPressRunnable = new Runnable() {
                            @Override
                            public void run() {
                                // Emergency stop - long press detected
                                if (isRunning) {
                                    isRunning = false;
                                    TapRepeaterService.stopTapping();
                                    updateBubbleAppearance();
                                    // Update button if panel is visible
                                    if (controlPanel != null && isPanelVisible) {
                                        Button startStopButton = controlPanel.findViewById(R.id.startStopButton);
                                        if (startStopButton != null) {
                                            updateStartStopButton(startStopButton);
                                        }
                                    }
                                }
                                if (isPlayingPattern) {
                                    stopPatternPlayback();
                                    // Update button if panel is visible
                                    if (controlPanel != null && isPanelVisible) {
                                        Button playPatternButton = controlPanel.findViewById(R.id.playPatternButton);
                                        if (playPatternButton != null) {
                                            playPatternButton.setText("▶ Play Pattern");
                                            playPatternButton.setBackgroundColor(Color.parseColor("#4ecca3"));
                                        }
                                    }
                                }
                            }
                        };
                        // Post long-press handler (1.5 seconds)
                        bubble.postDelayed(longPressRunnable, 1500);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);
                        // Only consider it a move if moved more than 10 pixels
                        if (deltaX > 10 || deltaY > 10) {
                            hasMoved = true;
                            // Cancel long-press if moved
                            if (longPressRunnable != null) {
                                bubble.removeCallbacks(longPressRunnable);
                            }
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Cancel long-press handler
                        if (longPressRunnable != null) {
                            bubble.removeCallbacks(longPressRunnable);
                            longPressRunnable = null;
                        }
                        
                        long touchDuration = System.currentTimeMillis() - touchStartTime;
                        // Toggle panel if it was a quick tap without significant movement
                        if (touchDuration < 300 && !hasMoved) {
                            toggleControlPanel();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void toggleControlPanel() {
        // Force check actual state
        boolean actuallyVisible = controlPanel != null && controlPanel.getParent() != null;
        
        if (actuallyVisible || isPanelVisible) {
            hideControlPanel();
        } else {
            showControlPanel();
        }
    }

    private void showControlPanel() {
        // Temporarily disable marker mode if active to ensure control panel buttons work
        boolean wasAddingMarkers = isAddingMarkers;
        if (isAddingMarkers) {
            disableMarkerMode();
            isAddingMarkers = false;
        }

        controlPanel = LayoutInflater.from(this).inflate(R.layout.control_panel, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, // Allow touches outside to pass through
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.CENTER;

        windowManager.addView(controlPanel, params);
        isPanelVisible = true;

        // Re-add floating bubble on top to ensure it can receive touches
        if (floatingView != null && floatingView.getParent() != null) {
            WindowManager.LayoutParams bubbleParams = (WindowManager.LayoutParams) floatingView.getLayoutParams();
            windowManager.removeView(floatingView);
            windowManager.addView(floatingView, bubbleParams);
            // Re-setup listeners since we re-added the view
            setupBubbleListeners(bubbleParams);
        }

        setupControlPanelListeners();
        
        // Restore marker mode state in the UI
        if (wasAddingMarkers) {
            Button addMarkerButton = controlPanel.findViewById(R.id.addMarkerButton);
            addMarkerButton.setText("✓ Done Adding");
            addMarkerButton.setBackgroundColor(Color.parseColor("#4ecca3"));
        }
    }

    private void setupControlPanelListeners() {
        Button addMarkerButton = controlPanel.findViewById(R.id.addMarkerButton);
        final Button startStopButton = controlPanel.findViewById(R.id.startStopButton);
        Button clearButton = controlPanel.findViewById(R.id.clearButton);
        Button closeButton = controlPanel.findViewById(R.id.closeButton);
        final Button recordPatternButton = controlPanel.findViewById(R.id.recordPatternButton);
        final Button playPatternButton = controlPanel.findViewById(R.id.playPatternButton);
        SeekBar frequencySeekBar = controlPanel.findViewById(R.id.frequencySeekBar);
        final TextView frequencyText = controlPanel.findViewById(R.id.frequencyText);
        final TextView markerCountText = controlPanel.findViewById(R.id.markerCountText);
        final TextView patternStatusText = controlPanel.findViewById(R.id.patternStatusText);

        updateMarkerCount(markerCountText);
        updateStartStopButton(startStopButton);

        frequencySeekBar.setMax(99);
        frequencySeekBar.setProgress(3); // Default to ~2.4 taps/min
        frequencyText.setText("Frequency: 2.4 taps/min");

        frequencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float freq = (progress + 1) * 0.6f; // taps per minute (0.6 to 60 taps/min)
                frequency = Math.round(60000.0f / freq); // milliseconds between taps (60000 ms = 1 minute)
                frequencyText.setText(String.format("Frequency: %.1f taps/min", freq));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        addMarkerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isAddingMarkers = !isAddingMarkers;
                if (isAddingMarkers) {
                    addMarkerButton.setText("✓ Done Adding");
                    addMarkerButton.setBackgroundColor(Color.parseColor("#4ecca3"));
                    enableMarkerMode();
                } else {
                    addMarkerButton.setText("+ Add Markers");
                    addMarkerButton.setBackgroundColor(Color.parseColor("#5588ff"));
                    disableMarkerMode();
                }
                updateMarkerCount(markerCountText);
            }
        });

        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    // Stop tapping
                    isRunning = false;
                    TapRepeaterService.stopTapping();
                    updateStartStopButton(startStopButton);
                    updateBubbleAppearance();
                } else {
                    // Start tapping
                    if (markers.isEmpty()) {
                        return;
                    }
                    isRunning = true;
                    TapRepeaterService.startTapping(markers, frequency);
                    updateStartStopButton(startStopButton);
                    updateBubbleAppearance();
                    hideControlPanel();
                }
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop tapping if running
                if (isRunning) {
                    isRunning = false;
                    TapRepeaterService.stopTapping();
                    updateStartStopButton(startStopButton);
                    updateBubbleAppearance();
                }
                // Remove all marker views from screen
                removeAllMarkerViews();
                // Clear the markers list
                markers.clear();
                // Update the UI
                updateMarkerCount(markerCountText);
            }
        });

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Stop tapping if running
                if (isRunning) {
                    isRunning = false;
                    TapRepeaterService.stopTapping();
                }
                // Stop pattern playback if running
                if (isPlayingPattern) {
                    stopPatternPlayback();
                }
                // Stop the service and close the floating bubble
                stopSelf();
            }
        });

        // Update pattern status
        updatePatternStatus(patternStatusText, recordPatternButton, playPatternButton);

        recordPatternButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    // Stop recording
                    stopRecording();
                    recordPatternButton.setText("🔴 Record Pattern");
                    recordPatternButton.setBackgroundColor(Color.parseColor("#ff6b6b"));
                    updatePatternStatus(patternStatusText, recordPatternButton, playPatternButton);
                } else {
                    // Start recording
                    startRecording();
                    recordPatternButton.setText("⏹ Stop Recording");
                    recordPatternButton.setBackgroundColor(Color.parseColor("#ff4444"));
                    patternStatusText.setText("Recording... Tap on screen to record");
                }
            }
        });

        playPatternButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPlayingPattern) {
                    // Stop playback
                    stopPatternPlayback();
                    playPatternButton.setText("▶ Play Pattern");
                    playPatternButton.setBackgroundColor(Color.parseColor("#4ecca3"));
                } else {
                    // Start playback
                    if (currentPattern != null && !currentPattern.taps.isEmpty()) {
                        startPatternPlayback();
                        playPatternButton.setText("⏸ Stop Playback");
                        playPatternButton.setBackgroundColor(Color.parseColor("#ff6b6b"));
                    } else {
                        patternStatusText.setText("No pattern recorded!");
                    }
                }
            }
        });
    }

    private void updateStartStopButton(Button button) {
        if (button == null) return;
        if (isRunning) {
            button.setText("⏸ Stop");
            button.setBackgroundColor(Color.parseColor("#ff6b6b"));
        } else {
            button.setText("▶ Start");
            button.setBackgroundColor(Color.parseColor("#4ecca3"));
        }
    }

    private void updateBubbleAppearance() {
        if (floatingView != null) {
            View bubble = floatingView.findViewById(R.id.floating_bubble);
            if (bubble != null) {
                if (isRunning || isPlayingPattern) {
                    // Change to red when tapping or pattern playback is active
                    bubble.setBackgroundColor(Color.parseColor("#ff6b6b"));
                } else {
                    // Restore original appearance
                    bubble.setBackgroundResource(R.drawable.bubble_background);
                }
            }
        }
    }

    private void updateMarkerCount(TextView textView) {
        textView.setText("Markers: " + markers.size());
    }

    private void enableMarkerMode() {
        markerOverlay = LayoutInflater.from(this).inflate(R.layout.marker_overlay, null);

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        markerOverlay.setTag("marker_overlay");

        windowManager.addView(markerOverlay, params);

        markerOverlay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Check if touch is on stop recording button
                if (stopRecordingButton != null && stopRecordingButton.getParent() != null) {
                    int[] location = new int[2];
                    stopRecordingButton.getLocationOnScreen(location);
                    Rect buttonRect = new Rect(
                            location[0],
                            location[1],
                            location[0] + stopRecordingButton.getWidth(),
                            location[1] + stopRecordingButton.getHeight()
                    );

                    if (buttonRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        // Convert to button's local coordinates and dispatch
                        MotionEvent localEvent = MotionEvent.obtain(event);
                        localEvent.offsetLocation(-location[0], -location[1]);
                        boolean handled = stopRecordingButton.dispatchTouchEvent(localEvent);
                        localEvent.recycle();
                        // Consume the event so marker overlay doesn't handle it
                        return true;
                    }
                }

                // Check if touch is on control panel
                if (controlPanel != null && isPanelVisible) {
                    int[] location = new int[2];
                    controlPanel.getLocationOnScreen(location);
                    Rect controlPanelRect = new Rect(
                            location[0],
                            location[1],
                            location[0] + controlPanel.getWidth(),
                            location[1] + controlPanel.getHeight()
                    );

                    if (controlPanelRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                        // Convert to control panel's local coordinates and dispatch
                        MotionEvent localEvent = MotionEvent.obtain(event);
                        localEvent.offsetLocation(-location[0], -location[1]);
                        boolean handled = controlPanel.dispatchTouchEvent(localEvent);
                        localEvent.recycle();
                        // Always consume touches on control panel to prevent marker adding
                        return true;
                    }
                }

                // Touch is outside control panel and stop button
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (isRecording) {
                        // Record tap during recording mode
                        recordTap((int) event.getRawX(), (int) event.getRawY());
                    } else if (isAddingMarkers) {
                        // Add marker in marker mode
                        addMarker((int) event.getRawX(), (int) event.getRawY());
                    }
                    return true;
                }
                
                // For other actions outside control panel, don't consume
                return false;
            }
        });
    }

    private View findViewAt(ViewGroup parent, int x, int y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            int childX = (int) child.getX();
            int childY = (int) child.getY();
            int childRight = childX + child.getWidth();
            int childBottom = childY + child.getHeight();
            
            if (x >= childX && x < childRight && y >= childY && y < childBottom) {
                if (child instanceof ViewGroup) {
                    View found = findViewAt((ViewGroup) child, x - childX, y - childY);
                    if (found != null) {
                        return found;
                    }
                }
                if (child.isClickable()) {
                    return child;
                }
            }
        }
        return null;
    }

    private void disableMarkerMode() {
        if (markerOverlay != null && markerOverlay.getParent() != null) {
            windowManager.removeView(markerOverlay);
            markerOverlay = null;
        }
    }

    private void addMarker(int x, int y) {
        TapMarker marker = new TapMarker(x, y);
        markers.add(marker);

        View markerView = new View(this);
        markerView.setBackgroundColor(Color.parseColor("#ff6b6b"));
        markerView.setTag("marker_" + markers.size());

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                40,
                40,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x - 20;
        params.y = y - 20;

        windowManager.addView(markerView, params);
        marker.view = markerView;
    }

    private void removeAllMarkerViews() {
        // Create a copy of the list to avoid concurrent modification issues
        List<TapMarker> markersCopy = new ArrayList<>(markers);
        for (TapMarker marker : markersCopy) {
            if (marker != null && marker.view != null) {
                try {
                    if (marker.view.getParent() != null) {
                        windowManager.removeView(marker.view);
                    }
                } catch (Exception e) {
                    // View might have already been removed, ignore
                }
                // Clear the view reference
                marker.view = null;
            }
        }
    }

    private void hideControlPanel() {
        if (isPanelVisible && controlPanel != null) {
            try {
                if (controlPanel.getParent() != null) {
                    windowManager.removeView(controlPanel);
                }
            } catch (Exception e) {
                // View might have already been removed
            }
            controlPanel = null;
            isPanelVisible = false;
        } else {
            // Force reset if state is inconsistent
            isPanelVisible = false;
            controlPanel = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (controlPanel != null && controlPanel.getParent() != null) {
            windowManager.removeView(controlPanel);
        }
        if (markerOverlay != null && markerOverlay.getParent() != null) {
            windowManager.removeView(markerOverlay);
        }
        if (stopRecordingButton != null && stopRecordingButton.getParent() != null) {
            windowManager.removeView(stopRecordingButton);
        }
        removeAllMarkerViews();
        TapRepeaterService.stopTapping();
        TapRepeaterService.stopPatternPlayback();
    }

    static class TapMarker {
        int x;
        int y;
        View view;

        TapMarker(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static class TapPattern {
        String name;
        List<PatternTap> taps;
        long duration; // Total duration in milliseconds

        TapPattern(String name) {
            this.name = name;
            this.taps = new ArrayList<>();
            this.duration = 0;
        }

        void addTap(int x, int y, long timestamp) {
            taps.add(new PatternTap(x, y, timestamp));
            duration = timestamp; // Update duration to last tap's timestamp
        }
    }

    static class PatternTap {
        int x;
        int y;
        long timestamp; // Time since recording started in milliseconds

        PatternTap(int x, int y, long timestamp) {
            this.x = x;
            this.y = y;
            this.timestamp = timestamp;
        }
    }

    private void startRecording() {
        if (isRecording) return;
        
        // Stop any running tapping
        if (isRunning) {
            isRunning = false;
            TapRepeaterService.stopTapping();
        }
        
        // Stop pattern playback if running
        if (isPlayingPattern) {
            stopPatternPlayback();
        }
        
        isRecording = true;
        currentPattern = new TapPattern("Recorded Pattern");
        recordingStartTime = System.currentTimeMillis();
        
        // Hide control panel so user can see the screen
        if (isPanelVisible) {
            hideControlPanel();
        }
        
        // Show floating stop recording button
        showStopRecordingButton();
        
        // Enable marker overlay to capture taps
        if (!isAddingMarkers) {
            enableMarkerMode();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;
        
        isRecording = false;
        
        // Hide stop recording button
        hideStopRecordingButton();
        
        // Disable marker overlay if not in marker mode
        if (!isAddingMarkers) {
            disableMarkerMode();
        }
    }

    private void recordTap(int x, int y) {
        if (!isRecording || currentPattern == null) return;
        
        long timestamp = System.currentTimeMillis() - recordingStartTime;
        currentPattern.addTap(x, y, timestamp);
    }

    private void startPatternPlayback() {
        if (isPlayingPattern || currentPattern == null || currentPattern.taps.isEmpty()) {
            return;
        }
        
        // Stop any running tapping
        if (isRunning) {
            isRunning = false;
            TapRepeaterService.stopTapping();
        }
        
        // Hide control panel so user can see the screen
        if (isPanelVisible) {
            hideControlPanel();
        }
        
        isPlayingPattern = true;
        TapRepeaterService.startPatternPlayback(currentPattern);
        updateBubbleAppearance();
    }

    private void stopPatternPlayback() {
        if (!isPlayingPattern) return;
        
        isPlayingPattern = false;
        TapRepeaterService.stopPatternPlayback();
        updateBubbleAppearance();
    }

    private void updatePatternStatus(TextView statusText, Button recordButton, Button playButton) {
        if (statusText == null) return;
        
        if (currentPattern != null && !currentPattern.taps.isEmpty()) {
            int tapCount = currentPattern.taps.size();
            long duration = currentPattern.duration;
            statusText.setText(String.format("Pattern: %d taps, %.1fs", tapCount, duration / 1000.0));
            if (playButton != null) {
                playButton.setEnabled(true);
            }
        } else {
            statusText.setText("No pattern recorded");
            if (playButton != null) {
                playButton.setEnabled(false);
            }
        }
    }

    private void showStopRecordingButton() {
        if (stopRecordingButton != null && stopRecordingButton.getParent() != null) {
            return; // Already visible
        }

        // Create stop recording button
        Button button = new Button(this);
        button.setText("⏹ Stop Recording");
        button.setTextSize(14);
        button.setBackgroundColor(Color.parseColor("#ff4444"));
        button.setTextColor(Color.WHITE);
        button.setPadding(24, 16, 24, 16);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
                // Update UI in control panel if it exists
                if (controlPanel != null) {
                    Button recordButton = controlPanel.findViewById(R.id.recordPatternButton);
                    TextView patternStatus = controlPanel.findViewById(R.id.patternStatusText);
                    Button playButton = controlPanel.findViewById(R.id.playPatternButton);
                    if (recordButton != null) {
                        recordButton.setText("🔴 Record Pattern");
                        recordButton.setBackgroundColor(Color.parseColor("#ff6b6b"));
                    }
                    updatePatternStatus(patternStatus, recordButton, playButton);
                }
            }
        });

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                0, // Make it focusable so it can receive touch events
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 50; // Position near top center

        windowManager.addView(button, params);
        stopRecordingButton = button;
        
        // Re-add stop button on top of marker overlay to ensure it receives touches
        if (markerOverlay != null && markerOverlay.getParent() != null) {
            WindowManager.LayoutParams buttonParams = (WindowManager.LayoutParams) button.getLayoutParams();
            windowManager.removeView(button);
            windowManager.addView(button, buttonParams);
            stopRecordingButton = button;
        }
    }

    private void hideStopRecordingButton() {
        if (stopRecordingButton != null && stopRecordingButton.getParent() != null) {
            windowManager.removeView(stopRecordingButton);
            stopRecordingButton = null;
        }
    }
}
