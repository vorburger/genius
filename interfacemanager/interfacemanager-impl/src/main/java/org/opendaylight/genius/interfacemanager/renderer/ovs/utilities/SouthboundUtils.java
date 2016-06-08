/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.interfacemanager.renderer.ovs.utilities;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.genius.interfacemanager.renderer.hwvtep.utilities.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.VlanId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge._interface.info.BridgeEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge._interface.info.bridge.entry.BridgeInterfaceEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.*;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SouthboundUtils {
    private static final Logger LOG = LoggerFactory.getLogger(SouthboundUtils.class);

    public static final String BFD_PARAM_ENABLE = "enable";
    static final String BFD_PARAM_MIN_TX = "min_tx";
    static final String BFD_PARAM_MIN_RX = "min_rx";
    static final String BFD_PARAM_DECAY_MIN_RX = "decay_min_rx";
    static final String BFD_PARAM_FORWARDING_IF_RX = "forwarding_if_rx";
    static final String BFD_PARAM_CPATH_DOWN = "cpath_down";
    static final String BFD_PARAM_CHECK_TNL_KEY = "check_tnl_key";
    // bfd params
    public static final String BFD_OP_STATE = "state";
    public static final String BFD_STATE_UP = "up";
    private static final String BFD_MIN_RX_VAL = "1000";
    private static final String BFD_MIN_TX_VAL = "100";
    private static final String BFD_DECAY_MIN_RX_VAL = "200";
    private static final String BFD_FORWARDING_IF_RX_VAL = "true";
    private static final String BFD_CPATH_DOWN_VAL = "false";
    private static final String BFD_CHECK_TNL_KEY_VAL = "false";

    public static final TopologyId OVSDB_TOPOLOGY_ID = new TopologyId(new Uri("ovsdb:1"));

    public static final ImmutableBiMap<Class<? extends TunnelTypeBase>,
            Class<? extends InterfaceTypeBase>> TUNNEL_TYPE_MAP =
            new ImmutableBiMap.Builder<Class<? extends TunnelTypeBase>, Class<? extends InterfaceTypeBase>>()
                    .put(TunnelTypeGre.class, InterfaceTypeGre.class)
                    .put(TunnelTypeVxlan.class, InterfaceTypeVxlan.class)
                    .build();

    public static void addPortToBridge(InstanceIdentifier<?> bridgeIid, Interface iface,
                                       OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                       String portName, DataBroker dataBroker, List<ListenableFuture<Void>> futures) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
        if (ifTunnel != null) {
            addTunnelPortToBridge(ifTunnel, bridgeIid, iface, bridgeAugmentation, bridgeName, portName, dataBroker, tx);
        }
        futures.add(tx.submit());
    }

    /*
     *  add all tunnels ports corresponding to the bridge to the topology config DS
     */
    public static void addAllPortsToBridge(BridgeEntry bridgeEntry, DataBroker dataBroker,
                                           InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                           OvsdbBridgeAugmentation bridgeNew,
                                           List<ListenableFuture<Void>> futures){
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        String bridgeName = bridgeNew.getBridgeName().getValue();
        LOG.debug("adding all ports to bridge: {}", bridgeName);
        List<BridgeInterfaceEntry> bridgeInterfaceEntries = bridgeEntry.getBridgeInterfaceEntry();
        if(bridgeInterfaceEntries != null) {
            for (BridgeInterfaceEntry bridgeInterfaceEntry : bridgeInterfaceEntries) {
                String portName = bridgeInterfaceEntry.getInterfaceName();
                InterfaceKey interfaceKey = new InterfaceKey(portName);
                Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
                if (iface != null) {
                    IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
                    if (ifTunnel != null) {
                        addTunnelPortToBridge(ifTunnel, bridgeIid, iface, bridgeNew, bridgeName, portName, dataBroker, writeTransaction);
                    }
                } else {
                    LOG.debug("Interface {} not found in config DS", portName);
                }
            }
        }
        futures.add(writeTransaction.submit());
    }

    /*
     *  add all tunnels ports corresponding to the bridge to the topology config DS
     */
    public static void removeAllPortsFromBridge(BridgeEntry bridgeEntry, DataBroker dataBroker,
                                                InstanceIdentifier<OvsdbBridgeAugmentation> bridgeIid,
                                                OvsdbBridgeAugmentation bridgeNew,
                                                List<ListenableFuture<Void>> futures){
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        String bridgeName = bridgeNew.getBridgeName().getValue();
        LOG.debug("removing all ports from bridge: {}", bridgeName);
        List<BridgeInterfaceEntry> bridgeInterfaceEntries = bridgeEntry.getBridgeInterfaceEntry();
        if(bridgeInterfaceEntries != null) {
            for (BridgeInterfaceEntry bridgeInterfaceEntry : bridgeInterfaceEntries) {
                String portName = bridgeInterfaceEntry.getInterfaceName();
                InterfaceKey interfaceKey = new InterfaceKey(portName);
                Interface iface = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
                if (iface != null) {
                    IfTunnel ifTunnel = iface.getAugmentation(IfTunnel.class);
                    if (ifTunnel != null) {
                        removeTerminationEndPoint(futures, dataBroker, bridgeIid, iface.getName());
                    }
                } else {
                    LOG.debug("Interface {} not found in config DS", portName);
                }
            }
        }
        futures.add(writeTransaction.submit());
    }

    private static void addVlanPortToBridge(InstanceIdentifier<?> bridgeIid, IfL2vlan ifL2vlan, IfTunnel ifTunnel,
                                            OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                            String portName, DataBroker dataBroker, WriteTransaction t) {
        if(ifL2vlan.getVlanId() != null) {
            addTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, portName, ifL2vlan.getVlanId().getValue(), null, null,
                    ifTunnel, t);
        }
    }

    private static void addTunnelPortToBridge(IfTunnel ifTunnel, InstanceIdentifier<?> bridgeIid, Interface iface,
                                              OvsdbBridgeAugmentation bridgeAugmentation, String bridgeName,
                                              String portName, DataBroker dataBroker, WriteTransaction t) {
        LOG.debug("adding tunnel port {} to bridge {}",portName, bridgeName);

        Class<? extends InterfaceTypeBase> type = TUNNEL_TYPE_MAP.get(ifTunnel.getTunnelInterfaceType());

        if (type == null) {
            LOG.warn("Unknown Tunnel Type obtained while creating interface: {}", iface);
            return;
        }

        int vlanId = 0;
        IfL2vlan ifL2vlan = iface.getAugmentation(IfL2vlan.class);
        if (ifL2vlan != null && ifL2vlan.getVlanId() != null) {
            vlanId = ifL2vlan.getVlanId().getValue();
        }

        Map<String, String> options = Maps.newHashMap();
        if((!ifTunnel.getTunnelInterfaceType().equals(TunnelTypeMplsOverGre.class) ) ){
            options.put("key", "flow");
        }

        IpAddress localIp = ifTunnel.getTunnelSource();
        options.put("local_ip", localIp.getIpv4Address().getValue());

        IpAddress remoteIp = ifTunnel.getTunnelDestination();
        options.put("remote_ip", remoteIp.getIpv4Address().getValue());

        addTerminationPoint(bridgeIid, bridgeAugmentation, bridgeName, portName, vlanId, type, options, ifTunnel, t);
    }

    // Update is allowed only for tunnel monitoring attributes
    public static void updateBfdParamtersForTerminationPoint(InstanceIdentifier<?> bridgeIid, IfTunnel ifTunnel, String portName,
                                                             WriteTransaction transaction){
        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), portName);
        LOG.debug("update bfd parameters for interface {}", tpIid);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();
        List<InterfaceBfd> bfdParams = getBfdParams(ifTunnel);
        tpAugmentationBuilder.setInterfaceBfd(bfdParams);

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

        transaction.merge(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);
    }

    private static void addTerminationPoint(InstanceIdentifier<?> bridgeIid, OvsdbBridgeAugmentation bridgeNode,
                                            String bridgeName, String portName, int vlanId, Class<? extends InterfaceTypeBase> type,
                                            Map<String, String> options, IfTunnel ifTunnel, WriteTransaction t) {
        InstanceIdentifier<TerminationPoint> tpIid = createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), portName);
        OvsdbTerminationPointAugmentationBuilder tpAugmentationBuilder = new OvsdbTerminationPointAugmentationBuilder();

        tpAugmentationBuilder.setName(portName);

        if (type != null) {
            tpAugmentationBuilder.setInterfaceType(type);
        }

        if (options != null) {
            List<Options> optionsList = new ArrayList<Options>();
            for (Map.Entry<String, String> entry : options.entrySet()) {
                OptionsBuilder optionsBuilder = new OptionsBuilder();
                optionsBuilder.setKey(new OptionsKey(entry.getKey()));
                optionsBuilder.setOption(entry.getKey());
                optionsBuilder.setValue(entry.getValue());
                optionsList.add(optionsBuilder.build());
            }
            tpAugmentationBuilder.setOptions(optionsList);
        }

        if (vlanId != 0) {
            tpAugmentationBuilder.setVlanMode(OvsdbPortInterfaceAttributes.VlanMode.Access);
            tpAugmentationBuilder.setVlanTag(new VlanId(vlanId));
        }


        if(bfdMonitoringEnabled(ifTunnel)) {
            List<InterfaceBfd> bfdParams = getBfdParams(ifTunnel);
            tpAugmentationBuilder.setInterfaceBfd(bfdParams);
        }

        TerminationPointBuilder tpBuilder = new TerminationPointBuilder();
        tpBuilder.setKey(InstanceIdentifier.keyOf(tpIid));
        tpBuilder.addAugmentation(OvsdbTerminationPointAugmentation.class, tpAugmentationBuilder.build());

        t.put(LogicalDatastoreType.CONFIGURATION, tpIid, tpBuilder.build(), true);

    }

    private static List<InterfaceBfd> getBfdParams(IfTunnel ifTunnel) {
        List<InterfaceBfd> bfdParams = new ArrayList<>();
        bfdParams.add(getIfBfdObj(BFD_PARAM_ENABLE,ifTunnel != null ? ifTunnel.isMonitorEnabled().toString() :"false"));
        bfdParams.add(getIfBfdObj(BFD_PARAM_MIN_TX, ifTunnel != null ? ifTunnel.getMonitorInterval().toString() : BFD_MIN_TX_VAL));
        bfdParams.add(getIfBfdObj(BFD_PARAM_MIN_RX, BFD_MIN_RX_VAL));
        bfdParams.add(getIfBfdObj(BFD_PARAM_DECAY_MIN_RX, BFD_DECAY_MIN_RX_VAL));
        bfdParams.add(getIfBfdObj(BFD_PARAM_FORWARDING_IF_RX, BFD_FORWARDING_IF_RX_VAL));
        bfdParams.add(getIfBfdObj(BFD_PARAM_CPATH_DOWN, BFD_CPATH_DOWN_VAL));
        bfdParams.add(getIfBfdObj(BFD_PARAM_CHECK_TNL_KEY, BFD_CHECK_TNL_KEY_VAL));
        return bfdParams;
    }

    private static InterfaceBfd getIfBfdObj(String key, String value) {
        InterfaceBfdBuilder bfdBuilder = new InterfaceBfdBuilder();
        bfdBuilder.setBfdKey(key).
                setKey(new InterfaceBfdKey(key)).setBfdValue(value);
        return bfdBuilder.build();
    }

    public static InstanceIdentifier<TerminationPoint> createTerminationPointInstanceIdentifier(NodeKey nodekey,
                                                                                                String portName){
        InstanceIdentifier<TerminationPoint> terminationPointPath = InstanceIdentifier
                .create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(OVSDB_TOPOLOGY_ID))
                .child(Node.class,nodekey)
                .child(TerminationPoint.class, new TerminationPointKey(new TpId(portName)));

        LOG.debug("Termination point InstanceIdentifier generated : {}",terminationPointPath);
        return terminationPointPath;
    }

    public static void removeTerminationEndPoint(List<ListenableFuture<Void>> futures, DataBroker dataBroker, InstanceIdentifier<?> bridgeIid,
                                                 String interfaceName) {
        LOG.debug("removing termination point for {}", interfaceName);
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<TerminationPoint> tpIid = SouthboundUtils.createTerminationPointInstanceIdentifier(
                InstanceIdentifier.keyOf(bridgeIid.firstIdentifierOf(Node.class)), interfaceName);
        transaction.delete(LogicalDatastoreType.CONFIGURATION, tpIid);
        futures.add(transaction.submit());
    }

    public static boolean bfdMonitoringEnabled(IfTunnel ifTunnel){
        if(ifTunnel.isMonitorEnabled() && TunnelMonitoringTypeBfd.class.isAssignableFrom(ifTunnel.getMonitorProtocol())) {
            return true;
        }
        return false;
    }

    public static boolean isMonitorProtocolBfd(IfTunnel ifTunnel){
        if(TunnelMonitoringTypeBfd.class.isAssignableFrom(ifTunnel.getMonitorProtocol())) {
            return true;
        }
        return false;

    }
}
