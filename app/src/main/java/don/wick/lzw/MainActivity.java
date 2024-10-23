package don.wick.lzw;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Button manualInputButton, cameraInputButton, galleryInputButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manualInputButton = findViewById(R.id.manualInputButton);
        cameraInputButton = findViewById(R.id.cameraInputButton);
        galleryInputButton = findViewById(R.id.galleryInputButton);

        // Manual input button handler
        manualInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ManualActivity.class);
                startActivity(intent);
            }
        });

        // Camera input button handler
        cameraInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResolutionDialog(true); // true for camera
            }
        });

        // Gallery input button handler
        galleryInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResolutionDialog(false); // false for gallery
            }
        });
    }

    // Method to show the resolution dialog
    private void showResolutionDialog(final boolean isCamera) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Select Resolution");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_resolution, null);
        final Spinner resolutionSpinner = dialogView.findViewById(R.id.resolutionSpinner);

        String[] resolutionOptions = {
                "320x480", "480x720", "640x960", "720x1280", "1080x1920", "1440x2560", "2160x3840"
        };

        ArrayAdapter<String> resolutionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, resolutionOptions);
        resolutionSpinner.setAdapter(resolutionAdapter);

        builder.setView(dialogView);

        builder.setPositiveButton("Proceed", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Get selected resolution pair
                String selectedResolution = (String) resolutionSpinner.getSelectedItem();
                String[] dimensions = selectedResolution.split("x");
                int selectedWidth = Integer.parseInt(dimensions[0]);
                int selectedHeight = Integer.parseInt(dimensions[1]);

                Intent intent;
                if (isCamera) {
                    intent = new Intent(MainActivity.this, CameraActivity.class);
                } else {
                    intent = new Intent(MainActivity.this, GalleryActivity.class);
                }

                intent.putExtra("SELECTED_WIDTH", selectedWidth);
                intent.putExtra("SELECTED_HEIGHT", selectedHeight);
                startActivity(intent);
            }
        });

        builder.setNegativeButton("Cancel", null);

        builder.create().show();
    }
}