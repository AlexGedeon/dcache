/*
 * FileMetaData.java
 *
 * Created on October 6, 2005, 1:58 PM
 */

package org.dcache.srm;

/**
 *
 * @author  timur
 */
public class FileMetaData extends diskCacheV111.srm.FileMetaData{
    public boolean isRegular = true;
    public boolean isDirectory =false ;
    public boolean isLink = false;
    public long creationTime=0;
    public long lastModificationTime=0;
    public long lastAccessTime=0;
    public long spaceTokens[]=null;
    /** Creates a new instance of FileMetaData */
    public FileMetaData() {
        super();
    }
    
    public FileMetaData(diskCacheV111.srm.FileMetaData fmd) {
        super(fmd);
        if( fmd instanceof FileMetaData ) {
            FileMetaData fmd1 = (FileMetaData)fmd;
            this.isRegular = fmd1.isRegular;
            this.isDirectory = fmd1.isDirectory;
            this.isLink = fmd1.isLink;
	    this.spaceTokens = new long[fmd1.spaceTokens.length];
	    System.arraycopy(fmd1.spaceTokens,0,this.spaceTokens,0,fmd1.spaceTokens.length);
        }
    }
}
