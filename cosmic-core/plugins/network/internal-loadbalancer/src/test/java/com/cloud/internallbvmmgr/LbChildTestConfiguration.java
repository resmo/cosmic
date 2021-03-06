package com.cloud.internallbvmmgr;

import com.cloud.agent.AgentManager;
import com.cloud.db.repository.ZoneRepository;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.engine.orchestration.service.NetworkOrchestrationService;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.lb.dao.ApplicationLoadBalancerRuleDao;
import com.cloud.network.IpAddressManager;
import com.cloud.network.NetworkModel;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.PhysicalNetworkServiceProviderDao;
import com.cloud.network.dao.VirtualRouterProviderDao;
import com.cloud.network.lb.LoadBalancingRulesManager;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.resource.ResourceManager;
import com.cloud.server.ConfigurationServer;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.test.utils.SpringUtils;
import com.cloud.user.AccountManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.NicDao;

import java.io.IOException;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

@Configuration
@ComponentScan(basePackageClasses = {NetUtils.class},
        includeFilters = {@Filter(value = LbChildTestConfiguration.Library.class, type = FilterType.CUSTOM)},
        useDefaultFilters = false)
public class LbChildTestConfiguration {

    public static class Library implements TypeFilter {

        @Bean
        public AccountManager accountManager() {
            return Mockito.mock(AccountManager.class);
        }

        @Bean
        public VirtualMachineManager virtualMachineManager() {
            return Mockito.mock(VirtualMachineManager.class);
        }

        @Bean
        public DomainRouterDao domainRouterDao() {
            return Mockito.mock(DomainRouterDao.class);
        }

        @Bean
        public ConfigurationDao configurationDao() {
            return Mockito.mock(ConfigurationDao.class);
        }

        @Bean
        public VirtualRouterProviderDao virtualRouterProviderDao() {
            return Mockito.mock(VirtualRouterProviderDao.class);
        }

        @Bean
        public ApplicationLoadBalancerRuleDao applicationLoadBalancerRuleDao() {
            return Mockito.mock(ApplicationLoadBalancerRuleDao.class);
        }

        @Bean
        public NetworkModel networkModel() {
            return Mockito.mock(NetworkModel.class);
        }

        @Bean
        public LoadBalancingRulesManager loadBalancingRulesManager() {
            return Mockito.mock(LoadBalancingRulesManager.class);
        }

        @Bean
        public NicDao nicDao() {
            return Mockito.mock(NicDao.class);
        }

        @Bean
        public NetworkDao networkDao() {
            return Mockito.mock(NetworkDao.class);
        }

        @Bean
        public NetworkOrchestrationService networkManager() {
            return Mockito.mock(NetworkOrchestrationService.class);
        }

        @Bean
        public IpAddressManager ipAddressManager() {
            return Mockito.mock(IpAddressManager.class);
        }

        @Bean
        public ServiceOfferingDao serviceOfferingDao() {
            return Mockito.mock(ServiceOfferingDao.class);
        }

        @Bean
        public PhysicalNetworkServiceProviderDao physicalNetworkServiceProviderDao() {
            return Mockito.mock(PhysicalNetworkServiceProviderDao.class);
        }

        @Bean
        public NetworkOfferingDao networkOfferingDao() {
            return Mockito.mock(NetworkOfferingDao.class);
        }

        @Bean
        public VMTemplateDao vmTemplateDao() {
            return Mockito.mock(VMTemplateDao.class);
        }

        @Bean
        public ResourceManager resourceManager() {
            return Mockito.mock(ResourceManager.class);
        }

        @Bean
        public AgentManager agentManager() {
            return Mockito.mock(AgentManager.class);
        }

        @Bean
        public DataCenterDao dataCenterDao() {
            return Mockito.mock(DataCenterDao.class);
        }

        @Bean
        public ConfigurationServer configurationServer() {
            return Mockito.mock(ConfigurationServer.class);
        }

        @Bean
        public AccountDao accountDao() {
            return Mockito.mock(AccountDao.class);
        }

        @Bean
        public ZoneRepository zoneRepository() {
            return Mockito.mock(ZoneRepository.class);
        }

        @Override
        public boolean match(final MetadataReader mdr, final MetadataReaderFactory arg1) throws IOException {
            mdr.getClassMetadata().getClassName();
            final ComponentScan cs = LbChildTestConfiguration.class.getAnnotation(ComponentScan.class);
            return SpringUtils.includedInBasePackageClasses(mdr.getClassMetadata().getClassName(), cs);
        }
    }
}
