package com.example.joseph.bleproject4;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@TargetApi(21)
/**
 * Multi-Target API Tutorial: http://www.truiton.com/2015/04/android-bluetooth-low-energy-ble-example/
 */
public class MainActivity extends Activity
{
    // Bluetooth Adapter on the device
    private BluetoothAdapter mBluetoothAdapter;

    // constant to handle requesting Bluetooth on device
    private int REQUEST_ENABLE_BT = 1;

    // android.os handler to handle device's api level
    private Handler mHandler;

    // milliseconds to scan for signals
    private static final long SCAN_PERIOD = 10 * 1000;

    // Android 5.0 implementation of LE Scanner
    private BluetoothLeScanner mLEScanner;

    // Android 5.0 options for scan modes: LOW_POWER, BALANCED, or LOW_LATENCY
    private ScanSettings scanSettings;

    // Android 5.0 implementation of scan scanFilters
    private List<ScanFilter> scanFilters;

    // provides Bluetooth GATT Profile
    // From: http://toastdroid.com/2014/09/22/android-bluetooth-low-energy-tutorial/
    // Generic Attribute Profile (GATT).
    // This is a general specification for sending and receiving short pieces of data
    // (known as attributes) over a low energy link.
    private BluetoothGatt mGatt;

    // Android 5.0 implementation of LE Advertiser
    private BluetoothLeAdvertiser mLEAdvertiser;

    // Android 5.0 options for advertise modes: LOW_POWER, BALANCED, or LOW_LATENCY
    private AdvertiseSettings advertiseSettings;

    // Android 5.0 implementation of advertise Filters
    private AdvertiseData advertiseData;

    // taken from iOS app
    private final String uuidStr = "7BF314DD-E050-4261-AB4A-1F1D21872E05";
    private AdvertiseCallback mAdvertiseCallback;
    private boolean mStarted;
    private AdvertiseCallback mAdvertisingClientCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try
        {
            Drawable d = Drawable.createFromStream(getAssets().open("images/PaerScreenGradient.png"), null);
            final RelativeLayout mRelativeLayout = (RelativeLayout) findViewById(R.id.mainRelativeLayout);
            mRelativeLayout.setBackground(d);
        }
        catch (IOException e)
        {
            // image not found
            e.printStackTrace();
        }

        // initialize mHandler
        mHandler = new Handler();

        // standard BLE checker for device
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        else
        {
            Toast.makeText(this, "BLE Supported!", Toast.LENGTH_SHORT).show();
        }

        Log.d("MainActivity", "Settuping up Bluetooth Adapter HERE");
        // standard setup for Bluetooth device
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        Log.d("MainActivity", "Multiple Advertisement Support: " + mBluetoothAdapter.isMultipleAdvertisementSupported());
        Toast.makeText(this, "Multiple Advertisement Support: " + mBluetoothAdapter.isMultipleAdvertisementSupported(), Toast.LENGTH_SHORT).show();


        final Button advertiseButton = (Button) findViewById(R.id.advertiseButton);
        advertiseButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                // Perform action on click
                // initialize Advertiser
                mLEAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();

                // build AdvertiseData
                AdvertiseData.Builder advertiseDataBuilder = new AdvertiseData.Builder();
                byte[] serviceUUIDBytes = (uuidStr.getBytes());
                ParcelUuid parcelUuid = parseUuidFrom(serviceUUIDBytes);
                advertiseDataBuilder.addServiceUuid(parcelUuid);

                AdvertiseSettings.Builder advertiseSettingsBuilder = new AdvertiseSettings.Builder();
                advertiseSettingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
                advertiseSettingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
                advertiseSettingsBuilder.setConnectable(false);

                mLEAdvertiser.startAdvertising(advertiseSettingsBuilder.build(), advertiseDataBuilder.build(), getAdvertiseCallback());
                //advertiseSettings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).build();
                //advertiseData = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(UUID.fromString(uuidStr))).build();
                //mLEAdvertiser.startAdvertising();
            }
        });
    }

    /**
     * Parse UUID from bytes. The {@code uuidBytes} can represent a 16-bit, 32-bit or 128-bit UUID,
     * but the returned UUID is always in 128-bit format.
     * Note UUID is little endian in Bluetooth.
     *
     * @param uuidBytes Byte representation of uuid.
     * @return {@link ParcelUuid} parsed from bytes.
     * @throws IllegalArgumentException If the {@code uuidBytes} cannot be parsed.
     *
     * Copied from java/android/bluetooth/BluetoothUuid.java
     * Copyright (C) 2009 The Android Open Source Project
     * Licensed under the Apache License, Version 2.0
     */
    private static ParcelUuid parseUuidFrom(byte[] uuidBytes) {
        /** Length of bytes for 16 bit UUID */
        final int UUID_BYTES_16_BIT = 2;
        /** Length of bytes for 32 bit UUID */
        final int UUID_BYTES_32_BIT = 4;
        /** Length of bytes for 128 bit UUID */
        final int UUID_BYTES_128_BIT = 16;
        final ParcelUuid BASE_UUID =
                ParcelUuid.fromString("00000000-0000-1000-8000-00805F9B34FB");
        if (uuidBytes == null) {
            throw new IllegalArgumentException("uuidBytes cannot be null");
        }
        int length = uuidBytes.length;
        if (length != UUID_BYTES_16_BIT && length != UUID_BYTES_32_BIT &&
                length != UUID_BYTES_128_BIT) {
            throw new IllegalArgumentException("uuidBytes length invalid - " + length);
        }
        // Construct a 128 bit UUID.
        if (length == UUID_BYTES_128_BIT) {
            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
            long msb = buf.getLong(8);
            long lsb = buf.getLong(0);
            return new ParcelUuid(new UUID(msb, lsb));
        }
        // For 16 bit and 32 bit UUID we need to convert them to 128 bit value.
        // 128_bit_value = uuid * 2^96 + BASE_UUID
        long shortUuid;
        if (length == UUID_BYTES_16_BIT) {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
        } else {
            shortUuid = uuidBytes[0] & 0xFF ;
            shortUuid += (uuidBytes[1] & 0xFF) << 8;
            shortUuid += (uuidBytes[2] & 0xFF) << 16;
            shortUuid += (uuidBytes[3] & 0xFF) << 24;
        }
        long msb = BASE_UUID.getUuid().getMostSignificantBits() + (shortUuid << 32);
        long lsb = BASE_UUID.getUuid().getLeastSignificantBits();
        return new ParcelUuid(new UUID(msb, lsb));
    }

    private AdvertiseCallback getAdvertiseCallback()
    {
        if (mAdvertiseCallback == null)
        {
            mAdvertiseCallback = new AdvertiseCallback()
            {
                @Override
                public void onStartFailure(int errorCode)
                {
                    Log.e("MainActivity","Advertisement start failed, code: " + errorCode);
                    if (mAdvertisingClientCallback != null)
                    {
                        mAdvertisingClientCallback.onStartFailure(errorCode);
                    }

                }

                @Override
                public void onStartSuccess(AdvertiseSettings settingsInEffect)
                {
                    Log.i("MainActivity","Advertisement start succeeded.");
                    mStarted = true;
                    if (mAdvertisingClientCallback != null)
                    {
                        mAdvertisingClientCallback.onStartSuccess(settingsInEffect);
                    }
                }
            };


        }
        return mAdvertiseCallback;
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        // standard Bluetooth checker
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
        {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else
        {
            // take advantage of 5.0's mLEScanner class
            if (Build.VERSION.SDK_INT >= 21)
            {
                // initialize mLEScanner for future scans
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                scanFilters = new ArrayList<ScanFilter>();
            }
            // starts scans
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled())
        {
            // stops scans
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy()
    {
        // handles device's profile for this session
        if (mGatt == null)
        {
            //return;
            // ^ causes problems with never reaching super.onDestroy();
            // instead, do nothing and immediately destroy
        }
        else
        {
            //Log.d("MainActivity", "mGatt before destroy: " + mGatt.toString());
            //Toast.makeText(this, "mGatt before destroy: " + mGatt.toString(), Toast.LENGTH_SHORT).show();
            // clean up data
            mGatt.close();
            mGatt = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_ENABLE_BT)
        {
            // request to enable BT
            if (resultCode == Activity.RESULT_CANCELED)
            {
                //Bluetooth not enabled.
                Toast.makeText(this, "Bluetooth not enabled!", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Helper method to handle scanning from the device
     * @param enable
     */
    private void scanLeDevice(final boolean enable)
    {
        if (enable)
        {
            // stops scan after SCAN_PERIOD time
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run()
                {
                    if (Build.VERSION.SDK_INT < 21)
                    {
                        Log.d("MainActivity", "stopping scan < 21 after SCAN_PERIOD");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                    else
                    {
                        Log.d("MainActivity", "stopping scan < 21 after SCAN_PERIOD");
                        mLEScanner.stopScan(mScanCallback);
                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21)
            {
                Log.d("MainActivity", "starting scan < 21");
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            else
            {
                Log.d("MainActivity", "starting scan > 21");
                mLEScanner.startScan(scanFilters, scanSettings, mScanCallback);
            }
        }
        else
        {
            if (Build.VERSION.SDK_INT < 21)
            {
                Log.d("MainActivity", "never scanned > 21");
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            else
            {
                Log.d("MainActivity", "never scanned < 21");
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            connectToDevice(btDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback()
            {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Log.i("onLeScan", device.toString());
                            connectToDevice(device);
                        }
                    });
                }
            };

    public void connectToDevice(BluetoothDevice device)
    {
        if (mGatt == null)
        {
            mGatt = device.connectGatt(this, false, gattCallback);
            TextView mGattDisplay = (TextView) findViewById(R.id.MainTextView);
            mGattDisplay.setText(mGatt.getDevice().getAddress());
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }

        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            Log.i("onServicesDiscovered", services.toString());
            gatt.readCharacteristic(services.get(1).getCharacteristics().get
                    (0));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            gatt.disconnect();
        }
    };
}
