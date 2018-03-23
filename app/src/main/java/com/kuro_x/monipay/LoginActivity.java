package com.kuro_x.monipay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;

import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import top.wuhaojie.bthelper.BtHelperClient;
import top.wuhaojie.bthelper.OnSearchDeviceListener;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    private String moneny;
    private MoneyTask mTask = null;
    private String Status;

    static public BluetoothDevice device;
    public String devicemac;

    private BtHelperClient btHelperClient;

    private String id = null;
    private String otp = "null";
    private String errorcode;
    // UI references.
    private TextView mUsername;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private Button mPair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mUsername = (TextView) findViewById(R.id.username);
        btHelperClient = BtHelperClient.from(LoginActivity.this);


        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mSignInButton.setOnClickListener(new OnClickListener() {

            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);


    }

    public void dialogShow(String msg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
        builder.setTitle("MoniPay");
        builder.setMessage(msg);
        builder.setPositiveButton("OK", null);
        builder.show();
    }


    private void getDevice(final String name) {

        btHelperClient.searchDevices(new OnSearchDeviceListener() {

            @Override
            public void onStartDiscovery() {
                // 在进行搜索前回调
//                dialogShow("正在搜索设备!请稍后");
                Toast.makeText(LoginActivity.this, "正在搜索设备!请稍后", Toast.LENGTH_SHORT).show();
                Log.i("Scan", "onStartDiscovery()");

            }

            @Override
            public void onNewDeviceFounded(BluetoothDevice device2) {
                // 当寻找到一个新设备时回调
                if (Objects.equals(device2.getName(), "raspberrypi3")) {
                    device = device2;
                    Toast.makeText(LoginActivity.this, "已找到设备！", Toast.LENGTH_SHORT).show();
                    SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString("device", devicemac);
                    editor.apply();
                }

                Log.i("Scan", "new device: " + device2.getName() + " " + device2.getAddress());


            }

            @Override
            public void onSearchCompleted(List<BluetoothDevice> bondedList, List<BluetoothDevice> newList) {
                // 当搜索蓝牙设备完成后回调

                Log.i("Scan", "SearchCompleted: bondedList" + bondedList.toString());
                Log.i("Scan", "SearchCompleted: newList" + newList.toString());

            }

            @Override
            public void onError(Exception e) {

                e.printStackTrace();

            }

        });

    }

    private void pairDevice() {
        BluetoothAdapter blueToothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bondedDevices = blueToothAdapter.getBondedDevices();

        if (!blueToothAdapter.isEnabled()) {
            dialogShow("蓝牙未打开");
            return;
        }
        blueToothAdapter.cancelDiscovery();

        for (BluetoothDevice t :
                bondedDevices) {
            if (Objects.equals(t.getName(), "raspberrypi3")) {
                device = t;
            }
        }
        if (device == null)
            getDevice("raspberrypi3");
        else {
            sendDevice("1");

//            SendThread thread=new SendThread(device);
//            thread.start();
        }

    }


    public int sendDevice(final String msg) {

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

//                dialogShow("无法连接手环");
                try {
                    socket.close();
                    return 2;
                } catch (IOException e3) {
                    Log.e("Connection", "unable to close() " + " socket during connection failure", e3);
                }
                return 1;

            }
        }
        MainActivity.sendDataToServer(socket, msg);
        String response;
        response = MainActivity.readDataFromServer(socket);
        if (Objects.equals(msg, "1"))
            id = response;
        else if (Objects.equals(msg, "2"))
            otp = response;
        return 0;
    }

    private int pair() {

        pairDevice();
        if (id != null) {
            mTask = new LoginActivity.MoneyTask(3, id + "\n");
            mTask.execute((Void) null);

            SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            editor.putString("hardwareID", id);
            editor.apply();

            Status = ("已绑定");
            return 0;
        } else {
            return 1;
//            dialogShow("未找到设备编号");
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mUsername.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String email = mUsername.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && !isPasswordValid(password)) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mUsername.setError(getString(R.string.error_field_required));
            focusView = mUsername;
            cancel = true;
        }
        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(email, password);
            mAuthTask.execute((Void) null);
        }

    }


    private boolean isPasswordValid(String password) {
        //TODO: Replace this with your own logic
        return password.length() > 4;
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }


    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mUsername;
        private final String mPassword;
        BufferedReader br = null;

        UserLoginTask(String username, String password) {
            mUsername = username;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            // TODO: attempt authentication against a network service.

            try {
                String content;
                Socket socket = new Socket("120.24.233.41", 8080);
                DataOutputStream writer = new DataOutputStream(socket.getOutputStream());
                writer.writeBytes("1\n" + mUsername + "\n" + mPassword + "\n");
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while ((content = br.readLine()) != null) {
                    if (!Objects.equals(content, "Success")) {
                        Log.i("login", "fail:" + content);
                        return false;
                    } else {
                        SharedPreferences pref = getSharedPreferences("data", MODE_PRIVATE);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("username", mUsername);
                        editor.putString("password", mPassword);
                        editor.apply();
                        content = br.readLine();
                        if (Objects.equals(content, "7")) {
                            if (!Objects.equals(Status, "已绑定")) {
                                Log.i("login", "pair:start" );
                                pair();
                            }
                            if (!Objects.equals(Status, "已绑定")) {
                                Log.i("login", "pair:fail" );
                                return false;
                            }
                        }
                        Status= "已绑定";
                    }
                }
                Log.i("login", "success");

            } catch (IOException e) {
                return false;
            }


            // TODO: register the new account here.


            Log.i("login", "complete");

            return true;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);

            if (success) {
                finish();
            } else {
                if (!Objects.equals(Status, "已绑定")) {
                    dialogShow("无法绑定手环,请重试");
                }
                else {
                    mPasswordView.setError(getString(R.string.error_incorrect_password));
                    mPasswordView.requestFocus();
                }
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

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
}

