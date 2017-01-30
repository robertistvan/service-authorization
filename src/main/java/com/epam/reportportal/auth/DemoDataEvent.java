package com.epam.reportportal.auth;

import com.epam.reportportal.auth.store.SocialProviderRepository;
import com.epam.reportportal.auth.store.entity.SocialProvider;
import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Created by andrei_varabyeu on 12/26/16.
 */
@Component
@Deprecated
//TODO do not forget to remove!
public class DemoDataEvent {

    @Autowired
    private SocialProviderRepository socialProviderRepository;

    @EventListener(ContextRefreshedEvent.class)
    public void onStart() {
        SocialProvider socialProvider = new SocialProvider();
        socialProvider.setId("github");
        socialProvider.setConfiguration(ImmutableMap.<String, String>builder()
                .put("clientId", "3e9d79ff81b114960d0b")
                .put("clientSecret", "6cabe2fe61b491a5580c769e6e9bb85d16b2c077").build());
        socialProviderRepository.save(socialProvider);

    }
}
