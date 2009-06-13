/**
 * ISRMImpl.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis 1.2RC2 Nov 16, 2004 (12:19:44 EST) WSDL2Java emitter.
 */

package org.dcache.srm.server;
import org.apache.axis.AxisProperties;
import javax.naming.Context;
import javax.naming.InitialContext;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import java.util.*;

import org.dcache.srm.SRMAuthorizationException;
import org.dcache.srm.SRMUser;
import org.dcache.commons.stats.*;
import org.dcache.srm.util.JDC;
import org.dcache.srm.client.ConvertUtil;
import org.dcache.srm.client.axis.*;
import java.util.Collection;
import org.gridforum.jgss.ExtendedGSSContext;
import org.dcache.commons.stats.RequestCounters;


public class SRMServerV1 implements org.dcache.srm.client.axis.ISRM_PortType{

   public Logger log;
   private SrmDCacheConnector srmConn;
   private SrmAuthorizer srmAuth = null;
   private final RequestCounters<String> srmServerCounters;
    
    public SRMServerV1() throws java.rmi.RemoteException {
       try
       {
          // srmConn = SrmDCacheConnector.getInstance();
          log = Logger.getLogger(this.getClass().getName());
          Context logctx = new InitialContext();
          String srmConfigFile =
                (String) logctx.lookup("java:comp/env/srmConfigFile");

         if(srmConfigFile == null) {
             String error = "name of srm config file is not specified";
             String error_details ="please insert the following xml codelet into web.xml\n"+
             " <env-entry>\n"+
             "  <env-entry-name>srmConfigFile</env-entry-name>\n"+
             "   <env-entry-value>INSERT SRM CONFIG FILE NAME HERE</env-entry-value>\n"+
             "  <env-entry-type>java.lang.String</env-entry-type>\n"+
             " </env-entry>";

             log.error(error);
             log.error(error_details);
             throw new java.rmi.RemoteException(error );
         }
             srmConn = SrmDCacheConnector.getInstance(srmConfigFile);
             if (srmConn == null) {
                 throw new java.rmi.RemoteException("Failed to get instance of srm." );
             }
             String logConfigFile = srmConn.getLogFile();
             System.out.println("ISRMImpl: reading log4j configuration from "+logConfigFile);
             DOMConfigurator.configure(logConfigFile);
             log.info("srmConfigFile: " + srmConfigFile);
             log.info(" initialize() got connector ="+srmConn);
             // Set up the authorization service
             srmAuth = new SrmAuthorizer(srmConn);   
             srmServerCounters = srmConn.getSrm().getSrmServerV1Counters();
       }
       catch ( java.rmi.RemoteException re) { throw re; }
       catch ( Exception e) {
           throw new java.rmi.RemoteException("exception",e);
       }
        
    }

      /**
       * increment particular request type count
       */

    private void incrementRequest(String operation) {
      srmServerCounters.incrementRequests(operation);
    }
        
    public org.dcache.srm.client.axis.RequestStatus put(java.lang.String[] arg0, 
            java.lang.String[] arg1, long[] arg2, boolean[] arg3, java.lang.String[] arg4)
            throws java.rmi.RemoteException {
      incrementRequest("put");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmput:");
      
      try {
          userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.put() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
          user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().put(user,requestCredential,arg0,arg1,arg2,arg3,arg4, userCred.clientHost);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm put failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.RequestStatus get(java.lang.String[] arg0, java.lang.String[] arg1) throws java.rmi.RemoteException {
      incrementRequest("get");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmget:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.get() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().get(user,
             requestCredential,
             arg0,
             arg1,
             userCred.clientHost);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm get failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.RequestStatus copy(java.lang.String[] arg0, java.lang.String[] arg1, boolean[] arg2) throws java.rmi.RemoteException {
      incrementRequest("copy");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmcopy:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.copy() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().copy(user,
             requestCredential,
             arg0,
             arg1,
             arg2,
             userCred.clientHost);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm put failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public boolean ping() throws java.rmi.RemoteException {
      incrementRequest("ping");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.ping() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      return true;
    }

    public org.dcache.srm.client.axis.RequestStatus pin(java.lang.String[] arg0) throws java.rmi.RemoteException {
      incrementRequest("pin");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmpin:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.pin() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
         throw new java.rmi.RemoteException("srm pin is not supported");
    }

    public org.dcache.srm.client.axis.RequestStatus unPin(java.lang.String[] arg0, int arg1) throws java.rmi.RemoteException {
      incrementRequest("unPin");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmunpin:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.unPin() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      throw new java.rmi.RemoteException("srm unPin is not supported");
    }

    public org.dcache.srm.client.axis.RequestStatus setFileStatus(int arg0, int arg1, java.lang.String arg2) throws java.rmi.RemoteException {
      incrementRequest("setFileStatus");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmsetstatus:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.setFileStatus() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().setFileStatus(user,requestCredential,arg0,arg1,arg2);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm setFileStatus failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.RequestStatus getRequestStatus(int arg0) throws java.rmi.RemoteException {
      incrementRequest("getRequestStatus");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmgetstatus:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.getRequestStatus() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().getRequestStatus(user,requestCredential,arg0);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm getRequestStatus failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.FileMetaData[] getFileMetaData(
    java.lang.String[] arg0) throws java.rmi.RemoteException {
              log.debug("Entering ISRMImpl.getFileMetaData");
      incrementRequest("getFileMetaData");
      
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmgetFileMetaData:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.getFileMetadata() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      log.debug("About to call getFileMetaData()");
      diskCacheV111.srm.FileMetaData[] fmdArray;
      try {
          
         fmdArray = srmConn.getSrm().getFileMetaData(user,requestCredential,arg0);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm getFileMetaData failed", e);
      }
      org.dcache.srm.client.axis.FileMetaData[] response = 
        ConvertUtil.FMDs2AxisFMDs(fmdArray);
      log.debug("About to return FileMetaData array ");
      return response;

    }

    public org.dcache.srm.client.axis.RequestStatus mkPermanent(java.lang.String[] arg0) throws java.rmi.RemoteException {
      incrementRequest("mkPermanent");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.mkPermanent() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().mkPermanent(user,requestCredential,arg0);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm mkPermanent failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.RequestStatus getEstGetTime(java.lang.String[] arg0, java.lang.String[] arg1) throws java.rmi.RemoteException {
      incrementRequest("getEstGetTime");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.getEstGetTime() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().getEstGetTime(user,requestCredential,arg0,arg1);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm getEstGetTime failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public org.dcache.srm.client.axis.RequestStatus getEstPutTime(java.lang.String[] arg0, java.lang.String[] arg1, long[] arg2, boolean[] arg3, java.lang.String[] arg4) throws java.rmi.RemoteException {
      incrementRequest("getEstPutTime");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.getEstPutTime() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         requestStatus = srmConn.getSrm().getEstPutTime(user,requestCredential,arg0,arg1,arg2,arg3,arg4);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm put failed", e);
      }
      org.dcache.srm.client.axis.RequestStatus response = 
        ConvertUtil.RS2axisRS(requestStatus);
        return response;
    }

    public void advisoryDelete(java.lang.String[] arg0) throws java.rmi.RemoteException {
      incrementRequest("advisoryDelete");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      JDC.createSession("v1:srmdelete:");
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.advisoryDelete() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      try {
          
          srmConn.getSrm().advisoryDelete(user,requestCredential,arg0);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm advisoryDelete failed", e);
      }
    }

    public java.lang.String[] getProtocols() throws java.rmi.RemoteException {
      incrementRequest("getProtocols");
      org.dcache.srm.server.UserCredential userCred = null;
      SRMUser user = null;
      org.dcache.srm.request.RequestCredential requestCredential = null;
      
      try {
         userCred = srmAuth.getUserCredentials();
          Collection roles = SrmAuthorizer.getFQANsFromContext((ExtendedGSSContext) userCred.context);                
          String role = roles.isEmpty() ? null : (String) roles.toArray()[0];
          log.debug("SRMServerV1.getProtocols() : role is "+role);
          requestCredential = srmAuth.getRequestCredential(userCred,role);
         user = srmAuth.getRequestUser(requestCredential,null,userCred.context);
      }
      catch (SRMAuthorizationException sae) {
         log.error("SRM Authorization failed", sae);
         throw new java.rmi.RemoteException(
            "SRM Authorization failed", sae);
      }
      
      diskCacheV111.srm.RequestStatus requestStatus;
      try {
          
         return srmConn.getSrm().getProtocols(user,requestCredential);
      } catch(Exception e) {
         log.fatal(e);
         throw new java.rmi.RemoteException("srm getProtocols failed", e);
      }
    }

}
