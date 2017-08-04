collectd-sml: collectd plugin to receive data from SML-capable energy meters
============================================================================

jSML is a Java library implementing the Smart Message Language (SML).

`collectd-sml` receives SML data files from an electronic energy meter and
makes it available to collectd via the collectd Java plugin interface.

The fields in collectd’s data packets are set as follows:

* hostname = [set at compile-time]
* plugin = `smlreceiver`
* plugin_instance = [SML server ID]
* type = `watts` or `watt_hours`
* type_instance = [OBIS code]

Context
-------

This software has been developed in the context of the bachelor's thesis
["Measuring, Visualizing, and Optimizing the Energy Consumption of Computer
Clusters"](https://www.sosy-lab.org/research/bsc/steinger/).
The thesis provides more information about the hardware and software
environment that this plugin was designed to be used in.

Setup
-----

All new data types must be manually added to Collectd's type list (the path to
which is specified in collectd.conf).
For most values from the SML energy meter, a simple positive-only gauge should
suffice, e.g.
```
watts       value:GAUGE:0:U
watt_hours  value:GAUGE:0:U
```

To load this plugin into a collectd instance, a configuration stanza similar to
the following has to be added to `collectd.conf`:
```
<Plugin "java">
  JVMArg "-Djava.class.path=/opt/collectd/lib/collectd/bindings/java"
  LoadPlugin "de.voidptr.collectd_plugins.SMLReceiver"
  <Plugin "de.voidptr.collectd_plugins.SMLReceiver">
    SerialPort = "/dev/ttyAMA0"
  </Plugin>
</Plugin>
```

Licensing
---------

jSML v1.0.17 was released under the GNU Lesser General Public License v2.1 (see
[licenses/COPYING.LESSER]) thus requiring all modifications to be licensed
under the LGPL v2.1 as well.

Where legally possible, new additions (such as
`src/de/voidptr/collectd_plugins/SMLReceiver.java`) are licensed under the
Apache License v2.0.

Note that as of v1.1.0 (19-Jul-2017), jSML is licensed under the Mozilla Public
License v2.0.
