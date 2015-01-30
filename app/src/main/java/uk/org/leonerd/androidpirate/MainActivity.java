package uk.org.leonerd.androidpirate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


public class MainActivity extends ActionBarActivity {

    private static final Uri HS_URI = Uri.parse("https://matrix.org");
    // #test-bot:matrix.org
    private static final String ROOM_ID = "!ewOgZEUrOZAAaQJNBv:matrix.org";

    private static final int MESSAGE_READ = 12;

    TextView txtStatus;

    MXSession mMatrixSession;

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
            }
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_READ:
                    Log.d("CONNECT", "Received " + msg.arg1 + " bytes: " + Arrays.toString((byte[]) msg.obj));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
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
        //String user = ((TextView) findViewById(R.id.editTextLogin)).getText().toString();
        //String password = ((TextView) findViewById(R.id.editTextPassword)).getText().toString();
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

        super.onStop();
    }

    public void connectToEcu(View view) {
        mBluetoothAdapter.cancelDiscovery();

        int index = Integer.parseInt(((TextView) findViewById(R.id.editTextIndex)).getText().toString());

        if (index >= mPossibleDevices.size()) {
            Log.d("CONNECT", "Index too large");
            return;
        }

        BluetoothDevice device = mPossibleDevices.get(index);
        Log.d("CONNECT", "Connecting to " + device);

        BluetoothSocket socket;
        try {
            // This method isn't exposed :(
            Method m = device.getClass().getMethod("createRfcommSocket", Integer.TYPE);
            socket = (BluetoothSocket) m.invoke(device, 1);

            socket.connect();
        } catch (IOException e) {
            Log.w("CONNECT", "Connect failed: " + e);
            return;
        } catch (Exception e) {
            Log.w("CONNECT", "Connect failed: " + e);
            return;
        }

        Log.d("CONNECT", "Connected");

        ConnectedThread thr;
        try {
            thr = new ConnectedThread(socket);
            thr.start();
        }
        catch (IOException e) {
            Log.w("CONNECT", "thread startup failed: " + e);
            return;
        }

        thr.write(new byte[]{'A', 'T', 'Z', '\r', '\n'});

        double pressure = 0;
        double temperature = 0;

        if (mMatrixSession != null) {
            Room room = mMatrixSession.getDataHandler().getRoom(ROOM_ID);

            room.sendMessage(new SensorDataMessage(pressure, temperature), new ApiCallback<Event>() {
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

    private static class SensorDataMessage extends TextMessage {
        public double pressure;
        public double temperature;

        public SensorDataMessage(double pressure_, double temperature_) {
            super();
            msgtype = "uk.org.leonerd.SensorData";

            pressure = pressure_;
            temperature = temperature_;

            body = String.format("Sensor reading: pressure=%.2f Pa, temperature=%.3f C",
                    pressure, temperature);
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        public ConnectedThread(BluetoothSocket socket) throws IOException {
            mSocket = socket;
            mInputStream = socket.getInputStream();
            mOutputStream = socket.getOutputStream();
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while(true) {
                try {
                    bytes = mInputStream.read(buffer);
                    byte[] theseBytes = Arrays.copyOf(buffer, bytes);
                    mHandler.obtainMessage(MESSAGE_READ, 0, 0, theseBytes)
                            .sendToTarget();
                }
                catch (IOException e) {
                    break;
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                mOutputStream.write(bytes);
            }
            catch (IOException e) { }
        }
    }
}
