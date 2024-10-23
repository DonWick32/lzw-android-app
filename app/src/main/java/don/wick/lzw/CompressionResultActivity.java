package don.wick.lzw;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompressionResultActivity extends AppCompatActivity {
    private ArrayList<int[][]> matrixData;
    private int channels;
    private TextView compressionDetails;
    private TableLayout lzwTable;
    private List<List<Integer>> compressedOutputs;
    private static final int MAX_ROWS_PER_TABLE = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compression_result);

        compressionDetails = findViewById(R.id.compressionDetails);
        lzwTable = findViewById(R.id.lzwTable);
        Button decompressButton = findViewById(R.id.decompressButton);

        String dataId = getIntent().getStringExtra("dataId");
        channels = getIntent().getIntExtra("channels", 1);

        compressedOutputs = new ArrayList<>();

        matrixData = ImageDataManager.getInstance().getImageData(dataId);


        if (matrixData == null || matrixData.isEmpty()) {
            System.out.println("No matrix data available.");
            return;
        }

        if (matrixData != null && !matrixData.isEmpty()) {
            processCompressionResultsNew();
        } else {
            compressionDetails.setText("Error: No valid matrix data received");
            finish();
        }

        decompressButton.setOnClickListener(v -> {

            String dataId2 = ImageDataManager.getInstance().saveCompressedData(compressedOutputs);

            Intent intent = new Intent(CompressionResultActivity.this, DecompressionResultActivity.class);
            intent.putExtra("dataId", dataId2);
            intent.putExtra("channels", channels);
            if (matrixData != null && !matrixData.isEmpty()) {
                intent.putExtra("originalDimensions", new int[]{
                        matrixData.get(0).length,
                        matrixData.get(0)[0].length
                });
            }
            startActivity(intent);
        });
    }

    private class CompressionTask extends AsyncTask<Void, Void, String> {
        private long startTime;
        private StringBuilder resultBuilder = new StringBuilder();
        private int totalOriginalSize = 0;
        private int totalCompressedSize = 0;

        @Override
        protected void onPreExecute() {
            // This runs on the main thread before the background task starts
            startTime = System.nanoTime();
            compressionDetails.setText("Compressing... Please wait.");
        }

        @Override
        protected String doInBackground(Void... voids) {

            int compressionBits = 0;
            // This is executed in the background thread
            for (int channel = 0; channel < channels; channel++) {
                resultBuilder.append("\nChannel ").append(channel + 1).append(" Results:\n");
                List<Integer> pixelList = convertMatrixToList(matrixData.get(channel));
                totalOriginalSize += pixelList.size() * 8;

                CompressionResult result = compressAndGenerateTable(pixelList, channel);
                compressedOutputs.add(result.compressedOutput);
                int calc = calculateCompressedSize(result.compressedOutput);
                totalCompressedSize += calc;
                int bitsNeeded = 32 - Integer.numberOfLeadingZeros(result.compressedOutput.stream().max(Integer::compare).orElse(0));
                compressionBits = Math.max(compressionBits, bitsNeeded);

                resultBuilder.append("Final encoded output: ");
                for (int i = 0; i < Math.min(10, result.compressedOutput.size()); i++) {
                    resultBuilder.append(result.compressedOutput.get(i)).append(" ");
                }
                if (result.compressedOutput.size() > 10) {
                    resultBuilder.append("...");
                }
                resultBuilder.append("\n");
            }

            long timeTaken = (System.nanoTime() - startTime) / 1000000;
            float compressionRatio = (totalOriginalSize * 1f) / totalCompressedSize;
            float compressionPercentage = (totalOriginalSize - totalCompressedSize) * 100f / totalOriginalSize;
            float redundancy = 1f - (1f / compressionRatio);

            return String.format(
                    "Compression completed in %d ms\n" +
                            "Initial No. of Pixels : %d " +
                            "(No. of bits: %d)\n" +
                            "Final No. of Entries : %d " +
                            "(No. of bits: %d)\n" +
                            "Original size: %.3f MB (%d bits)\n" +
                            "Compressed size: %.3f MB (%d bits)\n" +
                            "Compression ratio: %.2f\n" +
                            "Redundancy: %.2f\n" +
                            "Compression Percentage: %.2f%%\n\n%s",
                    timeTaken,
                    totalOriginalSize / 8,
                    8,
                    compressedOutputs.get(0).size() * channels,
                    compressionBits,
                    totalOriginalSize / (8.0 * 1024 * 1024), totalOriginalSize,
                    totalCompressedSize / (8.0 * 1024 * 1024), totalCompressedSize,
                    compressionRatio,
                    redundancy,
                    compressionPercentage,
                    resultBuilder.toString()
            );

        }

        @Override
        protected void onPostExecute(String result) {
            compressionDetails.setText(result);
        }
    }

    private void processCompressionResultsNew() {
        new CompressionTask().execute();
    }

    private List<Integer> convertMatrixToList(int[][] matrix) {
        List<Integer> pixelList = new ArrayList<>();
        for (int[] row : matrix) {
            for (int pixel : row) {
                pixelList.add(pixel);
            }
        }
        return pixelList;
    }

    private static class CompressionResult {
        List<Integer> compressedOutput;
        Map<String, Integer> dictionary;

        CompressionResult(List<Integer> output, Map<String, Integer> dict) {
            this.compressedOutput = output;
            this.dictionary = dict;
        }
    }

    private CompressionResult compressAndGenerateTable(List<Integer> pixelList, int channelNum) {
        Map<String, Integer> dictionary = new HashMap<>();
        List<Integer> compressedOutput = new ArrayList<>();

        for (int i = 0; i < 256; i++) {
            dictionary.put(String.valueOf(i), i);
        }

        runOnUiThread(() -> addTableHeader(channelNum));

        String w = "";
        int dictSize = 256;
        int rowCount = 0;

        for (int pixel : pixelList) {
            String wc = w.isEmpty() ? String.valueOf(pixel) : w + "-" + pixel;

            if (dictionary.containsKey(wc)) {
                if (rowCount < MAX_ROWS_PER_TABLE) {
                    String finalW = w;
                    int finalPixel = pixel;
                    runOnUiThread(() -> addTableRow(finalW, finalPixel, "-", "-", "Found: " + wc));
                }
                rowCount++;
                w = wc;
            } else {
                Integer outputCode = dictionary.get(w);
                if (outputCode != null) {
                    if (rowCount < MAX_ROWS_PER_TABLE) {
                        String finalW = w;
                        int finalPixel = pixel;
                        int finalDictSize = dictSize;
                        String finalWc = wc;
                        runOnUiThread(() -> addTableRow(finalW, finalPixel, outputCode, finalDictSize, finalWc));
                    }
                    rowCount++;

                    compressedOutput.add(outputCode);
                    dictionary.put(wc, dictSize++);
                    w = String.valueOf(pixel);
                } else if (!w.isEmpty()) {
                    w = String.valueOf(pixel);
                }
            }

            if (rowCount == MAX_ROWS_PER_TABLE) {
                runOnUiThread(this::addEllipsisRow);
            }
        }

        if (!w.isEmpty()) {
            Integer outputCode = dictionary.get(w);
            if (outputCode != null) {
                compressedOutput.add(outputCode);
                if (rowCount < MAX_ROWS_PER_TABLE) {
                    String finalW = w;
                    runOnUiThread(() -> addTableRow(finalW, -1, outputCode, -1, "END"));
                }
            }
        }

        return new CompressionResult(compressedOutput, dictionary);
    }


    private void addTableHeader(int channelNum) {
        TableRow headerRow = new TableRow(this);
        String[] headers = {"CRS    ", "Current Pixel   ", "Encoded Output  ", "Dict Location   ", "Dict Entry  "};

        TextView channelHeader = new TextView(this);
        channelHeader.setText("Channel " + (channelNum + 1));
        channelHeader.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        channelHeader.setPadding(16, 32, 16, 16);

        TableRow channelRow = new TableRow(this);
        channelRow.addView(channelHeader);
        lzwTable.addView(channelRow);

        for (String header : headers) {
            TextView textView = new TextView(this);
            textView.setText(header);
            textView.setTextAppearance(R.style.TableHeader);
            headerRow.addView(textView);
        }
        lzwTable.addView(headerRow);
    }

    private void addTableRow(String crs, int pixel, String encodedOutput, String dictLocation, String dictEntry) {
        TableRow row = new TableRow(this);

        TextView[] cells = {
                createTextView(crs),
                createTextView(pixel == -1 ? "END" : String.valueOf(pixel)),
                createTextView(encodedOutput),
                createTextView(dictLocation),
                createTextView(dictEntry)
        };

        for (TextView cell : cells) {
            row.addView(cell);
        }

        lzwTable.addView(row);
    }

    private void addTableRow(String crs, int pixel, int encodedOutput, int dictLocation, String dictEntry) {
        addTableRow(
                crs,
                pixel,
                String.valueOf(encodedOutput),
                dictLocation == -1 ? "END" : String.valueOf(dictLocation),
                dictEntry
        );
    }

    private TextView createTextView(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextAppearance(R.style.TableCell);
        return textView;
    }

    private void addEllipsisRow() {
        TableRow row = new TableRow(this);
        TextView ellipsisView = new TextView(this);
        ellipsisView.setText("...");
        ellipsisView.setTextAppearance(R.style.TableCell);
        row.addView(ellipsisView);
        lzwTable.addView(row);
    }

    private int calculateCompressedSize(List<Integer> compressed) {
        int maxValue = 0;
        for (int value : compressed) {
            maxValue = Math.max(maxValue, value);
        }
        int bitsNeeded = 32 - Integer.numberOfLeadingZeros(maxValue);
        return compressed.size() * bitsNeeded;
    }
}