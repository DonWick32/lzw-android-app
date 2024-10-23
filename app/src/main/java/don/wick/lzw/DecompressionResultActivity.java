package don.wick.lzw;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DecompressionResultActivity extends AppCompatActivity {
    private TextView decompressionDetails;
    private TableLayout lzwTable;
    private ImageView recoveredImageView;
    private LinearLayout matricesContainer;
    private static final int MAX_MATRIX_DISPLAY = 20;
    private List<List<Integer>> compressedData;
    private int channels;
    private int[] originalDimensions;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decompression_result);

        decompressionDetails = findViewById(R.id.decompressionDetails);
        lzwTable = findViewById(R.id.lzwTable);
        recoveredImageView = findViewById(R.id.recoveredImageView);
        matricesContainer = findViewById(R.id.matricesContainer);

        String dataId = getIntent().getStringExtra("dataId");
        channels = getIntent().getIntExtra("channels", 1);
        originalDimensions = getIntent().getIntArrayExtra("originalDimensions");

        compressedData = ImageDataManager.getInstance().getCompressedData(dataId);

        if (compressedData != null && !compressedData.isEmpty()) {
            new DecompressionTask().execute();
        } else {
            decompressionDetails.setText("Error: No valid compressed data received");
        }
    }

    private class DecompressionTask extends AsyncTask<Void, Void, DecompressionTaskResult> {
        private long startTime;

        @Override
        protected void onPreExecute() {
            startTime = System.nanoTime();
            decompressionDetails.setText("Decompressing... Please wait.");
        }

        @Override
        protected DecompressionTaskResult doInBackground(Void... voids) {
            try {
                List<int[][]> decompressedMatrices = new ArrayList<>();
                StringBuilder resultBuilder = new StringBuilder();
                List<ChannelDecompressionResult> channelResults = new ArrayList<>();

                for (int channel = 0; channel < channels; channel++) {
                    resultBuilder.append("\nChannel ").append(channel + 1).append(" Results:\n");
                    List<Integer> compressedChannel = new ArrayList<>(compressedData.get(channel));

                    DecompressionResult result = decompressWithSteps(compressedChannel);
                    int[][] channelMatrix = convertStringToMatrix(result.output);
                    decompressedMatrices.add(channelMatrix);

                    resultBuilder.append("First 20 decoded values: ");
                    for (int i = 0; i < Math.min(20, result.output.length()); i++) {
                        resultBuilder.append((int)result.output.charAt(i)).append(" ");
                    }
                    resultBuilder.append("...\n");

                    channelResults.add(new ChannelDecompressionResult(channel, result.steps));
                }

                Bitmap recoveredImage = createImageFromMatrices(decompressedMatrices);

                return new DecompressionTaskResult(
                        decompressedMatrices,
                        channelResults,
                        recoveredImage,
                        resultBuilder.toString(),
                        System.nanoTime() - startTime
                );
            } catch (Exception e) {
                Log.e("DecompressionActivity", "Error processing decompression results", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(DecompressionTaskResult result) {
            if (result == null) {
                decompressionDetails.setText("Error processing decompression results");
                recoveredImageView.setImageResource(android.R.drawable.ic_dialog_alert);
                return;
            }

            recoveredImageView.setImageBitmap(result.recoveredImage);
            displayMatrices(result.decompressedMatrices);

            for (ChannelDecompressionResult channelResult : result.channelResults) {
                addChannelStepsToTable(channelResult.channel, channelResult.steps);
            }

            decompressionDetails.setText(String.format(
                    "Decompression completed in %d ms\n" +
                            "Image dimensions: %dx%d\n" +
                            "No. of Entries in Input: %d\n" +
                            "No. of Entries in Output: %d\n" +
                            "Channels: %d\n\n%s",
                    result.timeTaken / 1000000,
                    originalDimensions[0], originalDimensions[1],
                    compressedData.get(0).size() * channels,
                    result.decompressedMatrices.get(0).length * result.decompressedMatrices.get(0)[0].length * channels,
                    channels,
                    result.resultString
            ));
        }
    }

    private static class DecompressionTaskResult {
        final List<int[][]> decompressedMatrices;
        final List<ChannelDecompressionResult> channelResults;
        final Bitmap recoveredImage;
        final String resultString;
        final long timeTaken;

        DecompressionTaskResult(
                List<int[][]> decompressedMatrices,
                List<ChannelDecompressionResult> channelResults,
                Bitmap recoveredImage,
                String resultString,
                long timeTaken
        ) {
            this.decompressedMatrices = decompressedMatrices;
            this.channelResults = channelResults;
            this.recoveredImage = recoveredImage;
            this.resultString = resultString;
            this.timeTaken = timeTaken;
        }
    }

    private static class ChannelDecompressionResult {
        final int channel;
        final List<DecompressionStep> steps;

        ChannelDecompressionResult(int channel, List<DecompressionStep> steps) {
            this.channel = channel;
            this.steps = steps;
        }
    }

    private static class DecompressionResult {
        String output;
        List<DecompressionStep> steps;

        DecompressionResult(String output, List<DecompressionStep> steps) {
            this.output = output;
            this.steps = steps;
        }
    }

    private static class DecompressionStep {
        int code;
        String entry;
        int dictionarySize;
        String currentString;

        DecompressionStep(int code, String entry, int dictionarySize, String currentString) {
            this.code = code;
            this.entry = entry;
            this.dictionarySize = dictionarySize;
            this.currentString = currentString;
        }
    }

    private DecompressionResult decompressWithSteps(List<Integer> compressed) {
        int dictSize = 256;
        Map<Integer, String> dictionary = new HashMap<>();
        List<DecompressionStep> steps = new ArrayList<>();

        for (int i = 0; i < 256; i++) {
            dictionary.put(i, "" + (char)i);
        }

        Integer val = compressed.remove(0);
        String w = "" + (char)(int)val;
        StringBuffer result = new StringBuffer(w);
        StringBuffer resultOutput = new StringBuffer(val + "-");


        for (int k : compressed) {
            String entry;
            StringBuilder entryOutput = new StringBuilder();

            if (dictionary.containsKey(k)) {
                entry = dictionary.get(k);

                for (int i = 0; i < entry.length() - 1; i++) {
                    entryOutput.append((int) entry.charAt(i)).append("-");
                }

                entryOutput.append((int) entry.charAt(entry.length() - 1));

            } else if (k == dictSize) {
                entry = w + w.charAt(0);

                for (int i = 0; i < w.length(); i++) {
                    entryOutput.append((int) w.charAt(i)).append("-");
                }

                entryOutput = new StringBuilder(entryOutput + "" + (int) w.charAt(0));
            } else {
                throw new IllegalArgumentException("Bad compressed k: " + k);
            }

            result.append(entry);
            resultOutput.append(entryOutput + "-");

            if (steps.size() < 20) {
                steps.add(new DecompressionStep(k, entryOutput.toString(), dictSize, resultOutput.toString().substring(0, resultOutput.length() - 1)));
            }

            dictionary.put(dictSize++, w + entry.charAt(0));

            w = entry;
        }

        return new DecompressionResult(result.toString(), steps);
    }

    private void addChannelStepsToTable(int channel, List<DecompressionStep> steps) {
        TableRow headerRow = new TableRow(this);
        TextView channelHeader = createTextView("Channel " + (channel + 1) + " Decompression Steps", true);
        channelHeader.setGravity(Gravity.CENTER);
        headerRow.addView(channelHeader);
        lzwTable.addView(headerRow);

        TableRow columnHeader = new TableRow(this);
        String[] headers = {"Step   ", "Code    ", "Entry   ", "Dict Size   ", "Current Output  "};
        for (String header : headers) {
            columnHeader.addView(createTextView(header, true));
        }
        lzwTable.addView(columnHeader);

        // Add steps
        for (int i = 0; i < steps.size(); i++) {
            DecompressionStep step = steps.get(i);
            TableRow row = new TableRow(this);

            row.addView(createTextView(String.valueOf(i + 1), false));
            row.addView(createTextView(String.valueOf(step.code), false));
            row.addView(createTextView(step.entry, false));
            row.addView(createTextView(String.valueOf(step.dictionarySize), false));
            row.addView(createTextView(step.currentString, false));

            lzwTable.addView(row);
        }

        if (steps.size() >= 20) {
            TableRow ellipsisRow = new TableRow(this);
            TextView ellipsis = createTextView("...", false);
            ellipsisRow.addView(ellipsis);
            lzwTable.addView(ellipsisRow);
        }

        TableRow spacingRow = new TableRow(this);
        spacingRow.addView(createTextView("", false));
        lzwTable.addView(spacingRow);
    }

    private void displayMatrices(List<int[][]> matrices) {
        for (int channel = 0; channel < matrices.size(); channel++) {
            LinearLayout channelContainer = new LinearLayout(this);
            channelContainer.setOrientation(LinearLayout.VERTICAL);
            channelContainer.setPadding(16, 0, 16, 0);

            // Add channel header
            TextView header = new TextView(this);
            header.setText("Channel " + (channel + 1));
            channelContainer.addView(header);

            TableLayout matrixTable = new TableLayout(this);
            int[][] matrix = matrices.get(channel);

            int maxRows = Math.min(MAX_MATRIX_DISPLAY, matrix.length);
            int maxCols = Math.min(MAX_MATRIX_DISPLAY, matrix[0].length);

            for (int i = 0; i < maxRows; i++) {
                TableRow row = new TableRow(this);
                for (int j = 0; j < maxCols; j++) {
                    TextView cell = createTextView(String.valueOf(matrix[i][j]), false);
                    cell.setPadding(4, 4, 4, 4);
                    row.addView(cell);
                }
                if (maxCols < matrix[0].length) {
                    row.addView(createTextView("...", false));
                }
                matrixTable.addView(row);
            }

            if (maxRows < matrix.length) {
                TableRow ellipsisRow = new TableRow(this);
                ellipsisRow.addView(createTextView("...", false));
                matrixTable.addView(ellipsisRow);
            }

            channelContainer.addView(matrixTable);
            matricesContainer.addView(channelContainer);
        }
    }

    private TextView createTextView(String text, boolean isHeader) {
        TextView tv = new TextView(this);
        tv.setText(text);
        if (isHeader) {
            tv.setTextAppearance(R.style.TableHeader);
        } else {
            tv.setTextAppearance(R.style.TableCell);
        }
        return tv;
    }

    private int[][] convertStringToMatrix(String decompressed) {
        int rows = originalDimensions[0];
        int cols = originalDimensions[1];
        int[][] matrix = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols && (i * cols + j) < decompressed.length(); j++) {
                matrix[i][j] = (int)decompressed.charAt(i * cols + j) & 0xFF;
            }
        }
        return matrix;
    }

    private Bitmap createImageFromMatrices(List<int[][]> matrices) {
        int height = originalDimensions[0];
        int width = originalDimensions[1];
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel;
                if (channels == 1) {
                    // Grayscale
                    int gray = matrices.get(0)[y][x];
                    pixel = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
                } else {
                    // RGB
                    int r = matrices.get(0)[y][x];
                    int g = matrices.get(1)[y][x];
                    int b = matrices.get(2)[y][x];
                    pixel = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
                bitmap.setPixel(x, y, pixel);
            }
        }
        return bitmap;
    }
}