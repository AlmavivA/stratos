/*
 * Licensed to the Apache Software Foundation (ASF) under one 
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
 * KIND, either express or implied.  See the License for the 
 * specific language governing permissions and limitations
 * under the License.
 */
//package org.apache.stratos.cloud.controller.functions;
//
//import org.apache.stratos.cloud.controller.context.CloudControllerContext;
//import org.apache.stratos.cloud.controller.domain.ClusterContext;
//import org.apache.stratos.cloud.controller.domain.ContainerClusterContext;
//import org.apache.stratos.cloud.controller.domain.KubernetesClusterContext;
//import org.apache.stratos.cloud.controller.domain.MemberContext;
//import org.apache.stratos.cloud.controller.util.CloudControllerUtil;
//import org.apache.stratos.common.constants.StratosConstants;
//import org.apache.stratos.kubernetes.client.model.Selector;
//import org.apache.stratos.kubernetes.client.model.Service;
//
//import com.google.common.base.Function;
//
///**
// * Is responsible for converting a {@link ContainerClusterContext} object to a Kubernetes
// * {@link Service} Object.
// */
//public class ContainerClusterContextToKubernetesService implements Function<ClusterContext, Service> {
//
//    @Override
//    public Service apply(ClusterContext clusterContext) {
//
//        String clusterId = clusterContext.getClusterId();
//        String kubernetesClusterId = CloudControllerUtil.getProperty(
//                clusterContext.getProperties(), StratosConstants.KUBERNETES_CLUSTER_ID);
//        KubernetesClusterContext kubClusterContext = CloudControllerContext.getInstance()
//                .getKubernetesClusterContext(kubernetesClusterId);
//
//
//
//        return service;
//    }
//}
