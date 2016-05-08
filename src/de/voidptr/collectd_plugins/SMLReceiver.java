package de.voidptr.collectd_plugins;

import org.collectd.api.*;

public class SMLReceiver implements CollectdConfigInterface, CollectdInitInterface {
    public SMLReceiver() {
        Collectd.logInfo("Register config callback...");
        Collectd.registerConfig("de.voidptr.collectd_plugins.SMLReceiver", this);
        Collectd.registerInit("de.voidptr.collectd_plugins.SMLReceiver", this);
    }

    @Override
    public int config(OConfigItem configRootItem) {
        for (OConfigItem subitem : configRootItem.getChildren()) {
            Collectd.logInfo("SMLReceiver got config option '" + subitem.getKey() + "' with value '" + subitem.getValues().toString() + "'.");
        }
        return 0;
    }

    @Override
    public int init() {
        Collectd.logInfo("Starting thread...");

        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(5000, 0);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Collectd.logInfo("Dispatching value...");
                ValueList valueList = new ValueList();
                // Commit cadc1d5 made it possible to use to hostname from collectd.conf for the Java plugin.
                // Unfortunately, that commit is only included in v5.5.0 and higher, while Ubuntu 14.04 ships v5.4.0.
                valueList.setHost("ubuntuhasanoutdatedcollectdversion");
                valueList.setPlugin("smlreceiver");
                valueList.setPluginInstance("09:01:45:4D:48:00:00:4E:B9:A6");
                valueList.setType("power");
                valueList.setTypeInstance("1:0:1:8:0:255");
                valueList.setInterval(1<<30);
                valueList.addValue(42);

                Collectd.dispatchValues(valueList);
            }
        }).start();

        return 0;
    }
}
