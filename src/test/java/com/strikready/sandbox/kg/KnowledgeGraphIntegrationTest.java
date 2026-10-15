package com.strikready.sandbox.kg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strikready.sandbox.models.kg.CaseCategory;
import com.strikready.sandbox.models.kg.CategorySummary;
import com.strikready.sandbox.models.kg.CourtLevel;
import com.strikready.sandbox.models.kg.GraphView;
import com.strikready.sandbox.models.kg.KgEdge;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.NodeType;
import com.strikready.sandbox.models.kg.ReferenceView;
import com.strikready.sandbox.models.kg.RelationType;
import com.strikready.sandbox.models.kg.VerdictResult;
import com.strikready.sandbox.models.kg.harvest.HarvestSource;
import com.strikready.sandbox.models.kg.harvest.HarvestTask;
import com.strikready.sandbox.repositories.kg.LegalCaseRepository;
import com.strikready.sandbox.repositories.kg.harvest.HarvestSourceRepository;
import com.strikready.sandbox.repositories.kg.harvest.HarvestTaskRepository;
import com.strikready.sandbox.services.kg.KnowledgeGraphService;
import com.strikready.sandbox.services.kg.harvest.DiscoveredDocument;
import com.strikready.sandbox.services.kg.harvest.HarvestDiscoveryService;
import com.strikready.sandbox.services.kg.harvest.HarvestQueue;
import com.strikready.sandbox.services.kg.importer.ImportResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Runs the knowledge graph and the harvest queue against a real MongoDB
 * (provided by the CI service container, or a local mongod on :27017), in a
 * separate database so it never touches application data.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = { "spring.data.mongodb.database=kg_integration_test", "kg.harvest.enabled=false" })
public class KnowledgeGraphIntegrationTest {

	private static final String JMFC = "demo-jmfc-ni138";
	private static final String SESSIONS = "demo-sessions-ni138-appeal";
	private static final String HC = "demo-hc-ni138-revision";

	@Autowired
	private KnowledgeGraphService graph;

	@Autowired
	private LegalCaseRepository cases;

	@Autowired
	private HarvestQueue queue;

	@Autowired
	private HarvestDiscoveryService discovery;

	@Autowired
	private HarvestSourceRepository sources;

	@Autowired
	private HarvestTaskRepository tasks;

	@Autowired
	private MongoTemplate mongo;

	@Autowired
	private ObjectMapper objectMapper;

	@Before
	public void loadSampleCases() throws Exception {
		// remove() keeps the indexes (incl. the unique ones) that drop() would delete.
		mongo.remove(new Query(), LegalCase.class);
		mongo.remove(new Query(), KgEdge.class);
		mongo.remove(new Query(), HarvestTask.class);
		mongo.remove(Query.query(Criteria.where("name").regex("^it-")), HarvestSource.class);

		List<LegalCase> sample;
		try (InputStream in = new ClassPathResource("kg/sample-cases.json").getInputStream()) {
			sample = objectMapper.readValue(in, new TypeReference<List<LegalCase>>() {
			});
		}
		graph.ingestAll(sample);
	}

	// ------------------------------------------------------- knowledge graph

	@Test
	public void classifiesAndPlacesSampleCasesInTheHierarchy() {
		LegalCase hc = cases.findById(HC).get();
		assertEquals(CaseCategory.CRIMINAL, hc.getCategory());
		assertEquals(CourtLevel.HIGH_COURT, hc.getCourtLevel());
		assertEquals(CaseCategory.RERA, cases.findById("demo-maharera-possession").get().getCategory());
		assertEquals(CaseCategory.LAND, cases.findById("demo-tahsildar-mutation").get().getCategory());
		assertEquals(Collections.singletonList("2025_scc_online_bom_9999_illustrative"), hc.getCitationKeys());
	}

	@Test
	public void appealChainRunsFromTrialCourtToHighCourt() {
		List<String> ids = new ArrayList<>();
		for (LegalCase c : graph.appealChain(SESSIONS)) {
			ids.add(c.getId());
		}
		assertEquals(Arrays.asList(JMFC, SESSIONS, HC), ids);
	}

	@Test
	public void categorySummaryGroupsByCourtLevelVerdictStatuteAndIssue() {
		CategorySummary criminal = null;
		for (CategorySummary s : graph.categorySummaries()) {
			if (s.getCategory() == CaseCategory.CRIMINAL) {
				criminal = s;
			}
		}
		assertNotNull(criminal);
		assertEquals(3, criminal.getTotalCases());
		assertEquals(Long.valueOf(1), criminal.getCasesByCourtLevel().get(CourtLevel.HIGH_COURT));
		assertEquals(CourtLevel.HIGH_COURT, criminal.getCasesByCourtLevel().keySet().iterator().next());
		assertEquals(Long.valueOf(1), criminal.getCasesByVerdict().get(VerdictResult.PARTLY_ALLOWED));
		assertEquals(Long.valueOf(3), criminal.getTopStatutes().get("NI_ACT:138"));
		assertEquals(Long.valueOf(2), criminal.getTopIssues().get("cheque_dishonour_legally_enforceable_debt"));
	}

	@Test
	public void sharedCitationConnectsCasesAndMovesToTheCaseWhenItArrives() {
		String citationNode = NodeType.CITATION.key("2019_4_scc_197");
		GraphView around = graph.neighbourhood(citationNode, 1, null);
		assertEquals(3, caseNodes(around).size());

		LegalCase birSingh = new LegalCase();
		birSingh.setId("demo-bir-singh");
		birSingh.setTitle("Bir Singh v. Mukesh Kumar");
		birSingh.setCategory(CaseCategory.CRIMINAL);
		birSingh.setCitations(Collections.singletonList("2019 (4) SCC 197"));
		graph.ingest(birSingh);

		assertTrue(mongo.find(Query.query(Criteria.where("target").is(citationNode)), KgEdge.class).isEmpty());
		List<ReferenceView> citedBy = graph.citedBy("demo-bir-singh");
		assertEquals(3, citedBy.size());
		boolean follows = false;
		for (ReferenceView r : citedBy) {
			follows |= r.getTreatment() == RelationType.FOLLOWS && JMFC.equals(r.getCaseId());
		}
		assertTrue(follows);
		assertEquals("demo-bir-singh", graph.references(HC).get(0).getCaseId());
	}

	@Test
	public void searchFindsCasesByTitleAndFiltersByVerdict() {
		assertEquals(3, graph.search("Kulkarni", null, null, 0, 20).getTotalElements());
		assertEquals(1, graph.search("Kulkarni", null, VerdictResult.DISMISSED, 0, 20).getTotalElements());
		assertEquals(1, graph.search("sentencing", CaseCategory.CRIMINAL, null, 0, 20).getTotalElements());
	}

	@Test
	public void reingestingTheSameCaseReplacesItsEdges() {
		LegalCase hc = cases.findById(HC).get();
		long before = mongo.count(Query.query(Criteria.where("source").is(NodeType.CASE.key(HC))), KgEdge.class);
		graph.ingest(hc);
		long after = mongo.count(Query.query(Criteria.where("source").is(NodeType.CASE.key(HC))), KgEdge.class);
		assertEquals(before, after);
	}

	// ---------------------------------------------------------- harvest queue

	@Test
	public void queueDeduplicatesClaimsRetriesAndCompletes() {
		DiscoveredDocument doc = new DiscoveredDocument("https://bombayhighcourt.nic.in/data/it/a.pdf", "A");
		assertTrue(queue.enqueue("it-source", doc, null));
		assertFalse(queue.enqueue("it-source",
				new DiscoveredDocument("https://BOMBAYHIGHCOURT.nic.in:443/data/./it/a.pdf#p2", "A again"), null));
		assertEquals(1, tasks.count());

		HarvestTask claimed = queue.claim();
		assertNotNull(claimed);
		assertEquals(HarvestTask.Status.IN_PROGRESS, claimed.getStatus());
		assertEquals(1, claimed.getAttempts());
		assertEquals(HarvestQueue.INSTANCE_ID, claimed.getLeaseOwner());
		assertNull("nothing else is runnable", queue.claim());

		assertEquals(HarvestTask.Status.RETRY, queue.fail(claimed, "HTTP 503", true));
		assertNull("retry waits for its backoff", queue.claim());

		assertTrue(queue.retry(claimed.getId()));
		HarvestTask again = queue.claim();
		assertEquals(claimed.getId(), again.getId());
		assertEquals(1, again.getAttempts());

		ImportResult ok = new ImportResult();
		ok.setStatus(ImportResult.Status.CREATED);
		queue.complete(again, ok);
		Map<HarvestTask.Status, Long> counts = queue.countsByStatus("it-source");
		assertEquals(Long.valueOf(1), counts.get(HarvestTask.Status.DONE));
	}

	@Test
	public void permanentFailureGoesToDeadAndExpiredLeaseIsReclaimed() {
		queue.enqueue("it-source", new DiscoveredDocument("https://x.gov.in/it/dead.pdf", null), null);
		HarvestTask t = queue.claim();
		assertEquals(HarvestTask.Status.DEAD, queue.fail(t, "HTTP 404", false));
		assertEquals(1, queue.retryDead("it-source"));

		HarvestTask leased = queue.claim();
		// Simulate a worker that died while holding the lease.
		mongo.updateFirst(Query.query(Criteria.where("_id").is(leased.getId())),
				new Update().set("leaseUntil", Instant.now().minusSeconds(60)).set("leaseOwner", "dead-worker"),
				HarvestTask.class);
		HarvestTask reclaimed = queue.claim();
		assertEquals(leased.getId(), reclaimed.getId());
		assertEquals(HarvestQueue.INSTANCE_ID, reclaimed.getLeaseOwner());
		assertEquals(2, reclaimed.getAttempts());
	}

	@Test
	public void discoveryRespectsAnotherInstancesLeaseAndRecordsProgress() {
		HarvestSource s = new HarvestSource();
		s.setName("it-url-list");
		s.setType(HarvestSource.Type.URL_LIST);
		s.setUrls(Arrays.asList("https://x.gov.in/it/1.pdf", "https://x.gov.in/it/2.pdf", "https://x.gov.in/it/1.pdf"));
		s.setLeaseOwner("other-instance");
		s.setLeaseUntil(Instant.now().plusSeconds(600));
		s = sources.save(s);

		assertEquals("leased elsewhere: skipped", 0, discovery.discover(s.getId(), true));
		assertEquals(0, tasks.count());

		mongo.updateFirst(Query.query(Criteria.where("_id").is(s.getId())),
				new Update().unset("leaseOwner").unset("leaseUntil"), HarvestSource.class);
		assertEquals(2, discovery.discover(s.getId(), true));

		HarvestSource after = sources.findById(s.getId()).get();
		assertTrue(after.isUrlListDone());
		assertNull(after.getLeaseOwner());
		assertNull(after.getLastError());
		assertEquals(3, after.getDiscoveredCount());
		assertEquals(2, after.getEnqueuedCount());
		assertEquals("list already done", 0, discovery.discover(s.getId(), true));
	}

	private static List<String> caseNodes(GraphView view) {
		List<String> ids = new ArrayList<>();
		for (GraphView.Node n : view.getNodes()) {
			if (n.getType() == NodeType.CASE) {
				ids.add(n.getId());
			}
		}
		return ids;
	}
}
