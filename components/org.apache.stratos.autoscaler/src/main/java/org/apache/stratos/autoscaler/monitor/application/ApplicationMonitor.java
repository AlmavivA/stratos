/*
 *     Licensed to the Apache Software Foundation (ASF) under one
 *     or more contributor license agreements.  See the NOTICE file
 *     distributed with this work for additional information
 *     regarding copyright ownership.  The ASF licenses this file
 *     to you under the Apache License, Version 2.0 (the
 *     "License"); you may not use this file except in compliance
 *     with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing,
 *     software distributed under the License is distributed on an
 *     "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *     KIND, either express or implied.  See the License for the
 *     specific language governing permissions and limitations
 *     under the License.
 */
package org.apache.stratos.autoscaler.monitor.application;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.autoscaler.grouping.DependencyBuilder;
import org.apache.stratos.autoscaler.monitor.AbstractClusterMonitor;
import org.apache.stratos.autoscaler.monitor.Monitor;
import org.apache.stratos.autoscaler.monitor.group.GroupMonitor;
import org.apache.stratos.autoscaler.status.checker.StatusChecker;
import org.apache.stratos.messaging.domain.topology.*;
import org.apache.stratos.messaging.domain.topology.util.GroupStatus;
import org.apache.stratos.messaging.event.Event;
import org.apache.stratos.messaging.message.receiver.topology.TopologyManager;

import java.util.*;

/**
 * ApplicationMonitor is to control the child monitors
 */
public class ApplicationMonitor extends Monitor {
    private static final Log log = LogFactory.getLog(ApplicationMonitor.class);

    public ApplicationMonitor(ParentBehavior application) {
        super(application);
        //keep
    }

    @Override
    public void update(Observable observable, Object arg) {
        if(arg instanceof Event) {

        }

    }

    @Override
    protected void onEvent(Event event) {

    }

    @Override
    protected void startDependency() {
        //Need to get the order every time as group/cluster might already been started
        //TODO breadth first search in a tree and find the parallel one
        //TODO build up the tree with ordered manner

        preOrderTraverse = DependencyBuilder.getStartupOrder(component);

        //start the first dependency
        if(!preOrderTraverse.isEmpty()) {
            String dependency = preOrderTraverse.poll();
            if (dependency.contains("group")) {
                startGroupMonitor(this, dependency.substring(6), component);
            } else if (dependency.contains("cartridge")) {
                ClusterDataHolder clusterDataHolder = component.getClusterData(dependency.substring(10));
                String clusterId = clusterDataHolder.getClusterId();
                String serviceName = clusterDataHolder.getServiceType();
                Cluster cluster = null;
                TopologyManager.acquireReadLock();
                cluster = TopologyManager.getTopology().getService(serviceName).getCluster(clusterId);
                TopologyManager.releaseReadLock();
                if (cluster != null) {
                    startClusterMonitor(cluster);
                } else {
                    //TODO throw exception since Topology is inconsistent
                }

            }
        } else {
            //all the groups/clusters have been started and waiting for activation
            log.info("All the groups/clusters of the [group]: " + this.id + " have been started.");
        }


    }


    /**
     * Find the group monitor by traversing recursively in the hierarchical monitors.
     * @param id the unique alias of the Group
     * @return the found GroupMonitor
     */
    public Monitor findGroupMonitorWithId(String id) {
        return findGroupMonitor(id, groupMonitors.values());

    }

    private Monitor findGroupMonitor(String id, Collection<GroupMonitor> monitors) {
        for (GroupMonitor monitor : monitors) {
            // check if alias is equal, if so, return
            if (monitor.equals(id)) {
                return monitor;
            } else {
                // check if this Group has nested sub Groups. If so, traverse them as well
                if (monitor.getGroupMonitors() != null) {
                    return findGroupMonitor(id, monitor.getGroupMonitors().values());
                }
            }
        }
        return null;
    }


    public Monitor findParentMonitorOfGroup(String groupId) {
      return findParentMonitorForGroup(groupId, this);
    }

    private Monitor findParentMonitorForGroup(String groupId, Monitor monitor) {
        //if this monitor has the group, return it as the parent
        if(monitor.getGroupMonitors().containsKey(groupId)) {
            return monitor;
        } else {
            if(monitor.getGroupMonitors() != null) {
                //check whether the children has the group and find its parent
                for(GroupMonitor groupMonitor : monitor.getGroupMonitors().values()) {
                    return findParentMonitorForGroup(groupId, groupMonitor);

                }
            }
        }
        return null;

    }

    public Monitor findParentMonitorOfCluster(String clusterId) {
        return findParentMonitorForCluster(clusterId, this);
    }

    private Monitor findParentMonitorForCluster(String clusterId, Monitor monitor) {
        //if this monitor has the group, return it as the parent
        if(monitor.getAbstractClusterMonitors().containsKey(clusterId)) {
            return monitor;
        } else {
            if(monitor.getGroupMonitors() != null) {
                //check whether the children has the group and find its parent
                for(GroupMonitor groupMonitor : monitor.getGroupMonitors().values()) {
                    return findParentMonitorForCluster(clusterId, groupMonitor);

                }
            }
        }
        return null;

    }

    @Override
    public void monitor() {
        startDependency();

        //evaluate dependency
    }

    public Queue<String> getPreOrderTraverse() {
        return preOrderTraverse;
    }

    public void setPreOrderTraverse(Queue<String> preOrderTraverse) {
        this.preOrderTraverse = preOrderTraverse;
    }
}
