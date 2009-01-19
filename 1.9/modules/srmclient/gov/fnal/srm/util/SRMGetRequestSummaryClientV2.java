//______________________________________________________________________________
//
// $Id: SRMGetRequestSummaryClientV2.java 10230 2008-09-24 19:04:23Z litvinse $
// $Author: litvinse $
//
// created 10/07 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________


package gov.fnal.srm.util;
import java.util.HashMap;
import java.util.Iterator;
import org.globus.util.GlobusURL;
import org.dcache.srm.v2_2.ISRM;
import org.dcache.srm.client.SRMClientV2;
import org.ietf.jgss.GSSCredential;
import java.io.IOException;
import java.text.DateFormat;
import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

public class SRMGetRequestSummaryClientV2 extends SRMClient  {

    private GlobusURL srmURL;
    private org.ietf.jgss.GSSCredential credential = null;
    private ISRM srmv2;
    
    public SRMGetRequestSummaryClientV2(Configuration configuration, 
					GlobusURL url) {
	super(configuration);
	srmURL=url;
	try {
	    credential = getGssCredential();
	}
	catch (Exception e) {
	    credential = null;
	    System.err.println("Couldn't getGssCredential.");
	}
    }
    
    public void connect() throws Exception {
	srmv2 = new SRMClientV2(srmURL, 
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
	    if (credential.getRemainingLifetime() < 60) 
		throw new Exception(
		    "Remaining lifetime of credential is less than a minute.");
	}
	catch (org.ietf.jgss.GSSException gsse) {
	    throw gsse;
	}
	try { 
	    String[] tokens = configuration.getArrayOfRequestTokens();
	    SrmGetRequestSummaryRequest request = new SrmGetRequestSummaryRequest();
	    
	    request.setArrayOfRequestTokens(new ArrayOfString(tokens));

	    SrmGetRequestSummaryResponse response = srmv2.srmGetRequestSummary(request);
	    if ( response == null ) { 
		throw new IOException(" null SrmGetRequestSummaryResponse ");
	    }
	    TReturnStatus rs = response.getReturnStatus();
	    if ( rs == null) { 
		throw new IOException(" null TReturnStatus ");	
	    }
	    if (RequestStatusTool.isFailedRequestStatus(rs)) {
		throw new IOException("srmGetRequestSummary failed, unexpected or failed return status : "+
				      rs.getStatusCode()+" explanation="+rs.getExplanation());
	    }
	    if (response.getArrayOfRequestSummaries()!=null) { 
		ArrayOfTRequestSummary summaries = response.getArrayOfRequestSummaries();
		if (summaries.getSummaryArray()!=null) { 
		    for (int i=0;i<summaries.getSummaryArray().length;i++){
			TRequestSummary summary = summaries.getSummaryArray(i);
			if (summary != null) { 
			    TReturnStatus st   = summary.getStatus();
			    TRequestType  type = summary.getRequestType();
			    System.out.println("\tRequest number  : "+summary.getRequestToken());
			    System.out.println("\t  Request type  : "+(type!=null ? type.getValue() : "UNKNOWN"));
			    System.out.println("\t Return status");
			    System.out.println("\t\t Status code  : "+(st!=null ? st.getStatusCode() : "null"));
			    System.out.println("\t\t Explanation  : "+(st!=null ? st.getExplanation() : "null"));
			    System.out.println("\tTotal # of files: "+summary.getTotalNumFilesInRequest());
			    System.out.println("\t completed files: "+summary.getNumOfCompletedFiles());
			    System.out.println("\t   waiting files: "+summary.getNumOfWaitingFiles());
			    System.out.println("\t    failed files: "+summary.getNumOfFailedFiles());
			}
		    }
		}
	    }
	}
	catch(Exception e) {
		throw e;
	}
    }
}
