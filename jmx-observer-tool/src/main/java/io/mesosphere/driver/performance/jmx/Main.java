package io.mesosphere.driver.performance.jmx;

import com.google.gson.Gson;
import sun.tools.attach.BsdAttachProvider;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.AttachNotSupportedException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 *
 * @author icharala
 */
public class Main {

    private static String addressByPid(String pidArgument) {
        try {
            VirtualMachine vm = VirtualMachine.attach(pidArgument);
            Properties props = vm.getAgentProperties();

            // Get the JMX agent address
            String value = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");

            // If the VM has no JMX agent loaded, load it now
            if (value == null) {

                // Configure properties for the agent
                props.setProperty("java.rmi.server.hostname", "127.0.0.1");
                props.setProperty("com.sun.management.jmxremote", "");
                props.setProperty("com.sun.management.jmxremote.local.only", "false");
                props.setProperty("com.sun.management.jmxremote.authenticate", "false");
                props.setProperty("com.sun.management.jmxremote.ssl", "false");

                // Try to load management agent jar
                String agent = vm.getSystemProperties().getProperty(
                        "java.home")+File.separator+"lib"+File.separator+
                        "management-agent.jar";
                vm.loadAgent(agent);

                // Wait for the sec for the agent to be loaded
                Thread.sleep(1000);

                // Try again
                value = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                if (value == null) {
                    System.err.println("ERROR: Unable to");
                    System.exit(1);
                    return "";
                }
            }

            return value;

        } catch (AttachNotSupportedException ex) {
            System.err.println("ERROR: The target process does not support attach");
            System.exit(1);
            return "";
        } catch (IOException ex) {
            System.err.println("ERROR: I/O Exception while attaching: " + ex.getMessage());
            System.exit(1);
            return "";
        } catch (AgentLoadException ex) {
            System.err.println("ERROR: Error loading JMX agent: " + ex.getMessage());
            System.exit(1);
            return "";
        } catch (AgentInitializationException ex) {
            System.err.println("ERROR: Error initializing JMX agent: " + ex.getMessage());
            System.exit(1);
            return "";
        } catch (InterruptedException ex) {
            return "";
        }
    }

    /**
     * Entry point
     * @param arguments
     */
    public static void main(String[] arguments) {

        // Pointless ussage to hint
        BsdAttachProvider p = new BsdAttachProvider();

        // Validate argument count
        if (arguments.length < 3) {
            System.err.println("ERROR: Expected arguments: <jmx_host> <jmx_port> [<sampling_interval>] ['<mbean>::<attribute>' ...]");
            System.exit(1);
            return;
        }

        // Prepare the connector URL
        String url = "service:jmx:rmi:///jndi/rmi://" + arguments[0] + ":" + arguments[1] + "/jmxrmi";
        if ("pid".equals(arguments[0])) {
            url = addressByPid(arguments[1]);
        }

        // Get a JMX connector and establish a MBeanServerConnection
        JMXServiceURL serviceUrl;
        JMXConnector jmxConnector;
        MBeanServerConnection mbeanConn;
        try {
            serviceUrl = new JMXServiceURL(url);
            jmxConnector = JMXConnectorFactory.connect(serviceUrl, null);
            mbeanConn = jmxConnector.getMBeanServerConnection();
        } catch (MalformedURLException ex) {
            System.err.println("ERROR: Malformed JMX URL generated");
            System.exit(1);
            return;
        } catch (IOException ex) {
            System.err.println("ERROR: I/O Error: " + ex.getMessage());
            System.exit(1);
            return;
        }

        // Extract possible sampling interval
        int ofs = 2;
        int samplingInterval = 500;
        try {
            samplingInterval = Integer.parseInt(arguments[2]);
            ofs += 1;
        } catch (NumberFormatException e) {
            // Don't advance offset
        }

        // Generate attribute readers for every expression
        List<Object> values = new ArrayList<Object>();
        List<AttributeReader> readers = new ArrayList<AttributeReader>();
        for (int i=ofs; i<arguments.length; ++i) {
            try {
                readers.add(new AttributeReader(arguments[i]));
                values.add(null);
            } catch (MalformedObjectNameException ex) {
                System.err.println("ERROR: Attribute[" + (i-1) + "]: Malformed name: " + ex.getMessage());
                System.exit(1);
                return;
            } catch (IllegalArgumentException ex) {
                System.err.println("ERROR: Attribute[" + (i-1) + "]: Illegal argument: " + ex.getMessage());
                System.exit(1);
                return;
            }
        }

        // Create serializer
        Gson gson = new Gson();
        String lastValue = "";

        // Until interrupted report everything at the given rate
        while (true) {

            // Collect reader values
            for (int i=0; i<readers.size(); ++i) {
                AttributeReader r = readers.get(i);
                values.set(i, r.read(mbeanConn));
            }

            // Serialize them
            String value = gson.toJson(values);
            if (!value.equals(lastValue)) {
                System.out.println(value);
                lastValue = value;
            }

            // Delay til next sample
            try {
                Thread.sleep(samplingInterval);
            } catch (InterruptedException ex) {
                System.exit(0);
                return;
            }
        }
    }

}
