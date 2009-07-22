package org.dcache.cells;

import java.util.Date;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.Formatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collection;
import java.util.Collections;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileReader;
import java.io.BufferedReader;
import java.lang.reflect.InvocationTargetException;
import java.beans.PropertyDescriptor;

import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.NoRouteToCellException;
import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellInfo;
import dmg.cells.nucleus.CellPath;
import dmg.cells.services.SetupInfoMessage;
import dmg.util.Args;
import dmg.util.CommandException;

import org.dcache.util.ClassNameComparator;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Universal cell for building complex cells from simpler components.
 *
 * This class aims at being a universal cell for dCache. It makes it
 * possible to construct complex cells from simpler cell
 * components. The Spring Framework is used to manage the lifetime of
 * the cell components, as well as the wiring between components. We
 * therfore borrow the term "bean" from the Spring Framework and refer
 * to cell components as beans.
 *
 * Beans can get access to core cell functionality by implementing one
 * or more of the following interfaces: CellInfoProvider,
 * CellCommunicationAware, ThreadFactoryAware, CellCommandListener,
 * and CellSetupAware. When instantiated through this class, those
 * interfaces are detected and the necessary wiring is performed
 * automatically.
 */
public class UniversalSpringCell
    extends AbstractCell
    implements BeanPostProcessor
{
    /**
     * Spring application context. All beans are created through this
     * context.
     */
    private ConfigurableApplicationContext _context;

    /**
     * List of registered info providers. Sorted to maintain
     * consistent ordering.
     */
    private final Set<CellInfoProvider> _infoProviders =
        new TreeSet<CellInfoProvider>(new ClassNameComparator());

    /**
     * List of registered setup providers. Sorted to maintain
     * consistent ordering.
     */
    private final Set<CellSetupProvider> _setupProviders =
        new TreeSet<CellSetupProvider>(new ClassNameComparator());

    /**
     * List of registered life cycle aware beans. Sorted to maintain
     * consistent ordering.
     */
    private final Set<CellLifeCycleAware> _lifeCycleAware =
        new TreeSet<CellLifeCycleAware>(new ClassNameComparator());


    /**
     * Cell name of the setup controller.
     */
    private String _setupController;

    /**
     * Setup class used for sending a setup to the setup controller.
     */
    private String _setupClass;

    /**
     * Setup to execute during start and to which to save the setup.
     */
    private File _setupFile;

    public UniversalSpringCell(String cellName, String arguments)
        throws InterruptedException, ExecutionException
    {
        super(cellName, arguments);
        doInit();
    }

    @Override
    protected void executeInit()
        throws Exception
    {
        /* Process command line arguments.
         */
        Args args = getArgs();
        if (args.argc() == 0)
            throw new IllegalArgumentException("Configuration location missing");

        _setupController = args.getOpt("setupController");
        info("Setup controller set to "
             + (_setupController == null ? "none" : _setupController));
        _setupFile =
            (args.getOpt("setupFile") == null)
            ? null
            : new File(args.getOpt("setupFile"));
        _setupClass = args.getOpt("setupClass");

        if (_setupController != null && _setupClass == null)
            throw new IllegalArgumentException("Setup class must be specified when a setup controller is used");

        /* Instantiate Spring application context. This will
         * eagerly instantiate all beans.
         */
        _context =
            new UniversalSpringCellApplicationContext(getArgs());

        /* This is a NOP except if somebody subclassed
         * UniversalSpringCell.
         */
        init();

        /* The timeout task is essential to handle cell message
         * timeouts.
         */
        startTimeoutTask();

        /* Execute setup. In case the setup file is missing, we wait
         * until it becomes available.
         */
        if (_setupFile != null) {
            while (!_setupFile.exists()) {
                error("Setup does not exists; waiting");
                Thread.sleep(30000);
            }
        }
        executeSetup();

        /* Now that everything is instantiated and configured, we can
         * start the cell.
         */
        start();

        /* Run the final initialisation hooks.
         */
        for (CellLifeCycleAware bean: _lifeCycleAware) {
            bean.afterStart();
        }
    }

    /**
     * Closes the application context, which will shutdown all beans.
     */
    public void cleanUp()
    {
        super.cleanUp();
        for (CellLifeCycleAware bean: _lifeCycleAware) {
            bean.beforeStop();
        }
        if (_context != null) {
            _context.close();
            _context = null;
        }
        _infoProviders.clear();
        _setupProviders.clear();
        _lifeCycleAware.clear();
    }

    private void executeSetup()
        throws IOException, CommandException
    {
        for (CellSetupProvider provider: _setupProviders) {
            provider.beforeSetup();
        }

        executeDefinedSetup();
        if (_setupFile != null) {
            execFile(_setupFile);
        }

        for (CellSetupProvider provider: _setupProviders) {
            provider.afterSetup();
        }
    }

    @Override
    public void messageToForward(CellMessage envelope)
    {
        super.messageToForward(envelope);
    }

    @Override
    public void messageArrived(CellMessage envelope)
    {
        super.messageArrived(envelope);
    }

    /**
     * Returns the name of a given bean, or null if no such bean is
     * defined.
     *
     * This method is relatively expensive and should not be used for
     * time critical purposes.
     */
    private String getNameOfBean(Object bean)
    {
        ConfigurableListableBeanFactory factory = _context.getBeanFactory();
        Map<String,Object> map = factory.getBeansOfType(bean.getClass());
        for (Map.Entry<String, Object> e: map.entrySet()) {
            if (e.getValue() == bean)
                return e.getKey();
        }
        return null;
    }

    /**
     * Returns the singleton bean with a given name. Returns null if
     * such a bean does not exist.
     */
    private Object getBean(String name)
    {
        try {
            if (_context != null && _context.isSingleton(name)) {
                return _context.getBean(name);
            }
        } catch (NoSuchBeanDefinitionException e) {
        }
        return null;
    }

    /**
     * Returns the names of beans that depend of a given bean.
     */
    private List<String> getDependentBeans(String name)
    {
        if (_context == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(_context.getBeanFactory().getDependentBeans(name));
        }
    }

    /**
     * Returns the collection of singleton bean names.
     */
    private List<String> getBeanNames()
    {
        if (_context == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(_context.getBeanFactory().getSingletonNames());
        }
    }

    /**
     * Collects information from all registered info providers.
     */
    public void getInfo(PrintWriter pw)
    {
        ConfigurableListableBeanFactory factory = _context.getBeanFactory();
        for (CellInfoProvider provider : _infoProviders) {
            String name = getNameOfBean(provider);
            try {
                BeanDefinition definition = factory.getBeanDefinition(name);
                String description = definition.getDescription();
                if (description != null) {
                    pw.println(String.format("--- %s (%s) ---",
                                             name, description));
                } else {
                    pw.println(String.format("--- %s ---", name));
                }
                provider.getInfo(pw);
                pw.println();
            } catch (NoSuchBeanDefinitionException e) {
                error("Failed to query bean definition for " + name);
            }
        }
    }

    /**
     * Collects information about the cell and returns these in a
     * CellInfo object. Information is collected from the CellAdapter
     * base class and from all beans implementing CellInfoProvider.
     */
    public CellInfo getCellInfo()
    {
        CellInfo info = super.getCellInfo();
        for (CellInfoProvider provider : _infoProviders)
            info = provider.getCellInfo(info);
        return info;
    }

    /**
     * Collects setup information from all registered setup providers.
     */
    protected void printSetup(PrintWriter pw)
    {
        pw.println("#\n# Created by " + getCellName() + "("
                   + getClass().getName() + ") at " + (new Date()).toString()
                   + "\n#");
        for (CellSetupProvider provider: _setupProviders)
            provider.printSetup(pw);
    }

    public final String hh_save = "[-sc=<setupController>|none] [-file=<filename>] # saves setup to disk or setup controller";
    public String ac_save(Args args)
        throws IOException, IllegalArgumentException, NoRouteToCellException
    {
        String controller = args.getOpt("sc");
        String file = args.getOpt("file");

        if ("none".equals(controller)) {
            controller = null;
            file = _setupFile.getPath();
        } else if (file == null && controller == null) {
            controller = _setupController;
            file = _setupFile.getPath();
        }

        if (file == null && controller == null) {
            throw new IllegalArgumentException("Either a setup controller or setup file must be specified");
        }

        if (controller != null) {
            if (_setupClass == null || _setupClass.equals(""))
                throw new IllegalStateException("Cannot save to a setup controller since the cell has no setup class");

            try {
                StringWriter sw = new StringWriter();
                printSetup(new PrintWriter(sw));

                SetupInfoMessage info =
                    new SetupInfoMessage("put", getCellName(),
                                         _setupClass, sw.toString());

                sendMessage(new CellMessage(new CellPath(controller), info));
            } catch (NoRouteToCellException e) {
                throw new NoRouteToCellException("Failed to send setup to " + controller + ": " + e.getMessage());
            }
        }

        if (file != null) {
            File path = new File(file).getAbsoluteFile();
            File directory = path.getParentFile();
            File temp = File.createTempFile(path.getName(), null, directory);
            temp.deleteOnExit();

            PrintWriter pw = new PrintWriter(new FileWriter(temp));
            try {
                printSetup(pw);
            } finally {
                pw.close();
            }

            renameWithBackup(temp, path);
        }

        return "";
    }

    private static void renameWithBackup(File source, File dest)
        throws IOException
    {
        File backup = new File(dest.getPath() + ".bak");

        if (dest.exists()) {
            if (!dest.isFile()) {
                throw new IOException("Cannot rename " + dest + ": Not a file");
            }
            if (backup.exists()) {
                if (!backup.isFile()) {
                    throw new IOException("Cannot delete " + backup + ": Not a file");
                }
                if (!backup.delete()) {
                    throw new IOException("Failed to delete " + backup);
                }
            }
            if (!dest.renameTo(backup)) {
                throw new IOException("Failed to rename " + dest);
            }
        }
        if (!source.renameTo(dest)) {
            throw new IOException("Failed to rename" + source);
        }
    }

    private void execFile(File setup)
        throws IOException, CommandException
    {
        BufferedReader br = new BufferedReader(new FileReader(setup));
        String line;
        try {
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                ++lineCount;

                line = line.trim();
                if (line.length() == 0)
                    continue;
                if (line.charAt(0) == '#')
                    continue;
                try {
                    command(new Args(line));
                } catch (CommandException e) {
                    throw new CommandException("Error at line " + lineCount
                                               + ": " + e.getMessage());
                }
            }
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                // ignored
            }
        }
    }

    public String hh_reload = "-yes";
    public String fh_reload =
        "This command destroys the current setup and replaces it" +
        "by the setup on disk.";
    public String ac_reload(Args args)
        throws IOException, CommandException
    {
        if (args.getOpt("yes") == null) {
            return
                " This command destroys the current setup\n" +
                " and replaces it by the setup on disk\n" +
                " Please use 'reload -yes' if you really want\n" +
                " to do that.";
        }

        if (_setupFile != null && !_setupFile.exists()) {
            return String.format("Setup file [%s] does not exist", _setupFile);
        }

        executeSetup();

        return "";
    }

    /**
     * Should be renamed to 'info' when command interpreter learns
     * to handle overloaded commands.
     */
    public static final String hh_infox = "<bean>";
    public String ac_infox_$_1(Args args)
    {
        String name = args.argv(0);
        Object bean = getBean(name);
        if (CellInfoProvider.class.isInstance(bean)) {
            StringWriter s = new StringWriter();
            PrintWriter pw = new PrintWriter(s);
            ((CellInfoProvider)bean).getInfo(pw);
            return s.toString();
        }
        return "No such bean: " + name;
    }

    public static final String hh_bean_ls = "# lists running beans";
    public String ac_bean_ls(Args args)
    {
        final String format = "%-30s %s\n";
        Formatter s = new Formatter(new StringBuilder());
        ConfigurableListableBeanFactory factory = _context.getBeanFactory();

        s.format(format, "Bean", "Description");
        s.format(format, "----", "-----------");
        for (String name : getBeanNames()) {
            try {
                BeanDefinition definition = factory.getBeanDefinition(name);
                String description = definition.getDescription();
                s.format(format, name,
                         (description != null ? description : "-"));
            } catch (NoSuchBeanDefinitionException e) {
                error("Failed to query bean definition for " + name);
            }
        }
        return s.toString();
    }

    public static final String hh_bean_dep = "# shows bean dependencies";
    public String ac_bean_dep(Args args)
    {
        final String format = "%-30s %s\n";
        Formatter s = new Formatter(new StringBuilder());
        ConfigurableListableBeanFactory factory = _context.getBeanFactory();

        s.format(format, "Bean", "Used by");
        s.format(format, "----", "-------");
        for (String name : getBeanNames()) {
            s.format(format, name, collectionToString(getDependentBeans(name)));
        }
        return s.toString();
    }

    /**
     * If given a simple bean name, returns that bean (equivalent to
     * calling getBean). If given a compound name using the syntax of
     * BeanWrapper, then the respective property value is
     * returned. E.g., getBeanProperty("foo") returns the bean named
     * "foo", whereas getBeanProperty("foo.bar") returns the value of
     * the "bar" property of bean "foo".
     */
    private Object getBeanProperty(String s)
    {
        String[] a = s.split("\\.", 2);
        Object o = getBean(a[0]);
        if (o != null && a.length == 2) {
            BeanWrapper bean = new BeanWrapperImpl(o);
            o = bean.isReadableProperty(a[1])
                ? bean.getPropertyValue(a[1])
                : null;
        }
        return o;
    }

    public static final String hh_bean_properties =
        "<bean> # shows properties of a bean";
    public String ac_bean_properties_$_1(Args args)
    {
        String name = args.argv(0);
        Object o = getBeanProperty(name);
        if (o != null) {
            StringBuilder s = new StringBuilder();
            BeanWrapper bean = new BeanWrapperImpl(o);
            for (PropertyDescriptor p : bean.getPropertyDescriptors()) {
                if (!p.isHidden()) {
                    String property = p.getName();
                    if (bean.isReadableProperty(property)) {
                        Object value = bean.getPropertyValue(property);
                        s.append(property).append('=').append(value);
                        if (!bean.isWritableProperty(property)) {
                            s.append(" [read-only]");
                        }
                        s.append('\n');
                    }
                }
            }
            return s.toString();
        }
        return "No such bean: " + name;
    }

    public static final String hh_bean_property =
        "<property-name> # shows property of a bean";
    public String ac_bean_property_$_1(Args args)
    {
        String name = args.argv(0);
        Object o = getBeanProperty(name);
        return (o != null) ? o.toString() : "No such bean: " + name;
    }

    /** Returns a formated name of a message class. */
    protected String getMessageName(Class c)
    {
        String name = c.getSimpleName();
        int length = name.length();
        if ((length > 7) && name.endsWith("Message")) {
            name = name.substring(0, name.length() - 7);
        } else if ((length > 3) && name.endsWith("Msg")) {
            name = name.substring(0, name.length() - 3);
        }

        return name;
    }

    public static final String hh_bean_messages =
        "[<bean>] # shows message types handled by beans";
    public String ac_bean_messages_$_0_1(Args args)
    {
        switch (args.argc()) {
        case 0:
            Map<String,Collection<Class>> map = new HashMap();
            for (String name: getBeanNames()) {
                Object bean = getBean(name);
                if (CellMessageReceiver.class.isInstance(bean)) {
                    Collection<Class> types =
                        _messageDispatcher.getMessageTypes(bean);
                    map.put(name, types);
                }
            }

            final String format = "%-40s %s\n";
            Formatter f = new Formatter(new StringBuilder());
            f.format(format, "Message", "Receivers");
            f.format(format, "-------", "---------");
            for (Map.Entry<Class,Collection<String>> e: invert(map).entrySet()) {
                f.format(format,
                         getMessageName(e.getKey()),
                         collectionToString(e.getValue()));
            }

            return f.toString();

        case 1:
            String name = args.argv(0);
            Object bean = getBean(name);
            if (CellMessageReceiver.class.isInstance(bean)) {
                StringBuilder s = new StringBuilder();
                Collection<Class> types =
                    _messageDispatcher.getMessageTypes(bean);
                for (Class t : types) {
                    s.append(getMessageName(t)).append('\n');
                }
                return s.toString();
            }
            return "No such bean: " + name;

        default:
            return "";
        }
    }

    /**
     * Registers an info provider. Info providers contribute to the
     * result of the <code>getInfo</code> method.
     */
    public void addInfoProviderBean(CellInfoProvider bean)
    {
        _infoProviders.add(bean);
    }

    /**
     * Add a message receiver. Message receiver receive messages via
     * message handlers (see CellMessageDispatcher).
     */
    public void addMessageReceiver(CellMessageReceiver bean)
    {
        addMessageListener(bean);
    }

    /**
     * Add a message sender. Message senders can send cell messages
     * via a cell endpoint.
     */
    public void addMessageSender(CellMessageSender bean)
    {
        bean.setCellEndpoint(this);
    }

    /**
     * Registers a setup provider. Setup providers contribute to the
     * result of the <code>save</code> method.
     */
    public void addSetupProviderBean(CellSetupProvider bean)
    {
        _setupProviders.add(bean);
    }

    /**
     * Registers a life cycle aware bean. Life cycle aware beans are
     * notified about cell start and stop events.
     */
    public void addLifeCycleAwareBean(CellLifeCycleAware bean)
    {
        _lifeCycleAware.add(bean);
    }

    /**
     * Registers a thread factory aware bean. Thread factory aware
     * bean provide hooks for registering thread factories. This
     * method registers the Cell nulceus thread factory on the bean.
     */
    public void addThreadFactoryAwareBean(ThreadFactoryAware bean)
    {
        bean.setThreadFactory(getNucleus());
    }


    /**
     * Part of the BeanPostProcessor implementation. Recognizes beans
     * implementing CellCommandListener, CellInfoProvider,
     * CellCommunicationAware, CellSetupProvider and
     * ThreadFactoryAware and performs the necessary wiring.
     */
    public Object postProcessBeforeInitialization(Object bean,
                                                  String beanName)
        throws BeansException
    {
        if (CellCommandListener.class.isInstance(bean)) {
            addCommandListener(bean);
        }

        if (CellInfoProvider.class.isInstance(bean)) {
            addInfoProviderBean((CellInfoProvider)bean);
        }

        if (CellMessageReceiver.class.isInstance(bean)) {
            addMessageReceiver((CellMessageReceiver)bean);
        }

        if (CellMessageSender.class.isInstance(bean)) {
            addMessageSender((CellMessageSender)bean);
        }

        if (CellSetupProvider.class.isInstance(bean)) {
            addSetupProviderBean((CellSetupProvider)bean);
        }

        if (CellLifeCycleAware.class.isInstance(bean)) {
            addLifeCycleAwareBean((CellLifeCycleAware)bean);
        }

        if (ThreadFactoryAware.class.isInstance(bean)) {
            addThreadFactoryAwareBean((ThreadFactoryAware)bean);
        }
        return bean;
    }

    public Object postProcessAfterInitialization(Object bean,
                                                 String beanName)
        throws BeansException
    {
        return bean;
    }

    class UniversalSpringCellApplicationContext
        extends ClassPathXmlApplicationContext
    {
        UniversalSpringCellApplicationContext(Args args)
        {
            super(args.argv(0));
        }

        private ByteArrayResource getDomainContextResource()
        {
            Args args = (Args)getArgs().clone();
            args.shift();

            Properties properties = new Properties();
            String arguments =
                args.toString().replaceAll("-?\\$\\{.*\\}", "");
            properties.setProperty("arguments", arguments);
            properties.setProperty("thisCell", getCellName());
            properties.setProperty("thisDomain", getCellDomainName());
            mergeProperties(properties, getDomainContext());
            mergeProperties(properties, args.options());

            /* Convert to byte array form such that we can make it
             * available as a Spring resource.
             */
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                properties.store(out, "");
            } catch (IOException e) {
                /* This should never happen with a ByteArrayOutputStream.
                 */
                throw new RuntimeException("Unexpected exception", e);
            }
            final byte[] _domainContext = out.toByteArray();

            return new ByteArrayResource(_domainContext) {
                /**
                 * Fake file name to make
                 * PropertyPlaceholderConfigurer happy.
                 */
                public String getFilename()
                {
                    return "domaincontext.properties";
                }
            };
        }

        public Resource getResource(String location)
        {
            if (location.startsWith("domaincontext:")) {
                return getDomainContextResource();
            } else {
                return super.getResource(location);
            }
        }

        protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory)
        {
            beanFactory.addBeanPostProcessor(UniversalSpringCell.this);
        }
    }

    /**
     * Utility method for converting a collection of objects to a
     * string. The string is formed by concatenating the string form
     * of the objects, separated by a comma.
     */
    private <T> String collectionToString(Collection<T> collection)
    {
        StringBuilder s = new StringBuilder();
        for (T o: collection) {
            if (s.length() > 0) {
                s.append(',');
            }
            s.append(o);
        }
        return s.toString();
    }

    /**
     * Merges a map into a property set.
     */
    private void mergeProperties(Properties properties, Map<?,?> map)
    {
        for (Map.Entry<?,?> e: map.entrySet()) {
            Object key = e.getKey();
            Object value = e.getValue();
            properties.setProperty(key.toString(), value.toString());
        }
    }

    /**
     * Utility method for inverting a map.
     *
     * Given a map { "a" => { 1, 2, 3}, "b" => {2, 3, 4}, c => {3, 4,
     * 5} }, this method returns a new map { 1 => { "a" }, 2 => { "a",
     * "b" }, 3 => { "a", "b", "c" }, 4 => { "b", "c" }, 5 => { "c"
     * }}.
     *
     * TODO: Should be moved to a utility library.
     */
    private <T1,T2> Map<T1,Collection<T2>> invert(Map<T2,Collection<T1>> map)
    {
        Map<T1,Collection<T2>> result = new HashMap();
        for (Map.Entry<T2,Collection<T1>> e : map.entrySet()) {
            for (T1 value : e.getValue()) {
                Collection<T2> collection = result.get(value);
                if (collection == null) {
                    collection = new ArrayList<T2>();
                    result.put(value, collection);
                }
                collection.add(e.getKey());
            }
        }
        return result;
    }
}