//______________________________________________________________________________
//
// $Id: SrmGetTransferProtocols.java,v 1.1 2006-12-21 17:37:29 timur Exp $
// $Author: timur $
//
// created 10/05 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________

/*
 * SrmCopy
 *
 * Created on 10/05
 */

package org.dcache.srm.handler;

import org.dcache.srm.v2_2.*;
import org.dcache.srm.request.RequestUser;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMException;
import org.dcache.srm.util.Configuration;
import org.dcache.srm.scheduler.Scheduler;
import org.apache.axis.types.URI;
import org.dcache.srm.request.ContainerRequest;
import org.dcache.srm.SRMProtocol;

/**
 *
 * @author  litvinse
 */

public class SrmGetTransferProtocols {
    private final static String SFN_STRING="?SFN=";
    AbstractStorageElement storage;
    RequestUser            user;
    Scheduler              scheduler;
    RequestCredential      credential;
    Configuration          configuration;
    SrmGetTransferProtocolsRequest request;
    SrmGetTransferProtocolsResponse        response;
    org.dcache.srm.SRM srm;
    
    public SrmGetTransferProtocols(RequestUser user,
            RequestCredential credential,
            SrmGetTransferProtocolsRequest request,
            AbstractStorageElement storage,
            org.dcache.srm.SRM srm,
            String client_host) {
        
        if (request == null) {
            throw new NullPointerException("request is null");
        }
        this.request    = request;
        this.user       = user;
        this.credential = credential;
        if (storage == null) {
            throw new NullPointerException("storage is null");
        }
        this.storage = storage;
        this.srm = srm;
        this.scheduler = srm.getCopyRequestScheduler();
        if (scheduler == null) {
            throw new NullPointerException("scheduler is null");
        }
        this.configuration = srm.getConfiguration();
        if (configuration == null) {
            throw new NullPointerException("configuration is null");
        }
    }
    
    private void say(String txt) {
        if(storage!=null) {
            storage.log(toString()+" "+txt);
        }
    }
    
    private void esay(String txt) {
        if(storage!=null) {
            storage.elog(toString()+" "+txt);
        }
    }
    
    private void esay(Throwable t) {
        if(storage!=null) {
            storage.elog(" SrmGetTransferProtocols exception : ");
            storage.elog(t);
        }
    }
    
    public SrmGetTransferProtocolsResponse getResponse() {
        if(response != null ) return response;
        response = new SrmGetTransferProtocolsResponse();
        String[] protocols;
        try {
          
         protocols = srm.getProtocols(user,credential);
      } catch(Exception e) {
         esay(e);
         return getFailedResponse("SrmGetTransferProtocols failed: "+e,
                 TStatusCode.SRM_INTERNAL_ERROR);
      }

        TSupportedTransferProtocol[] arrayOfProtocols = 
                new TSupportedTransferProtocol[protocols.length];
        for(int i =0 ; i<protocols.length; ++i) {
            arrayOfProtocols[i] = new TSupportedTransferProtocol(protocols[i],null);
        }
        ArrayOfTSupportedTransferProtocol protocolArray =
                new ArrayOfTSupportedTransferProtocol();
        
        protocolArray.setProtocolArray(arrayOfProtocols);
        response.setProtocolInfo(protocolArray);
        response.setReturnStatus(new TReturnStatus(TStatusCode.SRM_SUCCESS,
                "success"));
        return response;
    }
    
    public static final SrmGetTransferProtocolsResponse getFailedResponse(String text) {
        return getFailedResponse(text,null);
    }
    
    public static final SrmGetTransferProtocolsResponse getFailedResponse(String error, 
            TStatusCode statusCode) {
        if(statusCode == null) {
            statusCode = TStatusCode.SRM_FAILURE;
        }
        SrmGetTransferProtocolsResponse response = new SrmGetTransferProtocolsResponse();
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(statusCode);
        status.setExplanation(error);
        response.setReturnStatus(status);
        return response;
    }
}
