# dcos-perf-test-driver-jmx

Assisting middleware for the JMX observer for `dcos-perf-test-driver`

## Introcuction

This project aims to be a lightweight proxy between the `dcos-perf-test-driver`and
the JMX management interface in order to extract run-time metrics from a running 
java instance.

### Usage

The command-line have the following syntax:

```sh
   [host] [port] [interval in ms] "[MBean]::[Attrib]" ...
or   pid  [pid]  [interval in ms] "[MBean]::[Attrib]" ...
```

Every time some of the attributes change, the tool will echo a line containing 
a JSON array with the values of your bean attributes.

For example:

```sh
# Attach on running process
~$ java -jar dcos-perf-test-driver.jar \
    pid 32549 \
    "java.lang:type=Threading::ThreadCount"
[92]
[91]
[92]
[91]

# Connect on an oppened JMX port
~$ java -jar dcos-perf-test-driver.jar \
    127.0.0.1 9010 \
    "java.lang:type=Threading::ThreadCount"
[92]
[91]
[92]
[91]
```