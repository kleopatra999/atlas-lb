package org.openstack.atlas.rax.api.resource;

import org.openstack.atlas.api.resource.LoadBalancerResource;
import org.openstack.atlas.api.response.ResponseFactory;
import org.openstack.atlas.api.validation.context.HttpRequestType;
import org.openstack.atlas.api.validation.result.ValidatorResult;
import org.openstack.atlas.core.api.v1.LoadBalancer;
import org.openstack.atlas.rax.domain.entity.RaxLoadBalancer;
import org.openstack.atlas.service.domain.operation.Operation;
import org.openstack.atlas.service.domain.pojo.MessageDataContainer;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import javax.ws.rs.core.Response;

@Primary
@Controller
@Scope("request")
public class RaxLoadBalancerResource extends LoadBalancerResource {

    @Override
    public Response get() {
        try {
            org.openstack.atlas.service.domain.entity.LoadBalancer loadBalancer = loadBalancerRepository.getByIdAndAccountId(id, accountId);
            LoadBalancer _loadBalancer = dozerMapper.map(loadBalancer, LoadBalancer.class);
            return Response.status(Response.Status.OK).entity(_loadBalancer).build();
        } catch (Exception e) {
            return ResponseFactory.getErrorResponse(e);
        }
    }


    @Override
    public Response update(LoadBalancer loadBalancer) {
        ValidatorResult result = validator.validate(loadBalancer, HttpRequestType.PUT);

        if (!result.passedValidation()) {
            return ResponseFactory.getValidationFaultResponse(result);
        }

        try {
            RaxLoadBalancer raxLoadBalancer = dozerMapper.map(loadBalancer, RaxLoadBalancer.class);
            raxLoadBalancer.setId(id);
            raxLoadBalancer.setAccountId(accountId);

            loadBalancerService.update(raxLoadBalancer);

            MessageDataContainer msg = new MessageDataContainer();
            msg.setLoadBalancer(raxLoadBalancer);

            asyncService.callAsyncLoadBalancingOperation(Operation.UPDATE_LOADBALANCER, msg);
            return Response.status(Response.Status.ACCEPTED).build();
        } catch (Exception e) {
            return ResponseFactory.getErrorResponse(e, null, null);
        }
    }
}
