package org.apache.stratos.autoscaler.applications.topic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.stratos.messaging.broker.publish.EventPublisher;
import org.apache.stratos.messaging.broker.publish.EventPublisherPool;
import org.apache.stratos.messaging.domain.applications.Application;
import org.apache.stratos.messaging.domain.applications.Applications;
import org.apache.stratos.messaging.domain.applications.ClusterDataHolder;
import org.apache.stratos.messaging.event.Event;
import org.apache.stratos.messaging.event.applications.*;
import org.apache.stratos.messaging.util.Util;

import java.util.Set;

/**
 * This will publish application related events to application status topic.
 */
public class ApplicationsEventPublisher {
    private static final Log log = LogFactory.getLog(ApplicationsEventPublisher.class);

    public static void sendCompleteApplicationsEvent (Applications completeApplications) {

        publishEvent(new CompleteApplicationsEvent(completeApplications));
    }

    public static void sendApplicationCreatedEvent (Application application) {

        publishEvent(new ApplicationCreatedEvent(application));
    }

    public static void sendGroupCreatedEvent(String appId, String groupId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Group created event for [application]: " + appId +
                    " [group]: " + groupId);
        }
        GroupResetEvent groupCreatedEvent =
                new GroupResetEvent(appId, groupId, instanceId);

        publishEvent(groupCreatedEvent);
    }

    public static void sendGroupActivatedEvent(String appId, String groupId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Group activated event for [application]: " + appId +
                    " [group]: " + groupId);
        }
        GroupActivatedEvent groupActivatedEvent =
                new GroupActivatedEvent(appId, groupId, instanceId);

        publishEvent(groupActivatedEvent);
    }

    public static void sendGroupInActivateEvent(String appId, String groupId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Group in-activate event for [application]: " + appId +
                    " [group]: " + groupId);
        }
        GroupInactivatedEvent groupInactivateEvent = new GroupInactivatedEvent(appId, groupId, instanceId);

        publishEvent(groupInactivateEvent);
    }

    public static void sendGroupTerminatingEvent(String appId, String groupId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Group terminating event for [application]: " + appId +
                    " [group]: " + groupId);
        }
        GroupTerminatingEvent groupInTerminatingEvent =
                new GroupTerminatingEvent(appId, groupId, instanceId);
        publishEvent(groupInTerminatingEvent);
    }

    public static void sendGroupTerminatedEvent(String appId, String groupId, String instanceId) {

        if (log.isInfoEnabled()) {
            log.info("Publishing Group terminated event for [application]: " + appId +
                    " [group]: " + groupId);
        }
        GroupTerminatedEvent groupInTerminatedEvent =
                new GroupTerminatedEvent(appId, groupId, instanceId);
        publishEvent(groupInTerminatedEvent);
    }

    public static void sendApplicationActivatedEvent(String appId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Application activated event for [application]: " + appId);
        }
        ApplicationActivatedEvent applicationActivatedEvent =
                new ApplicationActivatedEvent(appId, instanceId);

        publishEvent(applicationActivatedEvent);
    }

    public static void sendApplicationInactivatedEvent(String appId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Application In-activated event for [application]: " + appId);
        }
        ApplicationInstanceInactivatedEvent applicationInActivatedEvent =
                new ApplicationInstanceInactivatedEvent(appId, instanceId);
        publishEvent(applicationInActivatedEvent);

    }

    public static void sendApplicationTerminatingEvent(String appId, String instanceId) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Application terminating event for [application]: " + appId);
        }
        ApplicationInstanceTerminatingEvent applicationTerminatingEvent =
                new ApplicationInstanceTerminatingEvent(appId, instanceId);
        publishEvent(applicationTerminatingEvent);
    }

    public static void sendApplicationTerminatedEvent(String appId, Set<ClusterDataHolder> clusterData) {
        if (log.isInfoEnabled()) {
            log.info("Publishing Application terminated event for [application]: " + appId);
        }
        ApplicationInstanceTerminatedEvent applicationTerminatedEvent =
                new ApplicationInstanceTerminatedEvent(appId, clusterData);
        publishEvent(applicationTerminatedEvent);
    }

    public static void publishEvent(Event event) {
        //publishing events to application status topic
        String applicationTopic = Util.getMessageTopicName(event);
        EventPublisher eventPublisher = EventPublisherPool.getPublisher(applicationTopic);
        eventPublisher.publish(event);
    }

}
