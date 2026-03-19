// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CustomDriverTypeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.custom_driver_type_editor_title);
        setContentView(R.layout.activity_custom_driver_type);

        EditText input = findViewById(R.id.custom_driver_type_input);
        Button cancel = findViewById(R.id.custom_driver_type_cancel);
        Button apply = findViewById(R.id.custom_driver_type_apply);

        String current = getIntent().getStringExtra(EmulatorSettings.EXTRA_CUSTOM_DRIVER_TYPE);
        if (current != null) {
            input.setText(current);
            input.setSelection(current.length());
        }

        cancel.setOnClickListener(v -> finish());

        apply.setOnClickListener(v -> {
            String value = input.getText() != null ? input.getText().toString().trim() : "";
            if (value.isEmpty()) {
                Toast.makeText(this, R.string.custom_driver_type_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            Intent result = new Intent();
            result.putExtra(EmulatorSettings.EXTRA_CUSTOM_DRIVER_TYPE, value);
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
