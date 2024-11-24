package com.example.hellomqtt;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private String host = "tcp://192.168.4.198:1883";
    private String passWord = "android_client";
    private String userName = "android_client";
    private String mqtt_id = "android_client_485cf136";
    private String mqtt_sub_topic = "app_test_topic"; // 订阅 app_sub_topic
    private String mqtt_pub_topic = "app_test_topic"; // 发布 app_pub_topic

    private ScheduledExecutorService scheduler;
    private MqttClient client;
    private MqttConnectOptions options;
    private Handler handler;

    private static final String TAG = "MainActivity";
    private Button btn_clear;
    private ImageView led;
    private int led_flag = 0;
    private ImageView fan;
    private int fan_flag;
    private TextView console;
    private int count = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindLed();
        bindFan();
        bindConsole();
        bindMqtt();
    }


    public void bindLed() {
        led = findViewById(R.id.button);
        led.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (led_flag == 0) {
                    led_flag = 1;
                } else {
                    led_flag = 0;
                }
                String msg = "{\"set_led\": " + led_flag + "}";
                publishMessage(mqtt_pub_topic, msg);
                addLog("发送消息: " + msg);
            }
        });
    }

    public void bindFan() {
        fan = findViewById(R.id.fan);
        fan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fan_flag == 0) {
                    fan_flag = 1;
                } else {
                    fan_flag = 0;
                }
                String msg = "{\"set_fan\": " + fan_flag + "}";
                publishMessage(mqtt_pub_topic, msg);
                addLog("发送消息: " + msg);
            }
        });
    }

    public void bindConsole() {

        btn_clear = findViewById(R.id.btn_clear);
        btn_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                console.setText("");
            }
        });
        console = findViewById(R.id.console);
        console.setText("Wait...");
    }

    public void addLog(String msg) {
        CharSequence text = console.getText();
        console.setText(text + "\n" + msg);
    }

    @SuppressLint("HandlerLeak")
    private void bindMqtt() {
        initMQTT();
        startMQTTReconnect();
        handler = new Handler() {
            @SuppressLint("SetTextI18n")
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 1: // 开机校验
                        addLog("开机校验");
                        break;
                    case 2:  // 反馈回传
                        addLog("反馈回传");
                        break;
                    case 3:  // 收到消息回传
                        addLog("收到消息: " + msg.obj.toString());
                        break;
                    case 30:  // 连接失败
                        addLog("连接失败");
                        break;
                    case 31:   // 连接成功
                        addLog("连接成功");
                        try {
                            client.subscribe(mqtt_sub_topic, 1);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void initMQTT() {
        try {
            // host 为主机名，mqtt_id 为连接 MQTT 的客户端ID，一般以客户端唯一标识符表示，MemoryPersistence 设置 client_id 的保存形式，默认为以内存保存
            client = new MqttClient(host, mqtt_id, new MemoryPersistence());
            //MQTT的连接设置
            options = new MqttConnectOptions();
            //设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(false);
            //设置连接的用户名
            options.setUserName(userName);
            //设置连接的密码
            options.setPassword(passWord.toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(10);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(20);
            //设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    //连接丢失后，一般在这里面进行重连
                    Log.i(TAG, "connectionLost: Lost");
                    // startReconnect();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // publish 后会执行到这里
                    Log.i(TAG, "deliveryComplete: " + token.isComplete());
                }

                @Override
                public void messageArrived(String topicName, MqttMessage message)
                        throws Exception {
                    Log.i(TAG, "messageArrived: ");
                    Message msg = new Message();
                    msg.what = 3;  // 消息标志位
                    msg.obj = topicName + "---" + message.toString();
                    handler.sendMessage(msg);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initMQTTConnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!(client.isConnected()))  //如果还未连接
                    {
                        client.connect(options);
                        Message msg = new Message();
                        msg.what = 31;
                        handler.sendMessage(msg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.what = 30;
                    handler.sendMessage(msg);
                }
            }
        }).start();
    }

    private void startMQTTReconnect() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!client.isConnected()) {
                    initMQTTConnect();
                }
            }
        }, 0 * 1000, 10 * 1000, TimeUnit.MILLISECONDS);
    }

    private void publishMessage(String topic, String message) {
        if (client == null || !client.isConnected()) {
            return;
        }
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setPayload(message.getBytes());
        try {
            client.publish(topic, mqttMessage);
        } catch (MqttException e) {

            e.printStackTrace();
        }
    }

}
