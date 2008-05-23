// $Id: SRMPutClientV2.java,v 1.25 2007/09/20 16:59:13 litvinse Exp $
// $Log: SRMPutClientV2.java,v $

/*
COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.
 
 
 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.
 
 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).
 
 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.
 
 
 Neither the name of Fermilab, the  URA, nor the names of the contributors
 may be used to endorse or promote products derived from this software
 without specific prior written permission.
 
 
 
  DISCLAIMER OF LIABILITY (BSD):
 
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.
 
 
  Liabilities of the Government:
 
  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.
 
 
  Export Control:
 
  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
 */

/*
 * SRMGetClient.java
 *
 * Created on January 28, 2003, 2:54 PM
 */

package gov.fnal.srm.util;

import org.globus.util.GlobusURL;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.io.IOException;
import java.io.File;
import org.dcache.srm.client.SRMClientV2;
import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

/**
 *
 * @author  timur
 */
public class SRMPutClientV2 extends SRMClient implements Runnable {
    private GlobusURL from[];
    private GlobusURL to[];
    private String protocols[];
    private HashMap pendingSurlsToIndex = new HashMap();
    private Copier copier;
    private Thread hook;
    private ISRM srmv2;
    private String requestToken;
    private SrmPrepareToPutResponse response;
    /** Creates a new instance of SRMGetClient */
    public SRMPutClientV2(Configuration configuration, GlobusURL[] from, GlobusURL[] to) {
        super(configuration);
        report = new Report(from,to,configuration.getReport());
        this.protocols = configuration.getProtocols();
        this.from = from;
        this.to = to;
    }
    
    
    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }
    
    public void connect() throws Exception {
        GlobusURL srmUrl = to[0];
        srmv2 = new SRMClientV2(srmUrl,
                getGssCredential(),
                configuration.getRetry_timeout(),
                configuration.getRetry_num(),
                configuration.getLogger(),
                doDelegation,
                fullDelegation,
                gss_expected_name,
                configuration.getWebservice_path());
    }
    
    public void start() throws Exception {
        try {
            copier = new Copier(urlcopy,configuration);
            copier.setDebug(debug);
            new Thread(copier).start();
            int len = from.length;
            TPutFileRequest fileRequests[] = new TPutFileRequest[len];
            String storagetype=configuration.getStorageType();
            String SURLS[] = new String[len];
            for(int i = 0; i < len; ++i) {
                GlobusURL filesource = from[i];
                int filetype = SRMDispatcher.getUrlType(filesource);
                if((filetype & SRMDispatcher.FILE_URL) == 0) {
                    throw new IOException(" source is not file "+ filesource.getURL());
                }
                if((filetype & SRMDispatcher.DIRECTORY_URL) == SRMDispatcher.DIRECTORY_URL) {
                    throw new IOException(" source is directory "+ filesource.getURL());
                }
                if((filetype & SRMDispatcher.CAN_READ_FILE_URL) == 0) {
                    throw new IOException(" source is not readable "+ filesource.getURL());
                }
                File f = new File(filesource.getPath());
                long filesize = f.length();
                SURLS[i] = to[i].getURL();
                URI uri = new URI(SURLS[i]);
                fileRequests[i] = new TPutFileRequest();
                fileRequests[i].setExpectedFileSize(
                        new org.apache.axis.types.UnsignedLong(filesize));
                fileRequests[i].setTargetSURL(uri);
                pendingSurlsToIndex.put(SURLS[i],new Integer(i));
            }
            
            hook = new Thread(this);
            Runtime.getRuntime().addShutdownHook(hook);
            
            SrmPrepareToPutRequest srmPrepareToPutRequest =
                    new SrmPrepareToPutRequest();
            if(storagetype.equals("volatile")){
                srmPrepareToPutRequest.setDesiredFileStorageType(TFileStorageType.VOLATILE);
            }else if(storagetype.equals("durable")){
                srmPrepareToPutRequest.setDesiredFileStorageType(TFileStorageType.DURABLE);
            }else{
                srmPrepareToPutRequest.setDesiredFileStorageType(TFileStorageType.PERMANENT);
            }
            srmPrepareToPutRequest.setDesiredTotalRequestTime(new Integer((int)configuration.getRequestLifetime()));
            
            srmPrepareToPutRequest.setArrayOfFileRequests(
                    new ArrayOfTPutFileRequest(fileRequests));

	    TAccessPattern  ap = TAccessPattern.TRANSFER_MODE;
	    if(configuration.getAccessPattern() != null ) {
		    ap = TAccessPattern.fromString(configuration.getAccessPattern());
            }
	    TConnectionType ct = TConnectionType.WAN;
	    if(configuration.getConnectionType() != null ) {
		    ct = TConnectionType.fromString(configuration.getConnectionType());
            }
	    srmPrepareToPutRequest.setTransferParameters(new TTransferParameters(ap,ct,null,new ArrayOfString(protocols)));
            TTransferParameters transferParams = new
                    TTransferParameters();
            TRetentionPolicy retentionPolicy = null;
            TAccessLatency accessLatency = null;
            if(configuration.getRetentionPolicy() != null ){
                  retentionPolicy = TRetentionPolicy.fromString(configuration.getRetentionPolicy());
                
            }
            
            if(  configuration.getAccessLatency() != null){
                accessLatency = TAccessLatency.fromString(configuration.getAccessLatency());
                
            }
            if(retentionPolicy != null) {
                TRetentionPolicyInfo retentionPolicyInfo =
                        new TRetentionPolicyInfo(retentionPolicy,accessLatency);
                srmPrepareToPutRequest.setTargetFileRetentionPolicyInfo(retentionPolicyInfo);
            }

            if(configuration.getOverwriteMode() != null) {
                srmPrepareToPutRequest.setOverwriteOption(TOverwriteMode.fromString(configuration.getOverwriteMode()));
            }
            if(configuration.getSpaceToken() != null) {
                srmPrepareToPutRequest.setTargetSpaceToken(configuration.getSpaceToken());
            }
            
	    if (configuration.getExtraParameters().size()>0) { 
		    TExtraInfo[] extraInfoArray = new TExtraInfo[configuration.getExtraParameters().size()];
		    int counter=0;
                    Map extraParameters = configuration.getExtraParameters();
		    for (Iterator i =extraParameters.keySet().iterator(); i.hasNext();) { 
                            String key = (String)i.next();
                            String value = (String)extraParameters.get(key);
			    extraInfoArray[counter++]=new TExtraInfo(key,value);
		    }
		    ArrayOfTExtraInfo arrayOfExtraInfo = new ArrayOfTExtraInfo(extraInfoArray);
		    srmPrepareToPutRequest.setStorageSystemInfo(arrayOfExtraInfo);
	    }

            //SrmPrepareToPutResponse response = srmv2.srmPrepareToPut(srmPrepareToPutRequest);
            response = srmv2.srmPrepareToPut(srmPrepareToPutRequest);
            if(response == null) {
                throw new IOException(" null response");
            }
            
            TReturnStatus status = response.getReturnStatus();
            if(status == null) {
                throw new IOException(" null return status");
            }
            
            TStatusCode statusCode = status.getStatusCode();
            
            if(statusCode == null) {
                throw new IOException(" null status code");
            }
            
            String explanation = status.getExplanation();
            
            if(RequestStatusTool.isFailedRequestStatus(status)){
                if(explanation != null){
                    throw new IOException("srmPrepareToPut submission failed, unexpected or failed status : "+ statusCode+" explanation= "+explanation);
                }else{
                    throw new IOException("srmPrepareToPut submission failed, unexpected or failed status : "+ statusCode);
                }
            }
            
            requestToken = response.getRequestToken();
            dsay(" srm returned requestToken = "+requestToken);
            if(response.getArrayOfFileStatuses() == null  ) {
                throw new IOException("returned PutRequestFileStatuses is an empty array");
            }
            TPutRequestFileStatus[] putRequestFileStatuses =
                    response.getArrayOfFileStatuses().getStatusArray();
            if(putRequestFileStatuses.length != len) {
                throw new IOException("incorrect number of GetRequestFileStatuses"+
                        "in RequestStatus expected "+len+" received "+
                        putRequestFileStatuses.length);
            }
            boolean haveCompletedFileRequests = false;
            while(!pendingSurlsToIndex.isEmpty()) {
                long estimatedWaitInSeconds = 5;
                for(int i = 0 ; i<len;++i) {
                    TPutRequestFileStatus putRequestFileStatus = putRequestFileStatuses[i];
                    URI surl = putRequestFileStatus.getSURL();
                    if(surl == null) {
                        esay("invalid putRequestFileStatus, surl is null");
                        continue;
                    }
                    String surl_string = surl.toString();
                    if(!pendingSurlsToIndex.containsKey(surl_string)) {
                        esay("invalid putRequestFileStatus, surl = "+surl_string+" not found");
                        continue;
                    }
                    TReturnStatus fileStatus = putRequestFileStatus.getStatus();
                    if(fileStatus == null) {
                        throw new IOException(" null file return status");
                    }
                    TStatusCode fileStatusCode = fileStatus.getStatusCode();
                    if(fileStatusCode == null) {
                        throw new IOException(" null file status code");
                    }
                    if(RequestStatusTool.isFailedFileRequestStatus(fileStatus)){
                        String error ="retrieval of surl "+surl_string+" failed, status = "+fileStatusCode+" explanation="+fileStatus.getExplanation();
                        esay(error);
                        int indx = ((Integer) pendingSurlsToIndex.remove(surl_string)).intValue();
                        setReportFailed(from[indx],to[indx],error);
                        haveCompletedFileRequests = true;
                        continue;
                    }
                    if(putRequestFileStatus.getTransferURL() != null &&
                            putRequestFileStatus.getTransferURL() != null) {
                        GlobusURL globusTURL = new GlobusURL(putRequestFileStatus.getTransferURL().toString());
                        int indx = ((Integer) pendingSurlsToIndex.remove(surl_string)).intValue();
                        setReportFailed(from[indx],to[indx],  "received TURL, but did not complete transfer");
                        CopyJob job = new SRMV2CopyJob(from[indx],globusTURL,srmv2,requestToken,logger,to[indx],false,this);
                        copier.addCopyJob(job);
                        haveCompletedFileRequests = true;
                        continue;
                    }
                    if(putRequestFileStatus.getEstimatedWaitTime() != null &&
                            putRequestFileStatus.getEstimatedWaitTime().intValue()< estimatedWaitInSeconds &&
                            putRequestFileStatus.getEstimatedWaitTime().intValue() >=1) {
                        estimatedWaitInSeconds = putRequestFileStatus.getEstimatedWaitTime().intValue();
                    }
                }
                
                if(pendingSurlsToIndex.isEmpty()) {
                    dsay("no more pending transfers, breaking the loop");
                    Runtime.getRuntime().removeShutdownHook(hook);
                    break;
                }
                // do not wait longer then 60 seconds
                if(estimatedWaitInSeconds > 60) {
                    estimatedWaitInSeconds = 60;
                }
                try {
                    say("sleeping "+estimatedWaitInSeconds+" seconds ...");
                    Thread.sleep(estimatedWaitInSeconds * 1000);
                }catch(InterruptedException ie) {
                    logger.elog(ie);
                }
                SrmStatusOfPutRequestRequest srmStatusOfPutRequestRequest = new SrmStatusOfPutRequestRequest();
                srmStatusOfPutRequestRequest.setRequestToken(requestToken);
                // if we do not have completed file requests
                // we want to get status for all files
                // we do not need to specify any surls
                int expectedResponseLength;
                // we do not know what to expect from the server when
                // no surls are specified int the status update request
                // so we always are sending the list of all pending srm urls
                //if(haveCompletedFileRequests){
                String [] pendingSurlStrings = (String[])pendingSurlsToIndex.keySet().toArray(new String[0]);
                expectedResponseLength= pendingSurlStrings.length;
                URI surlArray[] = new URI[expectedResponseLength];
                for(int i=0;i<expectedResponseLength;++i){
                    surlArray[i]=new org.apache.axis.types.URI(pendingSurlStrings[i]);
                }
                srmStatusOfPutRequestRequest.setArrayOfTargetSURLs(
                        new ArrayOfAnyURI(surlArray));
                //}else {
                //        expectedResponseLength = from.length;
                //}
                SrmStatusOfPutRequestResponse srmStatusOfPutRequestResponse = srmv2.srmStatusOfPutRequest(srmStatusOfPutRequestRequest);
                if(srmStatusOfPutRequestResponse == null) {
                    throw new IOException(" null srmStatusOfPutRequestResponse");
                }
                if(srmStatusOfPutRequestResponse.getArrayOfFileStatuses() == null ) {
                    esay( "putRequestFileStatuses == null");
                    throw new IOException( "putRequestFileStatuses == null");
                }
                putRequestFileStatuses =
                        srmStatusOfPutRequestResponse.getArrayOfFileStatuses().getStatusArray();
                if(  putRequestFileStatuses.length !=  expectedResponseLength) {
                    esay( "incorrect number of RequestFileStatuses");
                    throw new IOException("incorrect number of RequestFileStatuses");
                }
                
                status = srmStatusOfPutRequestResponse.getReturnStatus();
                if(status == null) {
                    throw new IOException(" null return status");
                }
                statusCode = status.getStatusCode();
                if(statusCode == null) {
                    throw new IOException(" null status code");
                }
                if(RequestStatusTool.isFailedRequestStatus(status)){
                    String error = "srmPrepareToPut update failed, status : "+ statusCode+
                            " explanation="+status.getExplanation();
                    esay(error);
                    for(int i = 0; i<expectedResponseLength;++i) {
                        TReturnStatus frstatus = putRequestFileStatuses[i].getStatus();
                        if ( frstatus != null) {
                            esay("PutFileRequest["+
                                    putRequestFileStatuses[i].getSURL()+
                                    "] status="+frstatus.getStatusCode()+
                                    " explanation="+frstatus.getExplanation()
                                    );
                        }
                    }
                    throw new IOException(error);
                }
            }
        }catch(Exception e) {
            esay(e.toString());
            try {
                if(copier != null) {
                    
                    say("stopping copier");
                    copier.stop();
                    abortAllPendingFiles();
                }
            }catch(Exception e1) {
                logger.elog(e1);
            }
        }finally{
            if(copier != null) {
                copier.doneAddingJobs();
                copier.waitCompletion();
            }
            report.dumpReport();
            if(!report.everythingAllRight()){
                System.err.println("srm copy of at least one file failed or not completed");
                System.exit(1);
            }
        }
    }
    
    // this is called when Ctrl-C is hit, or TERM signal received
    public void run() {
        try{
            say("stopping copier");
            copier.stop();
            abortAllPendingFiles();
        }catch(Exception e) {
            logger.elog(e);
        }
    }
    
    private void abortAllPendingFiles() throws Exception{
        if(pendingSurlsToIndex.isEmpty()) {
            return;
        }
        if(response != null) {
            requestToken = response.getRequestToken();
	    if (requestToken!=null) { 
		String[] surl_strings = (String[])pendingSurlsToIndex.keySet().toArray(new String[0]);
		int len = surl_strings.length;
		say("Releasing all remaining file requests");
		URI surlArray[] = new URI[len];
		for(int i=0;i<len;++i){
		    surlArray[i]=new org.apache.axis.types.URI(surl_strings[i]);;
		}
		SrmAbortFilesRequest srmAbortFilesRequest = new SrmAbortFilesRequest();
		srmAbortFilesRequest.setRequestToken(requestToken);
		srmAbortFilesRequest.setArrayOfSURLs(new ArrayOfAnyURI(surlArray));
		SrmAbortFilesResponse srmAbortFilesResponse = srmv2.srmAbortFiles(srmAbortFilesRequest);
		if(srmAbortFilesResponse == null) {
		    logger.elog(" srmAbortFilesResponse is null");
		} 
		else 
		{
		    TReturnStatus returnStatus = srmAbortFilesResponse.getReturnStatus();
		    if(returnStatus == null) {
			esay("srmAbortFiles return status is null");
			return;
		    }
		    say("srmAbortFiles status code="+returnStatus.getStatusCode());
		}
	    }
	    else { 
		if (response.getArrayOfFileStatuses()!=null){ 
		    if (response.getArrayOfFileStatuses().getStatusArray()!=null) { 
			for(int i=0;i<response.getArrayOfFileStatuses().getStatusArray().length;i++) { 
			    org.apache.axis.types.URI surl=response.getArrayOfFileStatuses().getStatusArray(i).getSURL();
			    TReturnStatus fst=response.getArrayOfFileStatuses().getStatusArray(i).getStatus();
			    esay("SURL["+i+"]="+surl.toString()+" status="+fst.getStatusCode()+" explanation="+fst.getExplanation());
			}
		    }
		}
	    }
	}
    }
}
