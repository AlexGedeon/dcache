package gplazma.authz.plugins.samlquery;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.opensciencegrid.authz.client.PRIMAAuthzModule;
import org.opensciencegrid.authz.common.LocalId;

import java.util.Enumeration;
import java.util.HashMap;
import java.net.Socket;
import java.net.URL;
import java.security.cert.X509Certificate;

import gplazma.authz.records.gPlazmaAuthorizationRecord;
import gplazma.authz.AuthorizationException;

/**
 * SAML1AuthorizationQuery.java
 * User: tdh
 * Date: Sep 16, 2008
 * Time: 1:43:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class SAML1AuthorizationPlugin extends SAMLAuthorizationPlugin {

    private static final HashMap<String, TimedLocalId> UsernameMap = new HashMap();

    public SAML1AuthorizationPlugin(String mappingServiceURL, String storageAuthzPath, long authRequestID) {
        super(mappingServiceURL, storageAuthzPath, authRequestID);
        getLogger().info("saml-vo-mapping plugin now loaded for URL " + mappingServiceURL);
    }

    public gPlazmaAuthorizationRecord authorize(String subjectDN, String role, X509Certificate[] chain, String desiredUserName, String serviceUrl, Socket socket)
            throws AuthorizationException {

        PRIMAAuthzModule authVO;
        this.desiredUserName = desiredUserName;
        LocalId localId;
        String serviceName;

        try {
            serviceName = getTargetServiceName();
        }
        catch (Exception e) {
            getLogger().error("Exception in finding targetServiceName : " + e);
            throw new AuthorizationException(e.toString());
        }
        synchronized(UsernameMap) {
          String key = subjectDN;
          if(getCacheLifetime()>0) {
              key = (role==null) ? key : key.concat(role);
              TimedLocalId tlocalId = getUsernameMapping(key);
              if( tlocalId!=null && tlocalId.age() < getCacheLifetime() &&
                      tlocalId.sameServiceName(serviceName) &&
                      tlocalId.sameDesiredUserName(desiredUserName)) {
                  getLogger().info("Using cached mapping for User with DN: " + subjectDN + " and Role " + role + " with Desired user name: " + desiredUserName);

                  gPlazmaAuthorizationRecord gauthrec = getgPlazmaAuthorizationRecord(tlocalId.getLocalId(), subjectDN, role);
                  if (gauthrec!=null) {
                      gauthrec.setSubjectDN(subjectDN);
                      gauthrec.setFqan(role);
                  }
                  return gauthrec;
              }
          }

          getLogger().info("Requesting mapping for User with DN: " + subjectDN + " and Role " + role + " with Desired user name: " + desiredUserName);

          getLogger().debug("Mapping Service URL configuration: " + getMappingServiceURL());
          try {
              URL mappingServiceURLobject = new URL(getMappingServiceURL());
              authVO = new PRIMAAuthzModule(mappingServiceURLobject);
          }
          catch (Exception e) {
              getLogger().error("Exception in VO mapping client instantiation: " + e);
              throw new AuthorizationException(e.toString());
          }

          try {
              localId = authVO.mapCredentials(subjectDN, role, serviceName, desiredUserName);
          }
          catch (Exception e ) {
              getLogger().error(" Exception occurred in mapCredentials: " + e);
              //e.printStackTrace();
              throw new AuthorizationException(e.toString());
          }

          if (localId == null) {
              String denied = DENIED_MESSAGE + ": No mapping retrieved service for DN " + subjectDN + " and role " + role;
              getLogger().warn(denied);
              throw new AuthorizationException(denied);
          }

          if(getCacheLifetime()>0) putUsernameMapping(key, new TimedLocalId(localId, serviceName, desiredUserName));
        }

        gPlazmaAuthorizationRecord gauthrec = getgPlazmaAuthorizationRecord(localId, subjectDN, role);
        if (gauthrec!=null) {
            gauthrec.setSubjectDN(subjectDN);
            gauthrec.setFqan(role);
        }
        return gauthrec;
    }

    private gPlazmaAuthorizationRecord getgPlazmaAuthorizationRecord(LocalId localId, String subjectDN, String role) throws AuthorizationException {

        String username = localId.getUserName();

        if(username==null) {
            String denied = DENIED_MESSAGE + ": non-null user record received, but with a null username";
            getLogger().warn(denied);
            throw new AuthorizationException(denied);
        } else {
            getLogger().info("saml-vo-mapping service returned Username: " + username);
        }

        return getgPlazmaAuthorizationRecord(username, subjectDN, role);
    }

    private synchronized void putUsernameMapping(String key, TimedLocalId tlocalId) {
        UsernameMap.put(key, tlocalId);
    }

    private synchronized TimedLocalId getUsernameMapping(String key) {
        return UsernameMap.get(key);
    }

    private class TimedLocalId extends LocalId {
        LocalId id;
        long timestamp;
        String serviceName=null;
        String desiredUserName=null;

        TimedLocalId(LocalId id) {
            this.id=id;
            this.timestamp=System.currentTimeMillis();
        }

        TimedLocalId(LocalId id, String serviceName, String desiredUserName) {
            this(id);
            this.serviceName=serviceName;
            this.desiredUserName=desiredUserName;
        }

        private LocalId getLocalId() {
            return id;
        }

        private long age() {
            return System.currentTimeMillis() - timestamp;
        }

        private boolean sameServiceName(String requestServiceName) {
            return (serviceName.equals(requestServiceName));
        }

        private boolean sameDesiredUserName(String requestDesiredUserName) {
            if(desiredUserName==null && requestDesiredUserName==null) return true;
            if(desiredUserName==null) return false;
            return (desiredUserName.equals(requestDesiredUserName));
        }
    }

}
