package dmg.cells.nucleus;

import java.io.NotSerializableException;

public class DelayedReply implements Reply
{
    private CellEndpoint _endpoint;
    private CellMessage _envelope;

    public synchronized void deliver(CellEndpoint endpoint, CellMessage envelope)
    {
        if (endpoint == null || envelope == null)
            throw new IllegalArgumentException("Arguments must not be null");
        _endpoint = endpoint;
        _envelope = envelope;
        notifyAll();
    }

    public synchronized void send(Object msg)
        throws NotSerializableException,
               NoRouteToCellException,
               InterruptedException
    {
        while (_endpoint == null || _envelope == null)
            wait();
        _envelope.setMessageObject(msg);
        _endpoint.sendMessage(_envelope);
    }
}