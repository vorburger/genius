/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.genius.datastoreutils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinatorMonitor;
import org.opendaylight.infrautils.jobcoordinator.workaround.Activator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DataStoreJobCoordinator.
 *
 * @deprecated Use org.opendaylight.infrautils.jobcoordinator.JobCoordinator
 *             instead of this. Please note that in its new reincarnation it's no
 *             longer a static singleton but now an OSGi service which you can (must)
 *             {@literal @}Inject as {@literal @}OsgiService into your class using it.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class DataStoreJobCoordinator implements JobCoordinator {

    private static final Logger LOG = LoggerFactory.getLogger(DataStoreJobCoordinator.class);

    private static DataStoreJobCoordinator instance;

    public static synchronized DataStoreJobCoordinator getInstance() {
        if (instance == null) {
            instance = new DataStoreJobCoordinator(Activator.getJobCoordinator(), Activator.getJobCoordinatorMonitor());
        }
        return instance;
    }

    public static void setInstance(DataStoreJobCoordinator dataStoreJobCoordinator) {
        if (instance != null && dataStoreJobCoordinator != null) {
            throw new IllegalStateException("@Deprecated DataStoreJobCoordinator instance static already initalized "
                    + "with a previous instance which needs to be closed and then null-ified, instead of overwritten "
                    + "(Component Tests should use JobCoordinatorTestModule in their GuiceRule)");
        }
        instance = dataStoreJobCoordinator;
    }

    private final JobCoordinator infrautilsJobCoordinatorDelegate;
    private final JobCoordinatorMonitor infrautilsJobCoordinatorMonitor;

    public DataStoreJobCoordinator(JobCoordinator jobCoordinator, JobCoordinatorMonitor jobCoordinatorMonitor) {
        this.infrautilsJobCoordinatorDelegate = jobCoordinator;
        this.infrautilsJobCoordinatorMonitor = jobCoordinatorMonitor;
    }

    public void close() {
        setInstance(null);
    }

    @Override
    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker) {
        enqueueJob(key, mainWorker, (RollbackCallable)null, 0);
    }

    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker,
            RollbackCallable rollbackWorker) {
        // https://jira.opendaylight.org/browse/GENIUS-93
        if (rollbackWorker != null) {
            infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker,
                    new InfrautilsRollbackCallableDelegate(rollbackWorker));
        } else {
            infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker);
        }
    }

    @Override
    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker,
            org.opendaylight.infrautils.jobcoordinator.RollbackCallable rollbackWorker) {
        enqueueJob(key, mainWorker, rollbackWorker, 0);
    }

    @Override
    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker,
            org.opendaylight.infrautils.jobcoordinator.RollbackCallable rollbackWorker, int maxRetries) {
        infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker, rollbackWorker, maxRetries);
    }

    @Override
    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker, int maxRetries) {
        infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker, maxRetries);
    }

    public void enqueueJob(String key, Callable<List<ListenableFuture<Void>>> mainWorker,
                           RollbackCallable rollbackWorker, int maxRetries) {
        // https://jira.opendaylight.org/browse/GENIUS-93
        if (rollbackWorker != null) {
            infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker,
                    new InfrautilsRollbackCallableDelegate(rollbackWorker), maxRetries);
        } else {
            infrautilsJobCoordinatorDelegate.enqueueJob(key, mainWorker, maxRetries);
        }
    }

    public long getIncompleteTaskCount() {
        return infrautilsJobCoordinatorMonitor.getIncompleteTaskCount();
    }

    private static class InfrautilsRollbackCallableDelegate
        implements org.opendaylight.infrautils.jobcoordinator.RollbackCallable {

        private final RollbackCallable geniusRollbackCallable;

        InfrautilsRollbackCallableDelegate(RollbackCallable rollbackCallable) {
            this.geniusRollbackCallable = Objects.requireNonNull(rollbackCallable, "rollbackCallable");
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> apply(List<ListenableFuture<Void>> failedFutures) {
            geniusRollbackCallable.setFutures(failedFutures);
            try {
                return geniusRollbackCallable.call();
            } catch (Exception e) {
                LOG.error("Error running rollback task", e);
                return Collections.emptyList();
            }
        }
    }

    @Override
    public String toString() {
        return "DataStoreJobCoordinator: " + infrautilsJobCoordinatorDelegate.toString();
    }
}
