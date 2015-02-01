package uk.org.leonerd.androidpirate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.TextMessage;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import pt.lighthouselabs.obd.commands.engine.EngineLoadObdCommand;
import pt.lighthouselabs.obd.commands.engine.EngineRPMObdCommand;
import pt.lighthouselabs.obd.commands.protocol.EchoOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.LineFeedOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.SelectProtocolObdCommand;
import pt.lighthouselabs.obd.commands.protocol.TimeoutObdCommand;
import pt.lighthouselabs.obd.commands.temperature.AmbientAirTemperatureObdCommand;
import pt.lighthouselabs.obd.commands.temperature.EngineCoolantTemperatureObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;


public class MainActivity extends ActionBarActivity {

    private static final Uri HS_URI = Uri.parse("https://matrix.org");
    // #test-bot:matrix.org
    private static final String ROOM_ID = "!ewOgZEUrOZAAaQJNBv:matrix.org";

    private static final ObdProtocols[] OBD_ALL_PROTOS = {
           ObdProtocols.SAE_J1850_PWM,         // 0
            ObdProtocols.SAE_J1850_VPW,        // 1
            ObdProtocols.ISO_9141_2,           // 2
            ObdProtocols.ISO_14230_4_KWP,      // 3
            ObdProtocols.ISO_14230_4_KWP_FAST, // 4
            ObdProtocols.ISO_15765_4_CAN,      // 5
            ObdProtocols.ISO_15765_4_CAN_B,    // 6
            ObdProtocols.ISO_15765_4_CAN_C,    // 7
            ObdProtocols.ISO_15765_4_CAN_D,    // 8
            ObdProtocols.SAE_J1939_CAN         // 9
    };

    TextView txtStatus;
    ArrayAdapter mDeviceListAdapter;
    EditText editTextProtocol;

    BluetoothSocket mSocket;

    MXSession mMatrixSession;

    PollingThread mPolling;

    BluetoothAdapter mBluetoothAdapter;
    List<BluetoothDevice> mPossibleDevices = new LinkedList<BluetoothDevice>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                Log.d("DISCOVER", "Device " + device.getName() + " found at index [" + mPossibleDevices.size() + "]");
                mPossibleDevices.add(device);
                mDeviceListAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        editTextProtocol = (EditText) findViewById(R.id.editTextProto);

        mDeviceListAdapter = new ArrayAdapter<BluetoothDevice>(this, R.layout.device_list_item,
                R.id.txtDeviceName, mPossibleDevices) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                BluetoothDevice dev = mPossibleDevices.get(position);
                TextView v = new TextView(MainActivity.this);
                v.setText(dev.getName());
                return v;
            }
        };

        ((Button) findViewById(R.id.btnRead)).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        readSensor();
                    }
                }
        );

        final ListView lstDevices = (ListView) findViewById(R.id.lstDevices);
        lstDevices.setAdapter(mDeviceListAdapter);

        lstDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connectToEcu((BluetoothDevice) lstDevices.getAdapter().getItem(position));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        txtStatus.setText("Starting");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.d("DISCOVER", "No bluetooth adapter");
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.d("DISCOVER", "Requesting enable bluetooth");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        Log.d("DISCOVER", "Starting bluetooth discovery...");
        mPossibleDevices.clear();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        mBluetoothAdapter.startDiscovery();
    }

    public void matrixLogin(View view) {
        String user = "test-leo";
        String password = "dh02y&t8xp";

        mMatrixSession = null;

        new LoginRestClient(HS_URI).loginWithPassword(user, password,
                new ApiCallback<Credentials>() {
                    @Override
                    public void onSuccess(Credentials credentials) {
                        mMatrixSession = new MXSession(
                                new MXDataHandler(new MXMemoryStore(), credentials),
                                credentials
                        );
                        txtStatus.setText("Matrix logged in");
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        txtStatus.setText("Matrix login failed: " + e);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        txtStatus.setText("Matrix login failed: " + e);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        txtStatus.setText("Matrix login failed: " + e);
                    }
                }
        );
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver);

        if (mPolling != null) {
            mPolling.cancel();
            mPolling = null;
        }

        super.onStop();
    }

    public void setStatus(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                txtStatus.setText(msg);
            }
        });
    }

    public void connectToEcu(BluetoothDevice device) {
        mBluetoothAdapter.cancelDiscovery();

        Log.d("CONNECT", "Connecting to " + device);

        try {
            // This method isn't exposed :(
            Method m = device.getClass().getMethod("createRfcommSocket", Integer.TYPE);
            mSocket = (BluetoothSocket) m.invoke(device, 1);

            mSocket.connect();
        } catch (IOException e) {
            Log.w("CONNECT", "Connect failed: " + e);
            return;
        } catch (Exception e) {
            Log.w("CONNECT", "Connect failed: " + e);
            return;
        }

        Log.d("CONNECT", "Connected");

        ((Button) findViewById(R.id.btnRead)).setEnabled(true);

        mPolling = new PollingThread();
        mPolling.start();
    }

    public void readSensor()
    {
        InputStream is;
        OutputStream os;
        try {
            is = mSocket.getInputStream();
            os = mSocket.getOutputStream();
        } catch (IOException e) {
            Log.w("OBD", "Failed to get streams: " + e);
            return;
        }

        EngineDataMessage message = new EngineDataMessage();

        setStatus("Starting OBD query...");

        int protoindex;
        try {
            protoindex = Integer.parseInt(editTextProtocol.getText().toString());
        } catch (Exception e) {
            setStatus("Cannot parseint: " + e);
            return;
        }

        if (protoindex < 0 || protoindex >= OBD_ALL_PROTOS.length) {
            setStatus("Index out of range - 0 to " + (OBD_ALL_PROTOS.length - 1));
        }

        try {
            new EchoOffObdCommand().run(is, os);
            setStatus("Echo is off");

            new LineFeedOffObdCommand().run(is, os);
            setStatus("Linefeed is off");

            new TimeoutObdCommand(250).run(is, os);
            setStatus("Set timeout to 1000msec");

            new SelectProtocolObdCommand(OBD_ALL_PROTOS[protoindex]).run(is, os);
            setStatus("Selected AUTO protocol");

            EngineRPMObdCommand cmdRpm = new EngineRPMObdCommand();
            cmdRpm.run(is, os);
            message.rpm = cmdRpm.getRPM();
            Log.i("OBD", "Engine RPM is " + cmdRpm.getRPM());

            EngineLoadObdCommand cmdLoad = new EngineLoadObdCommand();
            cmdLoad.run(is, os);
            message.load = cmdLoad.getPercentage();

            EngineCoolantTemperatureObdCommand cmdTemp = new EngineCoolantTemperatureObdCommand();
            cmdTemp.run(is, os);
            message.temperature = cmdTemp.getTemperature();
        }
        catch (Exception e) {
            Log.d("OBD", "OBD command failed: " + e);
            return;
        }

        setStatus("OBD query successful");
        Log.d("OBD", "Managed to poll some stuff");

        if (mMatrixSession != null) {
            Room room = mMatrixSession.getDataHandler().getRoom(ROOM_ID);

            message.fillBody();
            room.sendMessage(message, new ApiCallback<Event>() {
                @Override
                public void onSuccess(Event info) {
                }

                @Override
                public void onNetworkError(Exception e) {
                }

                @Override
                public void onMatrixError(MatrixError e) {
                }

                @Override
                public void onUnexpectedError(Exception e) {
                }
            });
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private static class EngineDataMessage extends TextMessage {
        public int rpm;
        public float load;
        public float temperature;

        public EngineDataMessage() {
            super();
            msgtype = "uk.org.leonerd.EngineData";
        }

        public void fillBody() {
            body = String.format("Engine reading: rpm=%d load=%.1f%% temperature=%.1fC",
                    rpm, load, temperature);
        }
    }

    private class PollingThread extends Thread {

        private static final int SLEEP_MSEC = 5000;

        private boolean mRunning = false;

        @Override
        public void run() {
            mRunning = true;

            while(mRunning) {
                readSensor();

                try {
                    Thread.sleep(SLEEP_MSEC);
                } catch (InterruptedException e) { }
            }
        }

        public void cancel() {
            mRunning = false;
        }
    }
}
