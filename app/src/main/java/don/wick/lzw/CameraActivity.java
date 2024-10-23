package don.wick.lzw;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class CameraActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 200;
     int MAX_PREVIEW_WIDTH = 1080;
     int MAX_PREVIEW_HEIGHT = 1920;

    private TextureView textureView;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Size previewSize;
    private Button captureButton;
    private Button switchColorButton;
    private boolean isColorMode = true;

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            setupCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        textureView = findViewById(R.id.textureView);
        captureButton = findViewById(R.id.captureButton);
        switchColorButton = findViewById(R.id.switchColorButton);

        Intent intent = getIntent();
        MAX_PREVIEW_WIDTH = intent.getIntExtra("SELECTED_WIDTH", 1080);
        MAX_PREVIEW_HEIGHT = intent.getIntExtra("SELECTED_HEIGHT", 1920);

        captureButton.setOnClickListener(v -> takePicture());
        switchColorButton.setOnClickListener(v -> {
            isColorMode = !isColorMode;
            switchColorButton.setText(isColorMode ? "Switch to Grayscale" : "Switch to Color");
        });

        if (checkCameraPermission()) {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        } else {
            requestCameraPermission();
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String camId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(camId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = camId;
                    break;
                }
            }

            previewSize = chooseOptimalSize(
                    manager.getCameraCharacteristics(cameraId),
                    width,
                    height
            );

            imageReader = ImageReader.newInstance(
                    previewSize.getWidth(),
                    previewSize.getHeight(),
                    ImageFormat.JPEG,
                    1
            );

            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image != null) {
                        processImage(image);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void processImage(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        if (bitmap.getWidth() > MAX_PREVIEW_WIDTH || bitmap.getHeight() > MAX_PREVIEW_HEIGHT) {
            float scale = Math.min(
                    (float) MAX_PREVIEW_WIDTH / bitmap.getWidth(),
                    (float) MAX_PREVIEW_HEIGHT / bitmap.getHeight()
            );
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true
            );
        }

        ArrayList<int[][]> matrices = new ArrayList<>();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (isColorMode) {
            // RGB mode - extract all three channels
            int[][][] rgbArrays = new int[3][width][height];

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = bitmap.getPixel(x, height - y - 1);
                    rgbArrays[0][x][y] = (pixel >> 16) & 0xff; // R
                    rgbArrays[1][x][y] = (pixel >> 8) & 0xff;  // G
                    rgbArrays[2][x][y] = pixel & 0xff;         // B
                }
            }

            matrices.add(rgbArrays[0]); // R channel
            matrices.add(rgbArrays[1]); // G channel
            matrices.add(rgbArrays[2]); // B channel
        } else {
            // Grayscale mode - convert to single channel
            int[][] grayArray = new int[width][height];

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = bitmap.getPixel(x, height - y - 1);
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;
                    // Standard grayscale conversion
                    grayArray[x][y] = (int)(0.299 * r + 0.587 * g + 0.114 * b);
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

    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            cameraCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(
                                    CameraActivity.this,
                                    "Configuration change",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    },
                    null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) return;
        try {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            cameraCaptureSession.setRepeatingRequest(
                    captureRequestBuilder.build(),
                    null,
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takePicture() {
        try {
            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            cameraCaptureSession.capture(
                    captureBuilder.build(),
                    null,
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size chooseOptimalSize(CameraCharacteristics characteristics, int width, int height) {
        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);

        // Choose the smallest size that's larger than our target
        return Collections.min(
                Arrays.asList(sizes),
                (a, b) -> Long.signum((long) a.getWidth() * a.getHeight() -
                        (long) b.getWidth() * b.getHeight())
        );
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                textureView.setSurfaceTextureListener(surfaceTextureListener);
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }
}