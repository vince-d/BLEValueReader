package edu.teco.blerssistrengthtest;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    public static final String TAG = "bPartVoltageReader";

    BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mDevice;
    private ProgressDialog mProgressDialog;

    private String mBLEAddress;
    private String mBLEName;

    private TextView tempView;
    private TextView humView;

    private int mValueInt = 0;
    private float mValueFloat = 0;

    private static UUID tempService = UUID.fromString("4b822f20-3941-4a4b-a3cc-b2602ffe0d00");
    private static UUID humService = UUID.fromString("4b822f30-3941-4a4b-a3cc-b2602ffe0d00");

    private static UUID tempCharacteristic = UUID.fromString("4b822f21-3941-4a4b-a3cc-b2602ffe0d00");
    private static UUID humCharacteristic = UUID.fromString("4b822f31-3941-4a4b-a3cc-b2602ffe0d00");

    BluetoothGattCharacteristic tempCharacteristic2;
    BluetoothGattCharacteristic humCharacteristic2;

    private boolean screenLockOn;

    private FloatingActionButton mFab;

    private SimpleXYSeries mSeriesNOX;
    private SimpleXYSeries mSeriesCO;

    private XYPlot mPlot = null;
    private int time = 0;

    BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);


            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "Connected to " + gatt.getDevice().getAddress());

                gatt.discoverServices();

            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from " + gatt.getDevice().getAddress());
            }

        }

        final Runnable mStartTempRunnable = new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt.readCharacteristic(tempCharacteristic2);
            }
        };
        final Runnable mStartHumRunnable = new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt.readCharacteristic(humCharacteristic2);
            }
        };


        // On connect cycle through services and characteristics and look for voltage char.
        // If voltage char is found, read.
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            BluetoothGattService batt_gatt = gatt.getService(humService);
            for (BluetoothGattCharacteristic charac : batt_gatt.getCharacteristics()) {
                if (charac.getUuid().equals(humCharacteristic)) {
                    humCharacteristic2 = charac;
                }
            }

            batt_gatt = gatt.getService(tempService);
            for (BluetoothGattCharacteristic charac : batt_gatt.getCharacteristics()) {
                if (charac.getUuid().equals(tempCharacteristic)) {
                    tempCharacteristic2 = charac;

                    gatt.readCharacteristic(charac);
                }
            }


        }

        // Convert voltage bytes to usable value and draw on graph.
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            // Reorder bytes.
            Log.i(TAG, characteristic.getUuid().toString());
            int val = 0;
            byte[] valByte = characteristic.getValue();
            byte[] reordered = new byte[2];
            reordered[0] = valByte[1];
            reordered[1] = valByte[0];

            // Convert to float.
            val = ByteBuffer.wrap(reordered).getShort();
            Log.i(TAG, val + "");

            if (characteristic.getUuid().equals(tempCharacteristic)) {
                // Add value to graph as new point.
                float valfl = val / 1000.0f;
                mSeriesNOX.addLast(time, valfl);
                mPlot.redraw();
                time++;
                mHandler.postDelayed(mStartHumRunnable, 1000);
                mValueFloat = valfl;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tempView.setText("Temperature (°C):  " + mValueFloat);
                    }
                });
            }

            if (characteristic.getUuid().equals(humCharacteristic)) {
                // Add value to graph as new point.
                mSeriesCO.addLast(time, val);
                mPlot.redraw();
                time++;
                mHandler.postDelayed(mStartTempRunnable, 1000);
                mValueInt = val;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        humView.setText("Humidity (%RH):    " + mValueInt);
                    }
                });
            }
        }
    };
    private PowerManager.WakeLock mWakeLock;

    // Convert byte array to hex String.
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        tempView = (TextView) findViewById(R.id.valview1);
        humView = (TextView) findViewById(R.id.valview2);
        tempView.setText("Temperature (°C):");
        humView.setText("Humidity (%RH):");

        screenLockOn = true;

        screenLock(true);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No BLE on this device.", Toast.LENGTH_LONG).show();
            finish();
        }

        final BluetoothManager bMan = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (bMan.getAdapter() == null || !bMan.getAdapter().isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first.", Toast.LENGTH_LONG).show();
            finish();
        }

        mBluetoothAdapter = bMan.getAdapter();


        mHandler = new Handler(Looper.getMainLooper());

        // Download stuff.


        mPlot = (XYPlot) findViewById(R.id.mySimpleXYPlot);
        mPlot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
        mPlot.getGraphWidget().getGridBackgroundPaint().setColor(Color.argb(43,255,255,255));
        mPlot.getGraphWidget().getRangeGridLinePaint().setColor(Color.argb(43, 0, 0, 0));
        mPlot.getGraphWidget().getDomainGridLinePaint().setColor(Color.argb(43, 0, 0, 0));
        mPlot.getGraphWidget().getDomainLabelPaint().setColor(Color.BLACK);
        mPlot.getGraphWidget().getRangeLabelPaint().setColor(Color.BLACK);
        mPlot.getGraphWidget().getRangeOriginLinePaint().setColor(Color.BLACK);
        mPlot.getGraphWidget().getRangeOriginLinePaint().setStrokeWidth(4);
        mPlot.getGraphWidget().getDomainOriginLinePaint().setColor(Color.BLACK);
        mPlot.getGraphWidget().getDomainOriginLinePaint().setStrokeWidth(4);
        mPlot.getGraphWidget().getCursorLabelBackgroundPaint().setColor(Color.GREEN);
        mPlot.getBorderPaint().setColor(Color.TRANSPARENT);
        mPlot.getBackgroundPaint().setColor(Color.TRANSPARENT);

        mSeriesNOX = new SimpleXYSeries("Temp.");
        mSeriesCO = new SimpleXYSeries("Hum.");

        mPlot.getLegendWidget().getTextPaint().setColor(Color.BLACK);
        mPlot.setDomainValueFormat(new DecimalFormat("0"));
        mPlot.setTicksPerDomainLabel(3);
        mPlot.setRangeValueFormat(new DecimalFormat("0"));

        mPlot.addSeries(mSeriesNOX, new LineAndPointFormatter(Color.rgb(100, 100, 200), null, null, null));
        mPlot.addSeries(mSeriesCO, new LineAndPointFormatter(Color.rgb(200, 100, 100), null, null, null));

        mPlot.setRangeBoundaries(-10, 80, BoundaryMode.FIXED);

        Intent intent = getIntent();
        mBLEAddress = intent.getStringExtra("mac");
        mBLEName = intent.getStringExtra("name");

        mDevice = mBluetoothAdapter.getRemoteDevice(mBLEAddress);
        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);

        TextView v = (TextView) findViewById(R.id.bleDeviceNameView);
        v.setText(mBLEName + " - " + mBLEAddress);

        View graphView = findViewById(R.id.mySimpleXYPlot);

        graphView.setVisibility(View.VISIBLE);

        mFab = (FloatingActionButton) findViewById(R.id.fab2);
        mFab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onBackPressed();
            }
        });

    }

    private void screenLock(boolean b) {
        if (b) {
            PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
                    getClass().getName());
            mWakeLock.acquire();
        } else {
            mWakeLock.release();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            if (screenLockOn) {
                Toast.makeText(this, "Screen brightness lock turned off", Toast.LENGTH_SHORT).show();
                screenLock(false);
                screenLockOn = false;
            } else {
                Toast.makeText(this, "Screen brightness lock turned on", Toast.LENGTH_SHORT).show();
                screenLock(true);
                screenLockOn = true;
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }




    @Override
    protected void onPause() {
        super.onPause();

        screenLock(false);

        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
    }
}
