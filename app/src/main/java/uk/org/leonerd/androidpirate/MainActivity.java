package uk.org.leonerd.androidpirate;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

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

import java.util.List;


public class MainActivity extends ActionBarActivity {

    private static final int MPL311A5_ADDR = 0x60;

    private static final Uri HS_URI = Uri.parse("https://matrix.org");
    // #test-bot:matrix.org
    private static final String ROOM_ID = "!ewOgZEUrOZAAaQJNBv:matrix.org";

    TextView txtStatus;
    BusPirate mPirate;

    MXSession mMatrixSession;

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

        mPirate = findBusPirate();
        if (mPirate == null) {
            txtStatus.setText("No pirate");
            return;
        }

        txtStatus.setText("Pirate ready");

        try {
            mPirate.enterI2CMode();
            txtStatus.setText("Pirate I2C ready");
        } catch (Exception e) {
            txtStatus.setText("Pirate failed: " + e);
            return;
        }

        try {
            mPirate.setPower(true);
        } catch (Exception e) {
            txtStatus.setText("Power failed: " + e);
            return;
        }

        try {
            // CTRL_REG1 = ACTIVE
            mPirate.i2cSend(MPL311A5_ADDR, new byte[]{0x26, 0x01});
            txtStatus.setText("MPL311A5 ready");
        } catch (Exception e) {
            txtStatus.setText("MPL311A5 failed: " + e);
            return;
        }

    }

    public void matrixLogin(View view) {
        String user = ((TextView) findViewById(R.id.editTextLogin)).getText().toString();
        String password = ((TextView) findViewById(R.id.editTextPassword)).getText().toString();

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
        if (mPirate != null) {
            mPirate.stop();
            mPirate = null;
        }

        super.onStop();
    }

    private static int intFromByte(byte b) {
        if(b < 0)
            return 256 + b;
        else
            return b;
    }

    public void readSensor(View view) {
        byte[] rawData;
        try {
            rawData = mPirate.i2cSendThenRecv(MPL311A5_ADDR, new byte[]{0x01}, 5);
        }
        catch (Exception e) {
            txtStatus.setText("Read failed: " + e);
            return;
        }

        double pressure =
                ((intFromByte(rawData[0]) << 16) |
                 (intFromByte(rawData[1]) <<  8) |
                  intFromByte(rawData[2])        ) / 64.0;
        double temperature =
                rawData[3] +
                intFromByte(rawData[4]) / 256.0;

        ((TextView)findViewById(R.id.txtPressure)).setText(String.format("%.2f Pa", pressure));
        ((TextView)findViewById(R.id.txtTemperature)).setText(String.format("%.3f C", temperature));

        if (mMatrixSession != null) {
            Room room = mMatrixSession.getDataHandler().getRoom(ROOM_ID);
            TextMessage message = new TextMessage();
            message.body = String.format("Sensor reading: pressure=%.2f Pa, temperature=%.3f C",
                    pressure, temperature);

            room.sendMessage(message, new ApiCallback<Event>() {
                @Override
                public void onSuccess(Event info) { }

                @Override
                public void onNetworkError(Exception e) { }

                @Override
                public void onMatrixError(MatrixError e) { }

                @Override
                public void onUnexpectedError(Exception e) { }
            });
        }
    }

    private BusPirate findBusPirate() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber()
                .findAllDrivers(manager);

        if (availableDrivers.isEmpty())
            return null;

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null)
            return null;

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            txtStatus.setText("Opening...");
            port.open(connection);
            port.setParameters(115200, 8, 1, 0);
        }
        catch (Exception e) {
            txtStatus.setText("Didn't open: " + e);
            return null;
        }

        try {
            txtStatus.setText("Initialising Pirate...");
            return new BusPirate(port);

        }
        catch (Exception e) {
            txtStatus.setText("Didn't like it: " + e);
            return null;
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
}
