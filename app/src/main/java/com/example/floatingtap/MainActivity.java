package com.example.floatingtap;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQUEST_CODE_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button enableAccessibilityButton = findViewById(R.id.enableAccessibilityButton);
        Button enableOverlayButton = findViewById(R.id.enableOverlayButton);
        Button startServiceButton = findViewById(R.id.startServiceButton);

        enableAccessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(MainActivity.this,
                        "Enable 'Floating Tap' in Accessibility",
                        Toast.LENGTH_LONG).show();
            }
        });

        enableOverlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_CODE_OVERLAY);
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Overlay permission already granted!",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAccessibilityServiceEnabled()) {
                    Toast.makeText(MainActivity.this,
                            "Please enable Accessibility Service first!",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this,
                            "Please enable Overlay permission first!",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                Intent intent = new Intent(MainActivity.this, OverlayService.class);
                startService(intent);
                Toast.makeText(MainActivity.this,
                        "Floating bubble started! Press Home button.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + TapRepeaterService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 1) {
                String services = Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if (services != null) {
                    return services.toLowerCase().contains(service.toLowerCase());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}