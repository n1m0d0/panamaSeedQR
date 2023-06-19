package com.diggate.seedqr;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.Locale;

public class ViewQR extends AppCompatActivity {
    LinearLayout llBody;
    TextView tvName;
    ImageView ivQR;
    ImageView ivPhoto1;
    ImageView ivPhoto2;
    TextView tvSecond;
    ProgressBar pbTime;
    JSONObject jsonObject;
    int status = 0;
    String token;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_qr);

        llBody = findViewById(R.id.llBody);
        tvName = findViewById(R.id.tvName);

        ivQR = findViewById(R.id.ivQR);
        tvSecond = findViewById(R.id.tvSecond);
        pbTime = findViewById(R.id.pbTime);

        ivPhoto1 = findViewById(R.id.ivPhoto1);
        ivPhoto2 = findViewById(R.id.ivPhoto2);

        try {
            bd connection = new bd(this);
            connection.open();
            Cursor cursor = connection.searchSessionActive();
            cursor.moveToFirst();
            token = cursor.getString(1);
            jsonObject = new JSONObject(cursor.getString(2));
            connection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        /*try {
            jsonObject.put("CODE", 0);
            jsonObject.put("DESCRIPTION", "Código valido");
            JSONObject jsonObject2 = new JSONObject();
            JSONArray jsonArray = new JSONArray();

            for (int i = 0; i < 5; i++)
            {
                JSONObject aux = new JSONObject();
                aux.put("IDFIELD", i);
                aux.put("LABEL", "Label" + i);
                aux.put("VALUE", "value" + i);
                jsonArray.put(aux);
            }

            jsonObject2.put("SUBTITLE", "GASTON");
            jsonObject2.put("FIELDS", jsonArray);
            jsonObject.put("CREDENTIAL", jsonObject2);

            Log.w("JSON", jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }*/

        try {
            JSONObject credential = jsonObject.getJSONObject("CREDENTIAL");
            tvName.setText(credential.getString("SUBTITLE"));
            JSONArray fields = credential.getJSONArray("FIELDS");
            for (int i = 0; i < fields.length(); i++) {
                JSONObject data = fields.getJSONObject(i);
                createTitle(data.getString("LABEL"));
                createText(data.getString("VALUE"));
            }
            setImageView(ivPhoto1, credential.getString("PHOTO1"));
            setImageView(ivPhoto2, credential.getString("PHOTO2"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        String textQR = token + "|" + encryptSeed(token + getDate());
        generateQR(textQR);
        startTimer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.session, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.logout:

                try {
                    closedSession();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Intent go = new Intent(this, MainActivity.class);
                startActivity(go);
                finish();

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void closedSession() throws Exception {
        bd connection = new bd(this);
        connection.open();
        Cursor cursor = connection.searchSessionActive();
        if (cursor.moveToFirst() != false) {
            int id = cursor.getInt(0);
            String token = cursor.getString(1);
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

    private void generateQR(String seed) {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = multiFormatWriter.encode(seed, BarcodeFormat.QR_CODE, 500, 500);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            ivQR.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    public String getDateTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "ss", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    public String getDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyyMMddHHmm", Locale.getDefault());
        Date date = new Date();
        return dateFormat.format(date);
    }

    private Runnable renew = new Runnable() {
        @Override
        public void run() {
            Log.w("ss", "" + getDateTime());
            int value1 = 60;
            int value2 = Integer.parseInt(getDateTime());
            int value3 = value1 - value2;
            pbTime.setProgress(value3);
            tvSecond.setText("" + value3);
            if(value3 == 60) {
                Log.w("date", "" + getDate());
                String textQR = token + "|" + encryptSeed(token + getDate());
                Log.w("txtQR", textQR);
                generateQR(textQR);
            }
            if (value3 <= 10)
            {
                tvSecond.setTextColor(Color.RED);
            } else {
                tvSecond.setTextColor(getResources().getColor(R.color.colorPrimary));
            }
            startTimer();
        }
    };

    public void startTimer() {
        handler.postDelayed(renew, 1000);
    }

    private static String encryptSeed(String seed) {
        String sha256 = "";
        try {
            MessageDigest crypt = MessageDigest.getInstance("SHA-256");
            crypt.reset();
            crypt.update(seed.getBytes("UTF-8"));
            sha256 = byteToHex(crypt.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return sha256;
    }

    private static String byteToHex(final byte[] hash) {
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    public boolean connectionTest() {
        boolean connected = false;
        ConnectivityManager connection = (ConnectivityManager) ViewQR.this
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networks = connection.getAllNetworkInfo();
        for (int i = 0; i < networks.length; i++) {
            if (networks[i].getState() == NetworkInfo.State.CONNECTED) {
                connected = true;
            }
        }
        return connected;
    }

    public void createTitle(String texto) {
        TextView tv;
        tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(20);
        LinearLayout.LayoutParams lastTxtParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lastTxtParams.setMargins(0, 10, 0, 0);
        tv.setLayoutParams(lastTxtParams);
        tv.setTextColor(getResources().getColor(R.color.colorTextTitle));
        llBody.addView(tv);
    }

    public void createText(String texto) {
        TextView tv;
        tv = new TextView(this);
        tv.setText(texto);
        tv.setTextSize(14);
        LinearLayout.LayoutParams lastTxtParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lastTxtParams.setMargins(0, 0, 0, 0);
        tv.setLayoutParams(lastTxtParams);
        tv.setTextColor(getResources().getColor(R.color.colorPrimary));
        llBody.addView(tv);
    }

    public void setImageView(ImageView imageView, String photo)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] imageBytes = baos.toByteArray();
        imageBytes = Base64.decode(photo, Base64.DEFAULT);
        Bitmap decodedImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        imageView.setImageBitmap(decodedImage);
    }
}