/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.itm.confighelpers;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.itm.impl.ItmUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.TransportZones;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TepsNotHostedInTransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TepsNotHostedInTransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZone;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.TransportZoneKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.tepsnothostedintransportzone.UnknownVteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.tepsnothostedintransportzone.UnknownVtepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.Vteps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rev160406.transport.zones.transport.zone.subnets.VtepsKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OvsdbTepRemoveConfigHelper {

    private static final Logger LOG = LoggerFactory.getLogger(OvsdbTepRemoveConfigHelper.class);

    /**
     * Removes the TEP from ITM configuration Datastore in one of the following cases.
     * 1) default transport zone
     * 2) Configured transport zone
     * 3) Unhosted transport zone
     * Function checks for above three cases and calls other sub-function to remove the TEP
     *
     * @param tepIp TEP-IP address in string
     * @param strDpnId bridge datapath ID in string
     * @param tzName transport zone name in string
     * @param dataBroker data broker handle to perform operations on config datastore
     * @param wrTx WriteTransaction object
     */

    public static void removeTepReceivedFromOvsdb(String tepIp, String strDpnId, String tzName,
                                                  DataBroker dataBroker, WriteTransaction wrTx) {
        BigInteger dpnId = BigInteger.valueOf(0);

        LOG.trace("Remove TEP: TEP-IP: {}, TZ name: {}, DPID: {}", tepIp, tzName, strDpnId);

        if (strDpnId != null && !strDpnId.isEmpty()) {
            dpnId = MDSALUtil.getDpnId(strDpnId);
        }

        // Get tep IP
        IpAddress tepIpAddress = new IpAddress(tepIp.toCharArray());
        TransportZone transportZone = null;

        // Case: TZ name is not given from OVS's other_config parameters.
        if (tzName == null) {
            tzName = ITMConstants.DEFAULT_TRANSPORT_ZONE;
            // add TEP into default-TZ
            transportZone = ItmUtils.getTransportZoneFromConfigDS(tzName, dataBroker);
            if (transportZone == null) {
                LOG.error("Error: default-transport-zone is not yet created.");
                return;
            }
            LOG.trace("Remove TEP from default-transport-zone.");
        } else {
            // Case: Add TEP into corresponding TZ created from Northbound.
            transportZone = ItmUtils.getTransportZoneFromConfigDS(tzName, dataBroker);
            if (transportZone == null) {
                // Case: TZ is not configured from Northbound, then add TEP into
                // "teps-not-hosted-in-transport-zone"
                LOG.trace("Removing TEP from unknown TZ into teps-not-hosted-in-transport-zone.");
                removeUnknownTzTepFromTepsNotHosted(tzName, tepIpAddress, dpnId, dataBroker, wrTx);
                return;
            } else {
                LOG.trace("Remove TEP from transport-zone already configured by Northbound.");
            }
        }

        // Remove TEP from (default transport-zone) OR (transport-zone already configured by Northbound)

        // Get subnet list of corresponding TZ created from Northbound.
        List<Subnets> subnetList = transportZone.getSubnets();

        if (subnetList == null || subnetList.isEmpty()) {
            LOG.trace("No subnet list in transport-zone. Nothing to do.");
        } else {
            String portName = ITMConstants.DUMMY_PORT;
            IpPrefix subnetMaskObj = ItmUtils.getDummySubnet();

            List<Vteps> vtepList = null;

            // subnet list already exists case; check for dummy-subnet
            for (Subnets subnet : subnetList) {
                if (subnet.getKey().getPrefix().equals(subnetMaskObj)) {
                    LOG.trace("Subnet exists in the subnet list of transport-zone {}.", tzName);
                    // get vtep list of existing subnet
                    vtepList = subnet.getVteps();
                    break;
                }
            }

            if (vtepList == null || vtepList.isEmpty()) {
                //  case: vtep list does not exist or it has no elements
                LOG.trace("No vtep list in subnet list of transport-zone. Nothing to do.");
            } else {
                //  case: vtep list has elements
                boolean vtepFound = false;
                Vteps oldVtep = null;

                for (Vteps vtep : vtepList) {
                    if (vtep.getDpnId().equals(dpnId)) {
                        vtepFound = true;
                        oldVtep = vtep;
                        // get portName of existing vtep
                        portName = vtep.getPortname();
                        break;
                    }
                }
                if (vtepFound) {
                    // vtep is found, update it with tep-ip
                    LOG.trace("Remove TEP from vtep list in subnet list of transport-zone.");
                    dpnId = oldVtep.getDpnId();
                    portName = oldVtep.getPortname();
                    removeVtepFromTZConfig(subnetMaskObj, tzName, dpnId, portName, wrTx);
                } else {
                    LOG.trace(
                        "TEP is not found in the vtep list in subnet list of transport-zone. Nothing to do.");
                }
            }
        }
    }

    /**
     * Removes the TEP from subnet list in the transport zone list
     * from ITM configuration Datastore by delete operation with write transaction.
     *
     * @param subnetMaskObj subnet mask in IpPrefix object
     * @param dpnId bridge datapath ID in BigInteger
     * @param tzName transport zone name in string
     * @param portName port name as a part of VtepsKey
     * @param wrTx WriteTransaction object
     */
    private static void removeVtepFromTZConfig(IpPrefix subnetMaskObj, String tzName, BigInteger dpnId,
        String portName, WriteTransaction wrTx) {
        SubnetsKey subnetsKey = new SubnetsKey(subnetMaskObj);
        VtepsKey vtepkey = new VtepsKey(dpnId, portName);

        InstanceIdentifier<Vteps> vtepPath = InstanceIdentifier.builder(TransportZones.class)
            .child(TransportZone.class, new TransportZoneKey(tzName))
            .child(Subnets.class, subnetsKey).child(Vteps.class, vtepkey).build();

        LOG.trace("Removing TEP (TZ: {} Subnet: {} DPN-ID: {}) in ITM Config DS.", tzName,
                subnetMaskObj.getValue().toString(), dpnId);
        // remove vtep
        wrTx.delete(LogicalDatastoreType.CONFIGURATION, vtepPath);
    }

    /**
     * Removes the TEP from the not-hosted transport zone in the TepsNotHosted list
     * from ITM configuration Datastore.
     *
     * @param tzName transport zone name in string
     * @param tepIpAddress TEP IP address in IpAddress object
     * @param dpnId bridge datapath ID in BigInteger
     * @param dataBroker data broker handle to perform operations on config datastore
     * @param wrTx WriteTransaction object
     */
    public static void removeUnknownTzTepFromTepsNotHosted(String tzName, IpAddress tepIpAddress,
                                                           BigInteger dpnId, DataBroker dataBroker,
                                                           WriteTransaction wrTx) {
        List<UnknownVteps> vtepList = null;

        TepsNotHostedInTransportZone unknownTz =
            ItmUtils.getUnknownTransportZoneFromITMConfigDS(tzName, dataBroker);
        if (unknownTz == null) {
            LOG.trace("Unhosted TransportZone does not exist. Nothing to do for TEP removal.");
            return;
        } else {
            vtepList = unknownTz.getUnknownVteps();
            if (vtepList == null || vtepList.isEmpty()) {
                //  case: vtep list does not exist or it has no elements
                LOG.trace(
                    "Remove TEP in unhosted TZ ({}) when no vtep-list in the TZ. Nothing to do.",
                    tzName);
            } else {
                //  case: vtep list has elements
                boolean vtepFound = false;
                UnknownVteps foundVtep = null;

                for (UnknownVteps vtep : vtepList) {
                    if (vtep.getDpnId().equals(dpnId)) {
                        vtepFound = true;
                        foundVtep = vtep;
                        break;
                    }
                }
                if (vtepFound) {
                    // vtep is found, update it with tep-ip
                    LOG.trace(
                        "Remove TEP with IP ({}) from unhosted TZ ({}) in TepsNotHosted list.",
                        tepIpAddress, tzName);
                    if (vtepList.size() == 1) {
                        removeTzFromTepsNotHosted(tzName, wrTx);
                    } else {
                        removeVtepFromTepsNotHosted(tzName, dpnId, wrTx);
                    }
                    vtepList.remove(foundVtep);
                }
            }
        }
    }

    /**
     * Removes the TEP from unknown vtep list under the transport zone in the TepsNotHosted list
     * from ITM configuration Datastore by delete operation with write transaction.
     *
     * @param tzName transport zone name in string
     * @param dpnId bridge datapath ID in BigInteger
     * @param wrTx WriteTransaction object
     */
    private static void removeVtepFromTepsNotHosted(String tzName, BigInteger dpnId,
                                                      WriteTransaction wrTx) {

        UnknownVtepsKey unknownVtepkey = new UnknownVtepsKey(dpnId);
        InstanceIdentifier<UnknownVteps> vtepPath = InstanceIdentifier.builder(TransportZones.class)
            .child(TepsNotHostedInTransportZone.class, new TepsNotHostedInTransportZoneKey(tzName))
            .child(UnknownVteps.class, unknownVtepkey).build();

        LOG.trace("Removing TEP from unhosted (TZ: {}, DPID: {}) from ITM Config DS.",
                tzName, dpnId);
        // remove vtep
        wrTx.delete(LogicalDatastoreType.CONFIGURATION, vtepPath);
    }

    /**
     * Removes the transport zone in the TepsNotHosted list
     * from ITM configuration Datastore by delete operation with write transaction.
     *
     * @param tzName transport zone name in string
     * @param wrTx WriteTransaction object
     */
    private static void removeTzFromTepsNotHosted(String tzName, WriteTransaction wrTx) {
        InstanceIdentifier<TepsNotHostedInTransportZone> tzTepsNotHostedTepPath =
                InstanceIdentifier.builder(TransportZones.class)
                .child(TepsNotHostedInTransportZone.class,
                    new TepsNotHostedInTransportZoneKey(tzName)).build();

        LOG.trace("Removing TZ ({})from TepsNotHosted list  from ITM Config DS.", tzName);
        // remove TZ from TepsNotHosted list
        wrTx.delete(LogicalDatastoreType.CONFIGURATION, tzTepsNotHostedTepPath);
    }
}
