package diskCacheV111.services.acl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.dcache.acl.ACLException;
import org.dcache.acl.Origin;
import org.dcache.acl.Subject;
import org.dcache.acl.enums.AccessType;
import org.dcache.acl.enums.FileAttribute;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;
import dmg.cells.nucleus.CellAdapter;

/**
 * This class implements interface PermissionHandler.
 * It reads Permission Handler configuration string containing comma separated list of
 * permission handlers and asks for permission to perform an action on
 * resources by calling these permission handlers.
 * That is, if first permission handler from the list cannot state whether
 * an operation is allowed or denied then next permission handler is used, and so on.
 * For example, if Permission Handler configuration string is defined as follows:
 *   permissionHandler=diskCacheV111.services.acl.ACLPermissionHandler,diskCacheV111.services.acl.UnixPermissionHandler
 * then ACL permissions are used first. If ACLs do not state whether
 * an operation is allowed or denied then UNIX permissions are used.
 *
 *
 * @author David Melkumyan
 *
 */
public class DelegatingPermissionHandler implements PermissionHandler {

    private static final Logger _logger = Logger.getLogger("logger.org.dcache.authorization." + DelegatingPermissionHandler.class.getName());

    private static final String DELIMITER = ",";

    private final PermissionHandler[] permHandlers;

    public DelegatingPermissionHandler(CellAdapter cell) throws ACLException {
        String config = cell.getArgs().getOpt("permission-handler");
        if (config == null || (config = config.trim()).length() == 0)
            throw new ACLException("Initialize Permission Handler failed: Argument 'config' is " + (config == null ? "NULL" : "Empty"));
        _logger.debug("Permission Handler configuration string is: " + config);

        if (cell == null)
            throw new ACLException("Initialize Permission Handler failed: Argument 'cell' is NULL.");

        Class<?>[] argClass = { CellAdapter.class };
        Object[] initargs = { cell };

        String[] classNames = config.split(DELIMITER);
        final int length = classNames.length;

        if (_logger.isDebugEnabled())
            _logger.debug("Permission handler" + ((length > 1) ? "s: " + Arrays.toString(classNames) : ": " + classNames[0]));

        permHandlers = new PermissionHandler[length];
        for (int index = 0; index < length; index++)
            permHandlers[index] = getPermissionHandler(classNames[index], argClass, initargs);
    }

    public String toUnixACL(PnfsId pnfsId) throws ACLException, CacheException {

        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            String res = permhandler.toUnixACL(pnfsId);
            if (res != null)
                return res;

        }
        return null;
    }

    public AccessType canCreateDir(PnfsId parentPnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canCreateDir(parentPnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canCreateFile(PnfsId parentPnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canCreateFile(parentPnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canDeleteDir(PnfsId pnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canDeleteDir(pnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canDeleteFile(PnfsId pnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canDeleteFile(pnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canGetAttributes(PnfsId pnfsId, Subject subject, Origin userOrigin, FileAttribute attribute) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canGetAttributes(pnfsId, subject, userOrigin, attribute);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canListDir(PnfsId pnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canListDir(pnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canReadFile(PnfsId pnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canReadFile(pnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canSetAttributes(PnfsId pnfsId, Subject subject, Origin userOrigin, FileAttribute attribute) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canSetAttributes(pnfsId, subject, userOrigin, attribute);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    public AccessType canWriteFile(PnfsId pnfsId, Subject subject, Origin origin) throws CacheException, ACLException {
        for (PermissionHandler permhandler : permHandlers) {
            if (_logger.isDebugEnabled())
                _logger.debug("Using permission handler: " + permhandler.getClass().getSimpleName());

            AccessType res = permhandler.canWriteFile(pnfsId, subject, origin);
            if (res != null && res != AccessType.ACCESS_UNDEFINED)
                return res;
        }
        return AccessType.ACCESS_UNDEFINED;
    }

    /**
    * private methods * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
    */
    private PermissionHandler getPermissionHandler(String className, Class<?>[] argClass, Object[] initargs) throws ACLException {
        try {
            if (className.length() == 0 || (className = className.trim()).length() == 0)
                throw new IllegalArgumentException("Class Name is empty.");

            _logger.debug("Initializing permission handler: " + className);

            Class<?> permissionHandlerClass = Class.forName(className);
            Constructor<?> permissionHandlerCon = permissionHandlerClass.getConstructor(argClass);
            return (PermissionHandler) permissionHandlerCon.newInstance(initargs);

        } catch (SecurityException e) {
            throw new ACLException("Initialize Permission Handler failed, SecurityException: ", e);

        } catch (IllegalArgumentException e) {
            throw new ACLException("Initialize Permission Handler failed, IllegalArgumentException: ", e);

        } catch (ClassNotFoundException e) {
            throw new ACLException("Initialize Permission Handler failed, ClassNotFoundException: ", e);

        } catch (NoSuchMethodException e) {
            throw new ACLException("Initialize Permission Handler failed, NoSuchMethodException: ", e);

        } catch (InstantiationException e) {
            throw new ACLException("Initialize Permission Handler failed, InstantiationException: ", e);

        } catch (IllegalAccessException e) {
            throw new ACLException("Initialize Permission Handler failed, IllegalAccessException: ", e);

        } catch (InvocationTargetException e) {
            throw new ACLException("Initialize Permission Handler failed, InvocationTargetException: ", e);
        }
    }

    public String toString() {
        final int length = permHandlers.length;
        String[] classNames = new String[length];
        for (int i = 0; i < length; i++)
            classNames[i] = permHandlers[i].getClass().getName();
        return Arrays.toString(classNames);
    }

}
