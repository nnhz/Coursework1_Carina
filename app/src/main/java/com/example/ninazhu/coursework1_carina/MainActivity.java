package com.example.ninazhu.coursework1_carina;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

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
    protected Button buttonSub;
    protected Button buttonPub;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // MQTT Subscribe stuff (may write separately layer)
        buttonSub = (Button) findViewById(R.id.subscribe_MQTT);


        String clientId = MqttClient.generateClientId();
        final MqttAndroidClient client =   //Changed to final from template
                new MqttAndroidClient(this.getApplicationContext(), "tcp://broker.hivemq.com:1883",
                        clientId);

        try {
            IMqttToken token = client.connect();
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                //    Log.d(TAG, "onSuccess");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                //    Log.d(TAG, "onFailure");

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }

        buttonSub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String topic = "foo/bar";
                int qos = 1;
                try {
                    IMqttToken subToken = client.subscribe(topic, qos);
                    subToken.setActionCallback(new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            // The message was published
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken,
                                              Throwable exception) {
                            // The subscription could not be performed, maybe the user was not
                            // authorized to subscribe on the specified topic e.g. using wildcards

                        }
                    });
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //Toast.makeText(MainActivity.this, new String(message.getPayload())), Toast.LENGTH_SHORT.show(); //added from video
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });



        // MQTT Publish stuff (may need to write as separate event)
        buttonPub = (Button) findViewById(R.id.publish_MQTT);

        buttonPub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String topic = "foo/bar";
                String payload = "the payload";
                byte[] encodedPayload = new byte[0];
                try {
                    encodedPayload = payload.getBytes("UTF-8");
                    MqttMessage message = new MqttMessage(encodedPayload);
                    client.publish(topic, message);
                } catch (UnsupportedEncodingException | MqttException e) {
                    e.printStackTrace();
                }
            }
        });


    }
}
