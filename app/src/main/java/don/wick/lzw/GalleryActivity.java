package don.wick.lzw;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 201;
    private static final int PICK_IMAGE_REQUEST = 1;
   int MAX_PREVIEW_WIDTH = 1080;
    int MAX_PREVIEW_HEIGHT = 1920;

    private ImageView previewImageView;
    private Button selectImageButton;
    private Button switchColorButton;
    private Button processButton;
    private boolean isColorMode = true;
    private Bitmap selectedImage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        previewImageView = findViewById(R.id.previewImageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        switchColorButton = findViewById(R.id.switchColorButton);
        processButton = findViewById(R.id.processButton);

        Intent intent = getIntent();
        MAX_PREVIEW_WIDTH = intent.getIntExtra("SELECTED_WIDTH", 1080);
        MAX_PREVIEW_HEIGHT = intent.getIntExtra("SELECTED_HEIGHT", 1920);

        selectImageButton.setOnClickListener(v -> checkPermissionAndPickImage());

        switchColorButton.setOnClickListener(v -> {
            isColorMode = !isColorMode;
            switchColorButton.setText(isColorMode ? "Switch to Grayscale" : "Switch to Color");
        });

        processButton.setOnClickListener(v -> {
            if (selectedImage != null) {
                processImage(selectedImage);
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        });

        processButton.setEnabled(false);
    }

    private String getRequiredPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
    }

    private void checkPermissionAndPickImage() {
        String permission = getRequiredPermission();
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {

            // Show rationale if needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Toast.makeText(this,
                        "Gallery access is needed to select images",
                        Toast.LENGTH_LONG).show();
            }

            ActivityCompat.requestPermissions(this,
                    new String[]{permission},
                    PERMISSION_REQUEST_CODE);
        } else {
            openGallery();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this,
                        "Permission denied. Cannot access gallery without permission.",
                        Toast.LENGTH_LONG).show();

                // If user checked "Don't ask again", we should explain how to grant permission
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, getRequiredPermission())) {
                    Toast.makeText(this,
                            "Please enable gallery permission from app settings",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                Uri imageUri = data.getData();
                if (imageUri == null) {
                    Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                // Scale down bitmap if too large
                if (selectedImage.getWidth() > MAX_PREVIEW_WIDTH ||
                        selectedImage.getHeight() > MAX_PREVIEW_HEIGHT) {
                    float scale = Math.min(
                            (float) MAX_PREVIEW_WIDTH / selectedImage.getWidth(),
                            (float) MAX_PREVIEW_HEIGHT / selectedImage.getHeight()
                    );
                    Matrix matrix = new Matrix();
                    matrix.postScale(scale, scale);
                    selectedImage = Bitmap.createBitmap(
                            selectedImage, 0, 0,
                            selectedImage.getWidth(), selectedImage.getHeight(),
                            matrix, true
                    );
                }

                previewImageView.setImageBitmap(selectedImage);
                processButton.setEnabled(true);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processImage(Bitmap bitmap) {
        ArrayList<int[][]> matrices = new ArrayList<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (isColorMode) {
            int[][][] rgbArrays = new int[3][height][width];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    rgbArrays[0][y][x] = (pixel >> 16) & 0xff; // R channel
                    rgbArrays[1][y][x] = (pixel >> 8) & 0xff;  // G channel
                    rgbArrays[2][y][x] = pixel & 0xff;         // B channel
                }
            }

            matrices.add(rgbArrays[0]); // R channel
            matrices.add(rgbArrays[1]); // G channel
            matrices.add(rgbArrays[2]); // B channel
        } else {
            int[][] grayArray = new int[height][width];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;
                    grayArray[y][x] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                }
            }

            matrices.add(grayArray);
        }

        String dataId = ImageDataManager.getInstance().saveImageData(matrices);
        Intent intent = new Intent(this, CompressionResultActivity.class);
        intent.putExtra("dataId", dataId);
        intent.putExtra("channels", isColorMode ? 3 : 1);
        startActivity(intent);
    }
}