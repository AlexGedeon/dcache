package diskCacheV111.vehicles;

import diskCacheV111.util.PnfsId;

public class StorageInfoMessage extends PnfsFileInfoMessage
{
    private long _transferTime;

    private static final long serialVersionUID = -4601114937008749384L;

    public StorageInfoMessage(String cellName, PnfsId pnfsId, boolean restore)
    {
        super(restore ? "restore" : "store", "pool", cellName, pnfsId);
    }

    public void setTransferTime(long transferTime)
    {
        _transferTime = transferTime;
    }

    public long getTransferTime()
    {
        return _transferTime;
    }

    public String toString()
    {
        return getInfoHeader() + " " +
               getFileInfo() + " " +
               _transferTime + " " +
               getTimeQueued() + " " +
               getResult();
    }

    @Override
    public void accept(InfoMessageVisitor visitor)
    {
        visitor.visit(this);
    }
}
