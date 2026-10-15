package com.strikready.sandbox.services.kg.importer;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.LegalIssue;
import com.strikready.sandbox.models.kg.SourceDocument;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import com.strikready.sandbox.services.kg.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Imports judgments into the knowledge graph straight from official websites.
 *
 * The document is fetched into memory, its text extracted and parsed, and only
 * the structured result plus a {@link SourceDocument} (URL, hash, fetch time)
 * is saved. No copy of the file or its full text is kept; the official URL is
 * the reference for the judgment itself.
 */
@Service
public class JudgmentImportService {

	private static final Logger log = LoggerFactory.getLogger(JudgmentImportService.class);
	private static final int MIN_TEXT_CHARS = 200;

	@Autowired
	private OfficialSourceFetcher fetcher;

	@Autowired
	private DocumentTextExtractor extractor;

	@Autowired
	private JudgmentParser parser;

	@Autowired
	private KnowledgeGraphService graphService;

	@Autowired
	private LegalCaseRepository caseRepository;

	public List<String> allowedHosts() {
		return fetcher.getAllowedHosts();
	}

	public ImportResult importFromUrl(ImportRequest request) {
		String url = request.getUrl();
		try {
			if (url == null || url.trim().isEmpty()) {
				throw new IllegalArgumentException("url is required");
			}
			url = url.trim();
			fetcher.validate(url);
			LegalCase existing = caseRepository.findBySourceUrl(url);
			return doImport(url, request.getMetadata(), existing, false);
		} catch (IllegalArgumentException e) {
			log.warn("Import rejected for {}: {}", url, e.getMessage());
			return ImportResult.failed(url, e.getMessage(), false);
		} catch (HttpStatusException e) {
			log.warn("Import failed for {}: {}", url, e.getMessage());
			return ImportResult.failed(url, e.getMessage(), e.isRetryable());
		} catch (IOException e) {
			log.warn("Import failed for {}: {}", url, e.getMessage());
			return ImportResult.failed(url, e.getMessage(), true);
		}
	}

	public List<ImportResult> importAll(List<ImportRequest> requests) {
		List<ImportResult> results = new ArrayList<>();
		for (ImportRequest r : requests) {
			results.add(importFromUrl(r));
		}
		return results;
	}

	/**
	 * Re-reads a case's official source. If the document is unchanged only the
	 * fetch time is updated; if it changed, the extracted fields are re-parsed
	 * while editorial fields (title, court, category, issue topics, summary,
	 * appeal link) keep their current values.
	 */
	public ImportResult refresh(String caseId) {
		LegalCase existing = caseRepository.findById(caseId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown case: " + caseId));
		if (existing.getSource() == null || existing.getSource().getUrl() == null) {
			throw new IllegalArgumentException("Case " + caseId + " was not imported from a URL");
		}
		String url = existing.getSource().getUrl();
		try {
			return doImport(url, editorialFields(existing), existing, true);
		} catch (IllegalArgumentException e) {
			return ImportResult.failed(url, e.getMessage(), false);
		} catch (HttpStatusException e) {
			return ImportResult.failed(url, e.getMessage(), e.isRetryable());
		} catch (IOException e) {
			return ImportResult.failed(url, e.getMessage(), true);
		}
	}

	private ImportResult doImport(String url, LegalCase metadata, LegalCase existing, boolean refresh)
			throws IOException {
		FetchedDocument doc = fetcher.fetch(url);
		ImportResult result = new ImportResult();
		result.setUrl(url);

		if (existing != null && existing.getSource() != null && doc.getSha256().equals(existing.getSource().getSha256())) {
			existing.getSource().setFetchedAt(doc.getFetchedAt());
			existing.getSource().setResolvedUrl(doc.getResolvedUrl());
			LegalCase saved;
			if (metadata != null && !refresh) {
				metadata.setId(existing.getId());
				LegalCase updated = merge(existing, metadata, null);
				updated.setSource(existing.getSource());
				saved = graphService.ingest(updated);
			} else {
				saved = caseRepository.save(existing);
			}
			result.setStatus(ImportResult.Status.UNCHANGED);
			result.setLegalCase(saved);
			return result;
		}

		if (existing == null) {
			LegalCase sameFile = caseRepository.findFirstBySource_Sha256(doc.getSha256());
			if (sameFile != null) {
				result.setStatus(ImportResult.Status.DUPLICATE);
				result.setLegalCase(sameFile);
				result.getWarnings().add("Same document already imported from " + sameFile.getSource().getUrl());
				return result;
			}
		}

		DocumentTextExtractor.ExtractedText extracted = extractor.extract(doc);
		String text = extracted.getText();
		if (text.trim().length() < MIN_TEXT_CHARS) {
			result.getWarnings().add("Document has little or no text layer (scanned PDF?); OCR is needed for "
					+ "automatic extraction. Only supplied metadata was used.");
		}
		LegalCase parsed = parser.parse(text);

		LegalCase target = metadata != null ? metadata
				: existing != null ? editorialFields(existing) : new LegalCase();
		if (existing != null) {
			target.setId(existing.getId());
		}
		LegalCase merged = merge(parsed, target, result.getExtractedFields());
		if (merged.getTitle() == null) {
			merged.setTitle(extracted.getTitle() != null && !extracted.getTitle().trim().isEmpty()
					? extracted.getTitle().trim()
					: merged.getCaseNumber());
		}
		if (merged.getCourtCode() == null) {
			result.getWarnings().add("Court not recognised; pass metadata.courtCode to place the case in the hierarchy");
		}
		if (merged.getVerdict() == null) {
			result.getWarnings().add("Verdict not recognised; pass metadata.verdict to set it");
		}

		merged.setSource(toSource(doc, extracted));
		merged.setStatus(merged.getStatus() != null ? merged.getStatus()
				: merged.getVerdict() != null ? "DISPOSED" : null);
		LegalCase saved = graphService.ingest(merged);
		result.setStatus(existing == null ? ImportResult.Status.CREATED : ImportResult.Status.UPDATED);
		result.setLegalCase(saved);
		log.info("Imported {} as case {} ({})", url, saved.getId(), result.getStatus());
		return result;
	}

	/**
	 * Fields set in {@code supplied} win; empty ones are taken from
	 * {@code parsed} and, if {@code extracted} is given, recorded there.
	 */
	static LegalCase merge(LegalCase parsed, LegalCase supplied, List<String> extracted) {
		LegalCase m = supplied;
		if (m.getCnr() == null && parsed.getCnr() != null) {
			m.setCnr(parsed.getCnr());
			note(extracted, "cnr");
		}
		if (m.getCaseNumber() == null && parsed.getCaseNumber() != null) {
			m.setCaseNumber(parsed.getCaseNumber());
			note(extracted, "caseNumber");
		}
		if (m.getCaseType() == null && parsed.getCaseType() != null) {
			m.setCaseType(parsed.getCaseType());
			note(extracted, "caseType");
		}
		if (m.getFilingYear() == null && parsed.getFilingYear() != null) {
			m.setFilingYear(parsed.getFilingYear());
		}
		if (m.getTitle() == null && parsed.getTitle() != null) {
			m.setTitle(parsed.getTitle());
			note(extracted, "title");
		}
		if (m.getCourtCode() == null && parsed.getCourtCode() != null) {
			m.setCourtCode(parsed.getCourtCode());
			note(extracted, "courtCode");
		}
		if (m.getDecisionDate() == null && parsed.getDecisionDate() != null) {
			m.setDecisionDate(parsed.getDecisionDate());
			note(extracted, "decisionDate");
		}
		if (m.getVerdict() == null && parsed.getVerdict() != null) {
			m.setVerdict(parsed.getVerdict());
			note(extracted, "verdict");
		}
		if (isEmpty(m.getJudges()) && !isEmpty(parsed.getJudges())) {
			m.setJudges(parsed.getJudges());
			note(extracted, "judges");
		}
		if (isEmpty(m.getPetitioners()) && !isEmpty(parsed.getPetitioners())) {
			m.setPetitioners(parsed.getPetitioners());
			note(extracted, "petitioners");
		}
		if (isEmpty(m.getRespondents()) && !isEmpty(parsed.getRespondents())) {
			m.setRespondents(parsed.getRespondents());
			note(extracted, "respondents");
		}
		if (isEmpty(m.getStatutes()) && !isEmpty(parsed.getStatutes())) {
			m.setStatutes(parsed.getStatutes());
			note(extracted, "statutes");
		}
		if (isEmpty(m.getIssues()) && !isEmpty(parsed.getIssues())) {
			m.setIssues(parsed.getIssues());
			note(extracted, "issues");
		}
		if (isEmpty(m.getReferences()) && !isEmpty(parsed.getReferences())) {
			m.setReferences(parsed.getReferences());
			note(extracted, "references");
		}
		if (isEmpty(m.getCitations()) && !isEmpty(parsed.getCitations())) {
			m.setCitations(parsed.getCitations());
			note(extracted, "citations");
		}
		// Not produced by the parser, but carried over when re-importing an existing case.
		if (m.getCategory() == null && parsed.getCategory() != null) {
			m.setCategory(parsed.getCategory());
			m.setCategoryConfidence(parsed.getCategoryConfidence());
		}
		if (m.getSubCategory() == null) {
			m.setSubCategory(parsed.getSubCategory());
		}
		if (m.getSummary() == null) {
			m.setSummary(parsed.getSummary());
		}
		if (isEmpty(m.getKeywords()) && !isEmpty(parsed.getKeywords())) {
			m.setKeywords(parsed.getKeywords());
		}
		if (m.getAppealFromCaseId() == null) {
			m.setAppealFromCaseId(parsed.getAppealFromCaseId());
		}
		if (m.getStatus() == null) {
			m.setStatus(parsed.getStatus());
		}
		return m;
	}

	/** The fields a refresh must not overwrite with freshly parsed values. */
	private static LegalCase editorialFields(LegalCase existing) {
		LegalCase e = new LegalCase();
		e.setId(existing.getId());
		e.setTitle(existing.getTitle());
		e.setCourtCode(existing.getCourtCode());
		e.setCategory(existing.getCategory());
		e.setCategoryConfidence(existing.getCategoryConfidence());
		e.setSubCategory(existing.getSubCategory());
		e.setSummary(existing.getSummary());
		e.setKeywords(existing.getKeywords());
		e.setAppealFromCaseId(existing.getAppealFromCaseId());
		List<LegalIssue> topical = new ArrayList<>();
		for (LegalIssue i : safe(existing.getIssues())) {
			if (i.getTopic() != null) {
				topical.add(i);
			}
		}
		e.setIssues(topical);
		return e;
	}

	private static SourceDocument toSource(FetchedDocument doc, DocumentTextExtractor.ExtractedText extracted)
			throws IOException {
		SourceDocument s = new SourceDocument();
		s.setUrl(doc.getRequestedUrl());
		s.setResolvedUrl(doc.getResolvedUrl());
		s.setHost(new URL(doc.getRequestedUrl()).getHost().toLowerCase(Locale.ROOT));
		s.setContentType(doc.getContentType());
		s.setContentLength(doc.getContent().length);
		s.setSha256(doc.getSha256());
		s.setFetchedAt(doc.getFetchedAt());
		s.setLastModified(doc.getLastModified());
		s.setDocumentTitle(extracted.getTitle());
		s.setPageCount(extracted.getPageCount());
		return s;
	}

	private static void note(List<String> extracted, String field) {
		if (extracted != null) {
			extracted.add(field);
		}
	}

	private static boolean isEmpty(Collection<?> c) {
		return c == null || c.isEmpty();
	}

	private static <T> List<T> safe(List<T> list) {
		return list == null ? new ArrayList<T>() : list;
	}
}
