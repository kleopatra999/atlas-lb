package org.openstack.atlas.rax.api.validation.validator.builder;

import org.openstack.atlas.api.validation.validator.builder.LoadBalancerValidatorBuilder;
import org.openstack.atlas.datamodel.Algorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.openstack.atlas.api.validation.context.HttpRequestType.POST;

@Primary
@Component
@Scope("request")
public class RaxLoadBalancerValidatorBuilder extends LoadBalancerValidatorBuilder {

    @Autowired
    public RaxLoadBalancerValidatorBuilder(Algorithm algorithm) {
        super(algorithm);
        // POST EXPECTATIONS
        result(validationTarget().getName()).must().not().exist().forContext(POST).withMessage("Must NOT provide a name for the load balancer. I guess your stuck! :)");
    }
}
