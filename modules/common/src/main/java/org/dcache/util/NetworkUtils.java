package org.dcache.util;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.net.InetAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Predicates.and;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterators.*;

/**
 * Various network related utility functions.
 */
public abstract class NetworkUtils {

    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    public static final String LOCAL_HOST_ADDRESS_PROPERTY = "org.dcache.net.localaddresses";

    private static final String canonicalHostName;
    private static final int RANDOM_PORT = 23241;

    private static final List<InetAddress> FAKED_ADDRESSES;

    private static final Supplier<Iterable<InetAddress>> LOCAL_ADDRESS_SUPPLIER =
            Suppliers.memoizeWithExpiration(new LocalAddressSupplier(), 5, TimeUnit.SECONDS);

    static {
        String value = nullToEmpty(System.getProperty(LOCAL_HOST_ADDRESS_PROPERTY));
        ImmutableList.Builder<InetAddress> fakedAddresses = ImmutableList.builder();
        for (String address: Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
            fakedAddresses.add(InetAddresses.forString(address));
        }
        FAKED_ADDRESSES = fakedAddresses.build();
        canonicalHostName = getPreferredHostName();
    }

    public static String getCanonicalHostName() {
        return canonicalHostName;
    }

    /**
     * Returns the list of IP addresses of this host.
     *
     * @return
     * @throws SocketException
     */
    public static Iterable<InetAddress> getLocalAddresses() {
        if (!FAKED_ADDRESSES.isEmpty()) {
            return FAKED_ADDRESSES;
        }
        return filter(LOCAL_ADDRESS_SUPPLIER.get(), isNotMulticast());
    }

    /**
     * Like URI.toURL, but translates exceptions to URISyntaxException
     * with a descriptive error message.
     */
    public static URL toURL(URI uri)
        throws URISyntaxException
    {
        try {
            return uri.toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            URISyntaxException exception =
                new URISyntaxException(uri.toString(), e.getMessage());
            exception.initCause(e);
            throw exception;
        }
    }

    /**
     * Return a local address that is likely reachable from {@code expectedSource}.
     */
    public static InetAddress getLocalAddress(InetAddress expectedSource)
            throws SocketException
    {
        InetAddress localAddress = getLocalAddress(expectedSource, getProtocolFamily(expectedSource));
        if (localAddress == null) {
            if (!FAKED_ADDRESSES.isEmpty()) {
                localAddress = FAKED_ADDRESSES.get(0);
            } else {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.connect(expectedSource, RANDOM_PORT);
                    localAddress = socket.getLocalAddress();

                    /* DatagramSocket#getLocalAddress reports errors by returning the
                     * wildcard address. There are several cases in which it does this,
                     * such as when the host it is unable to serve the protocol family,
                     * has no route to the address, or in case of Max OS X and Windows XP
                     * due to bugs (see http://goo.gl/ENXkD).
                     *
                     * We fall back to enumerating all local network addresses and choose
                     * the one with the smallest scope not smaller than the scope of the
                     * expected source.
                     */
                    if (localAddress.isAnyLocalAddress()) {
                        InetAddressScope minScope = InetAddressScope.of(expectedSource);
                        try {
                            return Ordering.natural().onResultOf(InetAddressScope.OF).min(
                                    filter(LOCAL_ADDRESS_SUPPLIER.get(),
                                           and(greaterThanOrEquals(minScope),
                                               isNotMulticast())));
                        } catch (NoSuchElementException e) {
                            throw new SocketException("Unable to find address that faces " + expectedSource);
                        }
                    }
                }
            }
        }
        return localAddress;
    }

    /**
     * Like getLocalAddress(InetAddress), but returns an addresses from the given protocolFamily
     * that is likely reachable from {@code expectedSource}. Returns null if such an address
     * could not be determined.
     */
    public static InetAddress getLocalAddress(InetAddress expectedSource, ProtocolFamily protocolFamily)
            throws SocketException
    {
        if (!FAKED_ADDRESSES.isEmpty()) {
            for (InetAddress address : FAKED_ADDRESSES) {
                if (getProtocolFamily(address) == protocolFamily) {
                    return address;
                }
            }
            return null;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(expectedSource, RANDOM_PORT);
            InetAddress localAddress = socket.getLocalAddress();

            /* DatagramSocket#getLocalAddress reports errors by returning the
             * wildcard address. There are several cases in which it does this,
             * such as when the host it is unable to serve the protocol family,
             * has no route to the address, or in case of Max OS X and Windows XP
             * due to bugs (see http://goo.gl/ENXkD).
             *
             * We fall back to enumerating all local network addresses and choose
             * the one with the smallest scope which matches the desired protocol
             * family and has a scope at least as big as the expected source.
             */
            if (localAddress.isAnyLocalAddress()) {
                InetAddressScope minScope = InetAddressScope.of(expectedSource);
                try {
                    return Ordering.natural().onResultOf(InetAddressScope.OF).min(
                            filter(LOCAL_ADDRESS_SUPPLIER.get(),
                                   and(greaterThanOrEquals(minScope),
                                       hasProtocolFamily(protocolFamily),
                                       isNotMulticast())));
                } catch (NoSuchElementException e) {
                    return null;
                }
            }

            /* It is quite possible that the expected source has a different protocol
             * family than the one we are expected to serve. In that case we try to
             * find a matching address from the same network interface (which we know
             * faces the expected source).
             */
            if (getProtocolFamily(localAddress) != protocolFamily) {
                InetAddressScope intendedScope = InetAddressScope.of(expectedSource);
                NetworkInterface byInetAddress = NetworkInterface.getByInetAddress(localAddress);
                try {
                    return Ordering.natural().onResultOf(InetAddressScope.OF).min(
                            Iterators.filter(forEnumeration(byInetAddress.getInetAddresses()),
                                             and(greaterThanOrEquals(intendedScope),
                                                 hasProtocolFamily(protocolFamily),
                                                 isNotMulticast())));
                } catch (NoSuchElementException e) {
                    return null;
                }
            }
            return localAddress;
        }
    }

    private static Predicate<InetAddress> isNotMulticast()
    {
        return new Predicate<InetAddress>()
        {
            @Override
            public boolean apply(InetAddress address)
            {
                return !address.isMulticastAddress();
            }
        };
    }

    private static Predicate<InetAddress> hasProtocolFamily(final ProtocolFamily protocolFamily)
    {
        return new Predicate<InetAddress>()
        {
            @Override
            public boolean apply(InetAddress address)
            {
                return getProtocolFamily(address) == protocolFamily;
            }
        };
    }

    private static Predicate<InetAddress> greaterThanOrEquals(final InetAddressScope scope)
    {
        return new Predicate<InetAddress>()
        {
            @Override
            public boolean apply(InetAddress address)
            {
                return InetAddressScope.of(address).ordinal() >= scope.ordinal();
            }
        };
    }

    public static ProtocolFamily getProtocolFamily(InetAddress address)
    {
        if (address instanceof Inet4Address) {
            return StandardProtocolFamily.INET;
        }
        if (address instanceof Inet6Address) {
            return StandardProtocolFamily.INET6;
        }
        throw new IllegalArgumentException("Unknown protocol family: " + address);
    }

    private static String getPreferredHostName() {
        List<InetAddress> addresses =
                Ordering.natural().onResultOf(InetAddressScope.OF).reverse().sortedCopy(getLocalAddresses());
        if (addresses.isEmpty()) {
            return "localhost";
        }
        /* For legibility, we prefer to see a traditional
         * DNS host name; but if there is no mapping,
         * use the first address.
         */
        for (InetAddress a: addresses) {
            String hostName = stripScope(a.getCanonicalHostName());
            if (!InetAddresses.isInetAddress(hostName)) {
                return hostName;
            }
        }
        return addresses.get(0).getCanonicalHostName();
    }

    /*
     * Workaround for bug in Guava, which should not
     * return the scoping portion of the address.  There
     * is a patch for this, but it has not yet been
     * applied to InetAddresses in our current library version.
     */
    private static String stripScope(String hostName) {
        int i = hostName.indexOf("%");
        if (i > 0) {
            return hostName.substring(0, i);
        }
        return hostName;
    }

    public static boolean isInetAddress(String hostname) {
        return InetAddresses.isInetAddress(stripScope(hostname));
    }

    /**
     * Returns an InetAddress with the result of InetAddress#getCanonicalHostName
     * filled in as the hostname. Subsequent calls to InetAddress#getHostName
     * will return the canonical name without further lookups.
     */
    public static InetAddress withCanonicalAddress(InetAddress address)
    {
        try {
            return InetAddress.getByAddress(address.getCanonicalHostName(), address.getAddress());
        } catch (UnknownHostException e) {
            return address;
        }
    }

    /**
     * The scope of an address captures the extend of the validity of
     * an internet address.
     */
    public static enum InetAddressScope
    {
        LOOPBACK,
        LINK,
        SITE,
        GLOBAL;

        public static InetAddressScope of(InetAddress address)
        {
            if (address.isLoopbackAddress()) {
                return LOOPBACK;
            }
            if (address.isLinkLocalAddress()) {
                return LINK;
            }
            if (address.isSiteLocalAddress()) {
                return SITE;
            }
            return GLOBAL;
        }

        public static final Function<InetAddress,InetAddressScope> OF =
                new Function<InetAddress, InetAddressScope>()
                {
                    @Override
                    public InetAddressScope apply(InetAddress address)
                    {
                        return of(address);
                    }
                };
    }

    /**
     *  A supplier that returns all internet addresses of network interfaces that are up.
     */
    private static class LocalAddressSupplier implements Supplier<Iterable<InetAddress>>
    {
        @Override
        public Iterable<InetAddress> get()
        {
            try {
                return Lists.newArrayList(
                        concat(transform(forEnumeration(NetworkInterface.getNetworkInterfaces()),
                                         new Function<NetworkInterface, Iterator<InetAddress>>()
                                         {
                                             @Override
                                             public Iterator<InetAddress> apply(NetworkInterface i)
                                             {
                                                 try {
                                                     if (i.isUp()) {
                                                         return forEnumeration(i.getInetAddresses());
                                                     }
                                                 } catch (SocketException ignored) {
                                                 }
                                                 return Collections.emptyIterator();
                                             }
                                         })));
            } catch (SocketException e) {
                logger.error("Failed to resolve local network addresses: {}", e.toString());
                return Collections.emptyList();
            }
        }
    }
}
