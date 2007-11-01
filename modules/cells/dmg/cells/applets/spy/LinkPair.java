package dmg.cells.applets.spy ;


public class LinkPair {

   int [] _pair = new int [2] ;
   
   public LinkPair( int x , int y ){
     if( x > y ){ _pair[0] = y ; _pair[1] = x ; }
     else       { _pair[0] = x ; _pair[1] = y ; }
   }
   public boolean equals( Object obj ){
	   
	   if( !(obj instanceof LinkPair) ) return false;
	   
       LinkPair x = (LinkPair)obj ;
       if( ( _pair[0] == x._pair[0] ) &&
           ( _pair[1] == x._pair[1] )    )return true ;
       return false ;
   }
   public int hashCode(){
      return ( _pair[0] << 16 ) | _pair[1] ;
   }
   public int compareTo( LinkPair x ){
      if( ( _pair[0] == x._pair[0] ) &&
          ( _pair[1] == x._pair[1] )     )return 0 ;
      if( ( _pair[0] < x._pair[0]  ) ||
         (( _pair[0] == x._pair[0] ) && 
          ( _pair[1] < x._pair[1] ) ) )return -1 ;
      return 1 ; 
   }
   public String toString(){
      return " [0]="+_pair[0]+" [1]="+_pair[1] ;
   }
   public int getBottom(){ return _pair[0] ; }
   public int getTop(){ return _pair[1] ; }
} 
