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
package com.epam.reportportal.auth.social.github;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.social.github.api.GitHubUserProfile;
import org.springframework.social.github.api.impl.UserTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Extension of Spring's {@link UserTemplate} adds emails and organizations to the resulting profile
 *
 * @author Andrei Varabyeu
 */
public class GitHubUserTemplate extends UserTemplate {
    private final RestTemplate restTemplate;

    GitHubUserTemplate(RestTemplate restTemplate, boolean isAuthorizedForUser) {
        super(restTemplate, isAuthorizedForUser);
        this.restTemplate = restTemplate;
    }

    @Override
    public GitHubUserProfile getUserProfile() {
        final GitHubUserProfile userProfile = super.getUserProfile();
        final List<EmailResource> emails = this.restTemplate.exchange(buildUri("user/emails"),
                HttpMethod.GET, null,
                new ParameterizedTypeReference<List<EmailResource>>() {
                }).getBody();
        String email = emails.stream()
                .filter(EmailResource::isPrimary)
                .filter(EmailResource::isVerified)
                .map(EmailResource::getEmail)
                .findAny().orElse(null);

        return new GitHubUserProfile(userProfile.getId(), userProfile.getUsername(), userProfile.getName(),
                userProfile.getLocation(), userProfile.getCompany(), userProfile.getBlog(), email,
                userProfile.getProfileImageUrl(), userProfile.getCreatedDate());
    }

    List<String> getUserOrganizations() {
        return this.restTemplate.exchange(buildUri("user/organizations"), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<OrganizationResource>>() {
                }).getBody()
                .stream().map(org -> org.login).collect(Collectors.toList());
    }

}
