package org.dcache.tests.repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.dcache.chimera.FsInode;
import org.dcache.chimera.HFile;
import org.dcache.chimera.HimeraFsException;
import org.dcache.chimera.IOHimeraFsException;
import org.dcache.chimera.JdbcFs;
import org.dcache.chimera.XMLconfig;
import org.dcache.pool.repository.DataFileRepository;

import diskCacheV111.util.PnfsId;

public class RepositoryHealerTestChimeraHelper implements DataFileRepository {


    private Connection _conn;
    private JdbcFs _fs;
    private FsInode _rootInode;


    RepositoryHealerTestChimeraHelper() throws Exception {


        Class.forName("org.hsqldb.jdbcDriver");

        _conn = DriverManager.getConnection("jdbc:hsqldb:mem:chimeramem", "sa", "");

        File sqlFile = new File("modules/external/Chimera/sql/create-hsqldb.sql");
        StringBuilder sql = new StringBuilder();

        BufferedReader dataStr = new BufferedReader(new FileReader(sqlFile));
        String inLine = null;

        while ((inLine = dataStr.readLine()) != null) {
            sql.append(inLine);
        }

        Statement st = _conn.createStatement();

        st.executeUpdate(sql.toString());

        tryToClose(st);


        _fs = new JdbcFs(new XMLconfig(new File("modules/external/Chimera/test-config.xml")));
        _rootInode = _fs.path2inode("/");
    }

    public void shutdown() {
        try {
            _conn.createStatement().execute("SHUTDOWN;");
            _conn.close();
        }catch (SQLException e) {
            // ignore
        }
    }

    FsInode add(PnfsId pnfsid) throws HimeraFsException {

        return _fs.createFile(_rootInode, pnfsid.toString() );

    }


    static void tryToClose(Statement o) {
        try {
            if (o != null)
                o.close();
        } catch (SQLException e) {

        }
    }


    public File get(PnfsId id) {
        return new HFile(_fs, id.toString() );
    }


    public long getFreeSpace() {
        // TODO Auto-generated method stub
        return 0;
    }


    public long getTotalSpace() {
        // TODO Auto-generated method stub
        return 0;
    }


    public boolean isOk() {
        return true;
    }


    public List<PnfsId> list() {


        List<PnfsId> entries = new ArrayList<PnfsId>();


        try {
            String[] list = _fs.listDir(_rootInode);

            for(String entry: list) {
                entries.add( new PnfsId(entry) );
            }

        } catch (IOHimeraFsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return entries;
    }
}
