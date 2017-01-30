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
package com.epam.reportportal.auth.store.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "social_connections")
@CompoundIndexes({
		@CompoundIndex(name = "primary", def = "{ 'userId' : 1, 'providerId' : 1, 'providerUserId' : 1 }", unique = true),
		@CompoundIndex(name = "rank", def = "{ 'userId' : 1, 'providerId' : 1, 'created' : 1 }", unique = true)
})
public class SocialConnection {

	@Id
	private String id;
	private Instant created;
	private String userId;
	private String providerId;
	private String providerUserId;
	private String displayName;
	private String profileUrl;
	private String imageUrl;
	private String accessToken;
	private String secret;
	private String refreshToken;
	private Long expireTime;

	public String getId() {
		return id;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public Instant getCreated() {
		return created;
	}

	public void setCreated(final Instant created) {
		this.created = created;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(final String userId) {
		this.userId = userId;
	}

	public String getProviderId() {
		return providerId;
	}

	public void setProviderId(final String providerId) {
		this.providerId = providerId;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

	public void setProviderUserId(final String providerUserId) {
		this.providerUserId = providerUserId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(final String displayName) {
		this.displayName = displayName;
	}

	public String getProfileUrl() {
		return profileUrl;
	}

	public void setProfileUrl(final String profileUrl) {
		this.profileUrl = profileUrl;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(final String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(final String accessToken) {
		this.accessToken = accessToken;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(final String secret) {
		this.secret = secret;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(final String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public Long getExpireTime() {
		return expireTime;
	}

	public void setExpireTime(final Long expireTime) {
		this.expireTime = expireTime;
	}
}
