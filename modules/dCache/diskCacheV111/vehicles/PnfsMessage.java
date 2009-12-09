//$Id: PnfsMessage.java,v 1.5 2004-11-05 12:07:19 tigran Exp $

package diskCacheV111.vehicles;
import  diskCacheV111.util.PnfsId ;
import org.dcache.acl.enums.AccessMask;
import java.util.Set;
import java.util.Collections;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Base class for messages to PnfsManager.
 */
public class PnfsMessage extends Message {

    private PnfsId _pnfsId = null;
    private String _path   = null ;
    private Set<AccessMask> _mask = Collections.emptySet();

    private static final long serialVersionUID = -3686370854772807059L;

    public PnfsMessage(PnfsId pnfsId){
	_pnfsId = pnfsId ;
    }

    public PnfsMessage(){ }

    public void setPnfsPath( String pnfsPath ){ _path = pnfsPath ; }
    public String getPnfsPath(){ return _path ;}

    public PnfsId getPnfsId(){
	return _pnfsId;
    }

    public void setPnfsId(PnfsId pnfsId){
	_pnfsId = pnfsId ;
    }

    public void setAccessMask(Set<AccessMask> mask)
    {
        if (mask == null) {
            throw new IllegalArgumentException("Null argument not allowed");
        }
        _mask = mask;
    }

    public Set<AccessMask> getAccessMask()
    {
        return _mask;
    }

    public String toString(){
        return _pnfsId==null?
               (_path==null?"NULL":("Path="+_path)):
               ("PnfsId="+_pnfsId.toString()) ;
    }

    @Override
    public boolean invalidates(Message message)
    {
        if (message instanceof PnfsMessage) {
            PnfsMessage msg = (PnfsMessage) message;
            if (getPnfsId() != null && msg.getPnfsId() != null &&
                !getPnfsId().equals(msg.getPnfsId())) {
                return false;
            }

            if (getPnfsPath() != null && msg.getPnfsPath() != null &&
                !getPnfsPath().equals(msg.getPnfsPath())) {
                return false;
            }
        }
        return true;
    }

    /**
     * For compatibility with pre-1.9.6 installations, we fill in the
     * _mask field if it is missing.
     */
    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        if (_mask == null) {
            _mask = Collections.emptySet();
        }
    }
}



