package com.epam.reportportal.auth.converter;

import com.epam.reportportal.auth.store.entity.SocialConnection;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionData;
import org.springframework.social.connect.ConnectionFactory;
import org.springframework.social.connect.ConnectionFactoryLocator;

import java.time.Instant;
import java.util.function.Function;

/**
 * Created by andrei_varabyeu on 1/26/17.
 */
public class MongoConnectionConverters {

	private final ConnectionFactoryLocator connectionFactoryLocator;
	private final TextEncryptor textEncryptor;

	public MongoConnectionConverters(final ConnectionFactoryLocator connectionFactoryLocator, final TextEncryptor textEncryptor) {
		this.connectionFactoryLocator = connectionFactoryLocator;
		this.textEncryptor = textEncryptor;
	}

	public Function<SocialConnection, String> toUserId() {
		return input -> {
			if (input == null) {
				return null;
			}
			return input.getUserId();
		};
	}

	public Function<SocialConnection, Connection<?>> toConnection() {
		return input -> {
			if (input == null) {
				return null;
			}
			final ConnectionData cd = new ConnectionData(input.getProviderId(), input.getProviderUserId(), input.getDisplayName(),
					input.getProfileUrl(), input.getImageUrl(), decrypt(input.getAccessToken()), decrypt(input.getSecret()),
					decrypt(input.getRefreshToken()), input.getExpireTime());
			final ConnectionFactory<?> connectionFactory = connectionFactoryLocator.getConnectionFactory(input.getProviderId());
			return connectionFactory.createConnection(cd);
		};
	}

	public Function<Connection<?>, SocialConnection> fromConnection(final String userId) {
		return input -> {
			if (input == null) {
				return null;
			}
			final ConnectionData cd = input.createData();
			final SocialConnection socialConnection = new SocialConnection();
			socialConnection.setCreated(Instant.now());
			socialConnection.setUserId(userId);
			socialConnection.setProviderId(cd.getProviderId());
			socialConnection.setProviderUserId(cd.getProviderUserId());
			socialConnection.setDisplayName(cd.getDisplayName());
			socialConnection.setProfileUrl(cd.getProfileUrl());
			socialConnection.setImageUrl(cd.getImageUrl());
			socialConnection.setAccessToken(encrypt(cd.getAccessToken()));
			socialConnection.setSecret(encrypt(cd.getSecret()));
			socialConnection.setRefreshToken(encrypt(cd.getRefreshToken()));
			socialConnection.setExpireTime(cd.getExpireTime());
			return socialConnection;
		};
	}

	private String encrypt(final String decrypted) {
		if (decrypted == null) {
			return null;
		}
		return textEncryptor.encrypt(decrypted);
	}

	private String decrypt(final String encrypted) {
		if (encrypted == null) {
			return null;
		}
		return textEncryptor.decrypt(encrypted);
	}
}
