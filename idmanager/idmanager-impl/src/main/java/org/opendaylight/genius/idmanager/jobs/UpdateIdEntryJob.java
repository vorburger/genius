/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.genius.idmanager.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.idmanager.IdUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.id.pool.IdEntries;

public class UpdateIdEntryJob implements Callable<List<ListenableFuture<Void>>> {

    private final String parentPoolName;
    private final String localPoolName;
    private final String idKey;
    private final List<Long> newIdValues;
    private final DataBroker broker;

    public UpdateIdEntryJob(String parentPoolName, String localPoolName,
            String idKey, List<Long> newIdValues, DataBroker broker) {
        super();
        this.parentPoolName = parentPoolName;
        this.localPoolName = localPoolName;
        this.idKey = idKey;
        this.newIdValues = newIdValues;
        this.broker = broker;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        IdUtils.updateChildPool(tx, parentPoolName, localPoolName);
        if (newIdValues != null && !newIdValues.isEmpty()) {
            IdEntries newIdEntry = IdUtils.createIdEntries(idKey, newIdValues);
            tx.merge(LogicalDatastoreType.CONFIGURATION, IdUtils.getIdEntriesInstanceIdentifier(parentPoolName, idKey),
                    newIdEntry);
            futures.add(tx.submit());
            return futures;
        }
        tx.delete(LogicalDatastoreType.CONFIGURATION, IdUtils.getIdEntriesInstanceIdentifier(parentPoolName, idKey));
        futures.add(tx.submit());
        return futures;
    }
}
