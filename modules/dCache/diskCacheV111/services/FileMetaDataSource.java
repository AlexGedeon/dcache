/*
 * $Id: FileMetaDataSource.java,v 1.1 2006-11-07 10:40:44 tigran Exp $
 */
package diskCacheV111.services;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileMetaData;
import diskCacheV111.util.PnfsId;

public interface FileMetaDataSource
{
    /**
     *
     * @param path
     * @return metadata of the file specified by <i>path</i>
     * @throws CacheException
     */
    public FileMetaData getMetaData(String path) throws CacheException ;

    /**
     *
     * @param pnfsId
     * @return metadata of the file specified by <i>pnfsId</i>
     * @throws CacheException
     */
    public FileMetaData getMetaData(PnfsId pnfsId) throws CacheException ;
}
