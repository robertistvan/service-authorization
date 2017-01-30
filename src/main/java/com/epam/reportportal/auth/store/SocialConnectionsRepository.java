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
package com.epam.reportportal.auth.store;

import com.epam.reportportal.auth.converter.MongoConnectionConverters;
import com.epam.reportportal.auth.store.entity.SocialConnection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.ConnectionFactoryLocator;
import org.springframework.social.connect.ConnectionKey;
import org.springframework.social.connect.ConnectionRepository;
import org.springframework.social.connect.ConnectionSignUp;
import org.springframework.social.connect.UsersConnectionRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Repository for Social Connections entity
 *
 * @author Andrei Varabyeu
 * @since 3.0
 */
public class SocialConnectionsRepository implements UsersConnectionRepository {

    private final MongoOperations mongo;
    private final ConnectionFactoryLocator connectionFactoryLocator;
    private final MongoConnectionConverters mongoConnectionConverters;
    private ConnectionSignUp connectionSignUp;

    public SocialConnectionsRepository(final MongoOperations mongo,
            final ConnectionFactoryLocator connectionFactoryLocator,
            final MongoConnectionConverters mongoConnectionConverters) {
        this.mongo = mongo;
        this.connectionFactoryLocator = connectionFactoryLocator;
        this.mongoConnectionConverters = mongoConnectionConverters;
    }

    public void setConnectionSignUp(final ConnectionSignUp connectionSignUp) {
        this.connectionSignUp = connectionSignUp;
    }

    @Override
    public List<String> findUserIdsWithConnection(final Connection<?> connection) {
        ConnectionKey key = connection.getKey();
        Query query = query(
                where("providerId").is(key.getProviderId()).and("providerUserId").is(key.getProviderUserId()));
        query.fields().include("userId");
        List<String> localUserIds = ImmutableList
                .copyOf(mongo.find(query, SocialConnection.class).stream().map(mongoConnectionConverters.toUserId())
                        .collect(Collectors.toList()));
        if (localUserIds.isEmpty() && connectionSignUp != null) {
            String newUserId = connectionSignUp.execute(connection);
            if (newUserId != null) {
                createConnectionRepository(newUserId).addConnection(connection);
                return ImmutableList.of(newUserId);
            }
        }
        return localUserIds;
    }

    @Override
    public Set<String> findUserIdsConnectedTo(final String providerId, final Set<String> providerUserIds) {
        Query query = query(where("providerId").is(providerId).and("providerUserId").in(providerUserIds));
        query.fields().include("userId");

        return ImmutableSet
                .copyOf(mongo.find(query, SocialConnection.class).stream().map(mongoConnectionConverters.toUserId())
                        .collect(Collectors.toList()));
    }

    @Override
    public ConnectionRepository createConnectionRepository(final String userId) {
        checkArgument(userId != null, "userId must be defined");
        return new SocialConnectionRepository(userId, mongo, connectionFactoryLocator, mongoConnectionConverters);
    }

}
