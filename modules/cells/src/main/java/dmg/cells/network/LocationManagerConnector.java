package dmg.cells.network;

import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import dmg.cells.nucleus.CellAdapter;
import dmg.util.DummyStreamEngine;
import dmg.util.StreamEngine;

import org.dcache.alarms.AlarmMarkerFactory;
import org.dcache.alarms.PredefinedAlarm;
import org.dcache.util.Args;
import org.dcache.util.NDC;

public class LocationManagerConnector
    extends CellAdapter
    implements Runnable
{
    private static final Logger _log =
        LoggerFactory.getLogger("org.dcache.cells.network");

    private final String _domain;
    private final InetSocketAddress _address;
    private Thread _thread;
    private String _status = "disconnected";
    private int _retries;

    public LocationManagerConnector(String cellName, String args)
    {
        super(cellName, "System", args);
        Args a = getArgs();
        _domain = a.getOpt("domain");
        HostAndPort where = HostAndPort.fromString(a.getOpt("where"));
        _address = new InetSocketAddress(where.getHostText(), where.getPort());
    }

    @Override
    protected void started()
    {
        _thread = getNucleus().newThread(this, "TunnelConnector-" + _domain);
        _thread.start();
    }

    private synchronized void setStatus(String s)
    {
        _status = s;
    }

    private synchronized String getStatus()
    {
        return _status;
    }

    private StreamEngine connect()
        throws IOException, InterruptedException
    {
        setStatus("Connecting to " + _address);
        Socket socket;
        try {
            socket = SocketChannel.open(_address).socket();
        } catch (UnsupportedAddressTypeException e) {
            throw new IOException("Unsupported address type: " + _address, e);
        } catch (UnresolvedAddressException e) {
            throw new IOException("Unable to resolve " + _address, e);
        } catch (InterruptedIOException | ClosedByInterruptException e) {
            throw e;
        } catch (IOException e) {
            throw new IOException("Failed to connect to " + _address + ": " + e, e);
        }
        socket.setKeepAlive(true);

        _log.info("Using clear text channel");
        return new DummyStreamEngine(socket);
    }

    @Override
    public void run()
    {
        /* Thread for creating the tunnel. There is a grace period of
         * 4 to 20 seconds between connection attempts. The thread is
         * terminated by interrupting it.
         */
        Args args = getArgs();
        String name = getCellName() + '*';
        Random random = new Random();
        NDC.push(_address.toString());
        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    _retries++;

                    LocationMgrTunnel tunnel = new LocationMgrTunnel(name, connect(), args);
                    try {
                        tunnel.start().get();
                        _retries = 0;
                        setStatus("Connected");
                        getNucleus().join(tunnel.getCellName());
                    } finally {
                        getNucleus().kill(tunnel.getCellName());
                    }
                } catch (InterruptedIOException | ClosedByInterruptException e) {
                    throw e;
                } catch (ExecutionException | IOException e) {
                    _log.warn(AlarmMarkerFactory.getMarker(PredefinedAlarm.LOCATION_MANAGER_FAILURE,
                                                           name,
                                                           _domain,
                                                           e.getMessage()),
                              "Failed to connect to " + _domain + ": "
                                                      + e.getMessage());
                }

                setStatus("Sleeping");
                long sleep = random.nextInt(16000) + 4000;
                _log.warn("Sleeping " + (sleep / 1000) + " seconds");
                Thread.sleep(sleep);
            }
        } catch (InterruptedIOException | InterruptedException | ClosedByInterruptException ignored) {
        } finally {
            NDC.pop();
            setStatus("Terminated");
        }
    }

    public String toString()
    {
        return getStatus();
    }

    @Override
    public void getInfo(PrintWriter pw)
    {
        pw.println("Location manager connector : " + getCellName());
        pw.println("Status   : " + getStatus());
        pw.println("Retries  : " + _retries);
    }

    @Override
    public void stopped()
    {
        if (_thread != null) {
            _thread.interrupt();
            Uninterruptibles.joinUninterruptibly(_thread);
        }
    }
}
