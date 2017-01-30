package com.epam.reportportal.auth.social.github;

import org.springframework.social.connect.UserProfile;
import org.springframework.social.connect.support.OAuth2ConnectionFactory;
import org.springframework.social.github.api.GitHub;
import org.springframework.social.github.api.UserOperations;
import org.springframework.social.github.api.impl.GitHubTemplate;
import org.springframework.social.github.connect.GitHubAdapter;
import org.springframework.social.github.connect.GitHubServiceProvider;

import java.util.List;

/**
 * @author Andrei Varabyeu
 */
public class GithubConnectionFactory extends OAuth2ConnectionFactory<GitHub> {

    public GithubConnectionFactory(String clientId, String clientSecret, List<String> allowedOrganizations) {
        super("github", new GithubExtendedServiceProvider(clientId, clientSecret), new ExtendedGithubAdapter(
                allowedOrganizations));
    }

    static class GithubExtendedServiceProvider extends GitHubServiceProvider {

        GithubExtendedServiceProvider(String clientId, String clientSecret) {
            super(clientId, clientSecret);
        }

        @Override
        public GitHub getApi(String accessToken) {
            return new GitHubTemplate(accessToken) {
                @Override
                public UserOperations userOperations() {
                    return new GitHubUserTemplate(getRestTemplate(), isAuthorized());
                }
            };
        }
    }

    static class ExtendedGithubAdapter extends GitHubAdapter {

        private final List<String> allowedOrganizations;

        ExtendedGithubAdapter(List<String> allowedOrganizations) {
            this.allowedOrganizations = allowedOrganizations;
        }

        @Override
        public UserProfile fetchUserProfile(GitHub github) {
            final UserProfile userProfile = super.fetchUserProfile(github);
            final GitHubUserTemplate extendedUserOperations = (GitHubUserTemplate) github.userOperations();
            if (!allowedOrganizations.isEmpty()) {
                boolean assignedToOrganization = extendedUserOperations.getUserOrganizations().stream()
                        .anyMatch(allowedOrganizations::contains);
                if (!assignedToOrganization) {
                    throw new GitHubTokenServices.InsufficientOrganizationException(
                            "User '" + userProfile.getUsername() + "' does not belong to allowed GitHUB organization");
                }
            }
            return userProfile;
        }
    }
}
