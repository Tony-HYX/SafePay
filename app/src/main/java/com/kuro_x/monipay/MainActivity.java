package com.kuro_x.monipay;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import top.wuhaojie.bthelper.BtHelperClient;
import top.wuhaojie.bthelper.MessageItem;
import top.wuhaojie.bthelper.OnSearchDeviceListener;
import top.wuhaojie.bthelper.OnSendMessageListener;

public class MainActivity extends AppCompatActivity {

    // UI references.
    private Button mLoginButton;
    private Button mCharge;
    private Button mTrans;

    private TextView mWelcome;
    private TextView mMoney;
    private TextView mStatus;
    private EditText mUser;
    private EditText mValue;
    private String moneny;
    private MoneyTask mTask = null;

    static public BluetoothDevice device;
    public String devicemac;

    private BtHelperClient btHelperClient;

    private String id = null;
    private String otp = "null";
    private String errorcode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLoginButton = findViewById(R.id.loginButton);
        mWelcome = findViewById(R.id.welcome);
        mMoney = findViewById(R.id.money);
        mCharge = findViewById(R.id.charge);
        mUser = findViewById(R.id.user2);
        mValue = findViewById(R.id.value);
        mTrans = findViewById(R.id.trans);
        mStatus = findViewById(R.id.status);


        btHelperClient = BtHelperClient.from(MainActivity.this);

        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        String name = pref.getString("username", "null");//第二个参数为默认值
        String hardwareID = pref.getString("hardwareID", "null");//第二个参数为默认值
        devicemac = pref.getString("device", null);//第二个参数为默认值
        if (!Objects.equals(name, "null")) {
            mWelcome.setText("Welcome: " + name);
            if (Objects.equals(hardwareID, "null"))
                mStatus.setText("未绑定");
            else
                mStatus.setText("已绑定");
            updateMoney();
        } else {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        }

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("username", "null");
                editor.putString("password", "null");
                editor.apply();
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        mCharge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                charge(10);
            }
        });
        mTrans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Objects.equals(mValue.getText().toString(), "")) {
                    dialogShow("金额不能为空！");
                } else if (Objects.equals(mUser.getText().toString(), "")) {
                    dialogShow("对方用户不能为空！");
                } else
                    Transfer(mUser.getText().toString(), Double.parseDouble(mValue.getText().toString()));
            }
        });
    }



    public void sendDevice(final String msg) {

        BluetoothSocket socket = null;
        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            Log.i("Connection", "begintoconnect");
            socket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and get out
            Log.i("Connection", connectException.toString());
            try {
                Log.i("Connection", "Trying fallback...");
                socket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 1);
                socket.connect();
                Log.i("Connection", "Connected");
            } catch (Exception e2) {
                Log.e("Connection", "Couldn't establish Bluetooth connection!");

                dialogShow("无法连接手环");
                try {
                    socket.close();
                    return;
                } catch (IOException e3) {
                    Log.e("Connection", "unable to close() " + " socket during connection failure", e3);
                }
            }
        }
        sendDataToServer(socket, msg);
        String response;
        response = readDataFromServer(socket);
        if (Objects.equals(msg, "1"))
            id = response;
        else if (Objects.equals(msg, "2"))
            otp = response;

    }

    static public String readDataFromServer(BluetoothSocket socket) {
        byte[] buffer = new byte[64];
        String s = null;
        try {
            InputStream is = socket.getInputStream();

            int cnt = is.read(buffer);
            is.close();

            s = new String(buffer, 0, cnt);
            Log.d("Recv", "收到服务端发来数据:" + s);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s;
    }

    static public void sendDataToServer(BluetoothSocket socket, String msg) {

        byte[] buffer = msg.getBytes();

        try {
            OutputStream os = socket.getOutputStream();

            os.write(buffer);
            os.flush();

            // os.close();
            // socket.close();

            Log.d("Send", "服务器端数据发送完毕!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private boolean charge(double i) {
        String OTP = getOTP();
        if (Objects.equals(otp, "null"))
            return false;
        mTask = new MoneyTask(8, i + "\n" + OTP + "\n");
        mTask.execute((Void) null);
        updateMoney();
        return true;
    }

    private String getOTP() {
        BluetoothAdapter blueToothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bondedDevices = blueToothAdapter.getBondedDevices();
        if (!blueToothAdapter.isEnabled()) {
            dialogShow("蓝牙未打开");
            return "null";
        }


        blueToothAdapter.cancelDiscovery();
        for (BluetoothDevice t :
                bondedDevices) {
            if (Objects.equals(t.getName(), "raspberrypi3")) {
                device = t;
            }
        }
        if (device == null) {
            dialogShow("未找到手环");
            return "null";
        } else {
            sendDevice("2");
        }
        return otp;
    }

    private boolean Transfer(String user, double i) {
        String OTP = getOTP();
        if (Objects.equals(otp, "null"))
            return false;
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        String hardwareID = pref.getString("hardwareID", "null");//第二个参数为默认值
        if (Objects.equals(hardwareID, "null"))
            mStatus.setText("未绑定");
        else
            mStatus.setText("已绑定");

        mTask = new MoneyTask(5, user + "\n" + i + "\n" + OTP + "\n");
        mTask.execute((Void) null);
        updateMoney();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
        String name = pref.getString("username", "null");//第二个参数为默认值
        if (!Objects.equals(name, "null")) {
            mWelcome.setText("Welcome: " + name);
            updateMoney();
        } else {
            Log.i("debug", "resume fail");

        }
    }

    private void updateMoney() {
        mTask = new MoneyTask(6, "");
        mTask.execute((Void) null);
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    private class MoneyTask extends AsyncTask<Void, Void, Boolean> {

        private final String mUsername;
        private final String mPassword;
        private String option;
        private final int mode;
        BufferedReader br = null;

        MoneyTask(int mode, String option) {
            SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
            mUsername = pref.getString("username", "null");
            mPassword = pref.getString("password", "null");
            this.mode = mode;
            this.option = option;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // TODO: attempt authentication against a network service.

            try {
                String content;

                Socket socket = new Socket("120.24.233.41", 8080);
                DataOutputStream writer = new DataOutputStream(socket.getOutputStream());
                writer.writeBytes(mode + "\n" + mUsername + "\n" + mPassword + "\n" + option + "\n");
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                if ((content = br.readLine()) != null) {
                    if (!Objects.equals(content, "Success")) {
                        content = br.readLine();
                        errorcode = content;


                        Log.i("Money", "fail:" + content);
                        return false;
                    }
                }
                if (mode == 6) {
                    moneny = br.readLine();
                }
                Log.i("Money", "success 6:" + moneny);

            } catch (IOException e) {
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mTask = null;

            if (success) {
                mMoney.setText(moneny);
            } else {
                if (Objects.equals(errorcode, "1"))
                    dialogShow("对方用户名不存在");
                if (Objects.equals(errorcode, "2"))
                    dialogShow("余额不足");
                if (Objects.equals(errorcode, "3"))
                    dialogShow("OTP值错误");
                errorcode = null;
            }
        }

    }

    public void dialogShow(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("MoniPay");
        builder.setMessage(msg);
        builder.setPositiveButton("OK", null);
        builder.show();
    }
}
