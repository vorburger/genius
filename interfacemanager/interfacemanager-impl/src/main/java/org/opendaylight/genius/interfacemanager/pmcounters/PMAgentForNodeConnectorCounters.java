/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.interfacemanager.pmcounters;

import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMAgentForNodeConnectorCounters {

    private MBeanServer mbServer = null;
    private ObjectName mbeanForOFPortDuration = null;
    private ObjectName mbeanForOFPortReceiveDrop = null;
    private ObjectName mbeanForOFPortReceiveError = null;
    private ObjectName mbeanForOFPortPacketSent = null;
    private ObjectName mbeanForOFPortPacketReceive = null;
    private ObjectName mbeanForOFPortBytesSent = null;
    private ObjectName mbeanForOFPortBytesReceive = null;
    private ObjectName mbeanForEntriesPerOFTable = null;
    private static final String REGISTERED_MBEAN_SUCCESSFULLY = "Registered Mbean {} successfully";

    private static final String BEANNAMEFOROFPORTDURATION = "SDNC.PM:type=CounterForOFPortDuration";
    private static final String BEANNAMEFOROFPORTREVEIVEDROP = "SDNC.PM:type=CounterForOFPortReceiveDrop";
    private static final String BEANNAMEFOROFPORTREVEIVEERROR = "SDNC.PM:type=CounterForOFPortReceiveError";
    private static final String BEANNAMEFOROFPORTPACKETSENT = "SDNC.PM:type=CounterForOFPortPacketSent";
    private static final String BEANNAMEFOROFPORTPACKETRECEIVE = "SDNC.PM:type=CounterForOFPortPacketReceive";
    private static final String BEANNAMEFOROFPORTBYTESSENT = "SDNC.PM:type=CounterForOFPortBytesSent";
    private static final String BEANNAMEFOROFPORTBYTESRECEIVE = "SDNC.PM:type=CounterForOFPortBytesReceive";
    private static final String BEANNAMEFORENTRIESPEROFTABLE = "SDNC.PM:type=CounterForEntriesPerOFTable";
    private static CounterForOFPortDuration counterForOFPortDurationBean = new CounterForOFPortDuration();
    private static CounterForOFPortReceiveDrop counterForOFPortReceiveDropBean = new CounterForOFPortReceiveDrop();
    private static CounterForOFPortReceiveError counterForOFPortReceiveErrorBean = new CounterForOFPortReceiveError();
    private static CounterForOFPortPacketSent counterForOFPortPacketSent = new CounterForOFPortPacketSent();
    private static CounterForOFPortPacketReceive counterForOFPortPacketReceive = new CounterForOFPortPacketReceive();
    private static CounterForOFPortBytesSent counterForOFPortBytesSent = new CounterForOFPortBytesSent();
    private static CounterForOFPortBytesReceive counterForOFPortBytesReceive = new CounterForOFPortBytesReceive();
    private static CounterForEntriesPerOFTable counterForEntriesPerOFTable = new CounterForEntriesPerOFTable();

    private static final Logger LOG = LoggerFactory.getLogger(PMAgentForNodeConnectorCounters.class);

    public PMAgentForNodeConnectorCounters() {
        // Get the platform MBeanServer
        mbServer = ManagementFactory.getPlatformMBeanServer();
        try {
            mbeanForOFPortDuration = new ObjectName(BEANNAMEFOROFPORTDURATION);
            mbeanForOFPortReceiveDrop = new ObjectName(BEANNAMEFOROFPORTREVEIVEDROP);
            mbeanForOFPortReceiveError = new ObjectName(BEANNAMEFOROFPORTREVEIVEERROR);
            mbeanForOFPortPacketSent = new ObjectName(BEANNAMEFOROFPORTPACKETSENT);
            mbeanForOFPortPacketReceive = new ObjectName(BEANNAMEFOROFPORTPACKETRECEIVE);
            mbeanForOFPortBytesSent = new ObjectName(BEANNAMEFOROFPORTBYTESSENT);
            mbeanForOFPortBytesReceive = new ObjectName(BEANNAMEFOROFPORTBYTESRECEIVE);
            mbeanForEntriesPerOFTable = new ObjectName(BEANNAMEFORENTRIESPEROFTABLE);
        } catch (MalformedObjectNameException e) {
            LOG.error("ObjectName instance creation failed with exception", e);

        }
    }

    public void registerMbean() {
        try {
            // Uniquely identify the MBeans and register them with the platform
            // MBeanServer
            if (!mbServer.isRegistered(mbeanForOFPortDuration)) {
                mbServer.registerMBean(counterForOFPortDurationBean, mbeanForOFPortDuration);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortDuration);
            }
            if (!mbServer.isRegistered(mbeanForOFPortReceiveDrop)) {
                mbServer.registerMBean(counterForOFPortReceiveDropBean, mbeanForOFPortReceiveDrop);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortReceiveDrop);
            }
            if (!mbServer.isRegistered(mbeanForOFPortReceiveError)) {
                mbServer.registerMBean(counterForOFPortReceiveErrorBean, mbeanForOFPortReceiveError);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortReceiveError);
            }
            if (!mbServer.isRegistered(mbeanForOFPortPacketSent)) {
                mbServer.registerMBean(counterForOFPortPacketSent, mbeanForOFPortPacketSent);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortPacketSent);
            }
            if (!mbServer.isRegistered(mbeanForOFPortPacketReceive)) {
                mbServer.registerMBean(counterForOFPortPacketReceive, mbeanForOFPortPacketReceive);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortPacketReceive);
            }
            if (!mbServer.isRegistered(mbeanForOFPortBytesSent)) {
                mbServer.registerMBean(counterForOFPortBytesSent, mbeanForOFPortBytesSent);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortBytesSent);
            }
            if (!mbServer.isRegistered(mbeanForOFPortBytesReceive)) {
                mbServer.registerMBean(counterForOFPortBytesReceive, mbeanForOFPortBytesReceive);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForOFPortBytesReceive);
            }
            if (!mbServer.isRegistered(mbeanForEntriesPerOFTable)) {
                mbServer.registerMBean(counterForEntriesPerOFTable, mbeanForEntriesPerOFTable);
                LOG.info(REGISTERED_MBEAN_SUCCESSFULLY, mbeanForEntriesPerOFTable);
            }
        } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
            LOG.error("Registeration failed with exception", e);
        }
    }

    public synchronized void connectToPMAgent(Map<String, String> ofPortDurationCounter,
            Map<String, String> ofPortReceiveDropCounter, Map<String, String> ofPortReceiveErrorCounter,
            Map<String, String> ofPortPacketSent, Map<String, String> ofPortPacketReceive,
            Map<String, String> ofPortBytesSent, Map<String, String> ofPortBytesReceive) {
        try {
            mbServer.invoke(mbeanForOFPortDuration, "invokePMManagedObjects", new Object[] { ofPortDurationCounter },
                    new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortReceiveDrop, "invokePMManagedObjects",
                    new Object[] { ofPortReceiveDropCounter }, new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortReceiveError, "invokePMManagedObjects",
                    new Object[] { ofPortReceiveErrorCounter }, new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortPacketSent, "invokePMManagedObjects", new Object[] { ofPortPacketSent },
                    new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortPacketReceive, "invokePMManagedObjects", new Object[] { ofPortPacketReceive },
                    new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortBytesSent, "invokePMManagedObjects", new Object[] { ofPortBytesSent },
                    new String[] { Map.class.getName() });
            mbServer.invoke(mbeanForOFPortBytesReceive, "invokePMManagedObjects", new Object[] { ofPortBytesReceive },
                    new String[] { Map.class.getName() });
        } catch (InstanceNotFoundException e) {
            LOG.error("InstanceNotFoundException", e);
        } catch (MBeanException e) {
            LOG.error("MBeanException", e);
        } catch (ReflectionException e) {
            LOG.error("ReflectionException", e);
        }
    }

    public synchronized void connectToPMAgentAndInvokeEntriesPerOFTable(Map<String, String> entriesPerOFTable) {
        try {
            mbServer.invoke(mbeanForEntriesPerOFTable, "invokePMManagedObjects", new Object[] { entriesPerOFTable },
                    new String[] { Map.class.getName() });
        } catch (InstanceNotFoundException e) {
            LOG.error("InstanceNotFoundException", e);
        } catch (MBeanException e) {
            LOG.error("MBeanException", e);
        } catch (ReflectionException e) {
            LOG.error("ReflectionException", e);
        }
    }
}
