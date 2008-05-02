package dmg.cells.network;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.NotSerializableException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.HashMap;


import org.apache.log4j.Logger;

import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.CellExceptionMessage;
import dmg.cells.nucleus.CellDomainInfo;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellNucleus;
import dmg.cells.nucleus.CellRoute;
import dmg.cells.nucleus.CellTunnel;
import dmg.cells.nucleus.CellTunnelInfo;
import dmg.cells.nucleus.MessageEvent;
import dmg.cells.nucleus.NoRouteToCellException;
import dmg.cells.nucleus.RoutedMessageEvent;
import dmg.util.Args;
import dmg.util.Gate;
import dmg.util.StreamEngine;

/**
 *
 *
 * @author Patrick Fuhrmann
 * @version 0.1, 5 Mar 2001
 */
public class LocationMgrTunnel
    extends CellAdapter
    implements CellTunnel, Runnable
{
    /**
     * This class encapsulates routing table management. It ensures
     * that at most one tunnel to any given domain is registered at a
     * time.
     *
     * It is assumed that all tunnels share the same cell glue (this
     * is normally the case for cells in the same domain).
     */
    static class Tunnels
    {
        private Map<String,LocationMgrTunnel> _tunnels =
            new HashMap<String,LocationMgrTunnel>();

        /**
         * Adds a new tunnel. A route for the tunnel destination is
         * registered in the CellNucleus. The same tunnel cannot be
         * registered twice; unregister it first.
         *
         * If another tunnel is already registered for the same
         * destination, then the other tunnel is killed.
         */
        public synchronized void add(LocationMgrTunnel tunnel)
            throws InterruptedException
        {
            CellNucleus nucleus = tunnel.getNucleus();

            if (_tunnels.containsValue(tunnel))
                throw new IllegalArgumentException("Cannot register the same tunnel twice");

            String domain = tunnel.getRemoteDomainName();

            /* Kill old tunnel first.
             */
            LocationMgrTunnel old;
            while ((old = _tunnels.get(domain)) != null) {
                old.kill();
                wait();
            }

            /* Add new route.
             */
            CellRoute route = new CellRoute(domain,
                                            tunnel.getCellName(),
                                            CellRoute.DOMAIN);
            try {
                nucleus.routeAdd(route);
            } catch (IllegalArgumentException e) {
                /* Crap, somehow the entry already exists. Well, we
                 * insist on adding a new one, so we delete the old
                 * one first.
                 */
                nucleus.routeDelete(route);
                nucleus.routeAdd(route);
            }

            /* Keep track of what we did.
             */
            _tunnels.put(domain, tunnel);
            notifyAll();
        }

        /**
         * Removes a tunnel and unregisters its routes. If the tunnel
         * was already removed, then nothing happens.
         *
         * It is crucial that the <code>_remoteDomainInfo</code> of
         * the tunnel does not change between the point at which it is
         * added and the point at which it is removed.
         */
        public synchronized void remove(LocationMgrTunnel tunnel)
        {
            CellNucleus nucleus = tunnel.getNucleus();
            String domain = tunnel.getRemoteDomainName();
            if (_tunnels.get(domain) == tunnel) {
                _tunnels.remove(domain);
                nucleus.routeDelete(new CellRoute(domain,
                                                  tunnel.getCellName(),
                                                  CellRoute.DOMAIN));
                notifyAll();
            }
        }
    }

    /**
     * We use a single shared instance of Tunnels to coordinate route
     * creation between tunnels.
     */
    private final static Tunnels _tunnels = new Tunnels();

    private final static Logger _logMessages =
        Logger.getLogger("logger.org.dcache.cells.messages");

    private final CellNucleus  _nucleus;

    private final CellDomainInfo  _remoteDomainInfo;
    private final Socket _socket;
    private final ObjectInputStream  _input;
    private final ObjectOutputStream _output;

    private boolean _down = false;

    //
    // some statistics
    //
    private int  _messagesToTunnel    = 0;
    private int  _messagesToSystem    = 0;

    public LocationMgrTunnel(String cellName, StreamEngine engine, Args args)
        throws IOException
    {
        super(cellName, "System", args, false);

        _nucleus = getNucleus();
        _socket = engine.getSocket();
        _output = new ObjectOutputStream(engine.getOutputStream());
        _input = new ObjectInputStream(new BufferedInputStream(engine.getInputStream()));
        _remoteDomainInfo = negotiateDomainInfo(_output, _input);

        getNucleus().newThread(this, "Tunnel").start();

        say("Established tunnel to " + getRemoteDomainName());

        start();
    }

    private CellDomainInfo negotiateDomainInfo(ObjectOutputStream out,
                                               ObjectInputStream in)
        throws IOException
    {
        try  {
            out.writeObject(_nucleus.getCellDomainInfo());
            out.flush();

            Object obj = in.readObject();
            if (obj == null)
                throw new IOException("EOS encountered while reading DomainInfo");
            return (CellDomainInfo)obj;
        } catch (ClassNotFoundException e) {
            throw new IOException("Cannot deserialize object. This is most likely due to a version mismatch.");
        }
    }

    synchronized private void setDown(boolean down)
    {
        _down = down;
        notifyAll();
    }

    synchronized private boolean isDown()
    {
        return _down;
    }

    private void logSend(CellMessage msg)
    {
        if (_logMessages.isDebugEnabled()) {
            Object object = msg.getMessageObject();
            String messageObject =
                object == null ? "NULL" : object.getClass().getName();
            _logMessages.debug("tunnelMessageArrived src="
                               + msg.getSourceAddress()
                               + " dest=" + msg.getDestinationAddress()
                               + " [" + messageObject + "] UOID="
                               + msg.getUOID().toString());
        }
    }

    private void logReceive(CellMessage msg)
    {
        if (_logMessages.isDebugEnabled()) {
            String messageObject =
                msg.getMessageObject() == null
                ? "NULL"
                : msg.getMessageObject().getClass().getName();

            _logMessages.debug("tunnelSendMessage src="
                               + msg.getSourceAddress()
                               + " dest=" + msg.getDestinationAddress()
                               + " [" + messageObject + "] UOID="
                               + msg.getUOID().toString());
        }
    }

    private void returnToSender(CellMessage msg, NoRouteToCellException e)
    {
        try {
            if (!(msg instanceof CellExceptionMessage)) {
                CellPath retAddr = (CellPath)msg.getSourcePath().clone();
                retAddr.revert();
                CellExceptionMessage ret = new CellExceptionMessage(retAddr, e);
                ret.setLastUOID(msg.getUOID());
                _nucleus.sendMessage(ret);
            }
        } catch (NotSerializableException f) {
            throw new RuntimeException("Bug: Unserializable vehicle detected.", f);
        } catch (NoRouteToCellException f) {
            esay("Unable to deliver message and unable to return it to sender: " + msg);
        }
    }

    private void receive()
        throws InterruptedException, IOException, ClassNotFoundException
    {
        CellMessage msg;
        while ((msg = (CellMessage)_input.readObject()) != null) {
            logReceive(msg);

            try {
                sendMessage(msg);
                _messagesToSystem++;
            } catch (NoRouteToCellException e) {
                returnToSender(msg, e);
            } catch (NotSerializableException e) {
                /* Ouch, the object we just deserialized could not
                 * be serialized. This should not happen, so if it
                 * does this is clearly a bug.
                 */
                throw new RuntimeException("Bug: Unserializable vehicle detected", e);
            }
        }
    }

    synchronized private void send(CellMessage msg)
        throws IOException
    {
        if (isDown())
            throw new IOException("Tunnel has been shut down.");

        logSend(msg);
        _output.writeObject(msg);
        _output.flush();
        _output.reset();
        _messagesToTunnel++;
    }

    public void run()
    {
        if (isDown())
            throw new IllegalStateException("Tunnel has already been closed");

        try {
            _tunnels.add(this);
            try {
                receive();
            } catch (EOFException e) {
            } catch (IOException e) {
                esay("Error while reading from tunnel: " + e.getMessage());
            } catch (ClassNotFoundException e) {
                esay("Cannot deserialize object. This is most likely due to a version mismatch.");
            } finally {
                _tunnels.remove(this);
                kill();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void messageArrived(MessageEvent me)
    {
        if (me instanceof RoutedMessageEvent) {
            CellMessage msg = me.getMessage();
            try {
                send(msg);
            } catch (IOException e) {
                esay("Error while sending message: " + e.getMessage());
                returnToSender(msg, 
                               new NoRouteToCellException("Communication failure. Message could not be delivered."));
                kill();
            }
        } else {
            super.messageArrived(me);
        }
    }

    public CellTunnelInfo getCellTunnelInfo()
    {
        return new CellTunnelInfo(getCellName(),
                                  _nucleus.getCellDomainInfo(),
                                  _remoteDomainInfo);
    }

    protected String getRemoteDomainName()
    {
        return _remoteDomainInfo.getCellDomainName();
    }

    public String toString()
    {
        return "Connected to " + getRemoteDomainName();
    }

    public void getInfo(PrintWriter pw)
    {
        pw.println("Location Mgr Tunnel : " + getCellName());
        pw.println("-> Tunnel     : " + _messagesToTunnel);
        pw.println("-> Domain     : " + _messagesToSystem);
        pw.println("Peer          : " + getRemoteDomainName());
    }

    public synchronized void cleanUp()
    {
        say("Closing tunnel to " + getRemoteDomainName());
        setDown(true);
        try {
            _socket.shutdownInput();
            _socket.close();
        } catch (IOException e) {
            esay("Failed to close socket: " + e.getMessage());
        }
    }

    public synchronized void join() throws InterruptedException
    {
        while (!isDown()) {
            wait();
        }
    }
}
