//______________________________________________________________________________
//
// $Id: SRMGetRequestTokensClientV2.java 10230 2008-09-24 19:04:23Z litvinse $
// $Author: litvinse $
//
// created 10/07 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________


package gov.fnal.srm.util;
import java.util.Calendar;
import org.globus.util.GlobusURL;
import org.dcache.srm.v2_2.ISRM;
import org.dcache.srm.client.SRMClientV2;
import org.ietf.jgss.GSSCredential;
import java.io.IOException;
import java.text.DateFormat;
import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

public class SRMGetRequestTokensClientV2 extends SRMClient  {
    private GlobusURL srmURL;
    private org.ietf.jgss.GSSCredential credential = null;
    private ISRM srmv2;

    public SRMGetRequestTokensClientV2(Configuration configuration, 
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
	    SrmGetRequestTokensRequest request = new SrmGetRequestTokensRequest();
	    request.setUserRequestDescription(configuration.getUserRequestDescription());
	    SrmGetRequestTokensResponse response = srmv2.srmGetRequestTokens(request);

	    if ( response == null ) { 
		throw new IOException(" null SrmGetRequestTokensResponse ");
	    }
	    TReturnStatus rs = response.getReturnStatus();
	    if ( rs == null) { 
		throw new IOException(" null TReturnStatus ");	
	    }
	    if (RequestStatusTool.isFailedRequestStatus(rs)) {
		throw new IOException("srmGetRequestTokens failed, unexpected or failed return status : "+
				      rs.getStatusCode()+" explanation="+rs.getExplanation());
	    }
	    if (response.getArrayOfRequestTokens()!=null) { 
		ArrayOfTRequestTokenReturn tokens = response.getArrayOfRequestTokens();
		if (tokens.getTokenArray()!=null) { 
		    TRequestTokenReturn tokenArray[] = tokens.getTokenArray();
		    for (int i=0;i<tokenArray.length;i++){
			String token = tokenArray[i].getRequestToken();
			Calendar date = tokenArray[i].getCreatedAtTime();
			System.out.println("Request token="+((token!=null?token:"null"))+" Created="+((date!=null)?date:"null"));
		    }
		}
		else { 	
		    System.err.println("Couldn't get list of request tokens");
		}
	    }
	    else { 
		System.err.println("No request tokens found");
	    }
	}
	catch(Exception e) {
	    say(e.toString());
	    
	}
    }
}
