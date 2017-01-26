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

import com.epam.ta.reportportal.commons.EntityUtils;
import com.epam.ta.reportportal.commons.validation.BusinessRule;
import com.epam.ta.reportportal.database.BinaryData;
import com.epam.ta.reportportal.database.DataStorage;
import com.epam.ta.reportportal.database.dao.ProjectRepository;
import com.epam.ta.reportportal.database.dao.UserRepository;
import com.epam.ta.reportportal.database.entity.Project;
import com.epam.ta.reportportal.database.entity.user.User;
import com.epam.ta.reportportal.database.entity.user.UserRole;
import com.epam.ta.reportportal.database.entity.user.UserType;
import com.epam.ta.reportportal.database.personal.PersonalProjectUtils;
import com.epam.ta.reportportal.database.search.Filter;
import com.epam.ta.reportportal.ws.model.ErrorType;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.UserProfile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static com.epam.ta.reportportal.database.search.FilterCondition.builder;

/**
 * Replicates GitHub account info with internal ReportPortal's database
 *
 * @author <a href="mailto:andrei_varabyeu@epam.com">Andrei Varabyeu</a>
 */
@Component
public class SocialDataReplicator {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocialDataReplicator.class);

	private final UserRepository userRepository;
	private final ProjectRepository projectRepository;
	private final DataStorage dataStorage;
	private final RestTemplate restTemplate;

	@Autowired
	public SocialDataReplicator(UserRepository userRepository, ProjectRepository projectRepository, DataStorage dataStorage) {
		this.userRepository = userRepository;
		this.projectRepository = projectRepository;
		this.dataStorage = dataStorage;

		this.restTemplate = new RestTemplate();
	}

	public User synchronizeUser(Connection<?> connection) {
		UserProfile profile = connection.fetchUserProfile();
		User user = userRepository.findOne(EntityUtils.normalizeUsername(Optional.ofNullable(profile.getId()).orElse(profile.getName())));

		BusinessRule.expect(user, Objects::nonNull).verify(ErrorType.USER_NOT_FOUND, profile.getId());
		BusinessRule.expect(user.getType(), userType -> Objects.equals(userType, UserType.GITHUB))
				.verify(ErrorType.INCORRECT_AUTHENTICATION_TYPE, "User '" + profile.getId() + "' is not GitHUB user");
		if (!Strings.isNullOrEmpty(profile.getId())) {
			user.setFullName(profile.getId());
		}
		user.getMetaInfo().setSynchronizationDate(Date.from(ZonedDateTime.now().toInstant()));

		String newPhotoId = uploadAvatar(profile.getId(), connection.getImageUrl());
		if (!Strings.isNullOrEmpty(newPhotoId)) {
			dataStorage.deleteData(user.getPhotoId());
			user.setPhotoId(newPhotoId);
		}
		userRepository.save(user);
		return user;
	}

	/**
	 * Replicates GitHub user to internal database (if does NOT exist). Creates personal project for that user
	 *
	 * @param connection Spring's Social connection representation
	 * @return Internal User representation
	 */
	public User replicateUser(Connection<?> connection) {
		UserProfile profile = connection.fetchUserProfile();
		String login = EntityUtils.normalizeUsername(Optional.ofNullable(profile.getId()).orElse(profile.getName()));
		User user = userRepository.findOne(login);
		if (null == user) {
			user = new User();
			user.setLogin(login);

			String email = profile.getEmail();
			if (Strings.isNullOrEmpty(email)) {
				//throw exception
			} else if (userRepository
					.exists(Filter.builder().withTarget(User.class).withCondition(builder().eq("email", email).build()).build())) {
				throw new UserSynchronizationException("User with email '" + email + "' already exists");
			}

			user.setEmail(EntityUtils.normalizeEmail(email));

			user.setFullName(profile.getFirstName() + " " + profile.getLastName());

			User.MetaInfo metaInfo = new User.MetaInfo();
			Date now = Date.from(ZonedDateTime.now().toInstant());
			metaInfo.setLastLogin(now);
			metaInfo.setSynchronizationDate(now);
			user.setMetaInfo(metaInfo);

			user.setType(UserType.GITHUB);
			user.setRole(UserRole.USER);

			if (!Strings.isNullOrEmpty(connection.getImageUrl())) {
				user.setPhotoId(uploadAvatar(login, connection.getImageUrl()));
			}

			user.setIsExpired(false);

			user.setDefaultProject(generatePersonalProject(user).getId());
			userRepository.save(user);

		} else if (!UserType.GITHUB.equals(user.getType())) {
			//if user with such login exists, but it's not GitHub user than throw an exception
			throw new UserSynchronizationException("User with login '" + user.getId() + "' already exists");
		}
		return user;
	}

	private String uploadAvatar(String login, String avatarUrl) {
		String photoId = null;
		if (null != avatarUrl) {
			ResponseEntity<Resource> photoRs = this.restTemplate.getForEntity(avatarUrl, Resource.class);
			try (InputStream photoStream = photoRs.getBody().getInputStream()) {
				BinaryData photo = new BinaryData(photoRs.getHeaders().getContentType().toString(), photoRs.getBody().contentLength(),
						photoStream);
				photoId = dataStorage.saveData(photo, photoRs.getBody().getFilename());
			} catch (IOException e) {
				LOGGER.error("Unable to load photo for user {}", login);
			}
		}
		return photoId;
	}

	/**
	 * Generates personal project if does NOT exists
	 *
	 * @param user Owner of personal project
	 * @return Created project
	 */
	private Project generatePersonalProject(User user) {
		String personalProjectName = PersonalProjectUtils.personalProjectName(user.getLogin());
		Project personalProject = projectRepository.findOne(personalProjectName);
		if (null == personalProject) {
			personalProject = PersonalProjectUtils.generatePersonalProject(user);
			projectRepository.save(personalProject);
		}
		return personalProject;
	}

	public static class UserSynchronizationException extends AuthenticationException {

		public UserSynchronizationException(String msg) {
			super(msg);
		}
	}
}
