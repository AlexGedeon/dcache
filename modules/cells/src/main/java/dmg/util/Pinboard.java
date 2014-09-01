package dmg.util ;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Vector;

public class Pinboard {

    private static final ThreadLocal<DateFormat> _df =
            new ThreadLocal<DateFormat>()
            {
                @Override
                protected DateFormat initialValue()
                {
                    return new SimpleDateFormat("hh.mm.ss ");
                }
            };

   private final List<PinEntry>       _pin = new Vector<>() ;
   private final int          _size;
   private class PinEntry {
      private String _message ;
      private Date   _date ;
      public PinEntry( String message ){
         _message = message ;
         _date = new Date() ;
      }
      public String toString(){
         return _df.get().format(_date)+" "+_message ;
      }
   }
   public Pinboard(){
      this( 20 ) ;
   }
   public Pinboard( int size ){
      _size = size ;
   }
   public synchronized void pin( String note ){
      _pin.add( new PinEntry( note ) ) ;
      if( _pin.size() > _size ) {
          _pin.remove(0);
      }
   }
   public synchronized void dump( StringBuffer sb ){
       dump( sb , _size ) ;
   }
   public synchronized void dump( StringBuffer sb , int last ){
       int i = _pin.size() - last + 1 ;
       for( i =i<0?0:i ; i < _pin.size() ; i++) {
           sb.append(_pin.get(i).toString()).append("\n");
       }
   }
   public synchronized void dump( File file ) throws IOException {
       dump( file , _size ) ;
   }
   public synchronized void dump( File file , int last ) throws IOException {
       int i =  _pin.size() - last + 1 ;

       PrintWriter pw = new PrintWriter( new FileWriter( file ) ) ;
       for( i =i<0?0:i ; i < _pin.size() ; i++) {
           pw.println(_pin.get(i).toString());
       }
       pw.close() ;

   }

}
