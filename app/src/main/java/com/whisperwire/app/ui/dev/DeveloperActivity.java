package com.whisperwire.app.ui.dev;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.whisperwire.app.R;

/**
 * Developer/debug screen.
 * Displays the device’s long-term identity public key and allows running the
 * local crypto benchmark (ECDH → HKDF → AES-GCM).
 *
 * Used only for demonstration/testing, not part of normal user-facing flows.
 */
public class DeveloperActivity extends AppCompatActivity {

    private DeveloperViewModel viewModel;

    // UI fields that show identity key material and benchmark results
    private TextView textIdentityKey;
    private TextView textResults;
    private Button buttonRunTest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);

        textIdentityKey = findViewById(R.id.textDevIdentityKey);
        textResults = findViewById(R.id.textDevResults);
        buttonRunTest = findViewById(R.id.buttonDevRunTest);

        // ViewModel handles Keystore access + benchmark execution
        viewModel = new ViewModelProvider(this).get(DeveloperViewModel.class);

        observeViewModel();
        setupClick();

        // Immediately fetch identity public key (stored in Android Keystore)
        viewModel.loadIdentityPublicKey();
    }

    /**
     * Observes public key, benchmark output, and errors emitted by the ViewModel.
     */
    private void observeViewModel() {
        viewModel.getIdentityPublicKey().observe(this, key -> {
            if (key != null) {
                // Display base64-encoded EC public key (secp256r1)
                textIdentityKey.setText(key);
            }
        });

        viewModel.getBenchmarkResult().observe(this, result -> {
            if (result != null) {
                // Showing raw benchmark output (AES-GCM, ECDH timings)
                textResults.setText(result);
            }
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                // Surface any crypto/Keystore errors
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Button triggers the full crypto pipeline benchmark via ViewModel.
     */
    private void setupClick() {
        buttonRunTest.setOnClickListener(v -> viewModel.runBenchmark());
    }
}
