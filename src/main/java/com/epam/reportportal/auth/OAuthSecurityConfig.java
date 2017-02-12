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

import com.epam.reportportal.auth.social.SocialDataReplicator;
import com.epam.reportportal.auth.social.SocialLoginConfiguration;
import com.epam.reportportal.auth.social.SocialProviderFactoryLocator;
import com.epam.reportportal.auth.social.github.GitHubTokenServices;
import com.epam.reportportal.auth.social.github.GitHubUserReplicator;
import com.epam.reportportal.auth.store.SocialMongoSessionRepository;
import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.SecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.OAuth2ClientContext;
import org.springframework.security.oauth2.client.filter.OAuth2ClientAuthenticationProcessingFilter;
import org.springframework.security.oauth2.client.filter.OAuth2ClientContextFilter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.social.connect.UsersConnectionRepository;
import org.springframework.social.security.AuthenticationNameUserIdSource;
import org.springframework.social.security.SocialAuthenticationFailureHandler;
import org.springframework.social.security.SocialAuthenticationFilter;
import org.springframework.social.security.SocialAuthenticationProvider;
import org.springframework.social.security.SocialUser;
import org.springframework.social.security.SocialUserDetails;
import org.springframework.social.security.SocialUserDetailsService;
import org.springframework.web.filter.CompositeFilter;
import org.springframework.web.util.UriComponentsBuilder;

import javax.inject.Provider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Main Security Extension Point.
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@Configuration
@EnableOAuth2Client
@Order(6)
@Conditional(OAuthSecurityConfig.HasExtensionsCondition.class)
@Import(SocialLoginConfiguration.class)
public class OAuthSecurityConfig extends WebSecurityConfigurerAdapter {

	protected static final String SSO_LOGIN_PATH = "/sso/login";
	static final String GITHUB = "github";

	@Autowired
	private OAuth2ClientContext oauth2ClientContext;

	@Autowired
	private GitHubUserReplicator githubReplicator;

	@Autowired
	protected OAuthSuccessHandler authSuccessHandler;

	@Autowired
	protected DynamicAuthProvider dynamicAuthProvider;

	@Autowired
	private MongoOperations mongoOperations;

	@Autowired
	private SocialMongoSessionRepository sessionStrategy;


	@Autowired
	private UsersConnectionRepository usersConnectionRepository;

	@Autowired
	private Provider<SocialProviderFactoryLocator> socialProviderFactoryLocator;

	@Autowired
	private SocialDataReplicator socialDataReplicator;


	/**
	 * Extension point. Other Implementations can add their own OAuth processing filters
	 *
	 * @param oauth2ClientContext OAuth Client context
	 * @return List of additional OAuth processing filters
	 * @throws Exception in case of error
	 */
	protected List<OAuth2ClientAuthenticationProcessingFilter> getAdditionalFilters(OAuth2ClientContext oauth2ClientContext)
			throws Exception {
		return Collections.emptyList();
	}

	@Override
	protected final void configure(HttpSecurity http) throws Exception {
		//@formatter:off
			 http
				.antMatcher("/**")
					 .authorizeRequests()
				.antMatchers(SSO_LOGIN_PATH + "/**", "/webjars/**", "/index.html", "/epam/**", "/info", "/health", "/signin/**", "/connect/**", "/**")
					 .permitAll()
				.anyRequest()
					 .authenticated()
 	            .and().csrf().disable()
				.sessionManagement()
				    .sessionCreationPolicy(SessionCreationPolicy.STATELESS);

		CompositeFilter authCompositeFilter = new CompositeFilter();
		List<OAuth2ClientAuthenticationProcessingFilter> additionalFilters = ImmutableList.<OAuth2ClientAuthenticationProcessingFilter>builder()
						.addAll(getDefaultFilters(oauth2ClientContext))
						.addAll(getAdditionalFilters(oauth2ClientContext)).build();

		/* make sure filters have correct exception handler */
//		additionalFilters.forEach(filter -> filter.setAuthenticationFailureHandler(OAUTH_ERROR_HANDLER));
//		authCompositeFilter.setFilters(additionalFilters);

		//install additional OAuth Authentication filters


		http.apply(new SecurityConfigurerAdapter<DefaultSecurityFilterChain, HttpSecurity>(){
			@Override
			public void configure(HttpSecurity builder) throws Exception {
				SocialAuthenticationFilter filter = new SocialAuthenticationFilter(
						http.getSharedObject(AuthenticationManager.class),
						new AuthenticationNameUserIdSource(),
						usersConnectionRepository,
						socialProviderFactoryLocator.get());
//				filter.setSessionStrategy(sessionStrategy);
//				filter.setAllowSessionCreation(false);
//				filter.setSessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy());
				filter.setFilterProcessesUrl(SSO_LOGIN_PATH);
				filter.setAuthenticationSuccessHandler(authSuccessHandler);
//				filter.setAuthenticationFailureHandler(new SocialAuthenticationFailureHandler(OAUTH_ERROR_HANDLER));


				http.authenticationProvider(
						new SocialAuthenticationProvider(usersConnectionRepository, new SocialUserDetailsService() {
							@Override
							public SocialUserDetails loadUserByUserId(String userId) throws UsernameNotFoundException {
								return new SocialUser(userId, "", Collections.singletonList(new SimpleGrantedAuthority("USER")));
							}
						}))
						.addFilterBefore(postProcess(filter), AbstractPreAuthenticatedProcessingFilter.class);
				http.addFilterAfter(filter, BasicAuthenticationFilter.class);
			}
		});

		//@formatter:on
	}

	@Bean
	FilterRegistrationBean oauth2ClientFilterRegistration(OAuth2ClientContextFilter filter) {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(filter);
		registration.setOrder(-100);
		return registration;
	}


	private List<OAuth2ClientAuthenticationProcessingFilter> getDefaultFilters(OAuth2ClientContext oauth2ClientContext) {
		OAuth2ClientAuthenticationProcessingFilter githubFilter = new OAuth2ClientAuthenticationProcessingFilter(
				SSO_LOGIN_PATH + "/github");

		githubFilter.setRestTemplate(dynamicAuthProvider.getRestTemplate(GITHUB, oauth2ClientContext));
		GitHubTokenServices tokenServices = new GitHubTokenServices(githubReplicator, dynamicAuthProvider.getLoginDetailsSupplier(GITHUB));
		githubFilter.setTokenServices(tokenServices);
		githubFilter.setAuthenticationSuccessHandler(authSuccessHandler);

		return Collections.singletonList(githubFilter);
	}

	/**
	 * Condition. Load this config is there are no subclasses in the application context
	 */
	protected static class HasExtensionsCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String[] enablers = context.getBeanFactory().getBeanNamesForAnnotation(EnableOAuth2Client.class);
			boolean extensions = Arrays.stream(enablers)
					.filter(name -> !context.getBeanFactory().getType(name).equals(OAuthSecurityConfig.class))
					.filter(name -> context.getBeanFactory().isTypeMatch(name, OAuthSecurityConfig.class)).findAny().isPresent();
			if (extensions) {
				return ConditionOutcome.noMatch("found @EnableOAuth2Client on a OAuthSecurityConfig subclass");
			} else {
				return ConditionOutcome.match("found no @EnableOAuth2Client on a OAuthSecurityConfig subsclass");
			}

		}
	}

	private static final AuthenticationFailureHandler OAUTH_ERROR_HANDLER = (request, response, exception) -> {
		if (!response.isCommitted()){
			response.sendRedirect(UriComponentsBuilder.fromHttpRequest(new ServletServerHttpRequest(request)).replacePath("ui/#login")
					.replaceQuery("errorAuth=" + exception.getMessage()).build().toUriString());
		}

	};

}
