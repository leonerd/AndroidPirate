package uk.org.leonerd.androidpirate;

import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

    public void setPower(boolean on) throws InterruptedException, BusPirateException {
        write((byte)(0x40 | (on ? 0x08 : 0)));
        readExpectingAck();
    }

    public void enterI2CMode() throws InterruptedException {
        write((byte) 0x02);
        readExactly(4);
    }

    protected void i2cStartBit() throws InterruptedException, BusPirateException {
        write((byte) 0x02);
        readExpectingAck();
    }

    protected void i2cStopBit() throws InterruptedException, BusPirateException {
        write((byte) 0x03);
        readExpectingAck();
    }

    protected void i2cWrite(byte[] src) throws InterruptedException, BusPirateException {
        int pos = 0;
        while (pos < src.length) {
            int chunkLen = 16;
            if (src.length - pos < chunkLen)
                chunkLen = src.length - pos;

            write((byte) (0x10 + chunkLen - 1));
            readExpectingAck();

            int chunkEnd = pos + chunkLen;
            for (; pos < chunkEnd; pos++) {
                write(src[pos]);
                readExpecting(1, new byte[]{0x00});
            }
        }
    }

    protected byte[] i2cRead(int len) throws InterruptedException, BusPirateException {
        byte[] ret = new byte[len];

        for (int pos = 0; pos < len; pos++) {
            write((byte) 0x04);
            byte[] b = readExactly(1);
            ret[pos] = b[0];
            if (pos < len - 1)
                write((byte) 0x06); // ACK
            else
                write((byte) 0x07); // NACK
            readExpectingAck();
        }

        return ret;
    }

    public synchronized void i2cSend(int addr, byte[] src) throws InterruptedException,
            BusPirateException {
        Log.d("BUSPIRATE", "I2C SEND to " + addr + ": " + Arrays.toString(src));
        i2cStartBit();
        i2cWrite(new byte[]{(byte)(addr << 1 | 0)});
        i2cWrite(src);
        i2cStopBit();
    }

    public synchronized byte[] i2cSendThenRecv(int addr, byte[] src, int recvLen) throws
            InterruptedException, BusPirateException {
        Log.d("BUSPIRATE", "I2C SEND-then-RECV to " + addr + ": " + Arrays.toString(src));
        i2cStartBit();
        i2cWrite(new byte[]{(byte)(addr << 1 | 0)});
        i2cWrite(src);
        i2cStartBit();
        i2cWrite(new byte[]{(byte)(addr << 1 | 1)});
        byte[] ret = i2cRead(recvLen);
        i2cStopBit();

        Log.d("BUSPIRATE", "  RECVed: " + Arrays.toString(ret));
        return ret;
    }

    protected void write(byte b) {
        write(new byte[]{b});
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

            mReadBuf.flip();
            mReadBuf.get(dest, 0, len);

            if (mReadBuf.limit() > mReadBuf.position()) {
                mReadBuf.compact();
            } else {
                mReadBuf.clear();
            }
        }

        return dest;
    }

    protected void readExpecting(int len, byte[] expectation) throws InterruptedException,
            BusPirateException {
        byte[] ret = readExactly(len);

        for (int i = 0; i < len; i++) {
            if (ret[i] != expectation[i])
                throw new BusPirateException("Expected " + expectation[i] + " got " + ret[i]);
        }
    }

    protected void readExpectingAck() throws InterruptedException, BusPirateException {
        readExpecting(1, new byte[]{0x01});
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
                    break;
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

    public class BusPirateException extends Exception {
        public BusPirateException(String msg) {
            super(msg);
        }
    }
}
