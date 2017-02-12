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

import com.epam.reportportal.auth.OAuthSuccessHandler;
import com.epam.reportportal.auth.converter.MongoConnectionConverters;
import com.epam.reportportal.auth.store.SocialConnectionsRepository;
import com.epam.reportportal.auth.store.SocialMongoSessionRepository;
import com.epam.reportportal.auth.store.SocialProviderRepository;
import com.epam.ta.reportportal.database.entity.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.social.config.annotation.EnableSocial;
import org.springframework.social.connect.*;
import org.springframework.social.connect.web.ConnectSupport;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.security.SocialAuthenticationServiceLocator;
import org.springframework.social.security.SocialAuthenticationServiceRegistry;
import org.springframework.social.security.SocialAuthenticationToken;
import org.springframework.social.support.URIBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.servlet.view.RedirectView;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

/**
 * Social (via third-party providers) login/sign-up configuration
 *
 * @author Andrei Varabyeu
 */
@Configuration
@EnableSocial
public class SocialLoginConfiguration {

    @Autowired
    private SocialDataReplicator socialDataReplicator;

    @Autowired
    private OAuthSuccessHandler oAuthSuccessHandler;

    @Autowired
    private SocialMongoSessionRepository sessionStrategy;

    @Autowired
    private SocialProviderRepository socialProviderRepository;

    @Autowired
    private MongoOperations mongoOperations;

//    @Bean
//    public ProviderSignInController providerSignInController() {
//        ProviderSignInController providerSignInController = new EpamProviderSignInController(connectionFactoryLocator(),
//                usersConnectionRepository(), rpSignInAdapter());
//        providerSignInController.setSessionStrategy(sessionStrategy);
//        providerSignInController.setApplicationUrl("http://localhost:8080/uat");
//
//        return providerSignInController;
//    }

    @Bean
//    @Scope(value = "singleton", proxyMode = ScopedProxyMode.INTERFACES)
    public SocialProviderFactoryLocator connectionFactoryLocator() {
        return new SocialProviderFactoryLocator(socialProviderRepository);
    }

    @Bean
//    @Scope(value = "singleton", proxyMode = ScopedProxyMode.INTERFACES)
    public UsersConnectionRepository usersConnectionRepository() {
        SocialConnectionsRepository repo = new SocialConnectionsRepository(mongoOperations, connectionFactoryLocator(),
                new MongoConnectionConverters(connectionFactoryLocator(), Encryptors.noOpText()));
        repo.setConnectionSignUp(new SignUpAdapter(socialDataReplicator));
        return repo;
    }

    @Bean
    public SignInAdapter rpSignInAdapter() {
        return (userId, connection, request) -> oAuthSuccessHandler
                .handle((HttpServletRequest) request.getNativeRequest(),
                        new SocialAuthenticationToken(connection, null));
    }

    static class SignUpAdapter implements ConnectionSignUp {

        private final SocialDataReplicator socialDataReplicator;

        SignUpAdapter(SocialDataReplicator socialDataReplicator) {
            this.socialDataReplicator = socialDataReplicator;
        }

        @Override
        public String execute(Connection<?> connection) {
            User user = socialDataReplicator.replicateUser(connection);
            return user.getId();
        }
    }


    @RequestMapping("/sso/login")
    static class EpamProviderSignInController extends ProviderSignInController {

        @Inject
        public EpamProviderSignInController(ConnectionFactoryLocator connectionFactoryLocator,
                UsersConnectionRepository usersConnectionRepository, SignInAdapter signInAdapter) {
            super(connectionFactoryLocator, usersConnectionRepository, signInAdapter);
        }

        @RequestMapping(value="/{providerId}", method= {RequestMethod.POST,RequestMethod.GET})
        public RedirectView signIn(@PathVariable String providerId, NativeWebRequest request) {
            return super.signIn(providerId, request);
        }

    }

}
