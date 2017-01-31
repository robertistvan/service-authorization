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
package com.epam.reportportal.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.stereotype.Component;

/**
 * Shows list of supported authentication providers
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@Component
public class AuthProvidersInfoContributor implements InfoContributor {

    private final ConnectionFactoryLocator socialFactoryLocator;

    @Autowired
    public AuthProvidersInfoContributor(ConnectionFactoryLocator socialFactoryLocator) {
        this.socialFactoryLocator = socialFactoryLocator;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("auth_extensions", socialFactoryLocator.registeredProviderIds());
    }
}
