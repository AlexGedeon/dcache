package dmg.security.cipher ;
import  dmg.security.cipher.idea.* ;
import  dmg.security.cipher.des.* ;
import  dmg.security.cipher.blowfish.* ;
import  java.util.*;

public class StreamTest {
  private static byte [] __key =
    { 10 , 33 , -10 , -49 , 112 , -8 , 7 , -109 ,
      55 , -9 , -101 , -4 , -112 , -46 , 94 , -124    } ;
  
  private static String __usage = "USAGE : StreamTest idea|des|blowfish ecb|cfb|cbc [blocks]" ;    
  public static void main( String [] args ){
     BlockCipher cipher = null  ;
     
     if( args.length < 3 ){
        System.err.println( __usage ) ;
        System.exit(3) ;
     }
     String cipherType = args[0] ;
     String cipherMode = args[1] ;
     int    blocks     = new Integer( args[2] ).intValue() ;
     Random r          = new Random( new Date().getTime() ) ;
//     byte [] key       = __key ;
     byte [] key = new byte[16] ;
     r.nextBytes( key ) ;     
     if( cipherType.equals( "idea" ) ){
         cipher = new Jidea( key ) ;
     }else if( cipherType.equals( "des" ) ){
         cipher = new Jdes( key ) ;
     }else if( cipherType.equals( "blowfish" ) ){
         cipher = new Jblowfish( key ) ;
     }else{
        System.err.println( __usage ) ;
        System.exit(4) ;
     }
     int     block  = cipher.getBlockLength() / 8 ;
     byte [] vector = new byte[block] ;
     
     StreamFromBlockCipher encrypt = 
        new StreamFromBlockCipher( cipher , vector ) ;
     StreamFromBlockCipher decrypt = 
        new StreamFromBlockCipher( cipher , vector ) ;
  
     byte [] in  = new byte[block*blocks] ;
     byte [] out = new byte[block*blocks] ;
     byte [] chk = new byte[block*blocks] ;
     
     r.nextBytes( in ) ;
     
     long start = 0 , en = 0 , de = 0 ;
     
     if( cipherMode.equals("ecb") ){
       start = new Date().getTime() ;
       for( int i = 0 ; i < blocks ; i++ )
          encrypt.encryptECB( in , i*block , out , i*block ) ;       
       
       en = new Date().getTime() ;
       for( int i = 0 ; i < blocks ; i++ )
          decrypt.decryptECB( out , i*block , chk , i*block ) ;       
       
       de = new Date().getTime() ;
     }else if( cipherMode.equals("cfb") ){
       start = new Date().getTime() ;
       encrypt.encryptCFB( in , 0 , out , 0 , block*blocks ) ;              
       en = new Date().getTime() ;
       decrypt.decryptCFB( out , 0 , chk , 0 , block*blocks ) ;              
       de = new Date().getTime() ;
     }else if( cipherMode.equals("cbc") ){
       start = new Date().getTime() ;
       encrypt.encryptCBC( in , 0 , out , 0 , block*blocks ) ;              
       en = new Date().getTime() ;
       decrypt.decryptCBC( out , 0 , chk , 0 , block*blocks ) ;              
       de = new Date().getTime() ;
     }else{
        System.err.println( __usage ) ;
        System.exit(5) ;
     }
     say( " Cipher Type       : "+cipherType ) ;
     say( " Cipher Mode       : "+cipherMode ) ;
     say( " Cipher Block size : "+block ) ;
     say( " Encryption Key    : "+byteToHexString( key ) ) ;
     say( " Encryption Time   : "+(en-start) ) ;
     say( " Decryption Time   : "+(de-en) ) ;
     if( blocks < 5 ){
        say( " Original Data     : "+byteToHexString( in ) ) ;
        say( " Encrypted Data    : "+byteToHexString( out ) ) ;
        say( " Decrypted Data    : "+byteToHexString( chk ) ) ;
     }
     int i; 
     for( i = 0 ; i < in.length ; i++ )
        if( in[i] != chk[i] )break ;
     if( i < in.length )System.exit(3) ;
     System.exit(0);
  
  }
  static public String byteToHexString( byte b ) {
       String s = Integer.toHexString( ( b < 0 ) ? ( 256 + (int)b ) : (int)b  ) ;
       if( s.length() == 1 )return "0"+s ;
       else return s ;
  }
  static public String byteToHexString( byte [] b ) {
       String out = "" ;
       for( int i = 0 ; i < b.length ; i ++ )
          out += ( byteToHexString( b[i] ) + " " ) ;
       return out ;
    
  }
  private static void say( String str ){ System.out.println( str ) ; }

}
