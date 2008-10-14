package org.dcache.xrootd.security;

import java.security.GeneralSecurityException;
import java.util.Map;

import org.dcache.doors.XrootdDoor;

/**
 * The interface to different authorization plugins .
 * @author radicke
 *
 */
public interface AuthorizationHandler {

	
	/**
	 * This method examines the granted permissions of the path (file) which is going to be opened. It then checks
	 * whether the permissions are sufficient to open the file in the requested mode (read or write).
	 * @param pathToOpen the file which is checked
	 * @param options the opaque data from the open request 
	 * @param wantToWrite the requested mode
	 * @return true, if and only if access is granted according to the requested open mode (authorization successful). 
	 * @throws GeneralSecurityException when the process of authorizing fails
	 */
	public boolean checkAuthz(String pathToOpen, Map options, boolean wantToWrite, XrootdDoor context) throws GeneralSecurityException;
	
	/**
	 * Indicates whether the authorization plugin provides an LFN (logical file name)-to-PFN (physical file name).
	 * In this case, the path contained in the xrootd open request is just the LFN. The "real" path which is going
	 * to be opened is then resolved by the authorization module. 
	 * 
	 * @return true, if the PFN is resolved by the athorization handler
	 */
	public boolean providesPFN();
	
	/**
	 * If authorization plugin provides the LFN-to-PFN-mapping, this method will return the PFN.
	 * @return the PFN or null if no mapping is done by the underlying authorization plugin.
	 */
	public String getPFN();

	
	/**
	 * Returns a username (e.g. DN) if available
	 * @return the username or null if not supported
	 */
	public String getUser();
}
