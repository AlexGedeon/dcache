package org.dcache.util.list;

import java.util.Set;
import java.io.File;
import diskCacheV111.util.CacheException;
import org.dcache.namespace.FileAttribute;
import org.dcache.util.Glob;
import org.dcache.util.Interval;

/**
 * Interface for components that can providing a directory listing.
 *
 * Should be merged with PnfsHandler or the new client lib for
 * PnfsManager.
 */
public interface DirectoryListSource
{
    /**
     * Lists the content of a directory. The content is returned as a
     * directory stream. An optional glob pattern and an optional
     * zero-based range can be used to limit the listing.
     *
     * @param path Path to directory to list
     * @param glob Glob to limit the result set; may be null
     * @param range The range of entries to return; may be null
     * @return A DirectoryStream of the entries in the directory
     * @see #list(File path, Glob pattern, Interval range, Set<FileAttribute> attrs)
     */
    DirectoryStream list(File path, Glob pattern, Interval range)
        throws InterruptedException, CacheException;

    /**
     * Lists the content of a directory. The content is returned as a
     * directory stream. An optional glob pattern and an optional
     * zero-based range can be used to limit the listing.
     *
     * The glob syntax is limitted to single character (question mark)
     * and multi character (asterix) wildcards. If glob is null, then
     * no filtering is applied.
     *
     * When a range is specified, only the part of the result set that
     * falls within the range is return. There is no guarantee that
     * the result set from two invocations is the same. For instance,
     * there is no guarantee that first listing [0;999] and then
     * listing [1000;1999] will actually cover the first 2000 entries:
     * Files may have been added or deleted from the directory, or the
     * ordering may have changed for some reason.
     *
     * @param path Path to directory to list
     * @param glob Glob to limit the result set; may be null
     * @param range The range of entries to return; may be null
     * @param attrs The file attributes to query for each entry
     * @return A DirectoryStream of the entries in the directory
     */
    DirectoryStream list(File path, Glob pattern, Interval range,
                         Set<FileAttribute> attrs)
        throws InterruptedException, CacheException;

    /**
     * Prints a file using a DirectoryListPrinter.
     *
     * @param printer The ListPrinter used to print the directory entry
     * @param path The path to the entry to print
     */
    void printFile(DirectoryListPrinter printer, File path)
        throws InterruptedException, CacheException;

    /**
     * Prints the entries of a directory using a DirectoryListPrinter.
     *
     * @param printer The DirectoryListPrinter used to print the
     *        directory content
     * @param path The path to the directory to print
     * @param glob An optional Glob used to filter which entries to
     *        print
     * @param range An optional interval used to filter which entries
     *        to print
     * @return The number of entries in the directory
     */
    int printDirectory(DirectoryListPrinter printer, File path, Glob glob, Interval range)
        throws InterruptedException, CacheException;
}