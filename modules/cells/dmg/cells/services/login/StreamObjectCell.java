package dmg.cells.services.login;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;

import jline.ConsoleReader;
import jline.History;

import dmg.cells.applets.login.DomainObjectFrame;
import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellNucleus;
import dmg.util.Args;
import dmg.util.CommandExitException;
import dmg.util.StreamEngine;

import org.apache.log4j.Logger;

public class StreamObjectCell
    extends CellAdapter
    implements Runnable
{
    private static final Logger _log = Logger.getLogger(StreamObjectCell.class);

    private static final int HISTORY_SIZE = 50;
    private static final String CONTROL_C_ANSWER =
        "Got interrupt. Please issue \'logoff\' from within the admin cell to end this session.\n";

    //
    // args.argv[0] must contain a class with the following signature.
    //    <init>(Nucleus nucleus, Args args)     or
    //    <init>(Nucleus nucleus) or
    //    <init>(Args args) or
    //    <init>()
    //
    private static final Class[][] CONST_SIGNATURE = {
        { java.lang.String.class, dmg.cells.nucleus.CellNucleus.class, dmg.util.Args.class },
        { dmg.cells.nucleus.CellNucleus.class, dmg.util.Args.class },
        { dmg.cells.nucleus.CellNucleus.class },
        { dmg.util.Args.class },
        {}
    };
    private static final Class[][] COM_SIGNATURE = {
        { java.lang.Object.class },
        { java.lang.String.class },
        { java.lang.String.class, java.lang.Object.class  },
        { java.lang.String.class, java.lang.String.class  }
    };

    private StreamEngine _engine;
    private InetAddress _host;
    private String _user;
    private Thread _workerThread;
    private CellNucleus _nucleus;
    private File _historyFile;

    private Object _commandObject;
    private Method[] _commandMethod = new Method[COM_SIGNATURE.length];
    private Method _promptMethod;
    private Method _helloMethod;

    public StreamObjectCell(String name, StreamEngine engine, Args args)
        throws Exception
    {
        super(name, args, false);

        _engine = engine;
        _nucleus = getNucleus();
        setCommandExceptionEnabled(true);
        try {
            if (args.argc() < 1)
                throw new
                    IllegalArgumentException("Usage : ... <commandClassName>");

            String s = args.getOpt("history");
            if (s != null && !s.isEmpty()) {
                _historyFile = new File(s);
            }

            _log.info("StreamObjectCell " + getCellName() + "; arg0=" + args.argv(0));

            _user = engine.getUserName().getName();
            _host = engine.getInetAddress();

            prepareClass(args.argv(0));
        } catch (Exception e) {
            start();
            kill();
            throw e;
        }
        useInterpreter(false);
        start();
        _workerThread = _nucleus.newThread(this, "Worker");
        _workerThread.start();
    }

    private void prepareClass(String className)
        throws ClassNotFoundException,
               NoSuchMethodException,
               InstantiationException,
               IllegalAccessException,
               InvocationTargetException
    {
        int commandConstMode = -1;
        Constructor commandConst = null;
        Class commandClass = Class.forName(className);
        NoSuchMethodException nsme = null;

        _log.info("Using class : " + commandClass);
        for (int i = 0; i < CONST_SIGNATURE.length; i++) {
            nsme = null;
            Class [] x = CONST_SIGNATURE[i];
            _log.info("Looking for constructor : " + i);
            for (int ix = 0; ix < x.length; ix++) {
                _log.info("   arg["+ix+"] "+x[ix]);
            }
            try {
                commandConst = commandClass.getConstructor(CONST_SIGNATURE[i]);
            } catch (NoSuchMethodException e) {
                _log.info("Constructor not found : " + CONST_SIGNATURE[i]);
                nsme = e;
                continue;
            }
            commandConstMode = i;
            break;
        }
        if (nsme != null) {
            throw nsme;
        }
        _log.info("Using constructor : " + commandConst);

        int validMethods = 0;
        for (int i= 0; i < COM_SIGNATURE.length; i++) {
            try {
                _commandMethod[i] = commandClass.getMethod("executeCommand",
                                                           COM_SIGNATURE[i]);
                validMethods ++;
            } catch (Exception e) {
                _commandMethod[i]= null;
                continue;
            }
            _log.info("Using method [" + i + "] " + _commandMethod[i]);
        }
        if (validMethods == 0)
            throw new
                IllegalArgumentException("no valid executeCommand found");

        try {
            _promptMethod = commandClass.getMethod("getPrompt", new Class[0]);
        } catch (Exception e) {
            _promptMethod = null;
        }
        if (_promptMethod != null)
            _log.info("Using promptMethod : " + _promptMethod);
        try {
            _helloMethod = commandClass.getMethod("getHello", new Class[0]);
        }catch(Exception ee){
            _helloMethod = null;
        }
        if (_helloMethod != null) {
            _log.info( "Using helloMethod : " + _helloMethod);
        }

        Args extArgs = (Args) getArgs().clone();
        Object [] args = null;
        extArgs.shift();
        switch (commandConstMode) {
        case 0:
            args = new Object[3];
            args[0] = _user;
            args[1] = getNucleus();
            args[2] = extArgs;
            break;
        case 1:
            args = new Object[2];
            args[0] = getNucleus();
            args[1] = extArgs;
            break;
        case 2:
            args = new Object[1];
            args[0] = getNucleus();
            break;
        case 3:
            args = new Object[1];
            args[0] = extArgs;
            break;
        case 4:
            args = new Object[0];
            break;

        }
        _commandObject = commandConst.newInstance(args);
    }

    private String getPrompt()
    {
        if (_promptMethod == null) {
            return "";
        }
        try {
            String s =
                (String) _promptMethod.invoke(_commandObject, new Object[0]);
            return (s == null) ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private String getHello()
    {
        if (_helloMethod == null) {
            return null;
        }
        try {
            String s =
                (String) _helloMethod.invoke(_commandObject, new Object[0]);
            return (s == null) ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    public void run()
    {
        try {
            History history;
            if (_historyFile != null) {
                history  = new History(_historyFile);
            } else {
                history = new History();
            }
            try {
                Socket socket = _engine.getSocket();
                final ConsoleReader console =
                    new ConsoleReader(_engine.getInputStream(),
                                      _engine.getWriter());
                history.setMaxSize(HISTORY_SIZE);
                console.setHistory(history);
                console.setUseHistory(true);
                console.addTriggeredAction(ConsoleReader.CTRL_C, new ActionListener()
                    {
                        public void actionPerformed(ActionEvent event)
                        {
                            try {
                                console.printString(CONTROL_C_ANSWER);
                                console.flushConsole();
                            } catch (IOException e) {
                                _log.warn("I/O failure: " + e);
                            }
                        }
                    });


                String hello = getHello();
                if (hello != null) {
                    console.printString(hello);
                    console.flushConsole();
                }

                runAsciiMode(console);

                /* To cleanly shut down the connection, we first
                 * shutdown the output and then wait for an EOF on the
                 * input stream.
                 */
                console.flushConsole();
                socket.shutdownOutput();
                while (console.readLine() != null);
            } finally {
                /* ConsoleReader doesn't close the history file itself.
                 */
                PrintWriter out = history.getOutput();
                if (out != null) {
                    out.close();
                }
            }
        } catch (IllegalAccessException e) {
            _log.fatal("Failed to execute command: " + e);
        } catch (ClassNotFoundException e) {
            _log.warn("Binary mode failure: " + e);
        } catch (IOException e) {
            _log.warn("I/O Failure: " + e);
        } finally {
            _log.info("StreamObjectCell (worker) done.");
            kill();
        }
    }

    private class BinaryExec implements Runnable
    {
        private ObjectOutputStream _out;
        private DomainObjectFrame _frame;
        private Thread _parent;

        BinaryExec(ObjectOutputStream out,
                   DomainObjectFrame frame, Thread parent)
        {
            _out = out;
            _frame  = frame;
            _parent = parent;
            _nucleus.newThread(this).start();
        }

        public void run()
        {
            Object result;
            boolean done = false;
            _log.info("Frame id "+_frame.getId()+" arrived");
            try {
                if (_frame.getDestination() == null) {
                    Object [] array  = new Object[1];
                    array[0] = _frame.getPayload();
                    if (_commandMethod[0] != null) {
                        _log.info("Choosing executeCommand(Object)");
                        result = _commandMethod[0].invoke(_commandObject, array);
                    } else if(_commandMethod[1] != null) {
                        _log.info("Choosing executeCommand(String)");
                        array[0] = array[0].toString();
                        result = _commandMethod[1].invoke(_commandObject, array);

                    } else {
                        throw new
                            Exception("PANIC : not found : executeCommand(String or Object)");
                    }
                } else {
                    Object [] array  = new Object[2];
                    array[0] = _frame.getDestination();
                    array[1] = _frame.getPayload();
                    if (_commandMethod[2] != null) {
                        _log.info("Choosing executeCommand(String destination, Object)");
                        result = _commandMethod[2].invoke(_commandObject, array);

                    } else if (_commandMethod[3] != null) {
                        _log.info("Choosing executeCommand(String destination, String)");
                        array[1] = array[1].toString();
                        result = _commandMethod[3].invoke(_commandObject, array);
                    } else {
                        throw new
                            Exception("PANIC : not found : "+
                                       "executeCommand(String/String or Object/String)");
                    }
                }
            } catch (InvocationTargetException ite) {
                result = ite.getTargetException();
                done = result instanceof CommandExitException;
            } catch (Exception ae) {
                result = ae;
            }
            _frame.setPayload(result);
            try {
                synchronized(_out){
                    _out.writeObject(_frame);
                    _out.flush();
                    _out.reset();  // prevents memory leaks...
                }
            } catch (IOException e) {
                _log.error("Problem sending result : " + e);
            }
            if (done) {
                _parent.interrupt();
            }
        }
    }

    private void runBinaryMode()
        throws IOException, ClassNotFoundException
    {
        ObjectOutputStream out =
            new ObjectOutputStream(_engine.getOutputStream());
        ObjectInputStream in =
            new ObjectInputStream(_engine.getInputStream());
        Object obj;
        while ((obj = in.readObject()) != null) {
            if (obj instanceof DomainObjectFrame) {
                new BinaryExec(out, (DomainObjectFrame)obj, Thread.currentThread());
            } else {
                _log.error("Won't accept non DomainObjectFrame : " + obj.getClass());
            }
        }
    }

    private void runAsciiMode(ConsoleReader console)
        throws IOException, ClassNotFoundException, IllegalAccessException
    {
        Method com =
            (_commandMethod[1] != null) ? _commandMethod[1] : _commandMethod[0];

        boolean done = false;
        while (!done) {
            String str = console.readLine(getPrompt());
            if (str == null) {
                break;
            }

            if (str.equals("$BINARY$")) {
                _log.info("Opening Object Streams");
                console.printString(str);
                runBinaryMode();
                break;
            }

            Object result;
            try {
                result = com.invoke(_commandObject, str);
            } catch (InvocationTargetException ite) {
                result = ite.getTargetException();
                done = (result instanceof CommandExitException);
            }
            if (result != null) {
                String s = result.toString();
                if (!s.isEmpty()){
                    console.printString(s);
                    if (s.charAt(s.length() - 1) != '\n') {
                        console.printNewline();
                    }
                    console.flushConsole();
                }
            }
        }
    }

    public void cleanUp()
    {
        try {
            _engine.getSocket().close();
        } catch (IOException e) {
            _log.error("Failed to close socket: " + e);
        }
        if (_workerThread != null) {
            _workerThread.interrupt();
        }
    }
}
