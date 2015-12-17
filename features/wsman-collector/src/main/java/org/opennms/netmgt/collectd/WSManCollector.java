/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.collectd;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.opennms.core.spring.BeanUtils;
import org.opennms.core.wsman.WSManClient;
import org.opennms.core.wsman.WSManClientFactory;
import org.opennms.core.wsman.WSManEndpoint;
import org.opennms.core.wsman.WSManVersion;
import org.opennms.core.wsman.cxf.CXFWSManClientFactory;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionInitializationException;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.ServiceCollector;
import org.opennms.netmgt.collection.support.CollectionSetBuilder;
import org.opennms.netmgt.collection.support.CollectionSetBuilder.Status;
import org.opennms.netmgt.config.wsman.Attrib;
import org.opennms.netmgt.config.wsman.Wpm;
import org.opennms.netmgt.config.wsman.WsmanCollection;
import org.opennms.netmgt.dao.WSManConfigDao;
import org.opennms.netmgt.dao.WSManDataCollectionConfigDao;
import org.opennms.netmgt.events.api.EventProxy;
import org.opennms.netmgt.rrd.RrdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * WS-Man Collector
 *
 * @author jwhite
 */
public class WSManCollector implements ServiceCollector {
    private static final Logger LOG = LoggerFactory.getLogger(WSManCollector.class);

    private WSManDataCollectionConfigDao m_wsManDataCollectionConfigDao;

    private WSManConfigDao m_wsManConfigDao;

    private WSManClientFactory m_factory = new CXFWSManClientFactory();
    private Map<CollectionAgent, WSManEndpoint> m_agentEndpoints = Maps.newConcurrentMap();

    @Override
    public void initialize(Map<String, String> parameters) throws CollectionInitializationException {
        LOG.debug("initialize({})", parameters);
        // Retrieve the configuration DAOs
        m_wsManConfigDao = BeanUtils.getBean("daoContext", "wsManConfigDao", WSManConfigDao.class);
        m_wsManDataCollectionConfigDao = BeanUtils.getBean("daoContext", "wsManDataCollectionConfigDao", WSManDataCollectionConfigDao.class);
    }

    @Override
    public void release() {
        LOG.debug("release()");
    }

    @Override
    public void initialize(CollectionAgent agent, Map<String, Object> parameters) throws CollectionInitializationException {
        LOG.debug("initialize({}, {})", agent, parameters);
        URL url;
        try {
            url = new URL(String.format("https://%s:443/wsman", agent.getHostAddress()));
        } catch (MalformedURLException e) {
            throw new CollectionInitializationException("" + e);
        }
        final WSManEndpoint endpoint = new WSManEndpoint.Builder(url)
                .withBasicAuth(m_wsManConfigDao.getConfig().getUsername(), m_wsManConfigDao.getConfig().getPassword())
                .withServerVersion(WSManVersion.WSMAN_1_0)
                .withMaxElements(100)
                .withStrictSSL(false)
                .build();
        LOG.info("MOO1: Using endpoint {} for agent {} with username: {} and password {}", endpoint);
        m_agentEndpoints.put(agent, endpoint);
    }

    @Override
    public void release(CollectionAgent agent) {
        LOG.debug("release({})", agent);
        m_agentEndpoints.remove(agent);
    }

    /**
     * FIXME: When do we throw exceptions vs returning a failed collection set?
     */
    @Override
    public CollectionSet collect(CollectionAgent agent, EventProxy eproxy, Map<String, Object> parameters) {
        LOG.debug("collect({}, {}, {})", agent, eproxy, parameters);
        final CollectionSetBuilder collectionSetBuilder = new CollectionSetBuilder(agent);

        final Object collectionNameParam = parameters.get("collection");
        if (collectionNameParam == null || !(collectionNameParam instanceof String)) {
            LOG.error("Collector configuration does not include the required 'collection' parameter.");
            return collectionSetBuilder.withStatus(Status.FAILED).build();
        }
        final String collectionName = (String)collectionNameParam;

        final WsmanCollection collection = m_wsManDataCollectionConfigDao.getDataCollectionByName(collectionName);
        if (collection == null) {
            LOG.error("No collection found with name: {}", collectionName);
            return collectionSetBuilder.withStatus(Status.FAILED).build();
        }

        final WSManEndpoint endpoint = m_agentEndpoints.get(agent);
        if (endpoint == null) {
            LOG.error("No agent configuration found for: {}", agent);
            return collectionSetBuilder.withStatus(Status.FAILED).build();
        }

        final WSManClient client = m_factory.getClient(endpoint);
        for (Wpm wpm : collection.getWpms().getWpm()) {
            final List<Node> nodes = Lists.newLinkedList();
            final String namespace = wpm.getWsmanNamespace();
            // FIXME: This might throw a RuntimeException
            LOG.info("Enumerating {} on {}", namespace, client);
            client.enumerateAndPull(namespace, nodes, true);
            LOG.info("Found {}", nodes);

            for (Node node : nodes) {
                Map<String, Double> elementValues = Maps.newHashMap();
                
                // Parse the value from the child nodes
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node child = children.item(i);

                    if (child.getLocalName() == null) {
                        continue;
                    }

                    double value = Double.NaN;
                    try {
                        String content = child.getTextContent();
                        if (content != null) {
                            value = Double.parseDouble(content.trim());
                        }
                    } catch (NumberFormatException e) {
                        // pass
                    }

                    elementValues.put(child.getLocalName(), value);
                }

                LOG.debug("Element values: {}", elementValues);
                for (Attrib attrib : wpm.getAttrib()) {
                    Double value = elementValues.get(attrib.getName());
                    if (value == null) {
                        continue;
                    }
                    collectionSetBuilder.withNumericAttribute(attrib.getName(), value);
                }
                
                //FIXME: We're only processing the first node
                break;
            }
        }

        return collectionSetBuilder.build();
    }

    @Override
    public RrdRepository getRrdRepository(String collectionName) {
        LOG.debug("getRrdRepository({})", collectionName);
        RrdRepository rrdRepository = new RrdRepository();
        rrdRepository.setHeartBeat(60);
        rrdRepository.setStep(30);
        rrdRepository.setRraList(Lists.newArrayList("RRA:AVERAGE:0.5:1:2016", "RRA:AVERAGE:0.5:12:1488", "RRA:AVERAGE:0.5:288:366", "RRA:MAX:0.5:288:366", "RRA:MIN:0.5:288:366"));
        rrdRepository.setRrdBaseDir(Paths.get(System.getProperty("opennms.home"), "share", "rrd", "snmp").toFile());
        return rrdRepository;
    }
}
