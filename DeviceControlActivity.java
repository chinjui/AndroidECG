/* // application accelerator edition.
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.bluetooth.le;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView mDeviceAddressHolder;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    //private BleWrapper mBleWrapper = null;
    private ArrayList<BleWrapper> mBleWrappers;
    private final String LOGTAG = "LOGTAG";
    private int deviceNum;
    private String[] deviceNames;
    private String[] deviceAddrs;
    ScheduledExecutorService speedTest;
    //ScheduledExecutorService scheduler;
    ArrayList<ScheduledExecutorService> schedulers;
    private int current;
    private String currentAddr;
    private int connectionCount;
    private int previousCount;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.


    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        Bundle b = intent.getExtras();

        deviceNum = b.getInt("count");
        deviceNames = b.getStringArray("deviceNames");
        deviceAddrs = b.getStringArray("deviceAddrs");
        //mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        //mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceName = deviceNames[0];
        mDeviceAddress = deviceAddrs[0];

        // Sets up UI references.
        //((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress); // set only when connected
        mDeviceAddressHolder = (TextView) findViewById(R.id.device_address);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        //mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);


        mBleWrappers = new ArrayList<BleWrapper>();
        for(int i = 0 ; i < deviceNum ; i++){
            BleWrapper mBleWrapper = new BleWrapper(this, new BleWrapperUiCallbacks.Null() //???????
            {
                @Override
                public void uiDeviceConnected(final BluetoothGatt gatt,
                                              final BluetoothDevice device){
                    Log.v("hey", "connected to " + device.getAddress());
                    connectionCount++;
                }

                @Override
                public void uiAvailableServices(BluetoothGatt gatt,
                                                BluetoothDevice device,
                                                List<BluetoothGattService> services
                )
                {
                    for (BluetoothGattService service : services)
                    {
                        String serviceName = BleNamesResolver.resolveUuid
                                (service.getUuid().toString());
                        Log.d("DEBUG", serviceName);
                    }
                }



                @Override
                void uiDeviceConnected() {

                }

                @Override
                public void uiDeviceDisconnected(BluetoothGatt gatt, BluetoothDevice device) {
                    Log.v("hey", "disconnected");
                }



                @Override
                public void uiCharacteristicForService(	BluetoothGatt gatt,
                                                           BluetoothDevice device,
                                                           BluetoothGattService service,
                                                           List<BluetoothGattCharacteristic> chars)
                {
                    super.uiCharacteristicForService(gatt, device, service, chars);
                    for (BluetoothGattCharacteristic c : chars)
                    {
                        String charName = BleNamesResolver.resolveCharacteristicName(c.getUuid().toString());
                    }
                }


                @Override
                public void uiSuccessfulWrite(	BluetoothGatt gatt,
                                                  BluetoothDevice device,
                                                  BluetoothGattService service,
                                                  BluetoothGattCharacteristic ch,
                                                  String description)
                {

                }

                @Override
                public void uiFailedWrite(	BluetoothGatt gatt,
                                              BluetoothDevice device,
                                              BluetoothGattService service,
                                              BluetoothGattCharacteristic ch,
                                              String description)
                {
                    super.uiFailedWrite(gatt, device, service, ch, description);
                }

                @Override
                public void uiNewValueForCharacteristic(BluetoothGatt gatt,
                                                        BluetoothDevice device,
                                                        BluetoothGattService service,
                                                        BluetoothGattCharacteristic ch,
                                                        String strValue,
                                                        int intValue,
                                                        byte[] rawValue,
                                                        String timestamp)
                {
                    super.uiNewValueForCharacteristic(gatt, device, service, ch, strValue, intValue, rawValue, timestamp);
                    for (byte b:rawValue)
                    {
                    }
                }

                @Override
                public void uiGotNotification(	BluetoothGatt gatt,
                                                  BluetoothDevice device,
                                                  BluetoothGattService service,
                                                  BluetoothGattCharacteristic characteristic)
                {
                    super.uiGotNotification(gatt, device, service, characteristic);
                    String ch = BleNamesResolver.resolveCharacteristicName(characteristic.getUuid().toString());

                }


            });
            mBleWrappers.add(mBleWrapper);
        }


        if (mBleWrappers.get(0).checkBleHardwareAvailable() == false)
        {
            Toast.makeText(this, "No BLE-compatible hardware detected",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
        //Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        //bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        */
        if (mBleWrappers.get(0).isBtEnabled() == false)
        {
            // Bluetooth is not enabled. Request to user to turn it on
            Intent enableBtIntent = new Intent(BluetoothAdapter.
                    ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            finish();
        }

        schedulers = new ArrayList<ScheduledExecutorService>(deviceNum);
        for(int ii = 0 ; ii < deviceNum ; ii++) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            final int finalIi = ii;

            /* initialize mBleWrappers */
            final BleWrapper mBleWrapper = mBleWrappers.get(finalIi);
            mBleWrapper.initialize();
            Log.v("ii", ""+finalIi);
            scheduler.scheduleWithFixedDelay(new Runnable() { // the latter connection will wait if the previous one doesn't complete

                public void run() {
                    Log.v("control", "currentAddr : " + deviceAddrs[finalIi]);
                    if (mBleWrapper.isConnected())
                        mBleWrapper.diconnect();
                    mBleWrapper.connect(deviceAddrs[finalIi]);
                    //mConnectTo.setText(deviceAddrs[current]); // can not be here?
                    currentAddr = deviceAddrs[current];
                    //connection success, connetionCount ++
                    //connectionCount++;
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);
            schedulers.add(scheduler);
        }

        speedTest =
                Executors.newSingleThreadScheduledExecutor();
        speedTest.scheduleAtFixedRate(new Runnable() {

            public void run() {
                Log.v("speed", "connected " + (connectionCount) + " times");
                //connectionCount = 0;
            }
        }, 2, 5, TimeUnit.SECONDS);
    }


    @Override
    protected void onPause() {
        super.onPause();
        //unregisterReceiver(mGattUpdateReceiver);
        speedTest.shutdown(); // stop routine
        //scheduler.shutdown(); // stop routine
        //mBleWrapper.diconnect();
        //mBleWrapper.close();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                return true;
            case R.id.menu_disconnect:
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
