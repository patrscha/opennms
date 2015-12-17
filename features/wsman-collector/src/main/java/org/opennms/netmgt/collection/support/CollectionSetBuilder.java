package org.opennms.netmgt.collection.support;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.opennms.netmgt.collection.api.AttributeGroupType;
import org.opennms.netmgt.collection.api.CollectionAgent;
import org.opennms.netmgt.collection.api.CollectionAttribute;
import org.opennms.netmgt.collection.api.CollectionResource;
import org.opennms.netmgt.collection.api.CollectionSet;
import org.opennms.netmgt.collection.api.Persister;
import org.opennms.netmgt.collection.api.ServiceCollector;
import org.opennms.netmgt.collection.support.AbstractCollectionResource;
import org.opennms.netmgt.collection.support.SingleResourceCollectionSet;

import com.google.common.collect.Maps;

public class CollectionSetBuilder {
    
    public static enum Status {
        UNKNOWN(ServiceCollector.COLLECTION_UNKNOWN),
        SUCCEEDED(ServiceCollector.COLLECTION_SUCCEEDED),
        FAILED(ServiceCollector.COLLECTION_FAILED);
        
        private final int m_code;

        Status(int code) {
            m_code = code;
        }

        int getCode() {
            return m_code;
        }
    }

    private final CollectionAgent m_agent;
    private Status m_status = Status.SUCCEEDED;
    private Date m_timestamp = new Date();
    private Map<String, Double> m_attributes = Maps.newHashMap();

    public CollectionSetBuilder(CollectionAgent agent) {
        m_agent = Objects.requireNonNull(agent, "agent cannot be null");
    }

    public CollectionSetBuilder withStatus(Status status) {
        m_status = Objects.requireNonNull(status, "status cannot be null");
        return this;
    }

    public CollectionSetBuilder withTimestamp(Date timestamp) {
        m_timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        return this;
    }

    public CollectionSetBuilder withNumericAttribute(String name, double value) {
        m_attributes.put(Objects.requireNonNull(name, "name cannot be null"), value);
        return this;
    }

    public CollectionSet build() {
        final AbstractCollectionResource resource = new AbstractCollectionResource(m_agent) {
            @Override
            public String getResourceTypeName() {
                return CollectionResource.RESOURCE_TYPE_NODE;
            }

            @Override
            public String getInstance() {
                return null;
            }

            @Override
            public String toString() {
                return "Node[" + m_agent.getNodeId() + "]/type[node]";
            }
        };
        
        for (Entry<String, Double> attribute : m_attributes.entrySet()) {
            final AttributeGroupType groupType = new AttributeGroupType("groupName", AttributeGroupType.IF_TYPE_ALL);
            final AbstractCollectionAttributeType attributeType = new AbstractCollectionAttributeType(groupType) {
                @Override
                public String getType() {
                    return "gauge";
                }

                @Override
                public String getName() {
                    return attribute.getKey();
                }

                @Override
                public void storeAttribute(CollectionAttribute attribute, Persister persister) {
                    persister.persistNumericAttribute(attribute);
                }

                @Override
                public String toString() {
                    return "AttributeType[" + getName() + "]/type[" + getType() + "]";
                }
            };

            resource.addAttribute(new AbstractCollectionAttribute(attributeType, resource) {
                @Override
                public String getMetricIdentifier() {
                    return attribute.getKey();
                }

                @Override
                public Number getNumericValue() {
                    return attribute.getValue();
                }

                @Override
                public String getStringValue() {
                    return null;
                }

                @Override
                public String toString() {
                    return "Attribute[" + getMetricIdentifier() + ": " + getNumericValue();
                }
            });
        }

        SingleResourceCollectionSet singleResourceCollectionSet = new SingleResourceCollectionSet(resource, m_timestamp);
        singleResourceCollectionSet.setStatus(m_status.getCode());
        return singleResourceCollectionSet;
    }
}
