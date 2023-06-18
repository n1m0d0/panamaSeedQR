package com.diggate.seedqr;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    EditText etSeed;
    Button btnStart;
    ImageView ivVisible;
    int status = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        etSeed = findViewById(R.id.etSeed);
        btnStart = findViewById(R.id.btnStart);
        ivVisible = findViewById(R.id.ivVisible);

        if (!connectionTest()) {
            Toast.makeText(MainActivity.this, "Debe contar con connection a internet", Toast.LENGTH_LONG).show();
        }

        try {
            if (!checkSession().equals("")) {
                JSONObject sendData = new JSONObject();
                sendData.put("seed", checkSession());
                Log.w("json", "" + sendData);
                verifySeed("validateSeed", sendData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!connectionTest()) {
                    Toast.makeText(MainActivity.this, "Debe contar con connection a internet", Toast.LENGTH_LONG).show();
                } else {
                    if (etSeed.getText().toString().trim().equals("")) {
                        Toast.makeText(MainActivity.this, "Debe Ingresar la Semilla", Toast.LENGTH_SHORT).show();
                    } else {
                        try {
                            JSONObject sendData = new JSONObject();
                            sendData.put("seed", etSeed.getText().toString().trim());
                            Log.w("json", "" + sendData);
                            startSeed("authSeed", sendData);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

        ivVisible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(status == 0) {
                    etSeed.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                    status = 1;
                    ivVisible.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_off_24));
                } else {
                    etSeed.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    status = 0;
                    ivVisible.setImageDrawable(getDrawable(R.drawable.ic_baseline_visibility_24));
                }

            }
        });
    }

    public boolean connectionTest() {
        boolean connected = false;
        ConnectivityManager connection = (ConnectivityManager) MainActivity.this
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networks = connection.getAllNetworkInfo();
        for (int i = 0; i < networks.length; i++) {
            if (networks[i].getState() == NetworkInfo.State.CONNECTED) {
                connected = true;
            }
        }
        return connected;
    }

    public String checkSession() throws Exception {
        String token = "";
        bd connection = new bd(this);
        connection.open();
        Cursor cursor = connection.searchSessionActive();
        if (cursor.moveToFirst() != false) {
            token = cursor.getString(1);
            Log.w("token", cursor.getString(1));
        }
        connection.close();
        return token;
    }

    public void session(String token, String json_data) throws Exception {
        bd connection = new bd(this);
        connection.open();
        Cursor cursor = connection.searchSessionActive();
        if (cursor.moveToFirst() != false) {
            int id = cursor.getInt(0);
            connection.updateSession(id, token, getTime());
        }
        connection.createSession(token, json_data, getTime());
        connection.close();
    }

    public void closedSession(String token) throws Exception {
        bd connection = new bd(this);
        connection.open();
        Cursor cursor = connection.searchSessionActive();
        if (cursor.moveToFirst() != false) {
            int id = cursor.getInt(0);
            connection.updateSession(id, token, getTime());
        }
        connection.close();
    }

    public String getTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    private void verifySeed(String direction, JSONObject data) {

        String url = getString(R.string.url) + "/" + direction;

        ProgressDialog progressDialog;
        progressDialog = new ProgressDialog(this);
        //progressDialog.setMax(100);
        progressDialog.setMessage("Cargando...");
        progressDialog.setTitle("Revisando la Informacion");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();
        progressDialog.setCancelable(false);

        RequestQueue requestQueue;
        requestQueue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest;

        jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, data, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                progressDialog.dismiss();
                Log.w("response", "" + response);
                try {
                    if (response.getInt("CODE") == 0) {
                        Toast.makeText(MainActivity.this, response.getString("DESCRIPTION"), Toast.LENGTH_LONG).show();
                        Intent ir = new Intent(MainActivity.this, ViewQR.class);
                        startActivity(ir);
                        finish();
                    } else {
                        closedSession(etSeed.getText().toString().trim());
                        etSeed.setText("");
                        Toast.makeText(MainActivity.this, response.getString("DESCRIPTION"), Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w("error", "" + error);
                progressDialog.dismiss();
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/json");
                //params.put("Authorization", "Bearer " + Utils.readSharedSetting(context, "access_token", ""));
                return params;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }

    private void startSeed(String direction, JSONObject data) {

        String url = getString(R.string.url) + "/" + direction;

        ProgressDialog progressDialog;
        progressDialog = new ProgressDialog(this);
        //progressDialog.setMax(100);
        progressDialog.setMessage("Cargando...");
        progressDialog.setTitle("Revisando la Informacion");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();
        progressDialog.setCancelable(false);

        RequestQueue requestQueue;
        requestQueue = Volley.newRequestQueue(this);
        JsonObjectRequest jsonObjectRequest;

        jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, data, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                progressDialog.dismiss();
                Log.w("response", "" + response);
                try {
                    if (response.getInt("CODE") == 0) {
                        session(etSeed.getText().toString().trim(), response.toString());
                        Toast.makeText(MainActivity.this, response.getString("DESCRIPTION"), Toast.LENGTH_LONG).show();
                        Intent ir = new Intent(MainActivity.this, ViewQR.class);
                        startActivity(ir);
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, response.getString("DESCRIPTION"), Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.w("error", "" + error);
                progressDialog.dismiss();
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Content-Type", "application/json");
                //params.put("Authorization", "Bearer " + Utils.readSharedSetting(context, "access_token", ""));
                return params;
            }
        };

        requestQueue.add(jsonObjectRequest);
    }
}