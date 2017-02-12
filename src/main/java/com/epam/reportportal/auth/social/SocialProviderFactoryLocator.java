/*
 * Copyright 2016 EPAM Systems
 *
 *
 * This file is part of EPAM Report Portal.
 * https://github.com/reportportal/service-authorization
 *
 * Report Portal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Report Portal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Report Portal.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.epam.reportportal.auth.social;

import com.epam.reportportal.auth.store.SocialProviderRepository;
import com.epam.reportportal.auth.store.entity.SocialProvider;
import com.google.common.base.Strings;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.support.OAuth2ConnectionFactory;
import org.springframework.social.security.SocialAuthenticationServiceLocator;
import org.springframework.social.security.provider.OAuth2AuthenticationService;
import org.springframework.social.security.provider.SocialAuthenticationService;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Social locator based on MongoDB data. Allows to configure providers dynamically
 *
 * @author Andrei Varabyeu
 * @since 3.0
 */
//TODO cache?
public class SocialProviderFactoryLocator implements SocialAuthenticationServiceLocator {

    private final SocialProviderRepository socialProviderRepository;

    @Inject
    public SocialProviderFactoryLocator(SocialProviderRepository socialProviderRepository) {
        this.socialProviderRepository = socialProviderRepository;
    }

    @Override
    public ConnectionFactory<?> getConnectionFactory(String providerId) {
        final SocialProvider socialProviderDetails = socialProviderRepository.findOne(providerId);
        final ConnectionType connectionType = ConnectionType.valueOf(providerId.toUpperCase());
        return connectionType
                .buildServiceProvider(socialProviderDetails.getConfiguration());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <A> ConnectionFactory<A> getConnectionFactory(Class<A> apiType) {
        //TODO check
        ConnectionType connectionType = ConnectionType.byApiType(apiType).orElse(null);
        return (ConnectionFactory<A>) connectionType
                .buildServiceProvider(socialProviderRepository.findOne(connectionType.getId()).getConfiguration());

    }

    @Override
    public Set<String> registeredProviderIds() {
        return socialProviderRepository.findAll().stream().map(SocialProvider::getId).collect(Collectors.toSet());
    }

    @Override
    public SocialAuthenticationService<?> getAuthenticationService(String providerId) {
        return new OAuth2AuthenticationService<Object>((OAuth2ConnectionFactory) getConnectionFactory(providerId)) {
            @Override
            protected String buildReturnToUrl(HttpServletRequest request) {

                final Map<String, List<String>> urlParams = getReturnToUrlParameters()
                        .stream()
                        .collect(Collectors
                                .toMap(p -> p, p -> {
                                    final String value = request.getParameter(p);
                                    if (Strings.isNullOrEmpty(value)) {
                                        return Collections.emptyList();
                                    }
                                    return Collections.singletonList(value);
                                }));
                return ServletUriComponentsBuilder
                        .fromHttpRequest(new ServletServerHttpRequest(request))
                        .queryParams(new LinkedMultiValueMap<>(urlParams))
                        .toUriString();
            }
        };
    }

    @Override
    public Set<String> registeredAuthenticationProviderIds() {
        return registeredProviderIds();
    }
}
