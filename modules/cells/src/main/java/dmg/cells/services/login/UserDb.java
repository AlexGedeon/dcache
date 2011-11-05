package dmg.cells.services.login ;

import dmg.util.cdb.* ;
import java.io.* ;
import java.util.* ;

public class UserDb extends CdbGLock  {
   public static void main( String [] args ) throws Exception {
   
        UserDb _db ;
        try{
           _db =  new UserDb( new File(".") , true ) ;
        }catch( Exception e ){
//           System.out.println( "Can't create , trying to open" ) ;
           _db = new UserDb( new File(".") , false ) ;
        }
        if( args.length < 2 ){
            System.err.println( "USAGE : ... create-user <user>" ) ;
            System.err.println( "            destroy-user <user>" ) ;
            System.err.println( "            add-user <group> <user>" ) ;
            System.err.println( "            create-group <group>" ) ;
            System.err.println( "            rm-user <group> <user>" ) ;
            System.err.println( "            show-user <user>" ) ;
            System.err.println( "            set-password <user> <password>" ) ;
            System.err.println( "            add-priv p|n <user> <privilege>" ) ;
            System.err.println( "            rm-priv  <user> <privilege>" ) ;
            System.err.println( "            get-parents <user>" ) ;
            System.err.println( "            isallowed <user> <privilege>" ) ;
            System.exit(4);
        }
        try{
        
        if( args[0].equals( "create-user" ) ){
           _db.createUser( args[1] ) ;
        }else if( args[0].equals( "create-group" ) ){
           _db.createGroup( args[1] ) ;
        }else if( args[0].equals( "destroy-user" ) ){
           _db.destroyUser( args[1] ) ;
        }else if( args[0].equals( "show-user" ) ){
           UserHandle user = _db.getUserByName( args[1] ) ;
           System.out.println( user.toString() ) ;
           System.out.println( "Global prives : " ) ;
           System.out.println( _db.getUserPrivileges(args[1]).toString() ) ;
        }else if( args[0].equals( "get-parents" ) ){
           long start = System.currentTimeMillis() ;
           String [] parents = _db.getAllParents( args[1] ) ;
           long diff = System.currentTimeMillis() -  start ;
           for( int i = 0 ; i < parents.length ; i++ ){
               System.out.println( parents[i] ) ;
           }
           System.out.println( "(Time="+diff+" millis)" ) ;
        }else if( args[0].equals( "add-user" ) ){
           if( args.length < 3 )
           throw new IllegalArgumentException( "add-user <group> <user>" ) ;
           _db.addUser( args[1] , args[2] ) ;
        }else if( args[0].equals( "rm-user" ) ){
           if( args.length < 3 )
           throw new IllegalArgumentException( "add-user <group> <user>" ) ;
           _db.addUser( args[1] , args[2] ) ;
        }else if( args[0].equals( "set-password" ) ){
           if( args.length < 3 )
           throw new IllegalArgumentException( "set-password <user> <passwd>" ) ;
           UserHandle user = _db.getUserByName( args[1] ) ;
           user.open( CdbLockable.WRITE ) ;
              user.setPassword( args[2] ) ;
           user.close( CdbLockable.COMMIT ) ;
        }else if( args[0].equals( "isallowed" ) ){
           if( args.length < 3 )
           throw new IllegalArgumentException( "isallowed <user> <privileged>" ) ;
           UserPrivileges priv = _db.getUserPrivileges( args[1] ) ;
           System.out.println( "Result : "+priv.isAllowed( args[2] ) ) ;
        }else if( args[0].equals( "add-priv" ) ){
           if( ( args.length < 4 ) || 
               ( ( ! args[1].equals("p") ) && ( ! args[1].equals("n") ) ) )
           throw new IllegalArgumentException( "add-priv p|n <user> <privilege>" ) ;
           UserHandle user = _db.getUserByName( args[2] ) ;
           user.open( CdbLockable.WRITE ) ;
              if( args[1].equals("p") )
                 user.addAllowed( args[3] ) ;
              else
                 user.addDenied( args[3] ) ;
           user.close( CdbLockable.COMMIT ) ;
        }else if( args[0].equals( "rm-priv" ) ){
           if( args.length < 3 ) 
           throw new IllegalArgumentException( "rm-priv <user> <privilege>" ) ;
           UserHandle user = _db.getUserByName( args[1] ) ;
           user.open( CdbLockable.WRITE ) ;
                 user.removeAllowed( args[2] ) ;
                 user.removeDenied( args[2] ) ;
           user.close( CdbLockable.COMMIT ) ;
        }else
           throw new 
           IllegalArgumentException( "Command not known : "+args[0] ) ;
           

      }catch(Exception eeee ){
         System.out.println( eeee.getMessage() ) ;
      }

   }
   private CdbDirectoryContainer _userContainer = null ;
   
   public UserDb( File file , boolean create ) throws CdbException {
      
      if( ! file.isDirectory() )
         throw new CdbException( "Database doesn't exits : "+file ) ;
         
      _userContainer = 
               new CdbDirectoryContainer(
                          this ,
                          dmg.util.cdb.CdbFileRecord.class ,
                          dmg.cells.services.login.UserHandle.class ,
                          new File( file , "users" ) ,
                          create ) ;
                          
 
   }
   public void destroyUser( String userName ) throws Exception {
   
       UserHandle user  = getUserByName( userName ) ;
       
       boolean isGroup ;
       String [] childs ;
       user.open( CdbLockable.READ ) ;
         isGroup = user.isGroup()  ;
         childs  = user.getChilds() ;
       user.close( CdbLockable.COMMIT ) ;
       
       if( isGroup && ( childs.length > 0 ) )
           throw new 
           IllegalArgumentException( "group not empty : "+userName ) ;
           
       user.open( CdbLockable.WRITE ) ;
          String [] parents = user.getParents() ;
          for( int i= 0 ; i < parents.length ; i++ ){
             UserHandle x = getUserByName( parents[i] ) ;
             x.open( CdbLockable.WRITE ) ;
             x.removeChild( userName ) ;
             x.close( CdbLockable.COMMIT ) ;
             user.removeParent( parents[i] ) ;
          }
       user.close( CdbLockable.COMMIT ) ;
       _userContainer.removeElement( userName ) ;
   }
   public void removeUser( String groupName , String userName ) throws Exception {
       UserHandle user  = getUserByName( userName ) ;
       UserHandle group = getUserByName( groupName ) ;
       boolean isGroup ;
       group.open( CdbLockable.READ ) ;
         isGroup = group.isGroup()  ;
       group.close( CdbLockable.COMMIT ) ;
       if( ! isGroup )
           throw new 
           IllegalArgumentException( "Not a group : "+groupName ) ;
           
       group.open( CdbLockable.WRITE ) ;
         group.removeChild( userName ) ;
       group.close( CdbLockable.COMMIT ) ;
       try{
          user.open( CdbLockable.WRITE ) ;
            user.removeParent( groupName ) ;
          user.close( CdbLockable.COMMIT ) ;
       }catch( Exception ee ){
          group.open( CdbLockable.WRITE ) ;
            group.addChild( userName ) ;
          group.close( CdbLockable.COMMIT ) ;
          throw ee ;
       }


   }
   private String [] getAllParents( String userName ) throws Exception {
       UserHandle user = getUserByName( userName ) ;
       String [] parents ;
       user.open( CdbLockable.READ ) ;
         parents = user.getParents()  ;
       user.close( CdbLockable.COMMIT ) ;
       Hashtable hash  = new Hashtable() ;
       for( int i = 0 ; i < parents.length ; i++ ){
           hash.put( parents[i] , parents[i] ) ;
           String [] x = getAllParents( parents[i] ) ;
           for( int j = 0 ; j < x.length ; j++ ){
              hash.put( x[j] , x[j] ) ;
           }
       }
       String [] result = new String[hash.size()] ;
       Enumeration e = hash.keys() ;
       for( int i  = 0  ; e.hasMoreElements() ;  i++  ){
           result[i] = (String)e.nextElement() ;
       }
       return result ;
   }
   public UserPrivileges getUserPrivileges( String userName ) throws Exception {
       UserHandle user ;
       try{
          user = getUserByName( userName ) ;
       }catch( Exception ee ){
          return  new UserPrivileges( userName ) ;
       }
       String [] parents ;
       UserPrivileges myPrivs = null ;
       user.open( CdbLockable.READ ) ;
         parents = user.getParents()  ;
         myPrivs = user.getUserPrivileges() ;
       user.close( CdbLockable.COMMIT ) ;
       
       UserPrivileges upper = new UserPrivileges() ;
       for( int i = 0 ; i < parents.length ; i++ ){
           upper.mergeHorizontal( getUserPrivileges( parents[i] ) ) ;
       }
       myPrivs.mergeVertical( upper ) ;
       
       return myPrivs ;
   
   }
   public void addUser( String groupName , String userName ) throws Exception {
       
       UserHandle user  = getUserByName( userName ) ;
       UserHandle group = getUserByName( groupName ) ;
       
       String [] parents = getAllParents( groupName ) ;
       int i = 0 ;
       for(  i = 0 ; 
             ( i < parents.length ) &&
             ( ! parents[i].equals(userName) ) ; i++ ) ;
       if( i < parents.length )
           throw new 
           IllegalArgumentException( "would create loop >"+groupName+"-"+userName+"<" ) ;
        
       boolean isGroup ;
       String [] childs ;
       group.open( CdbLockable.READ ) ;
         isGroup = group.isGroup()  ;
         childs  = group.getChilds() ;
       group.close( CdbLockable.COMMIT ) ;
       if( ! isGroup )
           throw new 
           IllegalArgumentException( "Not a group : "+groupName ) ;
           
       group.open( CdbLockable.WRITE ) ;
         try{
            group.addChild( userName ) ;
         }catch( IllegalArgumentException iae ){
            throw iae ;
         }finally{
             group.close( CdbLockable.COMMIT ) ;
         }
       try{
          user.open( CdbLockable.WRITE ) ;
            user.addParent( groupName ) ;
          user.close( CdbLockable.COMMIT ) ;
       }catch( Exception ee ){
          group.open( CdbLockable.WRITE ) ;
            group.removeChild( userName ) ;
          group.close( CdbLockable.COMMIT ) ;
          throw ee ;
       }
       
   }

   public UserHandle createUser( String name )
        throws CdbException , InterruptedException {
        return createUser( name , false ) ;
   }
   public UserHandle createGroup( String name )
        throws CdbException , InterruptedException {
        return createUser( name , true ) ;
   }
   public UserHandle createUser( String name , boolean isGroup )
        throws CdbException , InterruptedException {
          UserHandle user =
                (UserHandle)
                   _userContainer.createElement( name ) ;

        user.open( CdbLockable.WRITE ) ;
          user.setAttribute( "e-mail" , "unknown" ) ;
          user.setAttribute( "password" , "*" ) ;
          user.setAttribute( "allowed" , new String[0] ) ;
          user.setAttribute( "denied"  , new String[0] ) ;
          user.setAttribute( "parents"  , new String[0] ) ;
          if( isGroup ){
              user.setAttribute( "mode" , "group" ) ;
              user.setAttribute( "childs" , new String[0] ) ;
          }else{
              user.setAttribute( "mode" , "user" ) ;
          }
        user.close( CdbLockable.COMMIT ) ;

        return user ;
    }
    public String [] getUserNames(){
       return _userContainer.getElementNames() ;
    }
    public UserHandle 
           getUserByName( String name )
        throws CdbException , InterruptedException {
       return (UserHandle)
             _userContainer.getElementByName( name ) ;
    }
   
}
