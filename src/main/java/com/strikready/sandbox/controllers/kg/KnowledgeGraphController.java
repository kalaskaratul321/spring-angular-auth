package com.strikready.sandbox.controllers.kg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.CategorySummary;
import com.strikready.sandbox.models.kg.Classification;
import com.strikready.sandbox.models.kg.Court;
import com.strikready.sandbox.models.kg.CourtLevel;
import com.strikready.sandbox.models.kg.CourtTreeNode;
import com.strikready.sandbox.models.kg.GraphView;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.NodeType;
import com.strikready.sandbox.models.kg.ReferenceView;
import com.strikready.sandbox.models.kg.StatuteReference;
import com.strikready.sandbox.models.kg.VerdictResult;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import com.strikready.sandbox.services.kg.KnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the MahaLegal case knowledge graph.
 *
 * Reads are open to any authenticated user; ingest/delete require the Admin
 * authority (see WebSecurityConfig).
 */
@CrossOrigin(origins = "*")
@RequestMapping("/api/kg")
@RestController
public class KnowledgeGraphController {

	@Autowired
	private KnowledgeGraphService graphService;

	@Autowired
	private LegalCaseRepository caseRepository;

	// ----------------------------------------------------------- categories

	@GetMapping("/categories")
	public List<Map<String, String>> categories() {
		List<Map<String, String>> out = new ArrayList<>();
		for (CaseCategory c : CaseCategory.values()) {
			Map<String, String> m = new LinkedHashMap<>();
			m.put("code", c.name());
			m.put("name", c.getDisplayName());
			m.put("description", c.getDescription());
			out.add(m);
		}
		return out;
	}

	@GetMapping("/categories/summary")
	public List<CategorySummary> categorySummary() {
		return graphService.categorySummaries();
	}

	@GetMapping("/categories/{category}/cases")
	public Page<LegalCase> casesInCategory(@PathVariable CaseCategory category,
			@RequestParam(required = false) CourtLevel courtLevel, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return graphService.casesInCategory(category, courtLevel, page, size);
	}

	@GetMapping("/categories/{category}/graph")
	public GraphView categoryGraph(@PathVariable CaseCategory category,
			@RequestParam(required = false) CourtLevel courtLevel, @RequestParam(defaultValue = "50") int limit) {
		return graphService.categoryGraph(category, courtLevel, limit);
	}

	// --------------------------------------------------------------- courts

	@GetMapping("/courts")
	public List<Court> courts() {
		return graphService.courts();
	}

	@GetMapping("/courts/hierarchy")
	public List<CourtTreeNode> courtHierarchy() {
		return graphService.courtHierarchy();
	}

	@GetMapping("/courts/{code}/graph")
	public GraphView courtGraph(@PathVariable String code, @RequestParam(required = false) CaseCategory category) {
		return graphService.neighbourhood(NodeType.COURT.key(code), 1, category);
	}

	// ---------------------------------------------------------------- cases

	@PostMapping("/cases")
	public ResponseEntity<LegalCase> ingest(@RequestBody LegalCase legalCase) {
		return new ResponseEntity<>(graphService.ingest(legalCase), HttpStatus.CREATED);
	}

	@PostMapping("/cases/bulk")
	public ResponseEntity<List<LegalCase>> ingestAll(@RequestBody List<LegalCase> cases) {
		return new ResponseEntity<>(graphService.ingestAll(cases), HttpStatus.CREATED);
	}

	@PostMapping("/classify")
	public Classification classify(@RequestBody LegalCase legalCase) {
		return graphService.classify(legalCase);
	}

	@GetMapping("/cases/search")
	public Page<LegalCase> search(@RequestParam(required = false) String q,
			@RequestParam(required = false) CaseCategory category, @RequestParam(required = false) VerdictResult verdict,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return graphService.search(q, category, verdict, page, size);
	}

	@GetMapping("/cases/{id}")
	public ResponseEntity<LegalCase> getCase(@PathVariable String id) {
		return caseRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/cases/{id}")
	public ResponseEntity<Void> deleteCase(@PathVariable String id) {
		graphService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/cases/{id}/graph")
	public GraphView caseGraph(@PathVariable String id, @RequestParam(defaultValue = "2") int depth,
			@RequestParam(required = false) CaseCategory category) {
		return graphService.neighbourhood(NodeType.CASE.key(id), depth, category);
	}

	@GetMapping("/cases/{id}/appeal-chain")
	public List<LegalCase> appealChain(@PathVariable String id) {
		return graphService.appealChain(id);
	}

	@GetMapping("/cases/{id}/references")
	public List<ReferenceView> references(@PathVariable String id) {
		return graphService.references(id);
	}

	@GetMapping("/cases/{id}/cited-by")
	public List<ReferenceView> citedBy(@PathVariable String id) {
		return graphService.citedBy(id);
	}

	// ------------------------------------------------ issues, verdicts, citations

	@GetMapping("/issues/graph")
	public GraphView issueGraph(@RequestParam String topic, @RequestParam(required = false) CaseCategory category) {
		return graphService.issueGraph(topic, category);
	}

	@GetMapping("/verdicts")
	public List<Map<String, String>> verdicts() {
		List<Map<String, String>> out = new ArrayList<>();
		for (VerdictResult v : VerdictResult.values()) {
			Map<String, String> m = new LinkedHashMap<>();
			m.put("code", v.name());
			m.put("name", v.getDisplayName());
			out.add(m);
		}
		return out;
	}

	@GetMapping("/citations/graph")
	public GraphView citationGraph(@RequestParam String citation,
			@RequestParam(required = false) CaseCategory category) {
		return graphService.citationGraph(citation, category);
	}

	// ------------------------------------------------------------- statutes

	@GetMapping("/statutes/{act}/{section}/graph")
	public GraphView statuteGraph(@PathVariable String act, @PathVariable String section,
			@RequestParam(required = false) CaseCategory category) {
		return graphService.neighbourhood(NodeType.STATUTE.key(new StatuteReference(act, section).identifier()), 1,
				category);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("error", ex.getMessage());
		return ResponseEntity.badRequest().body(body);
	}
}
