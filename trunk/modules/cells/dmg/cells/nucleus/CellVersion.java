package dmg.cells.nucleus ;

import java.util.Date ;
import java.io.Serializable ;

/**
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.2, 19 Nov 2005
  */
public class CellVersion implements Serializable {

  static final long serialVersionUID = 883744769418282912L;

  private String _version  = "Unkown" ;
  private String _release  = "Unkown" ;
  private String _revision = "Unkown" ;

  public CellVersion(){}
//  public CellVersion( String version ){ 
//     _release = _revision = _version = version ; 
//  }
  public CellVersion( String release , String revision ){
      _revision = cvsStripOff(revision) ;
      _release  = release ;
      _version  = _release+"("+_revision+")";
  }
  private static String cvsStripOff( String rel ){
     int d1 = rel.indexOf("$Revision:") ;
     if( d1 < 0 )return rel ;
     String pre = ( d1 == 0 ) ? "" : rel.substring(0,d1);
     String tmp = rel.substring( d1 + 1 ) ;
     d1 = tmp.indexOf('$') ;
     if( d1 < 10 )return rel ;
     String tmp2 = tmp.substring( 9 , d1 ).trim() ;
     if( tmp2.length() == 0 )return rel ;
     String post = ( d1 == (tmp.length()-1) ) ? "" : tmp.substring(d1+1) ;
     
     return pre+tmp2+post ;
  }
  public String toString(){ return _version ; }
  public String getRelease(){ return  _release ; }
  public String getRevision(){ return _revision ; }

  public static void main( String [] args )throws Exception {
      System.out.println( cvsStripOff( args[0] ) ) ;
  }
}
