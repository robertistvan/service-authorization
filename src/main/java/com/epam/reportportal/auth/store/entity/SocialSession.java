package com.epam.reportportal.auth.store.entity;

import org.springframework.data.annotation.Id;

import java.util.Map;

/**
 * Created by andrei_varabyeu on 1/26/17.
 */
public class SocialSession {

	@Id
	private String id;

	private Map<String, Object> attributes;

	public SocialSession() {

	}

	public SocialSession(String id, Map<String, Object> attributes) {
		this.id = id;
		this.attributes = attributes;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}
}
