package don.wick.lzw;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class ManualActivity extends AppCompatActivity {

    private EditText rowsInput, columnsInput, channelInput;
    private TableLayout matrixTable;
    private Button createTableButton, submitButton;
    private Spinner channelSelector;
    private int rows = 4, columns = 4, channels = 3;
    private ArrayList<int[][]> rgbMatrices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual);

        rowsInput = findViewById(R.id.rowsInput);
        columnsInput = findViewById(R.id.columnsInput);
        channelInput = findViewById(R.id.channelInput);
        matrixTable = findViewById(R.id.matrixTable);
        createTableButton = findViewById(R.id.createTableButton);
        submitButton = findViewById(R.id.submitButton);
        channelSelector = findViewById(R.id.channelSelector);

        rowsInput.setText(String.valueOf(rows));
        columnsInput.setText(String.valueOf(columns));
        channelInput.setText(String.valueOf(channels));

        rgbMatrices = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rgbMatrices.add(new int[rows][columns]);
        }

        setDefaultMatrix();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.channels_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        channelSelector.setAdapter(adapter);
        channelSelector.setSelection(0);

        channelSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                displayMatrix(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {

            }
        });

        createTableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    rows = Integer.parseInt(rowsInput.getText().toString());
                    columns = Integer.parseInt(columnsInput.getText().toString());
                    channels = Integer.parseInt(channelInput.getText().toString());
                    createTable(rows, columns);
                } catch (NumberFormatException e) {
                    Toast.makeText(ManualActivity.this, "Invalid input for size or channels", Toast.LENGTH_SHORT).show();
                }
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                compressMatrix(channels);
            }
        });
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

    private void displayMatrix(int channel) {
        matrixTable.removeAllViews();
        int[][] matrix = rgbMatrices.get(channel);
        for (int i = 0; i < rows; i++) {
            TableRow tableRow = new TableRow(this);
            for (int j = 0; j < columns; j++) {
                EditText editText = new EditText(this);
                editText.setText(String.valueOf(matrix[i][j]));
                editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                editText.setEms(3);
                editText.setMaxLines(1);
                editText.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
                editText.setHint("0-255");
                int finalI = i;
                int finalJ = j;
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        if (!hasFocus) {
                            try {
                                int value = Integer.parseInt(editText.getText().toString());
                                if (value <= 0 || value >= 255) {
                                    Toast.makeText(ManualActivity.this, "Value must be between 0 and 255", Toast.LENGTH_SHORT).show();
                                    editText.setText("69");
                                } else {
                                    matrix[finalI][finalJ] = value;
                                }
                            } catch (NumberFormatException e) {
                                Toast.makeText(ManualActivity.this, "Invalid input", Toast.LENGTH_SHORT).show();
                                editText.setText("69");
                            }
                        }
                    }
                });
                tableRow.addView(editText);
            }
            matrixTable.addView(tableRow);
        }
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
