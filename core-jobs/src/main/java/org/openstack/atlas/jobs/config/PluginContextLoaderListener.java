package org.openstack.atlas.jobs.config;

import org.openstack.atlas.common.config.Configuration;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.ContextLoaderListener;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PluginContextLoaderListener extends ContextLoaderListener {
    @Override
    protected void customizeContext(ServletContext servletContext,
                                    ConfigurableWebApplicationContext wac) {
        System.out.println("Init Plugin");
        String locationPrefix = "classpath:";
        String locationSuffix = "-spring-context-public.xml";

        List<String> configLocations = new ArrayList<String>();
        configLocations.addAll(Arrays.asList(wac.getConfigLocations()));
        configLocations.add("classpath:core-persistence-spring-context-public.xml");

        List<String> pluginsTurnedOn = getPluginsFromConfiguration();
        for (String pluginName : pluginsTurnedOn) {
            String location = locationPrefix + pluginName + locationSuffix;
            configLocations.add(location);
        }

        wac.setConfigLocations(configLocations.toArray(new String[configLocations.size()]));
    }

    private List<String> getPluginsFromConfiguration() {
        Configuration configuration = new RestApiConfiguration();
        String adapter = configuration.getString(PublicApiServiceConfigurationKeys.adapter);
        String extensions = configuration.getString(PublicApiServiceConfigurationKeys.extensions);

        List<String> pluginsTurnedOn = new ArrayList<String>();

        if (adapter != null) {
            pluginsTurnedOn.add(adapter + "-adapter");
        }

        if (extensions != null) {
            pluginsTurnedOn.add(extensions + "-persistence");
        }

        return pluginsTurnedOn;
    }
}
