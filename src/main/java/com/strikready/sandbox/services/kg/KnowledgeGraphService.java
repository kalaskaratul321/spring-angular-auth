package com.strikready.sandbox.services.kg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.CategorySummary;
import com.strikready.sandbox.models.kg.Classification;
import com.strikready.sandbox.models.kg.Court;
import com.strikready.sandbox.models.kg.CourtLevel;
import com.strikready.sandbox.models.kg.CourtTreeNode;
import com.strikready.sandbox.models.kg.GraphView;
import com.strikready.sandbox.models.kg.KgEdge;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.NodeType;
import com.strikready.sandbox.models.kg.PrecedentLink;
import com.strikready.sandbox.models.kg.RelationType;
import com.strikready.sandbox.models.kg.StatuteReference;
import com.strikready.sandbox.repositories.kg.CourtRepository;
import com.strikready.sandbox.repositories.kg.KgEdgeRepository;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Builds and queries the case knowledge graph.
 *
 * Cases, courts and edges live in MongoDB. Every ingested case is linked to its
 * court, category, statutes, judges and parties, to the lower-court case it
 * arose from (APPEAL_FROM) and to the precedents it relies on. Category and
 * court level are also stored on the case so the graph can be grouped
 * Civil / Criminal / Land / RERA / ... and High Court -> Sessions Court cheaply.
 */
@Service
public class KnowledgeGraphService {

	private static final int MAX_DEPTH = 4;
	private static final int MAX_NODES = 500;

	@Autowired
	private LegalCaseRepository caseRepository;

	@Autowired
	private CourtRepository courtRepository;

	@Autowired
	private KgEdgeRepository edgeRepository;

	@Autowired
	private CaseCategoryClassifier classifier;

	@Autowired
	private MongoTemplate mongoTemplate;

	// ---------------------------------------------------------------- ingest

	public LegalCase ingest(LegalCase legalCase) {
		if (legalCase.getCnr() != null && legalCase.getId() == null) {
			LegalCase existing = caseRepository.findByCnr(legalCase.getCnr());
			if (existing != null) {
				legalCase.setId(existing.getId());
			}
		}

		Court court = legalCase.getCourtCode() == null ? null : courtRepository.findByCode(legalCase.getCourtCode());
		if (legalCase.getCourtCode() != null && court == null) {
			throw new IllegalArgumentException("Unknown court code: " + legalCase.getCourtCode());
		}
		if (court != null) {
			legalCase.setCourtLevel(court.getLevel());
		}

		if (legalCase.getCategory() == null) {
			Classification c = classifier.classify(legalCase, court == null ? null : court.getCategories());
			legalCase.setCategory(c.getCategory());
			legalCase.setCategoryConfidence(c.getConfidence());
			if (legalCase.getSubCategory() == null) {
				legalCase.setSubCategory(c.getSubCategory());
			}
		} else {
			legalCase.setCategoryConfidence(1.0);
		}

		if (legalCase.getAppealFromCaseId() != null) {
			validateAppeal(legalCase);
		}

		LegalCase saved = caseRepository.save(legalCase);
		rebuildEdges(saved);
		return saved;
	}

	public List<LegalCase> ingestAll(List<LegalCase> cases) {
		List<LegalCase> saved = new ArrayList<>();
		for (LegalCase c : cases) {
			saved.add(ingest(c));
		}
		return saved;
	}

	public Classification classify(LegalCase legalCase) {
		Court court = legalCase.getCourtCode() == null ? null : courtRepository.findByCode(legalCase.getCourtCode());
		return classifier.classify(legalCase, court == null ? null : court.getCategories());
	}

	public void delete(String caseId) {
		String key = NodeType.CASE.key(caseId);
		edgeRepository.deleteBySource(key);
		for (KgEdge incoming : edgeRepository.findByTargetIn(Collections.singletonList(key))) {
			edgeRepository.delete(incoming);
		}
		caseRepository.deleteById(caseId);
	}

	/** An appeal must go to a forum at least as senior as the one appealed from. */
	private void validateAppeal(LegalCase legalCase) {
		LegalCase lower = caseRepository.findById(legalCase.getAppealFromCaseId()).orElseThrow(
				() -> new IllegalArgumentException("Unknown appealFromCaseId: " + legalCase.getAppealFromCaseId()));
		if (lower.getId().equals(legalCase.getId())) {
			throw new IllegalArgumentException("A case cannot be an appeal from itself");
		}
		if (lower.getCourtLevel() != null && legalCase.getCourtLevel() != null
				&& lower.getCourtLevel().getRank() > legalCase.getCourtLevel().getRank()) {
			throw new IllegalArgumentException("Appeal from " + lower.getCourtLevel().getDisplayName() + " cannot lie to "
					+ legalCase.getCourtLevel().getDisplayName());
		}
	}

	private void rebuildEdges(LegalCase c) {
		String self = NodeType.CASE.key(c.getId());
		edgeRepository.deleteBySource(self);

		Map<String, KgEdge> edges = new LinkedHashMap<>();
		CaseCategory cat = c.getCategory();
		addEdge(edges, self, RelationType.BELONGS_TO, NodeType.CATEGORY.key(cat.name()), cat);
		if (c.getCourtCode() != null) {
			addEdge(edges, self, RelationType.HEARD_IN, NodeType.COURT.key(c.getCourtCode()), cat);
		}
		for (StatuteReference s : safe(c.getStatutes())) {
			String id = s.identifier();
			if (!id.isEmpty()) {
				addEdge(edges, self, RelationType.INVOKES, NodeType.STATUTE.key(id), cat);
			}
		}
		for (String judge : safe(c.getJudges())) {
			addEdge(edges, self, RelationType.DECIDED_BY, NodeType.JUDGE.key(normaliseName(judge)), cat);
		}
		for (String party : concat(c.getPetitioners(), c.getRespondents())) {
			addEdge(edges, self, RelationType.INVOLVES_PARTY, NodeType.PARTY.key(normaliseName(party)), cat);
		}
		if (c.getAppealFromCaseId() != null) {
			addEdge(edges, self, RelationType.APPEAL_FROM, NodeType.CASE.key(c.getAppealFromCaseId()), cat);
		}
		for (PrecedentLink p : safe(c.getPrecedents())) {
			if (p.getCaseId() == null || p.getCaseId().equals(c.getId())) {
				continue;
			}
			RelationType rel = p.getRelation() == null ? RelationType.CITES : p.getRelation();
			if (rel != RelationType.CITES && rel != RelationType.FOLLOWS && rel != RelationType.DISTINGUISHES
					&& rel != RelationType.OVERRULES) {
				throw new IllegalArgumentException("Invalid precedent relation: " + rel);
			}
			addEdge(edges, self, rel, NodeType.CASE.key(p.getCaseId()), cat);
		}
		edgeRepository.saveAll(edges.values());
	}

	private static void addEdge(Map<String, KgEdge> edges, String source, RelationType rel, String target,
			CaseCategory category) {
		edges.put(source + "|" + rel + "|" + target, new KgEdge(source, rel, target, category));
	}

	// ---------------------------------------------------------- traversal

	/**
	 * Breadth-first neighbourhood of any node (case, court, statute, judge...)
	 * following edges in both directions, optionally restricted to one category.
	 */
	public GraphView neighbourhood(String nodeKey, int depth, CaseCategory categoryFilter) {
		NodeType.ofKey(nodeKey);
		int maxDepth = Math.max(1, Math.min(depth, MAX_DEPTH));

		Set<String> visited = new LinkedHashSet<>();
		Map<String, KgEdge> collected = new LinkedHashMap<>();
		Set<String> frontier = new LinkedHashSet<>(Collections.singleton(nodeKey));
		visited.add(nodeKey);

		for (int level = 0; level < maxDepth && !frontier.isEmpty() && visited.size() < MAX_NODES; level++) {
			List<KgEdge> found = new ArrayList<>(edgeRepository.findBySourceIn(frontier));
			found.addAll(edgeRepository.findByTargetIn(frontier));
			Set<String> next = new LinkedHashSet<>();
			for (KgEdge e : found) {
				if (categoryFilter != null && e.getCategory() != categoryFilter) {
					continue;
				}
				collected.put(e.getSource() + "|" + e.getRelation() + "|" + e.getTarget(), e);
				for (String n : new String[] { e.getSource(), e.getTarget() }) {
					// Category/court hubs connect to thousands of cases; don't expand through them.
					if (visited.size() < MAX_NODES && visited.add(n) && !isHub(n)) {
						next.add(n);
					}
				}
			}
			frontier = next;
		}
		return toView(visited, collected.values());
	}

	private static boolean isHub(String key) {
		NodeType t = NodeType.ofKey(key);
		return t == NodeType.CATEGORY || t == NodeType.COURT;
	}

	/**
	 * The full appeal chain a case belongs to, ordered from the trial forum
	 * (e.g. JMFC / Sessions Court) up to the highest forum (High Court).
	 */
	public List<LegalCase> appealChain(String caseId) {
		LegalCase start = caseRepository.findById(caseId)
				.orElseThrow(() -> new IllegalArgumentException("Unknown case: " + caseId));

		List<LegalCase> lower = new ArrayList<>();
		Set<String> seen = new HashSet<>(Collections.singleton(start.getId()));
		LegalCase cursor = start;
		while (cursor.getAppealFromCaseId() != null && seen.add(cursor.getAppealFromCaseId())) {
			LegalCase prev = caseRepository.findById(cursor.getAppealFromCaseId()).orElse(null);
			if (prev == null) {
				break;
			}
			lower.add(prev);
			cursor = prev;
		}
		Collections.reverse(lower);

		List<LegalCase> chain = new ArrayList<>(lower);
		chain.add(start);
		cursor = start;
		while (true) {
			List<LegalCase> higher = caseRepository.findByAppealFromCaseId(cursor.getId());
			LegalCase next = null;
			for (LegalCase h : higher) {
				if (seen.add(h.getId()) && (next == null || rank(h) > rank(next))) {
					next = h;
				}
			}
			if (next == null) {
				break;
			}
			chain.add(next);
			cursor = next;
		}
		return chain;
	}

	private static int rank(LegalCase c) {
		return c.getCourtLevel() == null ? 0 : c.getCourtLevel().getRank();
	}

	// ----------------------------------------------------- category grouping

	/** Case counts per category, broken down by court level, with the most-invoked statutes. */
	public List<CategorySummary> categorySummaries() {
		Map<CaseCategory, CategorySummary> byCategory = new EnumMap<>(CaseCategory.class);
		for (CaseCategory cat : CaseCategory.values()) {
			byCategory.put(cat, new CategorySummary(cat));
		}

		Aggregation byLevel = Aggregation.newAggregation(Aggregation.group("category", "courtLevel").count().as("count"));
		AggregationResults<Document> levelResults = mongoTemplate.aggregate(byLevel, LegalCase.class, Document.class);
		Map<CaseCategory, Map<CourtLevel, Long>> raw = new EnumMap<>(CaseCategory.class);
		for (Document d : levelResults.getMappedResults()) {
			Document id = (Document) d.get("_id");
			CaseCategory cat = parseEnum(CaseCategory.class, id.getString("category"), CaseCategory.OTHER);
			CourtLevel level = parseEnum(CourtLevel.class, id.getString("courtLevel"), null);
			long count = ((Number) d.get("count")).longValue();
			CategorySummary s = byCategory.get(cat);
			s.setTotalCases(s.getTotalCases() + count);
			if (level != null) {
				Map<CourtLevel, Long> m = raw.computeIfAbsent(cat, k -> new EnumMap<>(CourtLevel.class));
				m.merge(level, count, Long::sum);
			}
		}
		for (Map.Entry<CaseCategory, Map<CourtLevel, Long>> e : raw.entrySet()) {
			byCategory.get(e.getKey()).setCasesByCourtLevel(sortByRank(e.getValue()));
		}

		Aggregation statutes = Aggregation.newAggregation(
				Aggregation.match(Criteria.where("relation").is(RelationType.INVOKES.name())),
				Aggregation.group("category", "target").count().as("count"),
				Aggregation.sort(Sort.Direction.DESC, "count"));
		AggregationResults<Document> statuteResults = mongoTemplate.aggregate(statutes, KgEdge.class, Document.class);
		for (Document d : statuteResults.getMappedResults()) {
			Document id = (Document) d.get("_id");
			CaseCategory cat = parseEnum(CaseCategory.class, id.getString("category"), CaseCategory.OTHER);
			Map<String, Long> top = byCategory.get(cat).getTopStatutes();
			if (top.size() < 10) {
				top.put(NodeType.identifierOf(id.getString("target")), ((Number) d.get("count")).longValue());
			}
		}
		return new ArrayList<>(byCategory.values());
	}

	public Page<LegalCase> casesInCategory(CaseCategory category, CourtLevel level, int page, int size) {
		Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
				Sort.by(Sort.Direction.DESC, "decisionDate"));
		return level == null ? caseRepository.findByCategory(category, pageable)
				: caseRepository.findByCategoryAndCourtLevel(category, level, pageable);
	}

	/**
	 * Subgraph of one category: its most recent cases (optionally at one court
	 * level) together with their courts, statutes, judges, appeal and precedent links.
	 */
	public GraphView categoryGraph(CaseCategory category, CourtLevel level, int limit) {
		List<LegalCase> cases = casesInCategory(category, level, 0, limit).getContent();
		Set<String> nodes = new LinkedHashSet<>();
		nodes.add(NodeType.CATEGORY.key(category.name()));
		List<String> caseKeys = new ArrayList<>();
		for (LegalCase c : cases) {
			caseKeys.add(NodeType.CASE.key(c.getId()));
		}
		nodes.addAll(caseKeys);
		List<KgEdge> edges = edgeRepository.findBySourceIn(caseKeys);
		for (KgEdge e : edges) {
			nodes.add(e.getTarget());
		}
		return toView(nodes, edges);
	}

	// ------------------------------------------------------------- courts

	public List<Court> courts() {
		return courtRepository.findAll(Sort.by("code"));
	}

	/** Court hierarchy as a forest rooted at the forums with no appellate court (the High Court benches etc). */
	public List<CourtTreeNode> courtHierarchy() {
		List<Court> all = courtRepository.findAll();
		Map<String, CourtTreeNode> byCode = new HashMap<>();
		for (Court c : all) {
			byCode.put(c.getCode(), new CourtTreeNode(c));
		}
		List<CourtTreeNode> roots = new ArrayList<>();
		for (CourtTreeNode node : byCode.values()) {
			CourtTreeNode parent = node.getCourt().getAppellateCourtCode() == null ? null
					: byCode.get(node.getCourt().getAppellateCourtCode());
			if (parent == null) {
				roots.add(node);
			} else {
				parent.getSubordinates().add(node);
			}
		}
		Comparator<CourtTreeNode> order = Comparator
				.comparing((CourtTreeNode n) -> -n.getCourt().getLevel().getRank())
				.thenComparing(n -> n.getCourt().getCode());
		sortTree(roots, order);
		return roots;
	}

	private static void sortTree(List<CourtTreeNode> nodes, Comparator<CourtTreeNode> order) {
		nodes.sort(order);
		for (CourtTreeNode n : nodes) {
			sortTree(n.getSubordinates(), order);
		}
	}

	// -------------------------------------------------------------- helpers

	private GraphView toView(Collection<String> nodeKeys, Collection<KgEdge> edges) {
		Map<NodeType, Set<String>> idsByType = new EnumMap<>(NodeType.class);
		for (String key : nodeKeys) {
			idsByType.computeIfAbsent(NodeType.ofKey(key), k -> new LinkedHashSet<>()).add(NodeType.identifierOf(key));
		}
		Map<String, LegalCase> cases = new HashMap<>();
		if (idsByType.containsKey(NodeType.CASE)) {
			for (LegalCase c : caseRepository.findByIdIn(idsByType.get(NodeType.CASE))) {
				cases.put(c.getId(), c);
			}
		}
		Map<String, Court> courts = new HashMap<>();
		if (idsByType.containsKey(NodeType.COURT)) {
			for (Court c : courtRepository.findByCodeIn(idsByType.get(NodeType.COURT))) {
				courts.put(c.getCode(), c);
			}
		}

		GraphView view = new GraphView();
		for (String key : nodeKeys) {
			NodeType type = NodeType.ofKey(key);
			String id = NodeType.identifierOf(key);
			GraphView.Node node = new GraphView.Node(key, type, id);
			if (type == NodeType.CASE) {
				LegalCase c = cases.get(id);
				if (c != null) {
					node.setLabel(c.getTitle() != null ? c.getTitle() : c.getCaseNumber());
					node.setCategory(c.getCategory());
					node.getProperties().put("caseNumber", c.getCaseNumber());
					node.getProperties().put("courtCode", c.getCourtCode());
					node.getProperties().put("courtLevel", c.getCourtLevel());
					node.getProperties().put("subCategory", c.getSubCategory());
					node.getProperties().put("decisionDate", c.getDecisionDate());
					node.getProperties().put("outcome", c.getOutcome());
				}
			} else if (type == NodeType.COURT) {
				Court c = courts.get(id);
				if (c != null) {
					node.setLabel(c.getName());
					node.getProperties().put("level", c.getLevel());
					node.getProperties().put("district", c.getDistrict());
				}
			} else if (type == NodeType.CATEGORY) {
				CaseCategory cat = parseEnum(CaseCategory.class, id, CaseCategory.OTHER);
				node.setLabel(cat.getDisplayName());
				node.setCategory(cat);
			} else if (type == NodeType.STATUTE) {
				node.setLabel(id.replace('_', ' ').replace(":", " s. "));
			} else {
				node.setLabel(toTitleCase(id));
			}
			view.getNodes().add(node);
		}
		for (KgEdge e : edges) {
			view.getEdges().add(new GraphView.Edge(e.getSource(), e.getTarget(), e.getRelation()));
		}
		return view;
	}

	private static Map<CourtLevel, Long> sortByRank(Map<CourtLevel, Long> counts) {
		List<CourtLevel> levels = new ArrayList<>(counts.keySet());
		levels.sort(Comparator.comparingInt((CourtLevel l) -> -l.getRank()).thenComparing(Enum::name));
		Map<CourtLevel, Long> sorted = new LinkedHashMap<>();
		for (CourtLevel l : levels) {
			sorted.put(l, counts.get(l));
		}
		return sorted;
	}

	static String normaliseName(String name) {
		return name.trim().toLowerCase(Locale.ROOT)
				.replaceAll("^((hon'?ble|mr\\.?|mrs\\.?|ms\\.?|dr\\.?|justice|shri|smt\\.?)\\s+)+", "")
				.replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
	}

	private static String toTitleCase(String id) {
		StringBuilder sb = new StringBuilder();
		for (String part : id.split("_")) {
			if (!part.isEmpty()) {
				sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
			}
		}
		return sb.toString().trim();
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
		if (value == null) {
			return fallback;
		}
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException ex) {
			return fallback;
		}
	}

	private static <T> List<T> safe(List<T> list) {
		return list == null ? Collections.<T>emptyList() : list;
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> out = new ArrayList<>(safe(a));
		out.addAll(safe(b));
		return out;
	}
}
