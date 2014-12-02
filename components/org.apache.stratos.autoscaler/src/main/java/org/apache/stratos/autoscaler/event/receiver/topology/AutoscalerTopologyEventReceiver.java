/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.stratos.autoscaler.event.receiver.topology;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.applications.ApplicationHolder;
import org.apache.stratos.autoscaler.context.AutoscalerContext;
import org.apache.stratos.autoscaler.context.cluster.ClusterContextFactory;
import org.apache.stratos.autoscaler.context.cluster.VMClusterContext;
import org.apache.stratos.autoscaler.event.publisher.ClusterStatusEventPublisher;
import org.apache.stratos.autoscaler.event.publisher.InstanceNotificationPublisher;
import org.apache.stratos.autoscaler.exception.partition.PartitionValidationException;
import org.apache.stratos.autoscaler.exception.policy.PolicyValidationException;
import org.apache.stratos.autoscaler.monitor.cluster.AbstractClusterMonitor;
import org.apache.stratos.autoscaler.monitor.component.ApplicationMonitor;
import org.apache.stratos.autoscaler.monitor.events.ClusterStatusEvent;
import org.apache.stratos.autoscaler.pojo.policy.PolicyManager;
import org.apache.stratos.autoscaler.pojo.policy.deployment.DeploymentPolicy;
import org.apache.stratos.autoscaler.util.AutoscalerUtil;
import org.apache.stratos.autoscaler.util.ServiceReferenceHolder;
import org.apache.stratos.messaging.domain.applications.Application;
import org.apache.stratos.messaging.domain.applications.Applications;
import org.apache.stratos.messaging.domain.applications.ClusterDataHolder;
import org.apache.stratos.messaging.domain.topology.Cluster;
import org.apache.stratos.messaging.domain.topology.ClusterStatus;
import org.apache.stratos.messaging.domain.topology.Service;
import org.apache.stratos.messaging.domain.topology.Topology;
import org.apache.stratos.messaging.event.Event;
import org.apache.stratos.messaging.event.topology.*;
import org.apache.stratos.messaging.listener.topology.*;
import org.apache.stratos.messaging.message.receiver.topology.TopologyEventReceiver;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;

/**
 * Autoscaler topology receiver.
 */
public class AutoscalerTopologyEventReceiver implements Runnable {

    private static final Log log = LogFactory.getLog(AutoscalerTopologyEventReceiver.class);

    private TopologyEventReceiver topologyEventReceiver;
    private boolean terminated;
    private boolean topologyInitialized;

    public AutoscalerTopologyEventReceiver() {
        this.topologyEventReceiver = new TopologyEventReceiver();
        addEventListeners();
    }

    @Override
    public void run() {
        //FIXME this activated before autoscaler deployer activated.
        /*try {
            Thread.sleep(15000);
        } catch (InterruptedException ignore) {
        }*/
        Thread thread = new Thread(topologyEventReceiver);
        thread.start();
        if (log.isInfoEnabled()) {
            log.info("Autoscaler topology receiver thread started");
        }

        // Keep the thread live until terminated
        while (!terminated) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }
        if (log.isInfoEnabled()) {
            log.info("Autoscaler topology receiver thread terminated");
        }
    }

    private boolean allClustersInitialized(Application application) {
        boolean allClustersInitialized = false;
        for (ClusterDataHolder holder : application.getClusterDataRecursively()) {
            TopologyManager.acquireReadLockForCluster(holder.getServiceType(),
                    holder.getClusterId());

            try {
                Topology topology = TopologyManager.getTopology();
                if (topology != null) {
                    Service service = topology.getService(holder.getServiceType());
                    if (service != null) {
                        if (service.clusterExists(holder.getClusterId())) {
                            allClustersInitialized = true;
                            return allClustersInitialized;
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("[Cluster] " + holder.getClusterId() + " is not found in " +
                                        "the Topology");
                            }
                            allClustersInitialized = false;
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Service is null in the CompleteTopologyEvent");
                        }
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("Topology is null in the CompleteTopologyEvent");
                    }
                }
            } finally {
                TopologyManager.releaseReadLockForCluster(holder.getServiceType(),
                        holder.getClusterId());
            }
        }
        return allClustersInitialized;
    }


    private void addEventListeners() {
        // Listen to topology events that affect clusters
        topologyEventReceiver.addEventListener(new CompleteTopologyEventListener() {
            @Override
            protected void onEvent(Event event) {
                if (!topologyInitialized) {
                    log.info("[CompleteTopologyEvent] Received: " + event.getClass());
                    ApplicationHolder.acquireReadLock();
                    try {
                        Applications applications = ApplicationHolder.getApplications();
                        if (applications != null) {
                            for (Application application : applications.getApplications().values()) {
                                if (allClustersInitialized(application)) {
                                    DeploymentPolicy policy = PolicyManager.getInstance().
                                            getDeploymentPolicyByApplication(
                                                    application.getUniqueIdentifier());
                                    if (policy != null) {
                                        AutoscalerUtil.getInstance().
                                                startApplicationMonitor(application.getUniqueIdentifier());
                                    } else {
                                        log.info("The relevant application policy is not yet " +
                                                "deployed for this [application] " +
                                                application.getUniqueIdentifier());
                                    }
                                } else {
                                    log.error("Complete Topology is not consistent with the applications " +
                                            "which got persisted");
                                }
                            }
                            topologyInitialized = true;
                        } else {
                            log.info("No applications found in the complete topology");
                        }
                    } catch (Exception e) {
                        log.error("Error processing event", e);
                    } finally {
                        ApplicationHolder.releaseReadLock();
                    }
                }
            }
        });


        topologyEventReceiver.addEventListener(new ApplicationClustersCreatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                try {
                    log.info("[ApplicationClustersCreatedEvent] Received: " + event.getClass());
                    ApplicationClustersCreatedEvent applicationClustersCreatedEvent =
                            (ApplicationClustersCreatedEvent) event;
                    String appId = applicationClustersCreatedEvent.getAppId();
                    try {
                        //acquire read lock
                        ApplicationHolder.acquireReadLock();
                        //start the application monitor
                        //startApplicationMonitor(appId);
                    } catch (Exception e) {
                        String msg = "Error processing event " + e.getLocalizedMessage();
                        log.error(msg, e);
                    } finally {
                        //release read lock
                        ApplicationHolder.releaseReadLock();

                    }
                } catch (ClassCastException e) {
                    String msg = "Error while casting the event " + e.getLocalizedMessage();
                    log.error(msg, e);
                }

            }
        });

        topologyEventReceiver.addEventListener(new ClusterActivatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterActivatedEvent] Received: " + event.getClass());
                ClusterActivatedEvent clusterActivatedEvent = (ClusterActivatedEvent) event;
                String clusterId = clusterActivatedEvent.getClusterId();
                AutoscalerContext asCtx = AutoscalerContext.getInstance();
                AbstractClusterMonitor monitor;
                monitor = asCtx.getClusterMonitor(clusterId);
                if (null == monitor) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                + "[cluster] %s", clusterId));
                    }
                    return;
                }
                //changing the status in the monitor, will notify its parent monitor

            }
        });

        topologyEventReceiver.addEventListener(new ClusterResetEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterCreatedEvent] Received: " + event.getClass());
                ClusterResetEvent clusterResetEvent = (ClusterResetEvent) event;
                String clusterId = clusterResetEvent.getClusterId();
                AutoscalerContext asCtx = AutoscalerContext.getInstance();
                AbstractClusterMonitor monitor;
                monitor = asCtx.getClusterMonitor(clusterId);
                if (null == monitor) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                + "[cluster] %s", clusterId));
                    }
                    return;
                }
                //changing the status in the monitor, will notify its parent monitor
                monitor.destroy();
                monitor.setStatus(ClusterStatus.Created);

            }
        });

        topologyEventReceiver.addEventListener(new ClusterCreatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterCreatedEvent] Received: " + event.getClass());
            }
        });

        topologyEventReceiver.addEventListener(new ClusterInActivateEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterInActivateEvent] Received: " + event.getClass());
                ClusterInactivateEvent clusterInactivateEvent = (ClusterInactivateEvent) event;
                String clusterId = clusterInactivateEvent.getClusterId();
                AutoscalerContext asCtx = AutoscalerContext.getInstance();
                AbstractClusterMonitor monitor;
                monitor = asCtx.getClusterMonitor(clusterId);
                if (null == monitor) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                + "[cluster] %s", clusterId));
                    }
                    return;
                }
                //changing the status in the monitor, will notify its parent monitor
                monitor.setStatus(ClusterStatus.Inactive);
            }
        });

        topologyEventReceiver.addEventListener(new ClusterTerminatingEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterTerminatingEvent] Received: " + event.getClass());
                ClusterTerminatingEvent clusterTerminatingEvent = (ClusterTerminatingEvent) event;
                String clusterId = clusterTerminatingEvent.getClusterId();
                String instanceId = clusterTerminatingEvent.getInstanceId();
                AutoscalerContext asCtx = AutoscalerContext.getInstance();
                AbstractClusterMonitor monitor;
                monitor = asCtx.getClusterMonitor(clusterId);
                if (null == monitor) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                + "[cluster] %s", clusterId));
                    }
                    // if monitor does not exist, send cluster terminated event
                    ClusterStatusEventPublisher.sendClusterTerminatedEvent(clusterTerminatingEvent.getAppId(),
                            clusterTerminatingEvent.getServiceName(), clusterId, instanceId);
                    return;
                }
                //changing the status in the monitor, will notify its parent monitor
                if (monitor.getStatus() == ClusterStatus.Active) {
                    // terminated gracefully
                    monitor.setStatus(ClusterStatus.Terminating);
                    InstanceNotificationPublisher.sendInstanceCleanupEventForCluster(clusterId);
                } else {
                    monitor.setStatus(ClusterStatus.Terminating);
                    monitor.terminateAllMembers();
                }
                ServiceReferenceHolder.getInstance().getClusterStatusProcessorChain().
                        process("", clusterId, instanceId);
            }
        });

        topologyEventReceiver.addEventListener(new ClusterTerminatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                log.info("[ClusterTerminatedEvent] Received: " + event.getClass());
                ClusterTerminatedEvent clusterTerminatedEvent = (ClusterTerminatedEvent) event;
                String clusterId = clusterTerminatedEvent.getClusterId();
                AutoscalerContext asCtx = AutoscalerContext.getInstance();
                AbstractClusterMonitor monitor;
                monitor = asCtx.getClusterMonitor(clusterId);
                if (null == monitor) {
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                + "[cluster] %s", clusterId));
                    }
                    // if the cluster monitor is null, assume that its termianted
                    ApplicationMonitor appMonitor = AutoscalerContext.getInstance().getAppMonitor(clusterTerminatedEvent.getAppId());
                    if (appMonitor != null) {
                        appMonitor.onChildStatusEvent(new ClusterStatusEvent(ClusterStatus.Terminated, clusterId, null));
                    }
                    return;
                }
                //changing the status in the monitor, will notify its parent monitor
                monitor.setStatus(ClusterStatus.Terminated);
                //Destroying and Removing the Cluster monitor
                monitor.destroy();
                AutoscalerContext.getInstance().removeClusterMonitor(clusterId);
            }
        });

        topologyEventReceiver.addEventListener(new MemberReadyToShutdownEventListener() {
            @Override
            protected void onEvent(Event event) {
                try {
                    MemberReadyToShutdownEvent memberReadyToShutdownEvent = (MemberReadyToShutdownEvent) event;
                    String clusterId = memberReadyToShutdownEvent.getClusterId();
                    AutoscalerContext asCtx = AutoscalerContext.getInstance();
                    AbstractClusterMonitor monitor;
                    monitor = asCtx.getClusterMonitor(clusterId);
                    if (null == monitor) {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                    + "[cluster] %s", clusterId));
                        }
                        return;
                    }
                    monitor.handleMemberReadyToShutdownEvent(memberReadyToShutdownEvent);
                } catch (Exception e) {
                    String msg = "Error processing event " + e.getLocalizedMessage();
                    log.error(msg, e);
                }
            }
        });


        topologyEventReceiver.addEventListener(new MemberStartedEventListener() {
            @Override
            protected void onEvent(Event event) {

            }
        });

        topologyEventReceiver.addEventListener(new MemberTerminatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                try {
                    MemberTerminatedEvent memberTerminatedEvent = (MemberTerminatedEvent) event;
                    String clusterId = memberTerminatedEvent.getClusterId();
                    AbstractClusterMonitor monitor;
                    AutoscalerContext asCtx = AutoscalerContext.getInstance();
                    monitor = asCtx.getClusterMonitor(clusterId);
                    if (null == monitor) {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                    + "[cluster] %s", clusterId));
                        }
                        return;
                    }
                    monitor.handleMemberTerminatedEvent(memberTerminatedEvent);
                } catch (Exception e) {
                    String msg = "Error processing event " + e.getLocalizedMessage();
                    log.error(msg, e);
                }
            }
        });

        topologyEventReceiver.addEventListener(new MemberActivatedEventListener() {
            @Override
            protected void onEvent(Event event) {
                try {
                    MemberActivatedEvent memberActivatedEvent = (MemberActivatedEvent) event;
                    String clusterId = memberActivatedEvent.getClusterId();
                    AbstractClusterMonitor monitor;
                    AutoscalerContext asCtx = AutoscalerContext.getInstance();
                    monitor = asCtx.getClusterMonitor(clusterId);
                    if (null == monitor) {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                    + "[cluster] %s", clusterId));
                        }
                        return;
                    }
                    monitor.handleMemberActivatedEvent(memberActivatedEvent);
                } catch (Exception e) {
                    String msg = "Error processing event " + e.getLocalizedMessage();
                    log.error(msg, e);
                }
            }
        });

        topologyEventReceiver.addEventListener(new MemberMaintenanceListener() {
            @Override
            protected void onEvent(Event event) {
                try {
                    MemberMaintenanceModeEvent maintenanceModeEvent = (MemberMaintenanceModeEvent) event;
                    String clusterId = maintenanceModeEvent.getClusterId();
                    AbstractClusterMonitor monitor;
                    AutoscalerContext asCtx = AutoscalerContext.getInstance();
                    monitor = asCtx.getClusterMonitor(clusterId);
                    if (null == monitor) {
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("A cluster monitor is not found in autoscaler context "
                                    + "[cluster] %s", clusterId));
                        }
                        return;
                    }
                    monitor.handleMemberMaintenanceModeEvent(maintenanceModeEvent);
                } catch (Exception e) {
                    String msg = "Error processing event " + e.getLocalizedMessage();
                    log.error(msg, e);
                }
            }
        });

        topologyEventReceiver.addEventListener(new ClusterInstanceCreatedEventListener() {
           @Override
           protected void onEvent(Event event) {

               ClusterInstanceCreatedEvent clusterInstanceCreatedEvent =
                       (ClusterInstanceCreatedEvent) event;
               AbstractClusterMonitor clusterMonitor = AutoscalerContext.getInstance().
                       getClusterMonitor(clusterInstanceCreatedEvent.getClusterId());
               String instanceId = ((ClusterInstanceCreatedEvent) event).getInstanceId();
               //FIXME to take lock when clusterMonitor is running
               if (clusterMonitor != null) {
                   TopologyManager.acquireReadLockForCluster(clusterInstanceCreatedEvent.getServiceName(),
                           clusterInstanceCreatedEvent.getClusterId());

                   try {
                       Service service = TopologyManager.getTopology().
                               getService(clusterInstanceCreatedEvent.getServiceName());

                       if (service != null) {
                           Cluster cluster = service.getCluster(clusterInstanceCreatedEvent.getClusterId());
                           if (cluster != null) {
                               try {
                                   if (cluster.isKubernetesCluster()) {
                                       clusterMonitor.setClusterContext(
                                               ClusterContextFactory.getKubernetesClusterContext(
                                                       instanceId,
                                                       cluster));
                                   } else {
                                       VMClusterContext clusterContext =
                                               (VMClusterContext) clusterMonitor.getClusterContext();
                                       if (clusterContext == null) {
                                           clusterMonitor.setClusterContext(
                                                   ClusterContextFactory.
                                                           getVMClusterContext(instanceId,
                                                                   cluster));
                                       } else {
                                           clusterContext.addInstanceContext(instanceId, cluster);
                                       }

                                   }
                                   if (clusterMonitor.hasMonitoringStarted().compareAndSet(false, true)) {
                                       clusterMonitor.startScheduler();
                                       log.info("Monitoring task for Cluster Monitor with cluster id " +
                                               clusterInstanceCreatedEvent.getClusterId() + " started successfully");
                                   }
                               } catch (PolicyValidationException e) {
                                   log.error(e.getMessage(), e);
                               } catch (PartitionValidationException e) {
                                   log.error(e.getMessage(), e);
                               }
                           }

                       } else {
                           log.error("Service " + clusterInstanceCreatedEvent.getServiceName() +
                                   " not found, no cluster instance added to ClusterMonitor " +
                                   clusterInstanceCreatedEvent.getClusterId());
                       }

                   } finally {
                       TopologyManager.releaseReadLockForCluster(clusterInstanceCreatedEvent.getServiceName(),
                               clusterInstanceCreatedEvent.getClusterId());
                   }

               } else {
                   log.error("No Cluster Monitor found for cluster id " +
                           clusterInstanceCreatedEvent.getClusterId());
               }
           }
       }

        );
    }

    /**
     * Terminate load balancer topology receiver thread.
     */

    public void terminate() {
        topologyEventReceiver.terminate();
        terminated = true;
    }


}
