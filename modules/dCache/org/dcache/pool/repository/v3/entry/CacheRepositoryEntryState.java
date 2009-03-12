package org.dcache.pool.repository.v3.entry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.dcache.pool.repository.v3.entry.state.Sticky;
import org.dcache.pool.repository.StickyRecord;
import org.dcache.pool.repository.EntryState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CacheRepositoryEntryState
{
    // new logger concept
    private static Logger _logBussiness = Logger.getLogger("logger.org.dcache.repository");


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
        if (state == _state)
            return;

        switch (state) {
        case NEW:
            throw new IllegalStateException("Entry is " + _state);
        case FROM_CLIENT:
            if (_state != EntryState.NEW)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case FROM_STORE:
            if (_state != EntryState.NEW)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case FROM_POOL:
            if (_state != EntryState.NEW)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case CACHED:
            if (_state == EntryState.REMOVED ||
                _state == EntryState.DESTROYED)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case PRECIOUS:
            if (_state == EntryState.REMOVED ||
                _state == EntryState.DESTROYED)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case BROKEN:
            if (_state == EntryState.REMOVED ||
                _state == EntryState.DESTROYED)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case REMOVED:
            if (_state == EntryState.DESTROYED)
                throw new IllegalStateException("Entry is " + _state);
            break;
        case DESTROYED:
            if (_state != EntryState.REMOVED)
                throw new IllegalStateException("Entry is " + _state);
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
        if (_state == EntryState.REMOVED || _state == EntryState.DESTROYED) {
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
        BufferedWriter out = new BufferedWriter(new FileWriter(_controlFile, false) );
        try {

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
            if( state != null && state.length() > 0 ) {
                out.write(state); out.newLine();
            }

            out.flush();

        }finally{
            out.close();
        }
    }


    private void loadState() throws IOException
    {
        BufferedReader in = null;

        List<String> lines = new ArrayList<String>();

        try {

            in = new BufferedReader( new FileReader(_controlFile) );

            boolean done = false;
            while(!done) {

                String line = in.readLine();
                if( line == null) {
                    done = true;
                    continue;
                }


                // ignore empty lines
                line = line.trim();
                if(line.length() == 0 ) {
                    continue;
                }

                // a comment or version string

                if( line.startsWith("#") ) {

                    Pattern p = Pattern.compile("#\\s+version\\s+[0-9]\\.[0-9]");
                    Matcher m = p.matcher(line);

                    // it's the version string
                    if( m.matches() ) {
                        String[] versionLine = line.split("\\s");
                        String[] versionNumber = versionLine[2].split("\\.");

                        int major = Integer.parseInt(versionNumber[0]);
                        int minor = Integer.parseInt(versionNumber[1]);

                        if( major > FORMAT_VERSION_MAJOR || minor != FORMAT_VERSION_MINOR ) {
                            throw new IOException("control file format mismatch: supported <= "
                                                  + FORMAT_VERSION_MAJOR + "." + FORMAT_VERSION_MINOR + " found: " + versionLine[2]);
                        }
                    }

                    continue;
                }

                lines.add(line);

            }

        }finally{
            if( in != null ) { in.close(); }
        }

        _state = EntryState.BROKEN;

        Iterator<String> stateIterator = lines.iterator();

        while( stateIterator.hasNext() ) {

            String state = stateIterator.next();

            if( state.equals("precious") ) {
                _state = EntryState.PRECIOUS;
                continue;
            }

            if( state.equals("cached") ) {
                _state = EntryState.CACHED;
                continue;
            }

            if( state.equals("from_client") ) {
                _state = EntryState.FROM_CLIENT;
                continue;
            }

            if( state.equals("from_store") ) {
                _state = EntryState.FROM_STORE;
                continue;
            }

            /*
             * backward compatibility
             */

            if( state.equals("receiving.store") ) {
                _state = EntryState.FROM_STORE;
                continue;
            }

            if( state.equals("receiving.cient") ) {
                _state = EntryState.FROM_CLIENT;
                continue;
            }

            // in case of some one fixed the spelling
            if( state.equals("receiving.client") ) {
                _state = EntryState.FROM_CLIENT;
                continue;
            }

            // FORMAT: sticky:owner:exipire

            if( state.startsWith("sticky") ) {

                String[] stickyOptions = state.split(":");

                String owner = "repository";
                long expire = -1;

                switch ( stickyOptions.length ) {
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
                    }catch(NumberFormatException nfe) {
                        // bad number
                        _state = EntryState.BROKEN;
                        return;
                    }

                    break;
                default:
                    _logBussiness.info("Unknow number of arguments in " +_controlFile.getPath() + " [" +state+"]");
                    _state = EntryState.BROKEN;
                    return;
                }

                _sticky.addRecord(owner, expire, true);

                continue;
            }


            // if none of knows states, then it's BAD state
            _logBussiness.error("Invalid state [" + state + "] for entry " + _controlFile);
            break;

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
        sb.append(_state == EntryState.DESTROYED     ? "D" : "-" );
        sb.append( _sticky.isSet()                   ? "X" : "-" );
        sb.append(_state == EntryState.BROKEN        ? "E" : "-" );
        return sb.toString();
    }
}
