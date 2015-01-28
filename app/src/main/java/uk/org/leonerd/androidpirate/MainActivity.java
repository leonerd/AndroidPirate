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
    UsbSerialPort mPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = (TextView) findViewById(R.id.txtStatus);
        txtStatus.setText("Starting");

        mPort = findBusPirate();
        if (mPort == null)
            return;
    }

    private UsbSerialPort findBusPirate(void) {
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
            byte buffer[] = new byte[20];

            // Start up the BusPirate into binary mode by writing twenty NULs to it
            port.write(buffer, 100);

            int len = port.read(buffer, 100);
            if(len < 4) {
                txtStatus.setText("Fewer than 4 bytes");
                return null;
            }

            if(buffer[0] == 'B' && buffer[1] == 'B' &&
                    buffer[2] == 'I' && buffer[3] == 'O') {
                txtStatus.setText("Recognised :)");
                return port;
            }
            else {
                txtStatus.setText("Not sure...");
            }
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
