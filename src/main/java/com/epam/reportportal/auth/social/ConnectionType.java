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

import com.epam.reportportal.auth.social.github.GithubConnectionFactory;
import com.google.common.base.Splitter;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.github.api.GitHub;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

/**
 * Connection types with configuration parameters
 *
 * @author Andrei Varabyeu
 */
public enum ConnectionType {
    GITHUB("github", new String[] { "clientId", "clientSecret" },
            configAttributes -> {
                String clientId = configAttributes.get("clientId");
                String clientSecret = configAttributes.get("clientSecret");

                List<String> allowedOrganizations = ofNullable(configAttributes.get("organizations"))
                        .map(it -> Splitter.on(",").omitEmptyStrings().splitToList(it))
                        .orElse(emptyList());
                return new GithubConnectionFactory(clientId, clientSecret, allowedOrganizations);

            }, GitHub.class);

    private final String id;
    private final String[] configAttributes;
    private final Function<Map<String, String>, ConnectionFactory<?>> providerSupplier;
    private final Class<?> apiType;

    ConnectionType(String id, String[] configAttributes,
            Function<Map<String, String>, ConnectionFactory<?>> providerSupplier, Class<?> apiType) {
        this.id = id;
        this.configAttributes = configAttributes;
        this.providerSupplier = providerSupplier;
        this.apiType = apiType;
    }

    public String getId() {
        return id;
    }

    public String[] getConfigAttributes() {
        return Arrays.copyOf(configAttributes, configAttributes.length);
    }

    public ConnectionFactory<?> buildServiceProvider(Map<String, String> configAttributes) {
        return providerSupplier.apply(configAttributes);
    }

    public static Optional<ConnectionType> byId(String providerId) {
        return Arrays.stream(values()).filter(connectionType -> connectionType.id.equals(providerId)).findAny();
    }

    public static <API> Optional<ConnectionType> byApiType(Class<API> clazz) {
        return Arrays.stream(values()).filter(c -> c.apiType.equals(clazz)).findAny();
    }
}
