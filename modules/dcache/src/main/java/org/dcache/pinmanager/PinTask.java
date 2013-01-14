package org.dcache.pinmanager;

import java.util.Date;
import javax.security.auth.Subject;

import org.dcache.cells.MessageReply;
import org.dcache.pinmanager.model.Pin;
import org.dcache.vehicles.FileAttributes;

import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.PoolMgrSelectReadPoolMsg;
import diskCacheV111.util.PnfsId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PinTask
{
    private final static Logger _log = LoggerFactory.getLogger(PinTask.class);

    private PinManagerPinMessage _request;
    private MessageReply<PinManagerPinMessage> _reply;
    private Pin _pin;
    private PoolMgrSelectReadPoolMsg.Context _readPoolSelectionContext;

    public PinTask(PinManagerPinMessage request, MessageReply<PinManagerPinMessage> reply, Pin pin)
    {
        _request = request;
        _reply = reply;
        _pin = pin;
    }

    public Pin getPin()
    {
        return _pin;
    }

    public void setPin(Pin pin)
    {
        _pin = pin;
    }

    public boolean isValidIn(long delay)
    {
        return _reply.isValidIn(delay);
    }

    public PnfsId getPnfsId()
    {
        return _request.getFileAttributes().getPnfsId();
    }

    public FileAttributes getFileAttributes()
    {
        return _request.getFileAttributes();
    }

    public void setFileAttributes(FileAttributes attributes)
    {
        _request.setFileAttributes(attributes);
    }

    public ProtocolInfo getProtocolInfo()
    {
        return _request.getProtocolInfo();
    }

    public Subject getSubject()
    {
        return _request.getSubject();
    }

    public String getRequestId()
    {
        return _request.getRequestId();
    }

    public long getLifetime()
    {
        return _request.getLifetime();
    }

    public long getPinId()
    {
        return _pin.getPinId();
    }

    public String getPool()
    {
        return _pin.getPool();
    }

    public String getSticky()
    {
        return _pin.getSticky();
    }

    public PoolMgrSelectReadPoolMsg.Context getReadPoolSelectionContext()
    {
        return _readPoolSelectionContext;
    }

    public void setReadPoolSelectionContext(PoolMgrSelectReadPoolMsg.Context context)
    {
        _readPoolSelectionContext = context;
    }

    public Date freezeExpirationTime()
    {
        long now = System.currentTimeMillis();
        long lifetime = getLifetime();
        Date date = (lifetime == -1) ? null : new Date(now + lifetime);
        _request.setExpirationTime(date);
        return date;
    }

    public Date getExpirationTime()
    {
        return _request.getExpirationTime();
    }

    public void fail(int rc, String error)
    {
        _reply.fail(_request, rc, error);
        _log.warn("Failed to pin {}: {} [{}]",
                  new Object[] { _pin.getPnfsId(), error, rc });
    }

    public void success()
    {
        _request.setPin(_pin);
        _reply.reply(_request);
        _log.info("Pinned {} on {} ({})",
                  new Object[] { _pin.getPnfsId(), _pin.getPool(), _pin.getPinId() });
    }
}