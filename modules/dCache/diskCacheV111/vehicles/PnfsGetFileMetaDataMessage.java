// $Id: PnfsGetFileMetaDataMessage.java,v 1.4 2004-11-05 12:07:19 tigran Exp $
package diskCacheV111.vehicles ;

import diskCacheV111.util.FileMetaData;
import diskCacheV111.util.PnfsId;
import java.util.Set;
import org.dcache.util.Checksum;
import org.dcache.vehicles.PnfsGetFileAttributes;
import org.dcache.vehicles.FileAttributes;
import org.dcache.namespace.FileAttribute;

import static org.dcache.namespace.FileAttribute.*;

public class PnfsGetFileMetaDataMessage extends PnfsGetFileAttributes
{
    private FileMetaData _metaData    = null ;
    private boolean      _resolve     = true ;
    private boolean _checksumsRequested = false ;

    private Set<Checksum> _checksums = null;

    private static final long serialVersionUID = 1591894346369251468L;

    public PnfsGetFileMetaDataMessage()
    {
        super((PnfsId) null, FileMetaData.getKnownFileAttributes());
        setReplyRequired(true);
    }

    public PnfsGetFileMetaDataMessage(String pnfsId)
    {
        super(new PnfsId(pnfsId), FileMetaData.getKnownFileAttributes());
	setReplyRequired(true);
    }

    public PnfsGetFileMetaDataMessage(PnfsId pnfsId)
    {
        super(pnfsId, FileMetaData.getKnownFileAttributes());
	setReplyRequired(true);
    }

    @Override
    public void setFileAttributes(FileAttributes fileAttributes)
    {
        super.setFileAttributes(fileAttributes);

        /* For backwards compatibility with old versions we set these
         * fields. We do this even though we don't use these fields.
         */
        _metaData = new FileMetaData(fileAttributes);
        if (fileAttributes.isDefined(CHECKSUM)) {
            _checksums = fileAttributes.getChecksums();
        }
    }

    public FileMetaData getMetaData()
    {
        return
            (_fileAttributes == null)
            ? null
            : new FileMetaData(_fileAttributes);
    }

    public void setResolve( boolean resolve ){ _resolve = resolve ; }
    public boolean resolve(){ return _resolve ; }

    public Set<Checksum> getChecksums()
    {
        return
            (_fileAttributes == null || !_fileAttributes.isDefined(CHECKSUM))
            ? null
            : _fileAttributes.getChecksums();
    }

    public boolean isChecksumsRequested() {
        return _checksumsRequested;
    }

    public void setChecksumsRequested(boolean checksumsRequested)
    {
        _checksumsRequested = checksumsRequested;
        if (checksumsRequested) {
            _attributes.add(CHECKSUM);
        } else {
            _attributes.remove(CHECKSUM);
        }
    }

    public void requestChecksum()
    {
        _checksumsRequested = true;
        _attributes.add(CHECKSUM);
    }

    /* To ensure backwards compatibility with pre 1.9.6 clients, we
     * explicitly add attributes compatible with
     * PnfsGetFileMetaDataMessage to the set of requested attributes
     * if the attribute set is null.
     */
    @Override
    public Set<FileAttribute> getRequestedAttributes()
    {
        Set<FileAttribute> attributes = _attributes;
        if (attributes == null) {
            attributes = FileMetaData.getKnownFileAttributes();
            if (_checksumsRequested) {
                attributes.add(CHECKSUM);
            }
        }
        return attributes;
    }

    @Override
    public boolean invalidates(Message message)
    {
        return false;
    }

    @Override
    public boolean isSubsumedBy(Message message)
    {
        if (message.getClass().equals(PnfsGetFileMetaDataMessage.class)) {
            PnfsId pnfsId = getPnfsId();
            String path = getPnfsPath();
            PnfsGetFileMetaDataMessage other =
                (PnfsGetFileMetaDataMessage) message;
            return
                other.resolve() == resolve() &&
                (pnfsId == null || pnfsId.equals(other.getPnfsId())) &&
                (path == null || path.equals(other.getPnfsPath())) &&
                (!isChecksumsRequested() || other.isChecksumsRequested());
        }

        return false;
    }

    @Override
    public boolean isIdempotent()
    {
        return true;
    }
}
