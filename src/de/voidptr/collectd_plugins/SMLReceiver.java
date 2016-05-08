package de.voidptr.collectd_plugins;

import org.collectd.api.*;
import org.openmuc.jsml.structures.*;
import org.openmuc.jsml.tl.SML_SerialReceiver;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SMLReceiver implements CollectdConfigInterface, CollectdInitInterface, CollectdShutdownInterface {

    /*
     All new types must be manually added to Collectd's type list (to path to which is specified in collectd.conf).
     For most values from the SML energy meter, a simple positive-only gauge should suffice, e.g.
     watts    value:GAUGE:0:U
     Since Collectd will reject values that reference an unknown data type, we need to keep a list of units for
     which we can pass on data to Collectd.
     */
    private static final Map<Integer, String> collectdTypeForSMLUnit;
    static {
        Map<Integer, String> temporaryMap = new HashMap<>();
        temporaryMap.put(SML_Unit.WATT, "watts");
        temporaryMap.put(SML_Unit.WATT_HOUR, "watt_hours");
        collectdTypeForSMLUnit = Collections.unmodifiableMap(temporaryMap);
    }

    private final SML_SerialReceiver receiver = new SML_SerialReceiver();
    private String serialPort = null;

    public SMLReceiver() {
        Collectd.logInfo("construct");
        Collectd.registerConfig("de.voidptr.collectd_plugins.SMLReceiver", this);
        Collectd.registerInit("de.voidptr.collectd_plugins.SMLReceiver", this);
        Collectd.registerShutdown("de.voidptr.collectd_plugins.SMLReceiver", this);
    }

    @Override
    public int config(OConfigItem configRootItem) {
        Collectd.logInfo("config");
        for (OConfigItem subitem : configRootItem.getChildren()) {
            if (subitem.getKey().toLowerCase().equals("serialport")) {
                String value = subitem.getValues().get(0).getString();
                Collectd.logDebug("Config option \"SerialPort\" found. Value = " + value);
                serialPort = value;
            }
        }
        return 0;
    }

    @Override
    public int init() {
        Collectd.logInfo("init");
        if (serialPort == null) {
            Collectd.logError("smlreceiver plugin: No serial port has been configured. Please use the \"SerialPort\" configuration option.");
            return 1;
        }

        // Force RXTX to use the serial port. Necessary for some kinds of serial port drivers
        // (e.g. ttyAMA0, the on-board serial port on Raspberry Pi devices).
        System.setProperty("gnu.io.rxtx.SerialPorts", serialPort);

        try {
            receiver.setupComPort(serialPort);

        } catch (Exception e) {
            Collectd.logError("Error while setting up serial port: " + e.getMessage());
            return 1;
        }

        Collectd.logDebug("smlreceiver plugin: Serial port setup complete. Launching receiver thread.");

        new Thread(new ValueDispatcherThread()).start();

        return 0;
    }

    @Override
    public int shutdown() {
        try {
            receiver.close();
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }
        return 0;
    }

    private class ValueDispatcherThread implements Runnable {

        private String octetStringToHexString(OctetString serverId) {
            StringBuilder result = new StringBuilder();

            for (byte b : serverId.getOctetString()) {
                result.append(String.format("%02X:", b));
            }

            result.deleteCharAt(result.length() - 1);

            return result.toString();
        }

        private int dispatchValue(String serverId, String obisCode, String unitName, double value) {
            Collectd.logInfo("Dispatching value...");

            ValueList valueList = new ValueList();

            // Commit cadc1d5 made it possible to use to hostname from collectd.conf for the Java plugin.
            // Unfortunately, that commit is only included in v5.5.0 and higher, while Ubuntu 14.04 ships v5.4.0.
            valueList.setHost("ubuntu.has.an.outdated.collectd.version");
            valueList.setPlugin("smlreceiver");
            valueList.setPluginInstance(serverId);
            valueList.setType(unitName);
            valueList.setTypeInstance(obisCode);
            valueList.setInterval(1<<30);
            valueList.addValue(value);

            return Collectd.dispatchValues(valueList);
        }

        @Override
        public void run() {
            while (true) {

                SML_File smlFile = null;
                try {
                    smlFile = receiver.getSMLFile();

                } catch (IOException e) {
                    Collectd.logError("Error while trying to read SML data: " + e.getMessage());
                    return;
                }

                List<SML_Message> smlMessages = smlFile.getMessages();

                for (SML_Message sml_message : smlMessages) {
                    int tag = sml_message.getMessageBody().getTag().getVal();
                    if (tag == SML_MessageBody.GetListResponse) {
                        SML_GetListRes resp = (SML_GetListRes) sml_message.getMessageBody().getChoice();
                        SML_List smlList = resp.getValList();

                        String serverIdString = octetStringToHexString(resp.getServerId());

                        SML_ListEntry[] list = smlList.getValListEntry();

                        for (SML_ListEntry entry : list) {
                            int unit = entry.getUnit().getVal();
                            // Only handle entries with units for which we have Collectd type definitions
                            if (! collectdTypeForSMLUnit.containsKey(unit)) {
                                continue;
                            }
                            String unitName = collectdTypeForSMLUnit.get(unit);
                            long numericalValue;

                            SML_Value value = entry.getValue();
                            ASNObject obj = value.getChoice();

                            if (obj.getClass().equals(Integer32.class)) {
                                Integer32 val = (Integer32) obj;
                                numericalValue = val.getVal();
                            } else if (obj.getClass().equals(Integer64.class)) {
                                Integer64 val = (Integer64) obj;
                                numericalValue = val.getVal();
                            } else {
                                System.out.println("Got non-numerical value for an energy measurement. Skipping.");
                                continue;
                            }

                            String obisCode = octetStringToHexString(entry.getObjName());

                            dispatchValue(serverIdString, obisCode, unitName, numericalValue / 10.0);
                        }
                    }
                }

                System.out.println();



            }
        }
    }
}
