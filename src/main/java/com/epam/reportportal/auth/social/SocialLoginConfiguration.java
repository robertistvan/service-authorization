package com.epam.reportportal.auth.social;

import com.epam.reportportal.auth.OAuthSuccessHandler;
import com.epam.reportportal.auth.TokenServicesFacade;
import com.epam.reportportal.auth.converter.MongoConnectionConverters;
import com.epam.reportportal.auth.store.SocialConnectionsRepository;
import com.epam.ta.reportportal.database.entity.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.social.config.annotation.EnableSocial;
import org.springframework.social.connect.*;
import org.springframework.social.connect.support.ConnectionFactoryRegistry;
import org.springframework.social.connect.web.ProviderSignInController;
import org.springframework.social.connect.web.ProviderSignInInterceptor;
import org.springframework.social.connect.web.SignInAdapter;
import org.springframework.social.github.connect.GitHubConnectionFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.context.request.WebRequest;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;

/**
 * Created by andrei_varabyeu on 1/26/17.
 */
@Configuration
@EnableSocial
public class SocialLoginConfiguration {

	/*
 	* Internal token services facade
 	*/
	@Autowired
	private Provider<TokenServicesFacade> tokenServicesFacade;

	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private SocialDataReplicator socialDataReplicator;

	@Autowired
	private OAuthSuccessHandler oAuthSuccessHandler;

	@Autowired
	private MongoOperations mongoOperations;

	@Bean
	public ProviderSignInController providerSignInController() {
		ProviderSignInController providerSignInController = new ProviderSignInController(connectionFactoryLocator(),
				usersConnectionRepository(), rpSignInAdapter());
//		providerSignInController.setSignInUrl("/sso/new/login");
		providerSignInController.addSignInInterceptor(new ProviderSignInInterceptor<Object>() {
			@Override
			public void preSignIn(ConnectionFactory<Object> connectionFactory, MultiValueMap<String, String> parameters,
					WebRequest request) {

				connectionFactory.createConnection()
			}

			@Override
			public void postSignIn(Connection<Object> connection, WebRequest request) {

			}
		});
		return providerSignInController;
	}

	@Bean
	@Scope(value = "singleton", proxyMode = ScopedProxyMode.INTERFACES)
	public ConnectionFactoryLocator connectionFactoryLocator() {
		ConnectionFactoryRegistry registry = new ConnectionFactoryRegistry();
		registry.addConnectionFactory(new GitHubConnectionFactory("3e9d79ff81b114960d0b", "6cabe2fe61b491a5580c769e6e9bb85d16b2c077"));

		return registry;
	}

	@Bean
	@Scope(value = "singleton", proxyMode = ScopedProxyMode.INTERFACES)
	public UsersConnectionRepository usersConnectionRepository() {
		SocialConnectionsRepository repo = new SocialConnectionsRepository(mongoOperations, connectionFactoryLocator(),
				new MongoConnectionConverters(connectionFactoryLocator(), Encryptors.noOpText()));
		repo.setConnectionSignUp(new SignUpAdapter(socialDataReplicator));
		return repo;
	}

	@Bean
	public SignInAdapter rpSignInAdapter() {
		return (userId, connection, request) -> oAuthSuccessHandler
				.getRedirectUrl((HttpServletRequest) request.getNativeRequest(), new UsernamePasswordAuthenticationToken(userId, ""));
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

}
