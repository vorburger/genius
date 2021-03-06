/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.genius.srm;

import com.google.common.util.concurrent.Futures;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.infra.FutureRpcResults;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.rpcs.rev170711.RecoverInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.rpcs.rev170711.RecoverOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.rpcs.rev170711.ReinstallInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.rpcs.rev170711.ReinstallOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.srm.rpcs.rev170711.SrmRpcsService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SrmRpcProvider implements SrmRpcsService {

    private static final Logger LOG = LoggerFactory.getLogger(SrmRpcProvider.class);

    private final DataBroker dataBroker;

    @Inject
    public SrmRpcProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public Future<RpcResult<RecoverOutput>> recover(RecoverInput input) {
        return FutureRpcResults.fromListenableFuture(LOG, "recover", input, () -> {
            return Futures.immediateFuture(SrmRpcUtils.callSrmOp(dataBroker, input));
        }).build();
    }

    @Override
    public Future<RpcResult<ReinstallOutput>> reinstall(ReinstallInput input) {
        return FutureRpcResults.fromListenableFuture(LOG, "reinstall", input, () -> {
            return Futures.immediateFuture(SrmRpcUtils.callSrmOp(dataBroker, input));
        }).build();
    }

}
