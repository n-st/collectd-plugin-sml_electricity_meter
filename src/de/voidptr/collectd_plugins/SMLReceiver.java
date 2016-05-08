package de.voidptr.collectd_plugins;

import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import org.collectd.api.*;
import org.openmuc.jsml.structures.*;
import org.openmuc.jsml.tl.SML_SerialReceiver;

import java.io.IOException;
import java.util.List;

public class SMLReceiver implements CollectdConfigInterface, CollectdInitInterface, CollectdShutdownInterface {

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

        private String serverIdToString(OctetString serverId) {
            StringBuilder result = new StringBuilder();

            for (byte b : serverId.getOctetString()) {
                result.append(String.format("%02X:", b));
            }

            result.deleteCharAt(result.length() - 1);

            return result.toString();
        }

        @Override
        public void run() {
            Collectd.logInfo("Dispatching value...");
            ValueList valueList = new ValueList();
            // Commit cadc1d5 made it possible to use to hostname from collectd.conf for the Java plugin.
            // Unfortunately, that commit is only included in v5.5.0 and higher, while Ubuntu 14.04 ships v5.4.0.
            valueList.setHost("ubuntu.has.an.outdated.collectd.version");
            valueList.setPlugin("smlreceiver");
            valueList.setPluginInstance("09:01:45:4D:48:00:00:4E:B9:A6");
            valueList.setType("power");
            valueList.setTypeInstance("1:0:1:8:0:255");
            valueList.setInterval(1<<30);
            valueList.addValue(42);

            Collectd.dispatchValues(valueList);

            while (true) {

                SML_File smlFile = null;
                try {
                    smlFile = receiver.getSMLFile();
                } catch (IOException e) {
                    Collectd.logError("Error while trying to read SML data: " + e.getMessage());
                    return;
                }

                List<SML_Message> smlMessages = smlFile.getMessages();

                for (int i = 0; i < smlMessages.size(); i++) {
                    SML_Message sml_message = smlMessages.get(i);
                    int tag = sml_message.getMessageBody().getTag().getVal();
                    if (tag == SML_MessageBody.GetListResponse) {
                        SML_GetListRes resp = (SML_GetListRes) sml_message.getMessageBody().getChoice();
                        SML_List smlList = resp.getValList();

                        System.out.print("Server-ID: ");
                        System.out.println(serverIdToString(resp.getServerId()));

                        SML_ListEntry[] list = smlList.getValListEntry();

                        for (SML_ListEntry entry : list) {
                            int unit = entry.getUnit().getVal();
                            String unitName = null;
                            // Only handle entries with meaningful units
                            switch (unit) {
                                case SML_Unit.WATT:
                                    unitName = "W";
                                    break;
                                case SML_Unit.WATT_HOUR:
                                    unitName = "Wh";
                                    break;
                            }
                            if (unitName != null) {
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

                                byte objNameBytes[] = entry.getObjName().getOctetString();
                                // We need to force Java to treat the bytes as unsigned integers by AND-ing them with 0xFF
                                System.out.printf("%d-%d:%d.%d.%d*%d = %.1f %s\n",
                                        0xFF & objNameBytes[0], 0xFF & objNameBytes[1], 0xFF & objNameBytes[2],
                                        0xFF & objNameBytes[3], 0xFF & objNameBytes[4], 0xFF & objNameBytes[5],
                                        numericalValue / 10.0,
                                        unitName);
                            }
                        }
                    }
                }

                System.out.println();

            }
        }
    }
}
