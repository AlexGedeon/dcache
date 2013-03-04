/*
 * Handler.java
 *
 * Created on October 17, 2003, 11:53 AM
 */

package dmg.cells.nucleus.protocols.context;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 *
 * @author  timur
 */
public class Handler extends URLStreamHandler{

    @Override
    protected URLConnection openConnection(URL u) {
        try {
            ClassLoader threadLoader = Thread.currentThread().getContextClassLoader();
            if (threadLoader != null) {
                Class<? extends URLConnection> cls = threadLoader.loadClass(
                   "dmg.cells.nucleus.CellUrl.DomainUrlConnection").asSubclass(URLConnection.class);
                Constructor<? extends URLConnection> constr=cls.getConstructor(new Class[]
                {URL.class,String.class});
                return constr.newInstance(u,"context");
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

}
