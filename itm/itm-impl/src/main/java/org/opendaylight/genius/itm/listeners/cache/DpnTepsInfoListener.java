/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.genius.itm.listeners.cache;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.utils.cache.DataStoreCache;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ehemgop on 18-08-2016.
 */
public class DpnTepsInfoListener extends AsyncClusteredDataTreeChangeListenerBase<DPNTEPsInfo,DpnTepsInfoListener> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(DpnTepsInfoListener.class);
    private final DataBroker broker;

    /**
     * Responsible for listening to DPNTEPsInfo change
     *
     */

    public DpnTepsInfoListener(final DataBroker broker) {
        super(DPNTEPsInfo.class, DpnTepsInfoListener.class);
        this.broker = broker;
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }


    @Override
    public void close() throws Exception {
        LOG.info("dpnTepsInfo Listener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<DPNTEPsInfo> identifier, DPNTEPsInfo del) {
        LOG.debug(" Remove from cache " + ITMConstants.DPN_TEPs_Info_CACHE_NAME + " Invoked for data Obj " + del.getDPNID() + " String ver " + del.getDPNID().toString());
        DataStoreCache.remove(ITMConstants.DPN_TEPs_Info_CACHE_NAME, del.getDPNID()) ;
    }

    @Override
    protected void update(InstanceIdentifier<DPNTEPsInfo> identifier, DPNTEPsInfo original,
                          DPNTEPsInfo update) {
        LOG.debug(" Update to cache " + ITMConstants.DPN_TEPs_Info_CACHE_NAME + " Invoked for data Obj " + update.getDPNID() ) ;
        DataStoreCache.add(ITMConstants.DPN_TEPs_Info_CACHE_NAME, update.getDPNID(), update);
    }

    @Override
    protected void add(InstanceIdentifier<DPNTEPsInfo> identifier, DPNTEPsInfo add) {
        LOG.debug(" Add to cache " + ITMConstants.DPN_TEPs_Info_CACHE_NAME + " Invoked for data Obj " + add.getDPNID() ) ;
        DataStoreCache.add(ITMConstants.DPN_TEPs_Info_CACHE_NAME, add.getDPNID(), add);
    }

    @Override
    protected DpnTepsInfoListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<DPNTEPsInfo> getWildCardPath() {
        return InstanceIdentifier.builder(DpnEndpoints.class).
                child(DPNTEPsInfo.class).build();
    }

}