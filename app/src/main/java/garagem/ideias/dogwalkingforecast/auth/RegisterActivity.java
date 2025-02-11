package garagem.ideias.dogwalkingforecast.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;
import garagem.ideias.dogwalkingforecast.MainActivity;
import garagem.ideias.dogwalkingforecast.R;
import java.util.HashMap;
import java.util.Map;
import android.widget.ProgressBar;

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        progressBar = findViewById(R.id.progressBar);
        MaterialButton registerButton = findViewById(R.id.registerButton);

        registerButton.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validate inputs
        if (name.isEmpty()) {
            nameInput.setError("Name is required");
            nameInput.requestFocus();
            return;
        }

        if (email.isEmpty()) {
            emailInput.setError("Email is required");
            emailInput.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Please provide a valid email");
            emailInput.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordInput.setError("Password is required");
            passwordInput.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordInput.setError("Password should be at least 6 characters");
            passwordInput.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordInput.setError("Passwords do not match");
            confirmPasswordInput.requestFocus();
            return;
        }

        // Show progress
        progressBar.setVisibility(View.VISIBLE);

        // Create user
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) {
                        // Create user info
                        Map<String, Object> userInfo = new HashMap<>();
                        userInfo.put("name", name);
                        userInfo.put("email", email);
                        userInfo.put("createdAt", Timestamp.now());
                        userInfo.put("uid", user.getUid());  // Store user ID

                        Log.d("RegisterActivity", "Creating user with ID: " + user.getUid());

                        // Save to Firestore
                        FirebaseFirestore.getInstance()  // Get new instance
                            .collection("users")
                            .document(user.getUid())
                            .set(userInfo)
                            .addOnSuccessListener(aVoid -> {
                                Log.d("RegisterActivity", "User info saved successfully for ID: " + user.getUid());
                                
                                // Verify the user is still authenticated
                                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(RegisterActivity.this, 
                                        "Account created successfully!", 
                                        Toast.LENGTH_SHORT).show();

                                    // Go to MainActivity
                                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Log.e("RegisterActivity", "User not authenticated after save");
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(RegisterActivity.this,
                                        "Authentication error. Please try logging in.",
                                        Toast.LENGTH_LONG).show();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e("RegisterActivity", "Error saving user info", e);
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(RegisterActivity.this,
                                    "Error saving user info: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            });
                    }
                } else {
                    Log.e("RegisterActivity", "Registration failed", task.getException());
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this,
                        "Registration failed: " + task.getException().getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            });
    }
} 