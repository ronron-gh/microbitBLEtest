package com.example.bletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private MyScancallback scancallback;
    private BluetoothDevice device;
    private BluetoothGatt mGatt;
    private BluetoothGattCallback gattCallback;
    private BluetoothGattService mTempService, mAccelService, mMagnetService, mUartService;

    private final String UART_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private final String UART_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private final String UART_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    private final String TEMP_SERVICE = "e95d6100-251d-470a-a062-fa1922dfa9a8";
    private final String TEMP_DATA = "e95d9250-251d-470a-a062-fa1922dfa9a8";
    private final String TEMP_PERIOD = "e95d9250-251d-470a-a062-fa1922dfa9a8";

    private final String ACCEL_SERVICE = "e95d0753-251d-470a-a062-fa1922dfa9a8";
    private final String ACCEL_DATA = "e95dca4b-251d-470a-a062-fa1922dfa9a8";
    private final String ACCEL_PERIOD = "e95dfb24-251d-470a-a062-fa1922dfa9a8";

    private final String MAGNET_SERVICE = "e95df2d8-251d-470a-a062-fa1922dfa9a8";
    private final String MAGNET_DATA = "e95dfb11-251d-470a-a062-fa1922dfa9a8";
    private final String MAGNET_PERIOD = "e95d386c-251d-470a-a062-fa1922dfa9a8";

    private final String NOTIFY_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb";

    private boolean mScanned = false;

    private final int PERMISSION_REQUEST = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //BLE対応端末かどうかを調べる。対応していない場合はメッセージを出して終了
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }


        //Bluetoothアダプターを初期化する
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        //bluetoothの使用が許可されていない場合は許可を求める。
        if( adapter == null || !adapter.isEnabled() ){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,PERMISSION_REQUEST);
        }
        else{
            Button button = findViewById(R.id.button_connect);
            button.setText("CONNECT");
            button.setEnabled(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if( requestCode == PERMISSION_REQUEST ){
            Button button = findViewById(R.id.button_connect);
            button.setEnabled(true);
        }
    }

    private Handler handler;
    private final int SCAN_PERIOD = 10000;


    class MyScancallback extends ScanCallback {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("scanResult","start");
            if( mScanned == true ) return;
            if( result.getDevice() == null ) return;
            if( result.getDevice().getName() == null )return;
            if( result.getDevice().getName().contains("BBC micro:bit") ){
                //BLE端末情報の保持
                device = result.getDevice();
                mScanned = true;
                //UIスレッドでボタン名称変更
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Button button = findViewById(R.id.button_connect);
                        button.setText("GET SERVICE");
                        button.setEnabled(true);
                    }
                });
                //スキャン停止
                scanner.stopScan(scancallback);
                return;
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
        }

        @Override
        public void onScanFailed(int errorCode) {
        }
    }


    class MyGattcallback extends BluetoothGattCallback{
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("onConnect","change");
            if( newState == BluetoothProfile.STATE_CONNECTED){
                gatt.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mGatt = gatt;
            if( status == BluetoothGatt.GATT_SUCCESS ) {
                Log.d("onServicesDiscovered", "success");

                List<BluetoothGattService> list = gatt.getServices();
                for (BluetoothGattService service : list) {

                    Log.d("onServicesDiscovered", "UUID : " + service.getUuid().toString());

                    //UARTサービスの確保
                    if( service.getUuid().toString().equals(UART_SERVICE) ) {
                        Log.d("onServicesDiscovered","uart");
                        mUartService = service;

                        //Descriptorの記述 (Indicationを有効化)
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(UART_TX));
                        mGatt.setCharacteristicNotification(characteristic,true);
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                        mGatt.writeDescriptor(descriptor);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //送信ボタンを有効化
                                Button button = findViewById(R.id.button_uartSend);
                                button.setEnabled(true);
                            }
                        });
                    }

                    //温度計サービスの確保
                    if( service.getUuid().toString().equals(TEMP_SERVICE) ) {
                        Log.d("onServicesDiscovered","temperature");
                        mTempService = service;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //温度読み取りボタンを有効化
                                Button button = findViewById(R.id.button_read_temp);
                                button.setEnabled(true);
                            }
                        });
                    }

                    //加速度サービスの確保
                    if( service.getUuid().toString().equals(ACCEL_SERVICE) ) {
                        Log.d("onServicesDiscovered","accel");
                        mAccelService = service;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //加速度読み取りボタンを有効化
                                Button button = findViewById(R.id.button_read_accel);
                                button.setEnabled(true);
                            }
                        });
                    }

                    //磁力計サービスの確保
                    if( service.getUuid().toString().equals(MAGNET_SERVICE) ) {
                        Log.d("onServicesDiscovered","magnetometer");
                        mMagnetService = service;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //磁力読み取りボタンを有効化
                                Button button = findViewById(R.id.button_read_magnet);
                                button.setEnabled(true);
                            }
                        });
                    }

                }
            }
        }

        //readCharacteristic()が成功したときにコールされる
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("onCharacteristic","read");
            getCharacteristicData(characteristic);
        }

        //writeCharacteristic()が成功したときにコールされる
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("onCharacteristic","write");
            if( characteristic.getUuid().toString().equals(UART_RX) ){

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Button button = findViewById(R.id.button_uartSend);
                        button.setEnabled(true);
                    }
                });
            }
        }

        //Notifyを有効にすると、デバイスから周期的にセンサデータが通知され、通知の度にこのメソッドがコールされる
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("onCharacteristic","change");

            if( characteristic.getUuid().toString().equals(UART_TX) ){
                byte[] t = characteristic.getValue();
                final String str = new String(t);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView textView = findViewById(R.id.textView_uartRecvData);
                        textView.setText(str);

                    }
                });
            }

            getCharacteristicData(characteristic);
        }
    }

    private void getCharacteristicData(BluetoothGattCharacteristic characteristic){
        if( characteristic.getUuid().toString().equals(TEMP_DATA) ){
            final int temp = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8,0);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.recvData_temp);
                    textView.setText("温度:" + temp);

                }
            });
        }

        if( characteristic.getUuid().toString().equals(ACCEL_DATA) ){
            final int x = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0);
            final int y = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,2);
            final int z = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,4);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.recvData_accel);
                    textView.setText("X:"+x+"  Y:"+y+"  Z:"+z);
                }
            });
        }

        if( characteristic.getUuid().toString().equals(MAGNET_DATA) ){
            final int x = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,0);
            final int y = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,2);
            final int z = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16,4);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView textView = findViewById(R.id.recvData_magnet);
                    textView.setText("X:"+x+"  Y:"+y+"  Z:"+z);
                }
            });
        }
    }

    public void pushConnect(View view) {
        Button button = (Button) view;
        if (button.getText().equals("CONNECT")) {
            scanner = adapter.getBluetoothLeScanner();
            scancallback = new MyScancallback();

            //スキャニングを10秒後に停止
            handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanner.stopScan(scancallback);
                }
            }, SCAN_PERIOD);
            //スキャンの開始
            scanner.startScan(scancallback);
        }
        else if (button.getText().equals("GET SERVICE")) {
            if (device != null) {
                gattCallback = new MyGattcallback();
                device.connectGatt(this, false, gattCallback);
                button.setText("DISCONNECT");
            }
        }
        else if (button.getText().equals("DISCONNECT")) {
            if (mGatt != null) {
                mGatt.close();
                mGatt = null;
                mUartService = null;
                mTempService = null;
                mAccelService = null;
                button.setText("GET SERVICE");

                Button serviceBtn = findViewById(R.id.button_uartSend);
                serviceBtn.setEnabled(false);

                serviceBtn = findViewById(R.id.button_read_temp);
                serviceBtn.setEnabled(false);

                serviceBtn = findViewById(R.id.button_read_accel);
                serviceBtn.setEnabled(false);

                serviceBtn = findViewById(R.id.button_read_magnet);
                serviceBtn.setEnabled(false);

            }
        }
    }

    public void pushUartSend(View view){
        if(mGatt != null){
            Button button = (Button) view;
            button.setEnabled(false);
            TextView textView = findViewById(R.id.editText_uartSendData);

            BluetoothGattService service = mGatt.getService(UUID.fromString(UART_SERVICE));
            BluetoothGattCharacteristic characteristic_rx = service.getCharacteristic(UUID.fromString(UART_RX));
            characteristic_rx.setValue(textView.getText().toString() + "\n");
            mGatt.writeCharacteristic(characteristic_rx);
        }
    }

    public void pushReadTemp(View view){
        if(mTempService != null){
            BluetoothGattCharacteristic characteristic = mTempService.getCharacteristic(UUID.fromString(TEMP_DATA));
            mGatt.readCharacteristic(characteristic);
        }
    }

    public void pushReadAccel(View view){
        if(mAccelService != null){
            BluetoothGattCharacteristic characteristic = mAccelService.getCharacteristic(UUID.fromString(ACCEL_DATA));
            mGatt.readCharacteristic(characteristic);
        }
    }

    public void pushReadMagnet(View view){
        if(mMagnetService != null){
            BluetoothGattCharacteristic characteristic = mMagnetService.getCharacteristic(UUID.fromString(MAGNET_DATA));
            mGatt.readCharacteristic(characteristic);
        }
    }


    public void checkNotifyEnable_temp(View view){
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        if(mTempService != null){
            if(checked){
                //速度落とす
                BluetoothGattCharacteristic characteristic = mTempService.getCharacteristic(UUID.fromString(TEMP_PERIOD));
                characteristic.setValue(1000,BluetoothGattCharacteristic.FORMAT_UINT16,0);
                //Descriptorの記述
                characteristic = mTempService.getCharacteristic(UUID.fromString(TEMP_DATA));
                mGatt.setCharacteristicNotification(characteristic,true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
            else{
                //Descriptorの記述
                BluetoothGattCharacteristic characteristic = mTempService.getCharacteristic(UUID.fromString(TEMP_DATA));
                mGatt.setCharacteristicNotification(characteristic,false);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
        }

    }

    public void checkNotifyEnable_accel(View view){
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        if(mAccelService != null){
            if(checked){
                //速度落とす
                BluetoothGattCharacteristic characteristic = mAccelService.getCharacteristic(UUID.fromString(ACCEL_PERIOD));
                characteristic.setValue(640,BluetoothGattCharacteristic.FORMAT_UINT16,0);
                //Descriptorの記述
                characteristic = mAccelService.getCharacteristic(UUID.fromString(ACCEL_DATA));
                mGatt.setCharacteristicNotification(characteristic,true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
            else{
                //Descriptorの記述
                BluetoothGattCharacteristic characteristic = mAccelService.getCharacteristic(UUID.fromString(ACCEL_DATA));
                mGatt.setCharacteristicNotification(characteristic,false);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
        }

    }

    public void checkNotifyEnable_magnet(View view){
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        if(mMagnetService != null){
            if(checked){
                //速度落とす
                BluetoothGattCharacteristic characteristic = mMagnetService.getCharacteristic(UUID.fromString(MAGNET_PERIOD));
                characteristic.setValue(640,BluetoothGattCharacteristic.FORMAT_UINT16,0);
                //Descriptorの記述
                characteristic = mMagnetService.getCharacteristic(UUID.fromString(MAGNET_DATA));
                mGatt.setCharacteristicNotification(characteristic,true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
            else{
                //Descriptorの記述
                BluetoothGattCharacteristic characteristic = mMagnetService.getCharacteristic(UUID.fromString(MAGNET_DATA));
                mGatt.setCharacteristicNotification(characteristic,false);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(NOTIFY_DESCRIPTOR));
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
        }

    }
}
