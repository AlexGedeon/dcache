/*
 * Report.java
 *
 * Created on April 22, 2005, 3:17 PM
 */

package gov.fnal.srm.util;

import org.globus.util.GlobusURL;
/**
 *
 * @author  timur
 */
public class Report {
    // return code constats
    public static final int OK_RC=0;
    public static final int ERROR_RC=1;
    public static final int FILE_EXISTS_RC=2;
    public static final int PERMISSION_RC=3;
    public static final int MAX_RC_VALUE=3;
    //
    public static final String INITIAL_ERROR=
        "copy did not complete or status unknown";
    // source urls
    GlobusURL from[];
    //destination urls
    GlobusURL to[];
    //return codes
    int rc[];
    //errors
    String error[];
    // number of url pairs
    int length;
    //
    private java.io.File reportFile;
    
    /** Creates a new instance of Report */
    public Report(GlobusURL from[],GlobusURL to[], String reportFileName)  {
        if(from == null || to == null) {
            throw new NullPointerException(
            "from url array and to url array should not be null");
        }
        if(reportFileName != null) {
            reportFile = new java.io.File(reportFileName);
            try {
                if(reportFile.exists() ) {
                    if(!reportFile.canWrite()) {
                        throw new IllegalArgumentException(
                            " can not write into report file : "+reportFileName);
                    }
                 }

                 else if(!reportFile.createNewFile()) {
                     throw new IllegalArgumentException(
                         " can not write into report file : "+reportFileName);
                }
            } catch ( java.io.IOException ioe) {
                throw new IllegalArgumentException(
                            " can not write into report file : "+reportFileName+
                            " : "+ioe);
            }
        }
        this.from = from;
        this.to = to;
        length = from.length;
        if(to.length != length) {
            throw new IllegalArgumentException(
            "legths of from and to archives should be the same");
        }
        rc = new int[length];
        error = new String[length];
        for(int i = 0; i <length; ++i) {
            rc[i] =ERROR_RC;
            error[i]=INITIAL_ERROR;
        }
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i <length; ++i) {
            sb.append(from[i].getURL());
            sb.append(' ');
            sb.append(to[i].getURL());
            sb.append(' ');
            sb.append(rc[i]);
            if(rc[i] != 0) {
                sb.append(' ');
                sb.append(error[i].replace('\n',' '));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
    
    public void setStatusBySourceUrl(GlobusURL srcURL,int returnCode,String errorDscr){
        //System.out.println("setStatusBySourceUrl "+srcURL.getURL()+" rc="+returnCode);
        if(!isValidRC(returnCode)) {
            throw new IllegalArgumentException("illegal return code value : "+returnCode);
        }
        for(int i = 0; i <length; ++i) {
            if(srcURL.equals(from[i])){
                rc[i] = returnCode;
                if(returnCode != 0) {
                    error[i] = errorDscr;
                }
            }
        }
    }
    
    public void setStatusByDestinationUrl(GlobusURL dstURL,int returnCode,String errorDscr){
        //System.out.println("setStatusByDestinationUrl "+dstURL.getURL()+" rc="+returnCode);
        if(!isValidRC(returnCode)) {
            throw new IllegalArgumentException("illegal return code value : "+returnCode);
        }
        for(int i = 0; i <length; ++i) {
            if(dstURL.equals(to[i])){
                rc[i] = returnCode;
                if(returnCode != 0) {
                    error[i] = errorDscr;
                }
                return;
            }
        }
        throw new IllegalArgumentException("record for dest="+
        dstURL.getURL()+" not found");
    }
    
    public void setStatusBySourceDestinationUrl(GlobusURL srcURL,GlobusURL dstURL,int returnCode,String errorDscr){
        //System.out.println("setStatusBySourceDestinationUrl "+srcURL.getURL()+" "+dstURL.getURL()+" rc="+returnCode);
        if(!isValidRC(returnCode)) {
            throw new IllegalArgumentException("illegal return code value : "+returnCode);
        }
        for(int i = 0; i <length; ++i) {
            if(srcURL.equals(from[i]) && dstURL.equals(to[i])){
                rc[i] = returnCode;
                if(returnCode != 0) {
                    error[i] = errorDscr;
                }
                return;
            }
        }
        throw new IllegalArgumentException("record for source="+
        srcURL.getURL()+" and dest="+dstURL.getURL()+" not found");
    }
    
    public boolean isValidRC(int rc) {
        return rc >= 0 && rc <= MAX_RC_VALUE;
    }
    
    public void dumpReport() {
        if(reportFile != null) {
            try {
                java.io.FileWriter fw = new java.io.FileWriter(reportFile);
                fw.write(toString());
                fw.close();
             }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public boolean everythingAllRight(){
        for(int i=0; i<rc.length;++i){
            if(rc[i] != OK_RC) {
                return false;
            }
        }
        return true;
    }
}
