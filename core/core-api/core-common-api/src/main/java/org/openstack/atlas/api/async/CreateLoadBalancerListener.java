package org.openstack.atlas.api.async;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openstack.atlas.api.atom.EntryHelper;
import org.openstack.atlas.api.helper.NodesHelper;
import org.openstack.atlas.service.domain.entity.*;
import org.openstack.atlas.service.domain.event.UsageEvent;
import org.openstack.atlas.service.domain.event.entity.EventType;
import org.openstack.atlas.service.domain.exception.EntityNotFoundException;
import org.openstack.atlas.service.domain.service.LoadBalancerService;
import org.openstack.atlas.service.domain.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.jms.Message;

import static org.openstack.atlas.api.atom.EntryHelper.*;
import static org.openstack.atlas.service.domain.entity.LoadBalancerStatus.ACTIVE;
import static org.openstack.atlas.service.domain.entity.LoadBalancerStatus.ERROR;
import static org.openstack.atlas.service.domain.entity.NodeStatus.OFFLINE;
import static org.openstack.atlas.service.domain.entity.NodeStatus.ONLINE;
import static org.openstack.atlas.service.domain.event.UsageEvent.SSL_ON;
import static org.openstack.atlas.service.domain.event.entity.CategoryType.CREATE;
import static org.openstack.atlas.service.domain.event.entity.CategoryType.UPDATE;
import static org.openstack.atlas.service.domain.event.entity.EventSeverity.CRITICAL;
import static org.openstack.atlas.service.domain.event.entity.EventSeverity.INFO;
import static org.openstack.atlas.service.domain.event.entity.EventType.*;
import static org.openstack.atlas.service.domain.service.helper.AlertType.DATABASE_FAILURE;
import static org.openstack.atlas.service.domain.service.helper.AlertType.LBDEVICE_FAILURE;

@Component
public class CreateLoadBalancerListener extends BaseListener {

    private final Log LOG = LogFactory.getLog(CreateLoadBalancerListener.class);
    @Autowired
    private LoadBalancerService loadBalancerService;
    @Autowired
    private NotificationService notificationService;

    @Override
    public void doOnMessage(final Message message) throws Exception {
        LOG.debug("Entering " + getClass());
        LOG.debug(message);

        LoadBalancer queueLb = getLoadbalancerFromMessage(message);
        LoadBalancer dbLoadBalancer;

        try {
            dbLoadBalancer = loadBalancerService.get(queueLb.getId(), queueLb.getAccountId());
        } catch (EntityNotFoundException enfe) {
            String alertDescription = String.format("Load balancer '%d' not found in database.", queueLb.getId());
            LOG.error(alertDescription, enfe);
            notificationService.saveAlert(queueLb.getAccountId(), queueLb.getId(), enfe, DATABASE_FAILURE.name(), alertDescription);
            sendErrorToEventResource(queueLb);
            return;
        }

        try {
            LOG.debug("Creating load balancer in LB Device...");
            reverseProxyLoadBalancerService.createLoadBalancer(dbLoadBalancer);
            LOG.debug("Successfully created a load balancer in LB Device.");
        } catch (Exception e) {
            dbLoadBalancer.setStatus(ERROR);
            NodesHelper.setNodesToStatus(dbLoadBalancer, OFFLINE);
            loadBalancerService.update(dbLoadBalancer);
            String alertDescription = String.format("An error occurred while creating loadbalancer '%d' in LB Device.", dbLoadBalancer.getId());
            LOG.error(alertDescription, e);
            notificationService.saveAlert(dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), e, LBDEVICE_FAILURE.name(), alertDescription);
            sendErrorToEventResource(queueLb);
            return;
        }

        // Update load balancer in DB
        dbLoadBalancer.setStatus(ACTIVE);
        NodesHelper.setNodesToStatus(dbLoadBalancer, ONLINE);
        dbLoadBalancer = loadBalancerService.update(dbLoadBalancer);

        addAtomEntryForLoadBalancer(queueLb, dbLoadBalancer);
        addAtomEntriesForNodes(queueLb, dbLoadBalancer);
        addAtomEntriesForVips(queueLb, dbLoadBalancer);
        addAtomEntryForHealthMonitor(queueLb, dbLoadBalancer);
        addAtomEntryForConnectionThrottle(queueLb, dbLoadBalancer);

        // Notify usage processor
        notifyUsageProcessor(message, dbLoadBalancer, UsageEvent.CREATE_LOADBALANCER);
        if (dbLoadBalancer.isUsingSsl()) notifyUsageProcessor(message, dbLoadBalancer, SSL_ON);

        LOG.info(String.format("Created load balancer '%d' successfully.", dbLoadBalancer.getId()));
    }

    private void addAtomEntryForConnectionThrottle(LoadBalancer queueLb, LoadBalancer dbLoadBalancer) {
        if (dbLoadBalancer.getConnectionThrottle() != null) {
            notificationService.saveConnectionLimitEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), dbLoadBalancer.getConnectionThrottle().getId(), UPDATE_THROTTLE_TITLE, EntryHelper.createConnectionThrottleSummary(dbLoadBalancer), UPDATE_CONNECTION_THROTTLE, UPDATE, INFO);
        }
    }

    private void addAtomEntryForHealthMonitor(LoadBalancer queueLb, LoadBalancer dbLoadBalancer) {
        if (dbLoadBalancer.getHealthMonitor() != null) {
            notificationService.saveHealthMonitorEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), dbLoadBalancer.getHealthMonitor().getId(), UPDATE_MONITOR_TITLE, EntryHelper.createHealthMonitorSummary(dbLoadBalancer), UPDATE_HEALTH_MONITOR, UPDATE, INFO);
        }
    }

    private void addAtomEntriesForVips(LoadBalancer queueLb, LoadBalancer dbLoadBalancer) {
        for (LoadBalancerJoinVip loadBalancerJoinVip : dbLoadBalancer.getLoadBalancerJoinVipSet()) {
            VirtualIp vip = loadBalancerJoinVip.getVirtualIp();
            notificationService.saveVirtualIpEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), vip.getId(), CREATE_VIP_TITLE, EntryHelper.createVirtualIpSummary(vip), EventType.CREATE_VIRTUAL_IP, CREATE, INFO);
        }

        for (LoadBalancerJoinVip6 loadBalancerJoinVip6 : dbLoadBalancer.getLoadBalancerJoinVip6Set()) {
            VirtualIpv6 vip = loadBalancerJoinVip6.getVirtualIp();
            notificationService.saveVirtualIpEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), vip.getId(), CREATE_VIP_TITLE, EntryHelper.createVirtualIpSummary(vip), EventType.CREATE_VIRTUAL_IP, CREATE, INFO);
        }
    }

    private void addAtomEntriesForNodes(LoadBalancer queueLb, LoadBalancer dbLoadBalancer) {
        for (Node node : queueLb.getNodes()) {
            notificationService.saveNodeEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(),
                    node.getId(), CREATE_NODE_TITLE, EntryHelper.createNodeSummary(node), CREATE_NODE, CREATE, INFO);
        }
    }

    private void addAtomEntryForLoadBalancer(LoadBalancer queueLb, LoadBalancer dbLoadBalancer) {
        String atomTitle = "Load Balancer Successfully Created";
        String atomSummary = createAtomSummary(dbLoadBalancer).toString();
        notificationService.saveLoadBalancerEvent(queueLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), atomTitle, atomSummary, CREATE_LOADBALANCER, CREATE, INFO);
    }

    private void sendErrorToEventResource(LoadBalancer lb) {
        String title = "Error Creating Load Balancer";
        String desc = "Could not create a load balancer at this time";
        notificationService.saveLoadBalancerEvent(lb.getUserName(), lb.getAccountId(), lb.getId(), title, desc, CREATE_LOADBALANCER, CREATE, CRITICAL);
    }

    private StringBuffer createAtomSummary(LoadBalancer lb) {
        StringBuffer atomSummary = new StringBuffer();
        atomSummary.append("Load balancer successfully created with ");
        atomSummary.append("name: '").append(lb.getName()).append("', ");
        atomSummary.append("algorithm: '").append(lb.getAlgorithm()).append("', ");
        atomSummary.append("protocol: '").append(lb.getProtocol()).append("', ");
        atomSummary.append("port: '").append(lb.getPort()).append("'");
        return atomSummary;
    }
}
