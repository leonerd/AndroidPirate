package uk.org.leonerd.androidpirate;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by leo on 28/01/15.
 */
public class BusPirate {
    private static final int BUFSIZ = 4096;

    private UsbSerialPort mPort;

    private final ByteBuffer mReadBuf = ByteBuffer.allocate(BUFSIZ);
    private final ByteBuffer mWriteBuf = ByteBuffer.allocate(BUFSIZ);

    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final BusPirateIOLoop mLoop = new BusPirateIOLoop();

    public BusPirate(UsbSerialPort port) throws IOException {
        mPort = port;

        mReadBuf.clear();
        mWriteBuf.clear();

        mExecutor.execute(mLoop);

        // Start up the BusPirate into binary mode by writing twenty NULs to it
        write(new byte[20]);

        try {
            byte[] buffer = readExactly(5);

            if (buffer[0] == 'B' && buffer[1] == 'B' &&
                    buffer[2] == 'I' && buffer[3] == 'O' &&
                    buffer[4] == '1')
                // all is good
                ;
            else
                throw new IOException("Not a Bus Pirate");
        } catch (InterruptedException e) {
            throw new IOException("Read interrupted");
        }
    }

    public void stop() {
        mLoop.stop();
    }

    protected void write(byte[] src) {
        synchronized (mWriteBuf) {
            mWriteBuf.put(src);
        }
    }

    protected byte[] readExactly(int len) throws InterruptedException {
        byte[] dest = new byte[len];

        synchronized (mReadBuf) {
            while (mReadBuf.position() < len)
                mReadBuf.wait();

            Log.d("MYTAG", "readExactly can get up to " + mReadBuf.position() + " bytes");

            mReadBuf.flip();
            mReadBuf.get(dest, 0, len);

            if(mReadBuf.limit() > mReadBuf.position()) {
                Log.d("MYTAG", "TODO: Need to preserve the last bits of data limit=" + mReadBuf.limit() + " pos=" + mReadBuf.position());
                mReadBuf.compact();
            }
            else {
                mReadBuf.clear();
            }
        }

        return dest;
    }

    private class BusPirateIOLoop implements Runnable {
        private static final int WRITE_TIMEOUT = 100;
        private static final int READ_TIMEOUT = 100;

        private boolean mRunning = true;

        @Override
        public void run() {
            while (mRunning) {
                try {
                    tick();
                } catch (IOException e) {
                }
            }
        }

        public void stop() {
            mRunning = false;
        }

        private void tick() throws IOException {
            byte[] toWrite = null;
            synchronized(mWriteBuf) {
                int len = mWriteBuf.position();
                if (len > 0) {
                    toWrite = new byte[len];
                    mWriteBuf.rewind();
                    mWriteBuf.get(toWrite, 0, len);
                    mWriteBuf.clear();
                }
            }
            if (toWrite != null)
                mPort.write(toWrite, WRITE_TIMEOUT);

            byte[] read = new byte[1024];
            int len = mPort.read(read, READ_TIMEOUT);
            if (len > 0) {
                synchronized(mReadBuf) {
                    mReadBuf.put(read, 0, len);
                    mReadBuf.notify();
                }
            }
        }
    }
}
