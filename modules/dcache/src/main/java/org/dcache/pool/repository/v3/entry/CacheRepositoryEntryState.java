package org.dcache.pool.repository.v3.entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dcache.pool.repository.EntryState;
import org.dcache.pool.repository.MetaDataRecord;
import org.dcache.pool.repository.StickyRecord;
import org.dcache.pool.repository.v3.entry.state.Sticky;

public class CacheRepositoryEntryState
{
    // new logger concept
    private static Logger _logBussiness = LoggerFactory.getLogger("logger.org.dcache.repository");

    private static final Pattern VERSION_PATTERN =
        Pattern.compile("#\\s+version\\s+[0-9]\\.[0-9]");

    // format version
    private static final int FORMAT_VERSION_MAJOR = 3;
    private static final int FORMAT_VERSION_MINOR = 0;

    // possible states of entry in the repository

    private final Sticky _sticky = new Sticky();
    private EntryState _state;

    // data, control and SI- files locations
    private final File _controlFile;

    public CacheRepositoryEntryState(File controlFile) throws IOException {
        _controlFile = controlFile;
        _state = EntryState.NEW;

        // read state from file
        try {
            loadState();
        }catch( FileNotFoundException fnf) {
            /*
             * it's not an error state.
             */
        }
    }

    /**
     * Copy state from existing MetaDataRecord.
     */
    public CacheRepositoryEntryState(File controlFile, MetaDataRecord entry)
        throws IOException
    {
        _controlFile = controlFile;
        _state = entry.getState();

        for (StickyRecord record: entry.stickyRecords()) {
            _sticky.addRecord(record.owner(), record.expire(), true);
        }

        makeStatePersistent();
    }

    public List<StickyRecord> removeExpiredStickyFlags()
    {
        List<StickyRecord> removed = _sticky.removeExpired();
        try {
            if (!removed.isEmpty()) {
                makeStatePersistent();
            }
        } catch (IOException e) {
            _logBussiness.error("Failed to store repository state: " +
                                e.getMessage());
        }
        return removed;
    }

    public void setState(EntryState state)
        throws IOException
    {
        if (state == _state) {
            return;
        }

        switch (state) {
        case NEW:
            throw new IllegalStateException("Entry is " + _state);
        case FROM_CLIENT:
            if (_state != EntryState.NEW) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case FROM_STORE:
            if (_state != EntryState.NEW) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case FROM_POOL:
            if (_state != EntryState.NEW) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case CACHED:
            if (_state == EntryState.REMOVED) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case PRECIOUS:
            if (_state == EntryState.REMOVED) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case BROKEN:
            if (_state == EntryState.REMOVED) {
                throw new IllegalStateException("Entry is " + _state);
            }
            break;
        case REMOVED:
            break;
        default:
            throw new IllegalArgumentException("Invalid state " + state);
        }

        _state = state;
        makeStatePersistent();
    }

    public EntryState getState()
    {
        return _state;
    }

    /*
     *
     *  State transitions
     *
     */

    public boolean setSticky(String owner, long expire, boolean overwrite)
        throws IllegalStateException, IOException
    {
        if (_state == EntryState.REMOVED) {
            throw new IllegalStateException("Entry in removed state");
        }

        // if sticky flag modified, make changes persistent
        if (_sticky.addRecord(owner, expire, overwrite)) {
            makeStatePersistent();
            return true;
        }
        return false;
    }

    public boolean isSticky()
    {
        return _sticky.isSet();
    }

    /**
     * store state in control file
     * @throws IOException
     */
    private void makeStatePersistent() throws IOException
    {

        //BufferedReader in = new BufferedReader( new FileReader(_controlFile) );
        try (BufferedWriter out = new BufferedWriter(new FileWriter(_controlFile, false))) {

            // write repository version number

            out.write("# version 3.0");
            out.newLine();

            switch (_state) {
            case PRECIOUS:
                out.write("precious");
                out.newLine();
                break;
            case CACHED:
                out.write("cached");
                out.newLine();
                break;
            case FROM_CLIENT:
                out.write("from_client");
                out.newLine();
                break;
            case FROM_STORE:
            case FROM_POOL:
                out.write("from_store");
                out.newLine();
                break;
            }

            String state = _sticky.stringValue();
            if (state != null && state.length() > 0) {
                out.write(state);
                out.newLine();
            }

            out.flush();

        }

    }

    private void loadState() throws IOException
    {
        try (BufferedReader in = new BufferedReader(new FileReader(_controlFile))) {
            _state = EntryState.BROKEN;

            String line;
            while ((line = in.readLine()) != null) {

                // ignore empty lines
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                // a comment or version string
                if (line.startsWith("#")) {
                    Matcher m = VERSION_PATTERN.matcher(line);

                    // it's the version string
                    if (m.matches()) {
                        String[] versionLine = line.split("\\s");
                        String[] versionNumber = versionLine[2].split("\\.");

                        int major = Integer.parseInt(versionNumber[0]);
                        int minor = Integer.parseInt(versionNumber[1]);

                        if (major > FORMAT_VERSION_MAJOR || minor != FORMAT_VERSION_MINOR) {
                            throw new IOException("control file format mismatch: supported <= "
                                    + FORMAT_VERSION_MAJOR + "." + FORMAT_VERSION_MINOR + " found: " + versionLine[2]);
                        }
                    }

                    continue;
                }

                if (line.equals("precious")) {
                    _state = EntryState.PRECIOUS;
                    continue;
                }

                if (line.equals("cached")) {
                    _state = EntryState.CACHED;
                    continue;
                }

                if (line.equals("from_client")) {
                    _state = EntryState.FROM_CLIENT;
                    continue;
                }

                if (line.equals("from_store")) {
                    _state = EntryState.FROM_STORE;
                    continue;
                }

                /*
                 * backward compatibility
                 */

                if (line.equals("receiving.store")) {
                    _state = EntryState.FROM_STORE;
                    continue;
                }

                if (line.equals("receiving.cient")) {
                    _state = EntryState.FROM_CLIENT;
                    continue;
                }

                // in case of some one fixed the spelling
                if (line.equals("receiving.client")) {
                    _state = EntryState.FROM_CLIENT;
                    continue;
                }

                // FORMAT: sticky:owner:exipire
                if (line.startsWith("sticky")) {

                    String[] stickyOptions = line.split(":");

                    String owner;
                    long expire;

                    switch (stickyOptions.length) {
                    case 1:
                        // old style
                        owner = "system";
                        expire = -1;
                        break;
                    case 2:
                        // only owner defined
                        owner = stickyOptions[1];
                        expire = -1;
                        break;
                    case 3:
                        owner = stickyOptions[1];
                        try {
                            expire = Long.parseLong(stickyOptions[2]);
                        } catch (NumberFormatException nfe) {
                            // bad number
                            _state = EntryState.BROKEN;
                            return;
                        }

                        break;
                    default:
                        _logBussiness
                                .info("Unknow number of arguments in " + _controlFile
                                        .getPath() + " [" + line + "]");
                        _state = EntryState.BROKEN;
                        return;
                    }

                    _sticky.addRecord(owner, expire, true);
                    continue;
                }

                // if none of knows states, then it's BAD state
                _logBussiness
                        .error("Invalid state [" + line + "] for entry " + _controlFile);
                break;
            }
        }

    }

    public List<StickyRecord> stickyRecords()
    {
        return _sticky.records();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(_state == EntryState.CACHED        ? "C" : "-" );
        sb.append(_state == EntryState.PRECIOUS      ? "P" : "-" );
        sb.append(_state == EntryState.FROM_CLIENT   ? "C" : "-" );
        sb.append((_state == EntryState.FROM_STORE
                   || _state == EntryState.FROM_POOL)? "S" : "-" );
        sb.append("-");
        sb.append("-");
        sb.append(_state == EntryState.REMOVED       ? "R" : "-" );
        sb.append("-");
        sb.append( _sticky.isSet()                   ? "X" : "-" );
        sb.append(_state == EntryState.BROKEN        ? "E" : "-" );
        return sb.toString();
    }
}
