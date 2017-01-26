package com.epam.reportportal.auth.store;

import com.epam.reportportal.auth.store.entity.SocialSession;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.social.connect.web.SessionStrategy;
import org.springframework.web.context.request.RequestAttributes;

import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Created by andrei_varabyeu on 1/26/17.
 */
public class SocialMongoSessionStrategy implements SessionStrategy {

	private final MongoOperations mongoOperations;

	public SocialMongoSessionStrategy(MongoOperations mongoOperations) {
		this.mongoOperations = mongoOperations;
	}

	@Override
	public void setAttribute(RequestAttributes request, String name, Object value) {
		mongoOperations.findAndModify(query(where("id").is(request.getSessionId())), Update.update("attributes." + name, value),
				FindAndModifyOptions.options().upsert(true), SocialSession.class);
	}

	@Override
	public Object getAttribute(RequestAttributes request, String name) {
		return getSocialSession(request.getSessionId()).map(SocialSession::getAttributes).map(map -> map.get(name)).orElse(null);
	}

	@Override
	public void removeAttribute(RequestAttributes request, String name) {
		mongoOperations.findAndModify(query(where("id").is(request.getSessionId())), new Update().unset("attributes." + name),
				FindAndModifyOptions.options().remove(true), SocialSession.class);
	}

	private Optional<SocialSession> getSocialSession(String sessionId) {
		return Optional.ofNullable(mongoOperations.findOne(query(where("id").is(sessionId)), SocialSession.class));
	}
}