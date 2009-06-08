package org.dcache.pool.repository;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;
import diskCacheV111.vehicles.StorageInfo;

import java.io.File;
import java.util.List;

public interface MetaDataRecord
{
    /**
     * Get the PnfsId of this entry.
     */
    public PnfsId getPnfsId();

    /**
     * Set the size of this entry. An entry has a size which normally
     * corresponds to the size of the file on disk. While the file is
     * created there may be a mismatch between the entry size and the
     * physical size.
     *
     * For a healthy entry and complete, the entry size will match the
     * file size stored in PNFS. For broken entries or while the file
     * is created, the two may not match.
     *
     * The size stored in the entries StorageInfo record is a cached
     * copy of the size stored in PNFS.
     */
    public void setSize(long size);

    /**
     * Get the size of this entry. May be different from the size of
     * the on-disk file.
     */
    public long getSize();

    public void setStorageInfo(StorageInfo storageInfo)
        throws CacheException;

    public StorageInfo getStorageInfo()
        throws CacheException;

    public void setState(EntryState state)
        throws CacheException;

    public EntryState getState();

    public File getDataFile()
        throws CacheException;

    public long getCreationTime();

    public long getLastAccessTime();

    public void touch() throws CacheException;

    public void decrementLinkCount() throws CacheException;

    public void incrementLinkCount() throws CacheException;

    public int getLinkCount();

    /**
     * Returns true if and only if the entry has one or more sticky
     * flags. Whether the sticky flags have expired or not MUST not
     * influence the return value.
     *
     * @return true if the stickyRecords methods would return a non
     * empty collection, false otherwise.
     */
    public boolean isSticky();

    /**
     * Removes expired sticky from the entry.
     *
     * @return The expired sticky flags removed from the record.
     */
    public List<StickyRecord> removeExpiredStickyFlags();

    /**
     * Set sticky flag for a given owner and time. There is at most
     * one flag per owner. If <code>overwrite</code> is true, then an
     * existing record for <code>owner</code> will be replaced. If it
     * is false, then the lifetime of an existing record will be
     * extended if and only if the new lifetime is longer.
     *
     * A lifetime of -1 indicates that the flag never expires. A
     * lifetime set in the past, for instance 0, expires immediately.
     *
     * @param owner flag owner
     * @param validTill time milliseconds since 00:00:00 1 Jan. 1970.
     * @param overwrite replace existing flag when true.
     * @throws CacheException
     * @return true if the collection returned by the stickyRecords
     * method has changed due to this call.
     */
    public boolean setSticky(String owner, long validTill, boolean overwrite)
        throws CacheException;

    /**
     *
     * @return list of StickyRecords held by the file
     */
    public List<StickyRecord> stickyRecords();
}