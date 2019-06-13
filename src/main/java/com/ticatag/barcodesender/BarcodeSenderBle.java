package com.ticatag.barcodesender;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.symbol.emdk.barcode.ScanDataCollection;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 Do an advertising to send the barcode.
 */
public class BarcodeSenderBle {
    private final static int REQUEST_ENABLE_BT = 481;

    private final static String UUID_16bit = "0000C9E3-0000-1000-8000-00805F9B34FB";
    private int mAdvertisingDuration = 4500;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothLeAdvertiser mAdvertiser;
    private Activity mActivity;


    public BarcodeSenderBle(Activity activity) {
        mActivity = activity;
    }

    public int getAdvertisingDuration() {
        return mAdvertisingDuration;
    }

    public void setAdvertisingDuration(int advertisingDuration) {
        this.mAdvertisingDuration = advertisingDuration;
    }

    /**
     Do advertising
     Precondition: scannerId >= 0 and <= 65535
     Precondition: barcode character count must be <= 255
     @param barcode The barcode to send
     @param barcodeType The barcode type
     @param scannerId 2-bytes unique value representing the scanner id
     @return true if succeed false else
     */
    public boolean advertise(String barcode, ScanDataCollection.LabelType barcodeType, int scannerId) {
        final int intBytesSize = 4;
        final int scannerIdBytesSize = 2;

        Log.d("DEBUG", barcode + ", " + barcodeType);

        byte barcodeBytes[] = barcode.getBytes();
        byte scannerIdBytes[] = new byte [scannerIdBytesSize];

        //  /--------------\
        //  | Check errors |
        //  \--------------/
        if (mBluetoothAdapter == null) {
            Log.d("Debug", "Bluetooth not supported. Can't send barcode");
            return false;
        }

        if (barcodeBytes.length > 255) {
            Log.e("ERROR", "Barcode length is " + barcodeBytes.length + " and must be <= 255");
            return false;
        }

        if (scannerId > 65535) {
            Log.e("ERROR", "scannerId is " + barcodeBytes.length + " and must be < 65535 (2 bytes)");
            return false;
        }

        //  /-----------------------------------------------------------\
        //  | Request bluetooth activation to the user if not activated |
        //  \-----------------------------------------------------------/
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            mActivity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            Toast.makeText(mActivity, "Bluetooth disabled. Please enable bluetooth and retry.", Toast.LENGTH_LONG).show();
            return false;
        }

        //  /-----------------------\
        //  | Build message to send |
        //  \-----------------------/
        ByteBuffer scannerIdByteBuffer = ByteBuffer.allocate(intBytesSize);
        scannerIdByteBuffer.putInt(scannerId);
        byte scannerIdIntBytes[] = scannerIdByteBuffer.array();
        for (int i = 0; i < scannerIdBytes.length && i < scannerIdIntBytes.length; i++) {
            scannerIdBytes[i] = scannerIdIntBytes[scannerIdIntBytes.length - 1 - i];
        }

        byte headerBytes[];

        headerBytes = combineByteArrays(
                new byte[]{
                        '0', // Advertising message code (2 bytes)
                        'A',
                },
                scannerIdBytes // Scanner id (2 bytes)
        );

        headerBytes = combineByteArrays(
                headerBytes,
                new byte[]{
                        (byte)barcodeType.ordinal(), // Barcode type from ScanDataCollection.LabelType enumcode (1 byte)
                        (byte)barcodeBytes.length // Barcode length (1 byte)
                }
        );
        byte scanRspData[] = combineByteArrays(headerBytes, barcodeBytes);

        //  /----------------------------\
        //  | Setup advertising settings |
        //  \----------------------------/
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode( AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY )
                .setTxPowerLevel( AdvertiseSettings.ADVERTISE_TX_POWER_HIGH )
                .setConnectable( false )
                .build();

        ParcelUuid serviceUuid = new ParcelUuid( UUID.fromString( UUID_16bit ));

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addManufacturerData(0xffff, hexStringToByteArray("ffffffff"))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceData(serviceUuid,  scanRspData)
                .build();

        final AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e( "BLE", "Advertising onStartFailure: " + errorCode );
                super.onStartFailure(errorCode);
            }
        };

        //  /-------------------\
        //  | Start advertising |
        //  \-------------------/
        mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        try {
            mAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertisingCallback);
        } catch (RuntimeException e) {
            Log.e("Error:", e.toString());
        }

        Handler mHandler = new Handler();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                if (mAdvertiser != null) {
                    mAdvertiser.stopAdvertising(advertisingCallback);
                }
            }
        }, mAdvertisingDuration);

        return true;
    }

    public static byte[] combineByteArrays(byte[] a, byte[] b){
        int length = a.length + b.length;
        byte[] result = new byte[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
}
