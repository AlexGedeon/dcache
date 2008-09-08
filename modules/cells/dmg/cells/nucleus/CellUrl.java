package dmg.cells.nucleus ;


import java.io.* ;
import java.net.* ;
import java.util.* ;
import java.lang.reflect.* ;

public class CellUrl {
   private                CellGlue  _glue   = null ;
   private              Dictionary _context = null ;
   private URLStreamHandlerFactory _others  = null ;

   public CellUrl( CellGlue glue ){
       
        java.util.Properties p = System.getProperties();
        String s = p.getProperty("java.protocol.handler.pkgs");
        if(s != null)
        {
            s= s+"|dmg.cells.nucleus.protocols";
        }
        else
        {
            s="dmg.cells.nucleus.protocols";
        }
                      
        p.setProperty("java.protocol.handler.pkgs",s);
        System.setProperties(p);
   }
   
   public static class DomainUrlConnection extends URLConnection 
   {
       private String      _protocol    = null ;
       private CellNucleus _nucleus     = null ;
       private Dictionary  _environment = null ;
       public DomainUrlConnection( URL url , String protocol ){
          super( url ) ;          
          _protocol = protocol ;
       }
       public void say(String s)
       {
           if(_nucleus != null)
           {
               _nucleus.say(s);
           }
       }
       public void esay(String s)
       {
           if(_nucleus != null)
           {
               _nucleus.esay(s);
           }
       }
       public void connect(){
          
           say( "DomainUrlConnection : Connect called" ) ;
           return ;
       }
       public void setNucleus( CellNucleus nucleus ){
          _nucleus = nucleus ;
       }
       
       public void setEnvironment( Dictionary environment ){
          _environment = environment ;
       }
       public InputStream getInputStream() throws IOException {
          if( _nucleus == null )
             throw new IOException( "Nucleus not defined" ) ;
           
          throw new IOException( "getInputStream not supported on : "+_protocol ) ;
          /*  
          if( _protocol.equals( "context" ) ){
          
          }else if( _protocol.equals( "env" ) ){
          
          }else if( _protocol.equals( "cell" ) ){
          
          }else
             throw new IOException( "Protocol not supported : "+_protocol ) ;
          */
       }
       public Reader getReader() throws IOException {
          if( _nucleus == null )
             throw new  IOException( "Nucleus not defined" ) ;
             
          if( _protocol.equals( "context" ) ){
             if( url.getHost().equals("") ){
                String filePart = url.getFile() ;
                filePart = ( filePart.length() > 0     ) && 
                           ( filePart.charAt(0) == '/' ) ? 
                           filePart.substring(1) :
                           filePart ;
                return 
                _nucleus.getDomainContextReader( filePart ) ;
             }else 
                return getRemoteContextReader(  _nucleus , url ) ;
          }else if( _protocol.equals( "env" ) ){
             if( _environment == null )
                throw new  IOException( "Nucleus not defined" ) ;
                
              String filePart = url.getFile() ;
              filePart = ( filePart.length() > 0     ) && 
                         ( filePart.charAt(0) == '/' ) ? 
                         filePart.substring(1) :
                         filePart ;
             return getDictionaryReader( _environment , filePart ) ;
          }else if( _protocol.equals( "cell" ) ){
             return getRemoteCellReader( _nucleus , url )  ;
          }else
             throw new IOException( "Protocol not supported : "+_protocol ) ;
       }
       public String getContentType(){ return "text/context" ; }
       public String toString(){ return "DomainUrlConnection of : "+_protocol ; }
       //
       // helpers
       //
       private Reader getRemoteCellReader( CellNucleus nucleus , URL url )
               throws IOException  {
               
          Object o = getRemoteData( nucleus ,
                                    url.getHost() ,
                                    url.getFile().substring(1) ,
                                    4000 ) ;
                                    
          if( o instanceof Exception )throw new IOException( o.toString() ) ;
          
          return new StringReader( o.toString() ) ;    
       }
       private Reader getRemoteContextReader( CellNucleus nucleus , URL url )
               throws IOException {
          
          Object o = getRemoteData( nucleus ,
                                    "System@"+url.getHost() ,
                                    "show context "+url.getFile().substring(1) ,
                                    4000 ) ;
                                    
          if( o instanceof Exception )throw new IOException( o.toString() ) ;
          
          return new StringReader( o.toString() ) ;    
       }
       private Object getRemoteData( CellNucleus nucleus ,
                                     String path , 
                                     String command , 
                                     long timeout    )
               throws IOException {
       
         CellMessage answer = null ;
         try{
            answer = nucleus.sendAndWait( 
                           new CellMessage( new CellPath( path ) , 
                                            command ) , 
                           timeout 
                                         ) ;
         }catch( Exception e ){
            throw new IOException( "sendAndWait : "+e.toString() ) ;
         }           
         if( answer == null )
            throw new IOException( "Request timed out" ) ;
                
         return answer.getMessageObject() ;

       }
       private Reader getDictionaryReader( Dictionary env , String name ) 
               throws IOException {
          Object o ;
          if( ( o = env.get( name ) ) == null )
            throw new IOException( "Not found : "+name ) ;
            
          return new StringReader( o.toString() ) ;
       }
       private Reader getDictionaryReaderx( Cell cell , String name ) 
               throws IOException {
               
          Class cellClass = cell.getClass() ;
          say("DomainUrlConnection : Cell Class is : "+cellClass ) ;
          Class [] argsClasses = new Class[0] ;
          try{
             Method method = cellClass.getDeclaredMethod( 
                                "getEnvironmentDictionary" , 
                                 argsClasses                  ) ;
             Object [] args = new Object[0] ;
             
             Dictionary dir = (Dictionary)method.invoke( cell , args ) ;    
          
             Object o = dir.get( name ) ;
             if( o == null )
                throw new IOException( "Not found : "+name ) ;
                
             return new StringReader( o.toString() ) ;
             
          }catch( Exception e ){
             throw new IOException( "Problem : "+e ) ;
          }
       
       }
   }
   public static void main(String args[]) throws Exception
   {
       new CellUrl(null);
       System.out.println("checking the creation of context url");
       URL url1 = new URL("context://localhost:1111//sfs");
       System.out.println("checking the creation of cell url");
       URL url2 = new URL("cell://localhost:1111//sfs");
       System.out.println("checking the creation of env url");
       URL url3 = new URL("env://localhost:1111//sfs");
       System.out.println("done");
   }
}
