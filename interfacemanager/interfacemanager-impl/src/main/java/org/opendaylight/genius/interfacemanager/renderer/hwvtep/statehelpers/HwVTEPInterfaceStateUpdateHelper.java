/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.interfacemanager.renderer.hwvtep.statehelpers;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.genius.interfacemanager.commons.InterfaceMetaUtils;
import org.opendaylight.genius.interfacemanager.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdStatus;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HwVTEPInterfaceStateUpdateHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HwVTEPInterfaceStateUpdateHelper.class);

    private HwVTEPInterfaceStateUpdateHelper() {
    }

    public static List<ListenableFuture<Void>> updatePhysicalSwitch(ManagedNewTransactionRunner txRunner,
            InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier, Tunnels tunnelsNew) {
        LOG.debug("updating physical switch for tunnels");
        return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
            String interfaceName = InterfaceMetaUtils
                    .getInterfaceForTunnelInstanceIdentifier(tunnelsInstanceIdentifier.toString(), tx);
            if (interfaceName != null) {
                // update opstate of interface if TEP has gone down/up as a result of
                // BFD monitoring
                InterfaceManagerCommonUtils.updateOpState(tx, interfaceName,
                        getTunnelOpState(tunnelsNew.getBfdStatus()));
            }
        }));
    }

    private static OperStatus getTunnelOpState(List<BfdStatus> tunnelBfdStatus) {
        OperStatus livenessState = OperStatus.Down;
        if (tunnelBfdStatus != null && !tunnelBfdStatus.isEmpty()) {
            for (BfdStatus bfdState : tunnelBfdStatus) {
                if (bfdState.getBfdStatusKey().equalsIgnoreCase(SouthboundUtils.BFD_OP_STATE)) {
                    String bfdOpState = bfdState.getBfdStatusValue();
                    if (bfdOpState.equalsIgnoreCase(SouthboundUtils.BFD_STATE_UP)) {
                        livenessState = OperStatus.Up;
                    } else {
                        livenessState = OperStatus.Down;
                    }
                    break;
                }
            }
        }
        return livenessState;
    }

    public static List<ListenableFuture<Void>> startBfdMonitoring(ManagedNewTransactionRunner txRunner,
            InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier, Tunnels tunnelsNew) {
        LOG.debug("starting bfd monitoring for the hwvtep {}", tunnelsInstanceIdentifier);

        TunnelsBuilder tunnelsBuilder = new TunnelsBuilder();
        tunnelsBuilder.setKey(new TunnelsKey(tunnelsNew.getLocalLocatorRef(), tunnelsNew.getRemoteLocatorRef()));
        tunnelsBuilder.setLocalLocatorRef(tunnelsNew.getLocalLocatorRef());
        tunnelsBuilder.setRemoteLocatorRef(tunnelsNew.getLocalLocatorRef());
        List<BfdParams> bfdParams = new ArrayList<>();
        SouthboundUtils.fillBfdParameters(bfdParams, null);
        tunnelsBuilder.setBfdParams(bfdParams);
        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.put(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier,
                    tunnelsBuilder.build(), WriteTransaction.CREATE_MISSING_PARENTS)));
    }
}
