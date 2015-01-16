/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2014 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package diskCacheV111.srm.dcache;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.annotation.Nonnull;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import dmg.cells.services.login.LoginBrokerHandler;
import dmg.cells.services.login.LoginBrokerInfo;

import org.dcache.srm.SRM;

import static com.google.common.base.Preconditions.checkNotNull;

public class SrmLoginBrokerHandler extends LoginBrokerHandler
{
    private final static Logger _log =
            LoggerFactory.getLogger(SrmLoginBrokerHandler.class);

    private int _httpsPort;
    private String _delegationEndpoint;
    private String _host;

    @Required
    public void setHttpsPort(int port)
    {
        _httpsPort = port;
    }

    public int getHttpsPort()
    {
        return _httpsPort;
    }

    @Required
    public void setHost(String host)
    {
        _host = checkNotNull(host);
    }

    @Nonnull
    public String getHost()
    {
        return _host;
    }

    @Required
    public void setSrm(final SRM srm)
    {
        setLoadProvider(srm::getLoad);
    }

    @Override
    public void start()
    {
        try {
            _delegationEndpoint = new URI("https", null, _host, _httpsPort,
                    "/srm/delegation", null, null).toASCIIString();
        } catch (URISyntaxException e) {
            _log.error("Failed to create delegation endpoint: {}", e);
            throw Throwables.propagate(e);
        }

        super.start();
    }

    @Override
    protected LoginBrokerInfo newInfo(String cell, String domain,
                                      String protocolFamily, String protocolVersion,
                                      String protocolEngine, String root, List<InetAddress> addresses, int port,
                                      double load, long updateTime)
    {
        return new SrmLoginBrokerInfo(cell, domain, protocolFamily,
                protocolVersion, protocolEngine, root, _delegationEndpoint, addresses, port, load, updateTime);
    }


    /**
     * LoginBrokerInfo that includes SRM-specific information.
     */
    public static class SrmLoginBrokerInfo extends LoginBrokerInfo
    {
        private static final long serialVersionUID = 1L;

        private final String _delegationEndpoint;

        public SrmLoginBrokerInfo(String cell, String domain,
                                  String protocolFamily, String protocolVersion,
                                  String protocolEngine, String root, String endpoint, List<InetAddress> addresses,
                                  int port, double load, long updateTime)
        {
            super(cell, domain, protocolFamily, protocolVersion, protocolEngine,
                    root, addresses, port, load, updateTime);
            _delegationEndpoint = endpoint;
        }

        @Nonnull
        public String getDelegationEndpoint()
        {
            return _delegationEndpoint;
        }
    }
}
