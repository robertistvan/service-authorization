package com.epam.reportportal.auth.social;

import com.epam.reportportal.auth.store.SocialProviderRepository;
import com.epam.reportportal.auth.store.entity.SocialProvider;
import com.epam.ta.reportportal.commons.Predicates;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.exception.ReportPortalException;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.social.security.SocialAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.epam.ta.reportportal.commons.Predicates.equalTo;
import static org.springframework.web.bind.annotation.RequestMethod.DELETE;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.springframework.web.bind.annotation.RequestMethod.PUT;

/**
 * Endpoint for oauth configs
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
public class SocialEndpoint {

    private final SocialDataReplicator replicator;
    private final SocialProviderRepository socialProviderRepository;

    @Autowired
    public SocialEndpoint(SocialDataReplicator replicator, SocialProviderRepository socialProviderRepository) {
        this.replicator = replicator;
        this.socialProviderRepository = socialProviderRepository;
    }

    @RequestMapping(value = { "/sso/me/synchronize" }, method = RequestMethod.POST)
    public OperationCompletionRS synchronize(OAuth2Authentication user) {
        Authentication userAuth = user.getUserAuthentication();
        BusinessRule.expect(userAuth, auth -> auth instanceof SocialAuthenticationToken)
                .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "Cannot synchronize GitHub User");
        this.replicator.synchronizeUser(((SocialAuthenticationToken) userAuth).getConnection());
        return new OperationCompletionRS("User info successfully synchronized");
    }

    /**
     * Updates oauth integration settings
     *
     * @param oauthDetails OAuth details resource update
     * @param providerId   ID of third-party OAuth provider
     * @return All defined OAuth integration settings
     */
    @RequestMapping(value = "/settings/social/{providerId}", method = { POST, PUT })
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Updates ThirdParty OAuth Server Settings")
    public Map<String, Map<String, String>> updateOAuthSettings(
            @PathVariable("providerId") String providerId,
            @RequestBody @Validated Map<String, String> oauthDetails) {

        //check we support it
        final Optional<ConnectionType> connectionType = ConnectionType.byId(providerId.toUpperCase());
        BusinessRule.expect(connectionType, Predicates.isPresent())
                .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "providerId");

        //validate config attributes
        Arrays.stream(connectionType.get().getConfigAttributes()).forEach(configAttribute -> {
            BusinessRule
                    .expect(Strings.isNullOrEmpty(oauthDetails.get(configAttribute)), Predicates.equalTo(false))
                    .verify(ErrorType.INCORRECT_REQUEST, "Attribute '" + configAttribute + "' is not present");

        });

        //build object and save (create or update) it
        SocialProvider socialProvider = new SocialProvider();
        socialProvider.setId(providerId);
        socialProvider.setConfiguration(oauthDetails);
        socialProviderRepository.save(socialProvider);
        return ImmutableMap.<String, Map<String, String>>builder().put(providerId, oauthDetails).build();
    }

    /**
     * Deletes oauth integration settings
     *
     * @param providerID settings ProfileID
     * @return All defined OAuth integration settings
     */
    @RequestMapping(value = "/settings/social/{providerID}", method = { DELETE })
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Deletes ThirdParty OAuth Server Settings")
    public OperationCompletionRS deleteOAuthSetting(
            @PathVariable("providerID") String providerID) {

        BusinessRule.expect(socialProviderRepository.exists(providerID), equalTo(true))
                .verify(ErrorType.OAUTH_INTEGRATION_NOT_FOUND, providerID);
        socialProviderRepository.delete(providerID);

        return new OperationCompletionRS("Auth settings '" + providerID + "' were successfully removed");
    }

    /**
     * Returns oauth integration settings
     *
     * @return All defined OAuth integration settings
     */
    @RequestMapping(value = "/settings/social/", method = { GET })
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Returns OAuth Server Settings", notes = "'default' profile is using till additional UI implementations")
    public Map<String, Map<String, String>> getOAuthSettings() {
        return socialProviderRepository.findAll().stream()
                .collect(Collectors.toMap(SocialProvider::getId, SocialProvider::getConfiguration));

    }

    /**
     * Returns oauth integration attributes
     *
     * @return All defined OAuth integration settings
     */
    @RequestMapping(value = "/settings/social/{providerId}/attributes", method = { GET })
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Returns OAuth Server Settings", notes = "'default' profile is using till additional UI implementations")
    public String[] getOAuthIntegrationAttributes(@PathVariable("providerId") String providerId) {
        //check we support it
        final Optional<ConnectionType> connectionType = ConnectionType.byId(providerId.toUpperCase());
        BusinessRule.expect(connectionType, Predicates.isPresent())
                .verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "providerId");
        return connectionType.get().getConfigAttributes();
    }

    /**
     * Returns oauth integration settings
     *
     * @param providerId ID of third-party OAuth provider
     * @return All defined OAuth integration settings
     */
    @RequestMapping(value = "/settings/social/{providerId}", method = { GET })
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @ApiOperation(value = "Returns OAuth Server Settings", notes = "'default' profile is using till additional UI implementations")
    public Map<String, String> getOAuthSettings(
            @PathVariable("providerId") String providerId) {
        return Optional.ofNullable(socialProviderRepository.findOne(providerId))
                .map(SocialProvider::getConfiguration)
                .orElseThrow(() -> new ReportPortalException(ErrorType.OAUTH_INTEGRATION_NOT_FOUND, providerId));

    }
}
