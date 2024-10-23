package don.wick.lzw;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TableLayout;
import android.widget.TableRow;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;

public class ManualActivity extends AppCompatActivity {

    private TextInputEditText rowsInput, columnsInput, channelInput;
    private TableLayout matrixTable;
    private AutoCompleteTextView channelSelector;
    private int rows = 4, columns = 4, channels = 3;
    private ArrayList<int[][]> rgbMatrices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);

        initializeViews();
        setupToolbar();
        initializeMatrices();
        setupChannelSelector();
        setupButtons();
    }

    private void initializeViews() {
        rowsInput = findViewById(R.id.rowsInput);
        columnsInput = findViewById(R.id.columnsInput);
        channelInput = findViewById(R.id.channelInput);
        matrixTable = findViewById(R.id.matrixTable);
        channelSelector = findViewById(R.id.channelSelector);

        // Set default values
        rowsInput.setText(String.valueOf(rows));
        columnsInput.setText(String.valueOf(columns));
        channelInput.setText(String.valueOf(channels));
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeMatrices() {
        rgbMatrices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rgbMatrices.add(new int[rows][columns]);
        }
        setDefaultMatrix();
    }

    private void setupChannelSelector() {
        String[] channels = {"Red Channel", "Green Channel", "Blue Channel"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.dropdown_item_resolution, channels);
        channelSelector.setAdapter(adapter);
        channelSelector.setText(channels[0], false);
        channelSelector.setOnItemClickListener((parent, view, position, id) ->
                displayMatrix(position));
    }

    private void setupButtons() {
        findViewById(R.id.createTableButton).setOnClickListener(v -> validateAndCreateTable());
        findViewById(R.id.submitButton).setOnClickListener(v -> validateAndCompressMatrix());
    }

    private void validateAndCreateTable() {
        try {
            rows = Integer.parseInt(rowsInput.getText().toString());
            columns = Integer.parseInt(columnsInput.getText().toString());
            channels = Integer.parseInt(channelInput.getText().toString());

            if (rows <= 0 || columns <= 0 || (channels != 1 && channels != 3)) {
                showError("Invalid input values. Please check your inputs.");
                return;
            }

            createTable(rows, columns);
            showSuccess("Matrix created successfully!");
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers");
        }
    }

    private void displayMatrix(int channel) {
        matrixTable.removeAllViews();
        int[][] matrix = rgbMatrices.get(channel);

        for (int i = 0; i < rows; i++) {
            TableRow tableRow = new TableRow(this);
            for (int j = 0; j < columns; j++) {
                TextInputLayout inputLayout = createMatrixInputLayout(i, j, matrix);
                TableRow.LayoutParams params = new TableRow.LayoutParams(
                        TableRow.LayoutParams.WRAP_CONTENT,
                        TableRow.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(4, 4, 4, 4);
                inputLayout.setLayoutParams(params);
                tableRow.addView(inputLayout);
            }
            matrixTable.addView(tableRow);
        }
    }

    private TextInputLayout createMatrixInputLayout(int row, int col, int[][] matrix) {
        TextInputLayout inputLayout = new TextInputLayout(this);
//        inputLayout.setStyle(com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox_Dense);

        TextInputEditText editText = new TextInputEditText(this);
        editText.setText(String.valueOf(matrix[row][col]));
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        editText.setEms(3);
        editText.setMaxLines(1);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                validateMatrixInput(editText, matrix, row, col);
            }
        });

        inputLayout.addView(editText);
        return inputLayout;
    }

    private void validateMatrixInput(TextInputEditText editText, int[][] matrix, int row, int col) {
        try {
            int value = Integer.parseInt(editText.getText().toString());
            if (value < 0 || value > 255) {
                editText.setText("69");
                showError("Value must be between 0 and 255");
            }
            matrix[row][col] = value;
        } catch (NumberFormatException e) {
            editText.setText("69");
            showError("Invalid input");
        }
    }

    private void validateAndCompressMatrix() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm Compression")
                .setMessage("Are you sure you want to compress this matrix?")
                .setPositiveButton("Compress", (dialog, which) -> compressMatrix(channels))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showError(String message) {
        Snackbar.make(findViewById(android.R.id.content), message,
                        Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(android.R.color.holo_red_light))
                .show();
    }

    private void showSuccess(String message) {
        Snackbar.make(findViewById(android.R.id.content), message,
                        Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(android.R.color.holo_green_light))
                .show();
    }

    private void setDefaultMatrix() {
        int[][] predefinedMatrix = {
                {39, 39, 126, 126},
                {39, 39, 126, 126},
                {39, 39, 126, 126},
                {39, 39, 126, 126}
        };

        int[][] defaultMatrix = rgbMatrices.get(0);
        for (int i = 0; i < rows; i++) {
            System.arraycopy(predefinedMatrix[i], 0, defaultMatrix[i], 0, columns);
        }

        for (int channel = 1; channel < 3; channel++) {
            int[][] matrix = rgbMatrices.get(channel);
            for (int i = 0; i < rows; i++) {
                System.arraycopy(predefinedMatrix[i], 0, matrix[i], 0, columns);
            }
        }

        displayMatrix(0);
    }

    private void createTable(int rows, int columns) {
        rgbMatrices.clear();
        for (int i = 0; i < 3; i++) {
            rgbMatrices.add(new int[rows][columns]);
        }

        setDefaultMatrix();

        displayMatrix(0);
    }

    private void compressMatrix(int channels) {
        String dataId = ImageDataManager.getInstance().saveImageData(rgbMatrices);

        Intent intent = new Intent(this, CompressionResultActivity.class);
        intent.putExtra("dataId", dataId);
        intent.putExtra("channels", channels);
        startActivity(intent);
    }
}
