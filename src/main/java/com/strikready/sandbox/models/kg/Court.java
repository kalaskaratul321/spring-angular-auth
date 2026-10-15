package com.strikready.sandbox.models.kg;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A court, bench or quasi-judicial forum. {@code appellateCourtCode} links the
 * forum to the one its decisions are appealed to, which is what lets the graph
 * walk from a Magistrate/Sessions court up to the High Court.
 */
@Document(collection = "kg_courts")
public class Court {

	@Id
	private String id;
	@Indexed(unique = true)
	private String code;
	private String name;
	private CourtLevel level;
	private String state;
	private String district;
	private String bench;
	private String appellateCourtCode;
	private List<CaseCategory> categories = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public CourtLevel getLevel() {
		return level;
	}

	public void setLevel(CourtLevel level) {
		this.level = level;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getDistrict() {
		return district;
	}

	public void setDistrict(String district) {
		this.district = district;
	}

	public String getBench() {
		return bench;
	}

	public void setBench(String bench) {
		this.bench = bench;
	}

	public String getAppellateCourtCode() {
		return appellateCourtCode;
	}

	public void setAppellateCourtCode(String appellateCourtCode) {
		this.appellateCourtCode = appellateCourtCode;
	}

	public List<CaseCategory> getCategories() {
		return categories;
	}

	public void setCategories(List<CaseCategory> categories) {
		this.categories = categories;
	}
}
