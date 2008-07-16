//______________________________________________________________________________
//
// $Id$
// $Author$
//
// created 06/21 by Neha Sharma (neha@fnal.gov)
//
//______________________________________________________________________________

/*
 * SrmGetPermission
 *
 * Created on 06/21
 */

package org.dcache.srm.handler;

import org.dcache.srm.FileMetaData;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.SRMUser;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMException;
import org.apache.axis.types.URI;

/**
 *
 * @author  litvinse
 */

public class SrmGetPermission {
	private final static String SFN_STRING="?SFN=";
	AbstractStorageElement storage;
	SrmGetPermissionRequest request;
	SrmGetPermissionResponse response;
	SRMUser user;
	
	public SrmGetPermission(SRMUser user,
				RequestCredential credential,
				SrmGetPermissionRequest request,
				AbstractStorageElement storage,
				org.dcache.srm.SRM srm,
				String client_host ) {
		this.request = request;
		this.user = user;
		this.storage = storage;
	}
    
	private void say(String txt) {
		if(storage!=null) {
			storage.log("SrmGetPermission "+txt);
		}
	}
    
	private void esay(String txt) {
		if(storage!=null) {
			storage.elog("SrmGetPermission "+txt);
		}
	}
	
	private void esay(Throwable t) {
		if(storage!=null) {
			storage.elog(" SrmGetPermission exception: ");
			storage.elog(t);
		}
	}
	
	public SrmGetPermissionResponse getResponse() {
		if(response != null ) return response;
		try {
			response = srmGetPermission();
		} 
		catch(Exception e) {
			storage.elog(e);
		}
		return response;
	}
	
	public static final SrmGetPermissionResponse getFailedResponse(String error) {
		return getFailedResponse(error,null);
	}
	
	public static final SrmGetPermissionResponse getFailedResponse(String error,TStatusCode statusCode) {
		if(statusCode == null) {
			statusCode =TStatusCode.SRM_FAILURE;
		}
		TReturnStatus status = new TReturnStatus();
		status.setStatusCode(statusCode);
		status.setExplanation(error);
		SrmGetPermissionResponse response = new SrmGetPermissionResponse();
		response.setReturnStatus(status);
		return response;
	}
	
	
	/**
	 * implementation of srm get permission
	 */
	
	public SrmGetPermissionResponse srmGetPermission() throws SRMException,org.apache.axis.types.URI.MalformedURIException {
		SrmGetPermissionResponse response  = new SrmGetPermissionResponse();
		TReturnStatus returnStatus = new TReturnStatus();
		returnStatus.setStatusCode(TStatusCode.SRM_SUCCESS);
		returnStatus.setExplanation("success");
		response.setReturnStatus(returnStatus);
		if(request==null) {
			return getFailedResponse(" null request passed to SrmGetPermission()");
		}
		ArrayOfAnyURI anyuriarray = request.getArrayOfSURLs();
		URI[] uriarray            = anyuriarray.getUrlArray();
		int length = uriarray.length;
		if (length==0) { 
			return getFailedResponse(" zero length array of URLS");
		}
		String path[]=new String[length];
		ArrayOfTPermissionReturn permissionarray=new ArrayOfTPermissionReturn();
		TPermissionReturn permissionsArray[] =new TPermissionReturn[length];
		permissionarray.setPermissionArray(permissionsArray);
		boolean haveFailure = false;
		int nfailed = 0;
		for(int i=0;i <length;i++){
			say("SURL["+i+"]= "+uriarray[i]);
			path[i]  = uriarray[i].getPath(true,true);
			int indx = path[i].indexOf(SFN_STRING);
			TReturnStatus rs = new TReturnStatus();
			rs.setStatusCode(TStatusCode.SRM_SUCCESS);
			TPermissionReturn p = new TPermissionReturn();
			p.setStatus(rs);
			p.setSurl(uriarray[i]);
			if(indx != -1) {
				path[i]=path[i].substring(indx+SFN_STRING.length());
			}
			try {
				FileMetaData fmd= storage.getFileMetaData(user,path[i]);
				String owner    = fmd.owner;
				int permissions = fmd.permMode;
				TPermissionMode  upm = PermissionMaskToTPermissionMode.maskToTPermissionMode(((permissions>>6)&0x7));
				TPermissionMode  gpm = PermissionMaskToTPermissionMode.maskToTPermissionMode(((permissions>>3)&0x7));
				TPermissionMode  opm = PermissionMaskToTPermissionMode.maskToTPermissionMode((permissions&0x7));
				ArrayOfTUserPermission arrayOfTUserPermissions = new ArrayOfTUserPermission();
				TUserPermission userPermissionArray[] = new TUserPermission[1];
				for (int j=0;j<userPermissionArray.length;j++) {
					userPermissionArray[j] = new TUserPermission(owner,upm);
				}
				arrayOfTUserPermissions.setUserPermissionArray(userPermissionArray);
				ArrayOfTGroupPermission arrayOfTGroupPermissions = new ArrayOfTGroupPermission();
				TGroupPermission groupPermissionArray[] = new TGroupPermission[1];
				for (int j=0;j<groupPermissionArray.length;j++) {
					groupPermissionArray[j] = new TGroupPermission(fmd.group,gpm);
				}
				arrayOfTGroupPermissions.setGroupPermissionArray(groupPermissionArray);
				p.setOwnerPermission(upm);
				p.setArrayOfUserPermissions(arrayOfTUserPermissions);
				p.setArrayOfGroupPermissions(arrayOfTGroupPermissions);
				p.setOtherPermission(opm);
				p.setOwner(owner);
			}
			catch (SRMException srme) {
				esay(srme);
				p.getStatus().setStatusCode(TStatusCode.SRM_FAILURE);
				p.getStatus().setExplanation(uriarray[i]+" "+srme.getMessage());
				haveFailure = true;
				nfailed++;
			}
			finally {
				permissionarray.setPermissionArray(i,p);
			}
		}
		response.setArrayOfPermissionReturns(permissionarray);
		if ( haveFailure ) { 
			if ( nfailed == length ) { 
				response.getReturnStatus().setStatusCode(TStatusCode.SRM_FAILURE);
				response.getReturnStatus().setExplanation("failed to get Permission for all requested surls");
			}
			else { 
				response.getReturnStatus().setStatusCode(TStatusCode.SRM_PARTIAL_SUCCESS);
				response.getReturnStatus().setExplanation("failed to get Permission for at least one file");
				
			}
			return response;
		}
		
		response.getReturnStatus().setStatusCode(TStatusCode.SRM_SUCCESS);
		response.getReturnStatus().setExplanation("success");
		return response;
	}
}
