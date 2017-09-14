package io.mesosphere.driver.performance.jmx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;

/**
 *
 * @author icharala
 */
public class AttributeReader {

    ObjectName objectName;
    String attribName;

    /**
     * Generate an MBean Attribute reader class
     *
     * @param expression The string expression to use
     * @throws MalformedObjectNameException
     */
    public AttributeReader(String expression) throws MalformedObjectNameException, IllegalArgumentException {
        String[] parts = expression.split("::");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid attribute syntax. Expecting: [mbean]::[attribute]");
        }

        objectName = new ObjectName(parts[0]);
        attribName = parts[1];
    }

    private Object translate(Object value) {
        if (value instanceof CompositeDataSupport) {
            return translateCompositeData((CompositeDataSupport) value);
        } else if (value instanceof TabularDataSupport) {
            return translateTabularData((TabularDataSupport) value);
        }

        return value;
    }

    private List<Object> translateTabularData(TabularData data) {
        List<Object> result = new ArrayList<Object>();
        for (Object rowData: data.values()) {
            result.add(translate(rowData));
        }

        return result;
    }

    private Map<String, Object> translateCompositeData(CompositeData data) {
        Map<String, Object> result = new HashMap<String, Object>();
        CompositeType type = data.getCompositeType();

        for (String itemName: type.keySet()) {
            result.put(itemName, translate(data.get(itemName)));
        }

        return result;
    }

    public Object read(MBeanServerConnection mbeanConn) {
        try {
            Object value = mbeanConn.getAttribute(objectName, attribName);
            return translate(value);
        } catch (MBeanException ex) {
            return "<mbean-error>";
        } catch (AttributeNotFoundException ex) {
            return "<missing>";
        } catch (InstanceNotFoundException ex) {
            return "<missing>";
        } catch (ReflectionException ex) {
            return "<reflection-error>";
        } catch (IOException ex) {
            return "<io-error>";
        }
    }
}
