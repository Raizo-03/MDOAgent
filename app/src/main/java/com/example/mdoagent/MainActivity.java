package com.example.mdoagent;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.InputStream;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URL;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    EditText inputText;
    Button sendButton;
    TextView chatOutput;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        inputText = findViewById(R.id.inputText);
        sendButton = findViewById(R.id.sendButton);
        chatOutput = findViewById(R.id.chatOutput);

        sendButton.setOnClickListener(v -> {
            String userMessage = inputText.getText().toString();
            if (!userMessage.isEmpty()) {
                chatOutput.append("You: " + userMessage + "\n");
                sendToDialogflow(userMessage);
            }
        });
    }

    private void sendToDialogflow(String message) {
        // Show loading indicator
        chatOutput.append("Bot: Typing...\n");

        executor.execute(() -> {
            try {
                // Load credentials
                InputStream stream = getResources().openRawResource(R.raw.mdoagent);
                GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"));
                AccessToken token = credentials.refreshAccessToken();

                // Build JSON request
                JSONObject body = new JSONObject();
                body.put("queryInput", new JSONObject()
                        .put("text", new JSONObject()
                                .put("text", message)
                                .put("languageCode", "en")));

                // Build full URL
                String projectId = "mdoagent-yrqi";
                URL url = new URL("https://dialogflow.googleapis.com/v2/projects/" + projectId + "/agent/sessions/123456789:detectIntent");

                // Build request
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer " + token.getTokenValue())
                        .post(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .build();

                Response response = client.newCall(request).execute();
                final String result;

                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JSONObject jsonResponse = new JSONObject(json);

                    // Log the full response for debugging
                    System.out.println("API Response: " + json);

                    // Parse JSON correctly based on actual response structure
                    JSONObject queryResult = jsonResponse.getJSONObject("queryResult");

                    // Handle the fulfillmentText first (simpler approach)
                    if (queryResult.has("fulfillmentText") && !queryResult.isNull("fulfillmentText")) {
                        result = queryResult.getString("fulfillmentText");
                    }
                    // If no fulfillmentText, try to parse fulfillmentMessages
                    else if (queryResult.has("fulfillmentMessages")) {
                        JSONArray messagesArray = queryResult.getJSONArray("fulfillmentMessages");
                        if (messagesArray.length() > 0) {
                            JSONObject firstMessage = messagesArray.getJSONObject(0);

                            // Check if it has a "text" field that's an object
                            if (firstMessage.has("text")) {
                                Object textField = firstMessage.get("text");

                                if (textField instanceof JSONObject) {
                                    // If text is a JSONObject with a "text" array
                                    JSONObject textObj = (JSONObject) textField;
                                    if (textObj.has("text")) {
                                        JSONArray textArray = textObj.getJSONArray("text");
                                        result = textArray.getString(0);
                                    } else {
                                        result = "Error: Could not find text array in response";
                                    }
                                }
                                else if (textField instanceof JSONArray) {
                                    // If text is directly a JSONArray
                                    JSONArray textArray = (JSONArray) textField;
                                    result = textArray.getString(0);
                                }
                                else {
                                    result = "Error: Unexpected format for text field";
                                }
                            } else {
                                result = "Error: No text field found in response";
                            }
                        } else {
                            result = "Error: Empty fulfillmentMessages array";
                        }
                    } else {
                        result = "Error: No fulfillmentText or fulfillmentMessages in response";
                    }
                } else {
                    result = "Error: " + (response.body() != null ? response.body().string() : response.message());
                }

                // Update UI on main thread
                handler.post(() -> {
                    // Remove typing indicator and show actual response
                    String currentText = chatOutput.getText().toString();
                    currentText = currentText.replace("Bot: Typing...\n", "");
                    chatOutput.setText(currentText);
                    chatOutput.append("Bot: " + result + "\n");
                    inputText.setText(""); // Clear the input field
                });

            } catch (Exception e) {
                e.printStackTrace();
                final String errorMessage = "Exception: " + e.getMessage();

                // Update UI on main thread
                handler.post(() -> {
                    // Remove typing indicator and show error
                    String currentText = chatOutput.getText().toString();
                    currentText = currentText.replace("Bot: Typing...\n", "");
                    chatOutput.setText(currentText);
                    chatOutput.append("Bot: " + errorMessage + "\n");
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor service when activity is destroyed
        executor.shutdown();
    }
}