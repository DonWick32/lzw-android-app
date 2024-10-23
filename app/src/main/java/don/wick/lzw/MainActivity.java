package don.wick.lzw;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    private Button manualInputButton, cameraInputButton, galleryInputButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manualInputButton = findViewById(R.id.manualInputButton);
        cameraInputButton = findViewById(R.id.cameraInputButton);
        galleryInputButton = findViewById(R.id.galleryInputButton);

        manualInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, ManualActivity.class);
                startActivity(intent);
            }
        });

        cameraInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResolutionDialog(true);
            }
        });

        galleryInputButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showResolutionDialog(false);
            }
        });
    }

    private void showResolutionDialog(final boolean isCamera) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Select Resolution");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_resolution, null);
        AutoCompleteTextView resolutionDropdown = dialogView.findViewById(R.id.resolutionSpinner);

        String[] resolutionOptions = {
                "320 × 480 (HVGA)",
                "480 × 720 (HD)",
                "640 × 960 (qHD)",
                "720 × 1280 (HD+)",
                "1080 × 1920 (FHD)",
                "1440 × 2560 (QHD)",
                "2160 × 3840 (4K UHD)"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.dropdown_item_resolution,
                resolutionOptions
        );

        resolutionDropdown.setAdapter(adapter);
        resolutionDropdown.setText(resolutionOptions[0], false); // Set default selection

        builder.setView(dialogView).setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String selectedResolution = resolutionDropdown.getText().toString();
                String[] dimensions = selectedResolution.split(" ");
                int selectedWidth = Integer.parseInt(dimensions[0]);
                int selectedHeight = Integer.parseInt(dimensions[2]);

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
        }).setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());;

        builder.create().show();
    }
}