/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.dao.jaxb;

import org.opennms.core.xml.AbstractJaxbConfigDao;
import org.opennms.netmgt.config.wsman.WsmanCollection;
import org.opennms.netmgt.config.wsman.WsmanDatacollectionConfig;
import org.opennms.netmgt.dao.WSManDataCollectionConfigDao;

public class WSManDataCollectionConfigDaoJaxb extends AbstractJaxbConfigDao<WsmanDatacollectionConfig, WsmanDatacollectionConfig> implements WSManDataCollectionConfigDao {

    public WSManDataCollectionConfigDaoJaxb() {
        super(WsmanDatacollectionConfig.class, "WS-Man Data Collection Configuration");
    }

    @Override
    public WsmanCollection getDataCollectionByName(String name) {
        WsmanDatacollectionConfig wsdcc = getContainer().getObject();
        for (WsmanCollection dataCol : wsdcc.getWsmanCollection()) {
            if(dataCol.getName().equals(name)) {
                return dataCol;
            }
        }

        return null;
    }

    @Override
    public WsmanCollection getDataCollectionByIndex(int idx) {
        WsmanDatacollectionConfig wsdcc = getContainer().getObject();
        return wsdcc.getWsmanCollection().get(idx);
    }

    @Override
    public WsmanDatacollectionConfig getConfig() {
        return getContainer().getObject();
    }

    @Override
    protected WsmanDatacollectionConfig translateConfig(WsmanDatacollectionConfig config) {
        return config;
    }
}
