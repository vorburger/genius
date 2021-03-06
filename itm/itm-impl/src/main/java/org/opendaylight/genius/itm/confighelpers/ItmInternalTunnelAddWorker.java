/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.itm.confighelpers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.itm.impl.ITMBatchingUtils;
import org.opendaylight.genius.itm.impl.ItmUtils;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelMonitoringTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelMonitoringTypeLldp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeLogicalGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.tunnel.optional.params.TunnelOptions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.config.rev160406.ItmConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnel.list.InternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnel.list.InternalTunnelKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItmInternalTunnelAddWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ItmInternalTunnelAddWorker.class) ;
    private static Boolean monitorEnabled;
    private static Integer monitorInterval;
    private static ItmConfig itmCfg;
    private static Class<? extends TunnelMonitoringTypeBase> monitorProtocol;

    private static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        @Override
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore operation");
        }

        @Override
        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore operation", error);
        }
    };

    private final DataBroker dataBroker;
    private final JobCoordinator jobCoordinator;

    public ItmInternalTunnelAddWorker(DataBroker dataBroker, JobCoordinator jobCoordinator) {
        this.dataBroker = dataBroker;
        this.jobCoordinator = jobCoordinator;
    }

    public List<ListenableFuture<Void>> buildAllTunnels(IMdsalApiManager mdsalManager, List<DPNTEPsInfo> cfgdDpnList,
                                                        List<DPNTEPsInfo> meshedDpnList, ItmConfig itmConfig) {
        LOG.trace("Building tunnels with DPN List {} " , cfgdDpnList);
        monitorInterval = ItmUtils.determineMonitorInterval(dataBroker);
        monitorProtocol = ItmUtils.determineMonitorProtocol(dataBroker);
        monitorEnabled = ItmUtils.readMonitoringStateFromCache(dataBroker);
        itmCfg = itmConfig;
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        if (null == cfgdDpnList || cfgdDpnList.isEmpty()) {
            LOG.error(" Build Tunnels was invoked with empty list");
            return futures;
        }

        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        for (DPNTEPsInfo dpn : cfgdDpnList) {
            //#####if dpn is not in meshedDpnList
            buildTunnelFrom(dpn, meshedDpnList, dataBroker, mdsalManager, transaction);
            if (null == meshedDpnList) {
                meshedDpnList = new ArrayList<>();
            }
            meshedDpnList.add(dpn);
            // Update the config datastore -- FIXME -- Error Handling
            updateDpnTepInfoToConfig(dpn);
        }
        futures.add(transaction.submit()) ;
        return futures ;
    }

    private static void updateDpnTepInfoToConfig(DPNTEPsInfo dpn) {
        LOG.debug("Updating CONFIGURATION datastore with DPN {} ", dpn);
        InstanceIdentifier<DpnEndpoints> dep = InstanceIdentifier.builder(DpnEndpoints.class).build() ;
        List<DPNTEPsInfo> dpnList = new ArrayList<>() ;
        dpnList.add(dpn) ;
        DpnEndpoints tnlBuilder = new DpnEndpointsBuilder().setDPNTEPsInfo(dpnList).build() ;
        ITMBatchingUtils.update(dep, tnlBuilder, ITMBatchingUtils.EntityType.DEFAULT_CONFIG);
    }

    private void buildTunnelFrom(DPNTEPsInfo srcDpn, List<DPNTEPsInfo> meshedDpnList, DataBroker dataBroker,
                                 IMdsalApiManager mdsalManager, WriteTransaction transaction) {
        LOG.trace("Building tunnels from DPN {} " , srcDpn);
        if (null == meshedDpnList || meshedDpnList.isEmpty()) {
            LOG.debug("No DPN in the mesh ");
            return ;
        }
        for (DPNTEPsInfo dstDpn: meshedDpnList) {
            if (!srcDpn.equals(dstDpn)) {
                wireUpWithinTransportZone(srcDpn, dstDpn, dataBroker, mdsalManager,
                        transaction);
            }
        }

    }

    private void wireUpWithinTransportZone(DPNTEPsInfo srcDpn, DPNTEPsInfo dstDpn, DataBroker dataBroker,
                                           IMdsalApiManager mdsalManager, WriteTransaction transaction) {
        LOG.trace("Wiring up within Transport Zone for Dpns {}, {} " , srcDpn, dstDpn);
        List<TunnelEndPoints> srcEndPts = srcDpn.getTunnelEndPoints();
        List<TunnelEndPoints> dstEndPts = dstDpn.getTunnelEndPoints();

        for (TunnelEndPoints srcte : srcEndPts) {
            for (TunnelEndPoints dstte : dstEndPts) {
                // Compare the Transport zones
                if (!srcDpn.getDPNID().equals(dstDpn.getDPNID())) {
                    if (!ItmUtils.getIntersection(srcte.getTzMembership(), dstte.getTzMembership()).isEmpty()) {
                        // wire them up
                        wireUpBidirectionalTunnel(srcte, dstte, srcDpn.getDPNID(), dstDpn.getDPNID(), dataBroker,
                                mdsalManager, transaction);
                        if (!ItmTunnelAggregationHelper.isTunnelAggregationEnabled()) {
                            // CHECK THIS -- Assumption -- One end point per Dpn per transport zone
                            break;
                        }
                    }
                }
            }
        }
    }

    private void wireUpBidirectionalTunnel(TunnelEndPoints srcte, TunnelEndPoints dstte, BigInteger srcDpnId,
                                           BigInteger dstDpnId, DataBroker dataBroker, IMdsalApiManager mdsalManager,
                                           WriteTransaction transaction) {
        // Setup the flow for LLDP monitoring -- PUNT TO CONTROLLER

        if (monitorProtocol.isAssignableFrom(TunnelMonitoringTypeLldp.class)) {
            ItmUtils.setUpOrRemoveTerminatingServiceTable(srcDpnId, mdsalManager, true);
            ItmUtils.setUpOrRemoveTerminatingServiceTable(dstDpnId, mdsalManager, true);
        }
        // Create the forward direction tunnel
        if (!wireUp(srcte, dstte, srcDpnId, dstDpnId, dataBroker,
                transaction)) {
            LOG.error("Could not build tunnel between end points {}, {} " , srcte, dstte);
        }

        // CHECK IF FORWARD IS NOT BUILT , REVERSE CAN BE BUILT
        // Create the tunnel for the reverse direction
        if (!wireUp(dstte, srcte, dstDpnId, srcDpnId, dataBroker,
                transaction)) {
            LOG.error("Could not build tunnel between end points {}, {} " , dstte, srcte);
        }
    }

    private boolean wireUp(TunnelEndPoints srcte, TunnelEndPoints dstte, BigInteger srcDpnId, BigInteger dstDpnId,
                           DataBroker dataBroker, WriteTransaction transaction) {
        // Wire Up logic
        LOG.trace("Wiring between source tunnel end points {}, destination tunnel end points {}", srcte, dstte);
        String interfaceName = srcte.getInterfaceName();
        Class<? extends TunnelTypeBase> tunType = srcte.getTunnelType();
        String tunTypeStr = srcte.getTunnelType().getName();
        // Form the trunk Interface Name

        String trunkInterfaceName = ItmUtils.getTrunkInterfaceName(interfaceName,
                new String(srcte.getIpAddress().getValue()),
                new String(dstte.getIpAddress().getValue()),
                tunTypeStr);
        String parentInterfaceName = null;
        if (tunType.isAssignableFrom(TunnelTypeVxlan.class)) {
            parentInterfaceName = createLogicalGroupTunnel(srcDpnId, dstDpnId, dataBroker);
        }
        createTunnelInterface(srcte, dstte, srcDpnId, tunType, trunkInterfaceName, parentInterfaceName);
        // also update itm-state ds?
        createInternalTunnel(srcDpnId, dstDpnId, tunType, trunkInterfaceName, transaction);
        return true;
    }

    private static void createTunnelInterface(TunnelEndPoints srcte, TunnelEndPoints dstte,
                                              BigInteger srcDpnId, Class<? extends TunnelTypeBase> tunType,
                                              String trunkInterfaceName, String parentInterfaceName) {
        String gateway = srcte.getIpAddress().getIpv4Address() != null ? "0.0.0.0" : "::";
        IpAddress gatewayIpObj = new IpAddress(gateway.toCharArray());
        IpAddress gwyIpAddress =
                srcte.getSubnetMask().equals(dstte.getSubnetMask()) ? gatewayIpObj : srcte.getGwIpAddress() ;
        LOG.debug(" Creating Trunk Interface with parameters trunk I/f Name - {}, parent I/f name - {}, "
                + "source IP - {}, destination IP - {} gateway IP - {}",
                trunkInterfaceName, srcte.getInterfaceName(), srcte.getIpAddress(), dstte.getIpAddress(), gwyIpAddress);
        boolean useOfTunnel = ItmUtils.falseIfNull(srcte.isOptionOfTunnel());

        List<TunnelOptions> tunOptions = ItmUtils.buildTunnelOptions(srcte, itmCfg);
        Boolean isMonitorEnabled = tunType.isAssignableFrom(TunnelTypeLogicalGroup.class) ? false : monitorEnabled;
        Interface iface = ItmUtils.buildTunnelInterface(srcDpnId, trunkInterfaceName,
                String.format("%s %s",ItmUtils.convertTunnelTypetoString(tunType), "Trunk Interface"),
                true, tunType, srcte.getIpAddress(), dstte.getIpAddress(), gwyIpAddress, srcte.getVLANID(), true,
                isMonitorEnabled, monitorProtocol, monitorInterval, useOfTunnel, parentInterfaceName, tunOptions);
        LOG.debug(" Trunk Interface builder - {} ", iface);
        InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(trunkInterfaceName);
        LOG.debug(" Trunk Interface Identifier - {} ", trunkIdentifier);
        LOG.trace(" Writing Trunk Interface to Config DS {}, {} ", trunkIdentifier, iface);
        ITMBatchingUtils.update(trunkIdentifier, iface, ITMBatchingUtils.EntityType.DEFAULT_CONFIG);
        ItmUtils.ITM_CACHE.addInterface(iface);
    }

    private static void createLogicalTunnelInterface(BigInteger srcDpnId,
            Class<? extends TunnelTypeBase> tunType, String interfaceName) {
        Interface iface = ItmUtils.buildLogicalTunnelInterface(srcDpnId, interfaceName,
                String.format("%s %s",ItmUtils.convertTunnelTypetoString(tunType), "Interface"), true);
        InstanceIdentifier<Interface> trunkIdentifier = ItmUtils.buildId(interfaceName);
        ITMBatchingUtils.update(trunkIdentifier, iface, ITMBatchingUtils.EntityType.DEFAULT_CONFIG);
        ItmUtils.ITM_CACHE.addInterface(iface);
    }

    private static void createInternalTunnel(BigInteger srcDpnId, BigInteger dstDpnId,
                                             Class<? extends TunnelTypeBase> tunType,
                                             String trunkInterfaceName, WriteTransaction transaction) {
        InstanceIdentifier<InternalTunnel> path = InstanceIdentifier.create(TunnelList.class)
                .child(InternalTunnel.class, new InternalTunnelKey(dstDpnId, srcDpnId, tunType));
        InternalTunnel tnl = ItmUtils.buildInternalTunnel(srcDpnId, dstDpnId, tunType, trunkInterfaceName);
        // Switching to individual transaction submit as batching latencies is causing ELAN failures.
        // Will revert when ELAN can handle this.
        // ITMBatchingUtils.update(path, tnl, ITMBatchingUtils.EntityType.DEFAULT_CONFIG);
        transaction.merge(LogicalDatastoreType.CONFIGURATION,path, tnl, true) ;
        ItmUtils.ITM_CACHE.addInternalTunnel(tnl);
    }

    private String createLogicalGroupTunnel(BigInteger srcDpnId, BigInteger dstDpnId, DataBroker dataBroker) {
        String logicTunnelGroupName = null;
        boolean tunnelAggregationEnabled = ItmTunnelAggregationHelper.isTunnelAggregationEnabled();
        if (!tunnelAggregationEnabled) {
            return logicTunnelGroupName;
        }
        logicTunnelGroupName = ItmUtils.getLogicalTunnelGroupName(srcDpnId, dstDpnId);
        ItmTunnelAggregationWorker addWorker =
                new ItmTunnelAggregationWorker(logicTunnelGroupName, srcDpnId, dstDpnId, dataBroker);
        jobCoordinator.enqueueJob(logicTunnelGroupName, addWorker);
        return logicTunnelGroupName;
    }

    private static class ItmTunnelAggregationWorker implements Callable<List<ListenableFuture<Void>>> {

        private final String logicTunnelGroupName;
        private final BigInteger srcDpnId;
        private final BigInteger dstDpnId;
        private final DataBroker dataBroker;

        ItmTunnelAggregationWorker(String logicGroupName, BigInteger srcDpnId, BigInteger dstDpnId, DataBroker broker) {
            this.logicTunnelGroupName = logicGroupName;
            this.srcDpnId = srcDpnId;
            this.dstDpnId = dstDpnId;
            this.dataBroker = broker;
        }

        @Override
        public List<ListenableFuture<Void>> call() throws Exception {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            //The logical tunnel interface be created only when the first tunnel interface on each OVS is created
            InternalTunnel tunnel = ItmUtils.ITM_CACHE.getInternalTunnel(logicTunnelGroupName);
            if (tunnel == null) {
                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                LOG.info("MULTIPLE_VxLAN_TUNNELS: add the logical tunnel group {} because a first tunnel"
                        + " interface on srcDpnId {} dstDpnId {} is created", logicTunnelGroupName, srcDpnId, dstDpnId);
                createLogicalTunnelInterface(srcDpnId, TunnelTypeLogicalGroup.class, logicTunnelGroupName);
                createInternalTunnel(srcDpnId, dstDpnId, TunnelTypeLogicalGroup.class, logicTunnelGroupName, tx);
                futures.add(tx.submit());
            } else {
                LOG.debug("MULTIPLE_VxLAN_TUNNELS: not first tunnel on srcDpnId {} dstDpnId {}", srcDpnId, dstDpnId);
            }
            return futures;
        }
    }
}
