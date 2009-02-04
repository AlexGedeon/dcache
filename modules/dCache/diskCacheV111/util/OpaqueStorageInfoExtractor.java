/**
 * 
 */
package diskCacheV111.util;

import java.io.File;
import java.net.URI;

import diskCacheV111.namespace.StorageInfoProvider;
import diskCacheV111.vehicles.StorageInfo;

/**
 * The OpaqueStorageInfoExtractor assumes that some data is written to
 * a PNFS level when a file is stored to tape and that that level is empty
 * otherwise.
 * 
 * No attempt is made to understand the format of this information.  We assume
 * that the HSM script will parse any required information itself.
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class OpaqueStorageInfoExtractor extends OsmInfoExtractor {
	
	private static final URI DUMMY_URI = URI.create("dummy://unknown-HSM/unknown-path");

	// FIXME: this should be configurable.
	private final int _pnfsLevel = 3;
	

	/**
	 * Extract a StorageInfo object describing this file.
	 * 
	 * Since the format is opaque, the only thing we can know is whether the file is stored or not.
	 * 
	 * @see diskCacheV111.util.StorageInfoExtractable#getStorageInfo(java.lang.String, diskCacheV111.util.PnfsId)
	 */
	@Override
	public StorageInfo getStorageInfo(String pnfsMountpoint, PnfsId pnfsId)
			throws CacheException {
		
        PnfsFile pnfsFile = PnfsFile.getFileByPnfsId( pnfsMountpoint , pnfsId );
        
        if( pnfsFile == null )
           throw new CacheException( 37 , "Not a valid PnfsId "+pnfsId ) ;
		
		StorageInfo info = extractDirectory( pnfsMountpoint, pnfsFile);
		
        File levelFile = pnfsFile.getLevelFile( _pnfsLevel);
        
        if( levelFile.length() > 0)
        	info.addLocation( DUMMY_URI);
		
		return info;
	}

	/**
	 * Since the format is opaque, no StorageInfo data may be stored.  Attempting to store a StorageInfo
	 * object will either be ignored or could throw an exception.
	 * 
	 * However, the access-latency and retention-policy must be stored in level-2, so we do that.
	 * 
	 * @see diskCacheV111.util.StorageInfoExtractable#setStorageInfo(java.lang.String, diskCacheV111.util.PnfsId, diskCacheV111.vehicles.StorageInfo, int)
	 */
	@Override
	public void setStorageInfo(String pnfsMountpoint, PnfsId pnfsId,
			StorageInfo storageInfo, int accessMode) throws CacheException {

        PnfsFile pnfsFile = PnfsFile.getFileByPnfsId( pnfsMountpoint, pnfsId );
        
        if( pnfsFile == null )
        	throw new CacheException( 107 , "Not a valid PnfsId "+pnfsId ) ;

		storeAlRpInLevel2( storageInfo, pnfsFile);

        // Silently ignore attempts to store StorageInfo object, unless such access is prohibited.
        switch( accessMode ) {
        	case  StorageInfoProvider.SI_EXCLUSIVE :
        		File levelFile = pnfsFile.getLevelFile( _pnfsLevel);
        		if( levelFile.length() > 0 )
        			throw new CacheException( 38 , "File already exits (can't overwrite mode=0)" );
        		break;
        		
        	case StorageInfoProvider.SI_APPEND :
        	case StorageInfoProvider.SI_OVERWRITE :
	        	  // Silently ignore requests to write metadata.
        		break;
        		
        	default :
	             throw new CacheException( 39 , "Illegal Access Mode : "+accessMode ) ;
        }

	}

}
