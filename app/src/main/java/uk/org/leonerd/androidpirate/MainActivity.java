package uk.org.leonerd.androidpirate;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;


public class MainActivity extends ActionBarActivity {

    TextView txtStatus;
    BusPirate mPirate;

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
    }

    @Override
    protected void onStop() {
        if (mPirate != null) {
            mPirate.stop();
            mPirate = null;
        }

        super.onStop();
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
