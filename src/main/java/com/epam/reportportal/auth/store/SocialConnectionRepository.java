package com.epam.reportportal.auth.store;

import com.epam.reportportal.auth.converter.MongoConnectionConverters;
import com.epam.reportportal.auth.store.entity.SocialConnection;
import com.google.common.collect.ImmutableList;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.social.connect.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

/**
 * Created by andrei_varabyeu on 1/26/17.
 */
public class SocialConnectionRepository implements ConnectionRepository {

	private final String userId;
	private final MongoOperations mongo;
	private final ConnectionFactoryLocator connectionFactoryLocator;
	private final MongoConnectionConverters mongoConnectionConverters;

	public SocialConnectionRepository(final String userId, final MongoOperations mongo,
			final ConnectionFactoryLocator connectionFactoryLocator, final MongoConnectionConverters mongoConnectionConverters) {
		this.userId = userId;
		this.mongo = mongo;
		this.connectionFactoryLocator = connectionFactoryLocator;
		this.mongoConnectionConverters = mongoConnectionConverters;
	}

	@Override
	public MultiValueMap<String, Connection<?>> findAllConnections() {
		final Query query = query(where("userId").is(userId)).with(sortByProviderId().and(sortByCreated()));

		final List<Connection<?>> results = findConnections(query);

		final MultiValueMap<String, Connection<?>> connections = new LinkedMultiValueMap<>();
		for (String registeredProviderId : connectionFactoryLocator.registeredProviderIds()) {
			connections.put(registeredProviderId, ImmutableList.of());
		}

		for (Connection<?> connection : results) {
			final String providerId = connection.getKey().getProviderId();
			if (connections.get(providerId).isEmpty()) {
				connections.put(providerId, new LinkedList<>());
			}
			connections.add(providerId, connection);
		}
		return connections;
	}

	@Override
	public List<Connection<?>> findConnections(final String providerId) {
		final Query query = query(where("userId").is(userId).and("providerId").is(providerId)).with(sortByCreated());
		return ImmutableList.copyOf(findConnections(query));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A> List<Connection<A>> findConnections(final Class<A> apiType) {
		final List<?> connections = findConnections(getProviderId(apiType));
		return (List<Connection<A>>) connections;
	}

	@Override
	public MultiValueMap<String, Connection<?>> findConnectionsToUsers(final MultiValueMap<String, String> providerUserIds) {
		if (providerUserIds == null || providerUserIds.isEmpty()) {
			throw new IllegalArgumentException("providerUserIds must be defined");
		}

		final List<Criteria> filters = new ArrayList<>(providerUserIds.size());
		for (Map.Entry<String, List<String>> entry : providerUserIds.entrySet()) {
			final String providerId = entry.getKey();
			filters.add(where("providerId").is(providerId).and("providerUserId").in(entry.getValue()));
		}

		final Criteria criteria = where("userId").is(userId);
		criteria.orOperator(filters.toArray(new Criteria[filters.size()]));

		final Query query = new Query(criteria).with(sortByProviderId().and(sortByCreated()));
		final List<Connection<?>> results = mongo.find(query, SocialConnection.class).stream()
				.map(mongoConnectionConverters.toConnection()).collect(toList());

		MultiValueMap<String, Connection<?>> connectionsForUsers = new LinkedMultiValueMap<>();
		for (Connection<?> connection : results) {
			final String providerId = connection.getKey().getProviderId();
			final String providerUserId = connection.getKey().getProviderUserId();
			final List<String> userIds = providerUserIds.get(providerId);
			List<Connection<?>> connections = connectionsForUsers.get(providerId);
			if (connections == null) {
				connections = new ArrayList<>(userIds.size());
				for (int i = 0; i < userIds.size(); i++) {
					connections.add(null);
				}
				connectionsForUsers.put(providerId, connections);
			}
			final int connectionIndex = userIds.indexOf(providerUserId);
			connections.set(connectionIndex, connection);
		}
		return connectionsForUsers;
	}

	@Override
	public Connection<?> getConnection(final ConnectionKey connectionKey) {
		final Query query = query(where("userId").is(userId).and("providerId").is(connectionKey.getProviderId()).and("providerUserId")
				.is(connectionKey.getProviderUserId()));
		final Connection<?> connection = findOneConnection(query);
		if (connection == null) {
			throw new NoSuchConnectionException(connectionKey);
		} else {
			return connection;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A> Connection<A> getConnection(final Class<A> apiType, final String providerUserId) {
		final String providerId = getProviderId(apiType);
		return (Connection<A>) getConnection(new ConnectionKey(providerId, providerUserId));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A> Connection<A> getPrimaryConnection(final Class<A> apiType) {
		final String providerId = getProviderId(apiType);
		final Connection<A> connection = (Connection<A>) findPrimaryConnection(providerId);
		if (connection == null) {
			throw new NotConnectedException(providerId);
		} else {
			return connection;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <A> Connection<A> findPrimaryConnection(final Class<A> apiType) {
		final String providerId = getProviderId(apiType);
		return (Connection<A>) findPrimaryConnection(providerId);
	}

	@Override
	public void addConnection(final Connection<?> connection) {
		try {
			final SocialConnection socialConnection = mongoConnectionConverters.fromConnection(userId).apply(connection);
			mongo.insert(socialConnection);
		} catch (DuplicateKeyException ex) {
			throw new DuplicateConnectionException(connection.getKey());
		}
	}

	@Override
	public void updateConnection(final Connection<?> connection) {
		final SocialConnection socialConnection = mongoConnectionConverters.fromConnection(userId).apply(connection);
		final Query query = query(where("userId").is(userId).and("providerId").is(socialConnection.getProviderId()).and("providerUserId")
				.is(socialConnection.getProviderUserId()));
		final Update update = update("displayName", socialConnection.getDisplayName()).set("profileUrl", socialConnection.getProfileUrl())
				.set("imageUrl", socialConnection.getImageUrl()).set("accessToken", socialConnection.getAccessToken())
				.set("secret", socialConnection.getSecret()).set("refreshToken", socialConnection.getRefreshToken())
				.set("expireTime", socialConnection.getExpireTime());
		mongo.updateFirst(query, update, SocialConnection.class);
	}

	@Override
	public void removeConnections(final String providerId) {
		final Query query = query(where("userId").is(userId).and("providerId").is(providerId));
		mongo.remove(query, SocialConnection.class);
	}

	@Override
	public void removeConnection(final ConnectionKey connectionKey) {
		final Query query = query(where("userId").is(userId).and("providerId").is(connectionKey.getProviderId()).and("providerUserId")
				.is(connectionKey.getProviderUserId()));
		mongo.remove(query, SocialConnection.class);
	}

	private Connection<?> findPrimaryConnection(String providerId) {
		final Query query = query(where("userId").is(userId).and("providerId").is(providerId)).with(sortByCreated());
		return findOneConnection(query);
	}

	private List<Connection<?>> findConnections(Query query) {
		return mongo.find(query, SocialConnection.class).stream().map(mongoConnectionConverters.toConnection()).collect(toList());
	}

	private Connection<?> findOneConnection(Query query) {
		return mongoConnectionConverters.toConnection().apply(mongo.findOne(query, SocialConnection.class));
	}

	private <A> String getProviderId(Class<A> apiType) {
		return connectionFactoryLocator.getConnectionFactory(apiType).getProviderId();
	}

	private Sort sortByProviderId() {
		return new Sort(Sort.Direction.ASC, "providerId");
	}

	private Sort sortByCreated() {
		return new Sort(Sort.Direction.DESC, "created");
	}
}
