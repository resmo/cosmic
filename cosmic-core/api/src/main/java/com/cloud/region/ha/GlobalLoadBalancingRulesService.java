package com.cloud.region.ha;

import com.cloud.api.command.user.region.ha.gslb.AssignToGlobalLoadBalancerRuleCmd;
import com.cloud.api.command.user.region.ha.gslb.CreateGlobalLoadBalancerRuleCmd;
import com.cloud.api.command.user.region.ha.gslb.DeleteGlobalLoadBalancerRuleCmd;
import com.cloud.api.command.user.region.ha.gslb.ListGlobalLoadBalancerRuleCmd;
import com.cloud.api.command.user.region.ha.gslb.RemoveFromGlobalLoadBalancerRuleCmd;
import com.cloud.api.command.user.region.ha.gslb.UpdateGlobalLoadBalancerRuleCmd;
import com.cloud.network.rules.LoadBalancer;

import java.util.List;

public interface GlobalLoadBalancingRulesService {

    /*
     * methods for managing life cycle of global load balancing rules
     */
    GlobalLoadBalancerRule createGlobalLoadBalancerRule(CreateGlobalLoadBalancerRuleCmd createGslbCmd);

    boolean deleteGlobalLoadBalancerRule(DeleteGlobalLoadBalancerRuleCmd deleteGslbCmd);

    GlobalLoadBalancerRule updateGlobalLoadBalancerRule(UpdateGlobalLoadBalancerRuleCmd updateGslbCmd);

    boolean revokeAllGslbRulesForAccount(com.cloud.user.Account caller, long accountId) throws com.cloud.exception.ResourceUnavailableException;

    /*
     * methods for managing sites participating in global load balancing
     */
    boolean assignToGlobalLoadBalancerRule(AssignToGlobalLoadBalancerRuleCmd assignToGslbCmd);

    boolean removeFromGlobalLoadBalancerRule(RemoveFromGlobalLoadBalancerRuleCmd removeFromGslbCmd);

    GlobalLoadBalancerRule findById(long gslbRuleId);

    List<GlobalLoadBalancerRule> listGlobalLoadBalancerRule(ListGlobalLoadBalancerRuleCmd listGslbCmd);

    List<LoadBalancer> listSiteLoadBalancers(long gslbRuleId);
}
