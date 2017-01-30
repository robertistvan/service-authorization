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

import com.epam.reportportal.auth.store.entity.SocialSession;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.social.connect.web.SessionStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;

import javax.inject.Inject;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * OAuth Session Data repository to still be able to consider this service as stateless
 *
 * @author Andrei Varabyeu
 * @since 3.0
 */
@Component
public class SocialMongoSessionRepository implements SessionStrategy {

    private final MongoOperations mongoOperations;

    @Inject
    public SocialMongoSessionRepository(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    @Override
    public void setAttribute(RequestAttributes request, String name, Object value) {
        mongoOperations.findAndModify(query(where("id").is(request.getSessionId())),
                Update.update("attributes." + name, value),
                FindAndModifyOptions.options().upsert(true), SocialSession.class);
    }

    @Override
    public Object getAttribute(RequestAttributes request, String name) {
        return getSocialSession(request.getSessionId()).map(SocialSession::getAttributes).map(map -> map.get(name))
                .orElse(null);
    }

    @Override
    public void removeAttribute(RequestAttributes request, String name) {
        mongoOperations
                .findAndModify(query(where("id").is(request.getSessionId())), new Update().unset("attributes." + name),
                        FindAndModifyOptions.options().remove(true), SocialSession.class);
    }

    private Optional<SocialSession> getSocialSession(String sessionId) {
        return Optional.ofNullable(mongoOperations.findOne(query(where("id").is(sessionId)), SocialSession.class));
    }
}
