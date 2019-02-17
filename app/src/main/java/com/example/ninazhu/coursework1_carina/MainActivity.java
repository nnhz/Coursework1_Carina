package com.example.ninazhu.coursework1_carina;

import android.media.RingtoneManager;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.NotificationCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.app.NotificationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;


import com.sackcentury.shinebuttonlib.ShineButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    // Declare UI elements
    protected Button buttonSleep;
    protected Button buttonReconnect;
    protected Button buttonCalibrate;
    protected ShineButton buttonReturn;
    protected MqttAndroidClient client;
    protected String state;
    protected String distance;
    protected String dimension;
    protected TextView sleepInfo;
    protected TextView cribDimension;
    protected TextView calibrateInfo;
    protected ImageView cribImage;
    protected ConstraintLayout homepage;
    protected ConstraintLayout brokenPage;
    protected ConstraintLayout loadingPage;
    protected ConstraintLayout calibratePage;

    Handler handler;
    Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonSleep = findViewById(R.id.sleep);
        buttonReconnect = findViewById(R.id.reconnect);
        buttonCalibrate = findViewById(R.id.calibrate);
        buttonReturn = findViewById(R.id.calibrate_return);
        sleepInfo = findViewById(R.id.sleep_info);
        homepage = findViewById(R.id.homepage);
        brokenPage = findViewById(R.id.connection_broken);
        loadingPage = findViewById(R.id.page_loading);
        calibratePage = findViewById(R.id.calibrate_page);
        calibrateInfo = findViewById(R.id.calibrate_text);
        cribDimension = findViewById(R.id.crib_dimension);
        cribImage = findViewById(R.id.crib_image);

        distance = "0";
        state = "monitorOff";


        buttonSleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (client.isConnected()) {
                        Subscribe("IC.embedded/Carina/mode");
                        Subscribe("IC.embedded/Carina/sleep");
                        Subscribe("IC.embedded/Carina/calibrateResult");
                        Publish();
                    }
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });

        buttonCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (client.isConnected()) {
                        Subscribe("IC.embedded/Carina/mode");
                        Subscribe("IC.embedded/Carina/calibrateResult");
                        state = "calibrate";
                        PublishC();
                    }
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });

        buttonReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (client.isConnected()) {
                        Subscribe("IC.embedded/Carina/mode");
                        Publish();
                    }
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });

        buttonReconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new ConnectTask().execute("");
            }
        });

        new ConnectTask().execute("");

    }



    public void MQTTConnect(){
        String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(this.getApplicationContext(), "tcp://ee-estott-octo.ee.ic.ac.uk:1883",
                clientId);

        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    // Log.d("Successful MQTT Connection", "onSuccess");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    // Log.e("Failed MQTT Connection", "onFailure: " + exception);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void Subscribe(String topic){
        try {
            if (client.isConnected()) {
                client.subscribe(topic, 1);
                client.setCallback(new MqttCallback() {

                    @Override
                    public void connectionLost(Throwable cause) {
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        System.out.println(topic+": "+ message.toString());

                        // depending on the topic, do different actions

                        if (topic.equals("IC.embedded/Carina/calibrateResult")) {
                            dimension = message.toString();
                        }

                        if (topic.equals("IC.embedded/Carina/mode")){

                            state = message.toString();
                            Log.v("state", state);
                            if (state.equals("monitorOn")){
                                calibratePage.setVisibility(View.GONE);
                                homepage.setVisibility(View.VISIBLE);
                                homepage.setBackgroundColor(getResources().getColor(R.color.homePageSleepColor));
                                sleepInfo.setText("Baby is sleeping");
                                buttonSleep.setText("Baby is awake");

                            } else if (state.equals("monitorOff")) {
                                calibratePage.setVisibility(View.GONE);
                                homepage.setVisibility(View.VISIBLE);
                                homepage.setBackgroundColor(getResources().getColor(R.color.homePageColor));
                                sleepInfo.setText("Baby is awake");
                                buttonSleep.setText("Baby is sleeping");

                            } else if (state.equals("calibrate")) {
                                homepage.setVisibility(View.GONE);
                                calibratePage.setVisibility(View.VISIBLE);
                                calibrateInfo.setText("Calibrating...");
                                cribImage.setVisibility(View.INVISIBLE);
                                cribDimension.setVisibility(View.INVISIBLE);
                                buttonReturn.setVisibility(View.INVISIBLE);

                            } else { // in state "calibrateDone"
                                homepage.setVisibility(View.GONE);
                                calibratePage.setVisibility(View.VISIBLE);
                                calibrateInfo.setText("Calibration Done!");
                                cribImage.setVisibility(View.VISIBLE);
                                cribDimension.setVisibility(View.VISIBLE);
                                buttonReturn.setVisibility(View.VISIBLE);
                                cribDimension.setText(dimension);

                            }


                        }
                        if (topic.equals("IC.embedded/Carina/sleep")) {
                            distance = message.toString();
                            Log.v("baby moved", distance);
                            sendNotification();  // baby moved (distance changed), now send notification
                        }


                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {

                    }
                });

            }
        } catch (Exception e) {
            Log.d("tag","Error :" + e);
        }
    }

    public void Publish() throws MqttException {
        // changes state between monitorOn and monitorOff
        String topic = "IC.embedded/Carina/mode";
        String payload;
        if (state.equals("monitorOff")){ //state == "0"
            payload = "monitorOn";
        } else { //state == "monitorOn" or "calibrateDone"
            payload = "monitorOff";
        }
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(topic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }

    public void PublishC() throws MqttException {
        // to start calibration
        String topic = "IC.embedded/Carina/mode";
        String payload = "calibrate";

        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = payload.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            client.publish(topic, message);
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }


    public class ConnectTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            MQTTConnect();
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            loadingPage.setVisibility(View.GONE);
            homepage.setVisibility(View.VISIBLE);

            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    if (!client.isConnected()){
                        brokenPage.setVisibility(View.VISIBLE);
                        homepage.setVisibility(View.GONE);
                    } else {
                        brokenPage.setVisibility(View.GONE);
                        homepage.setVisibility(View.VISIBLE);
                    }
                    handler.postDelayed(this, 1000);
                }
            };
            //Start
            handler.postDelayed(runnable, 1000);

        }

        @Override
        protected void onPreExecute() {
            loadingPage.setVisibility(View.VISIBLE);
            homepage.setVisibility(View.GONE);
        }

        @Override
        protected void onProgressUpdate(Void... values) {

        }
    }

    public void sendNotification() {
        // send notification (with sound and vibration)
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(MainActivity.this)
                        .setSmallIcon(R.drawable.moon)
                        .setContentTitle("Baby awake notification")
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setContentText("Your baby is awake");

        NotificationManager mNotificationManager =

                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(001, mBuilder.build());

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            //deprecated in API 26
            v.vibrate(1000);
        }
    }

}
