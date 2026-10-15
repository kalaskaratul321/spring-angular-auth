package com.strikready.sandbox.services.kg.importer;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.strikready.sandbox.models.kg.CaseReference;
import com.strikready.sandbox.models.kg.LegalCase;
import com.strikready.sandbox.models.kg.LegalIssue;
import com.strikready.sandbox.models.kg.NodeType;
import com.strikready.sandbox.models.kg.RelationType;
import com.strikready.sandbox.models.kg.StatuteReference;
import com.strikready.sandbox.models.kg.Verdict;
import com.strikready.sandbox.models.kg.VerdictResult;
import org.springframework.stereotype.Component;

/**
 * Heuristic extraction of knowledge-graph fields from the text of an Indian
 * judgment or order (Bombay High Court, district courts, MahaRERA and similar):
 * case number and type, CNR, parties, coram, decision date, statutes, issues,
 * verdict, the judgment's own neutral citation and the judgments it cites.
 *
 * It only fills what it can recognise; anything supplied by the caller wins
 * (see JudgmentImportService). The judgment text itself is not retained.
 */
@Component
public class JudgmentParser {

	private static final int HEADER_CHARS = 4000;
	private static final int OPERATIVE_CHARS = 4000;
	private static final int MAX_ISSUES = 6;
	private static final int MAX_FIELD = 300;

	private static final Pattern CNR = Pattern.compile("\\b([A-Z]{4}\\d{2})-?(\\d{6})-?((?:19|20)\\d{2})\\b");
	private static final Pattern CASE_NO = Pattern.compile(
			"(?im)^[\\s\\p{Punct}]*([A-Z][A-Z .()/&-]{2,80}?)\\s+NO\\s*\\.?\\s*:?\\s*([A-Z]{0,4}\\s*\\d{1,7})\\s*(?:OF|/)\\s*((?:19|20)\\d{2})\\b");
	private static final Pattern RERA_COMPLAINT_NO = Pattern.compile("(?i)\\bCOMPLAINT\\s+NO\\s*\\.?\\s*:?\\s*(CC\\d{9,})");
	private static final Pattern VERSUS_LINE = Pattern.compile("(?i)^\\s*(?:V/?S\\.?|VS\\.?|VERSUS|V\\.)\\s*$");
	private static final Pattern VERSUS_INLINE = Pattern.compile("(?i)^(.{3,150}?)\\s+(?:V/?S\\.?|VS\\.?|VERSUS)\\s+(.{3,150})$");
	private static final Pattern PARTY_ROLE = Pattern.compile(
			"(?i)\\s*(?:\\.{2,}|…|:)?\\s*(?:\\(?ORIGINAL\\s+)?(?:PETITIONERS?|APPELLANTS?|APPLICANTS?|COMPLAINANTS?|PLAINTIFFS?|RESPONDENTS?|ACCUSED|OPPONENTS?|DEFENDANTS?)\\b.*$");
	private static final Pattern PARTY_NOISE = Pattern.compile(
			"(?i)^(?:1\\.|1\\)|\\(1\\))?\\s*|,?\\s*(?:AGE[D]?|AGED ABOUT|OCC(?:UPATION)?|R/O|RESIDING|THROUGH)\\b.*$");
	private static final Pattern CORAM = Pattern.compile("(?is)\\bCORAM\\s*:?\\s*(.{3,400}?)(?:\\n\\s*\\n|\\bDATED?\\b|\\bRESERVED\\b|\\bPRONOUNCED\\b|\\bJUDGMENT\\b|\\bORDER\\b)");
	private static final Pattern JUDGE_SUFFIX = Pattern.compile(",?\\s*\\b(?:C\\.\\s*)?J{1,2}\\s*\\.");
	private static final Pattern JUDGE_SEPARATOR = Pattern.compile("\\n|\\s+AND\\s+|\\s+and\\s+|&|;");
	private static final Pattern JUDGE_NAME = Pattern.compile("[A-Z][A-Za-z.' ()-]{2,60}");
	private static final Pattern MEMBER = Pattern.compile("(?i)(?:CHAIRPERSON|MEMBER|PRESIDING OFFICER)\\s*[:\\-]?\\s*((?:SHRI|SMT|DR|MR|MS)\\.?\\s+[A-Z][A-Za-z. ]{3,60})");
	private static final Pattern DATE_LABELLED = Pattern.compile(
			"(?i)(?:PRONOUNCED\\s+ON|DATE\\s+OF\\s+(?:THE\\s+)?(?:JUDGMENT|ORDER|PRONOUNCEMENT|DECISION)|JUDGMENT\\s+(?:PRONOUNCED\\s+)?ON|ORDER\\s+(?:PRONOUNCED\\s+)?ON|DECIDED\\s+ON|DATED?)\\s*:?\\s*([0-9]{1,2}(?:ST|ND|RD|TH)?[\\s./,-]+(?:[0-9]{1,2}|[A-Z]{3,9})[\\s./,-]+(?:19|20)[0-9]{2})");
	private static final Pattern DATE_PARTS = Pattern.compile("(?i)([0-9]{1,2})(?:ST|ND|RD|TH)?[\\s./,-]+([0-9]{1,2}|[A-Z]{3,9})[\\s./,-]+((?:19|20)[0-9]{2})");

	private static final String SECTION_LIST = "(\\d{1,4}[A-Z]{0,2}(?:\\s*\\(\\s*[0-9a-z]{1,3}\\s*\\))*(?:\\s*(?:,|&|/|AND|R/W|READ\\s+WITH|TO)\\s*\\d{1,4}[A-Z]{0,2}(?:\\s*\\(\\s*[0-9a-z]{1,3}\\s*\\))*)*)";
	private static final String ACT_ABBR = "(CODE\\s+OF\\s+CRIMINAL\\s+PROCEDURE|CODE\\s+OF\\s+CIVIL\\s+PROCEDURE|I\\.?\\s?P\\.?\\s?C\\.?|CR\\.?\\s?P\\.?\\s?C\\.?|C\\.?\\s?P\\.?\\s?C\\.?|B\\.?N\\.?S\\.?S\\.?|B\\.?N\\.?S\\.?|B\\.?S\\.?A\\.?|N\\.?\\s?I\\.?\\s?ACT|NDPS\\s+ACT|N\\.D\\.P\\.S\\.\\s+ACT|RERA|M\\.?L\\.?R\\.?C\\.?|M\\.?T\\.?A\\.?L\\.?\\s+ACT|MOFA|HMA|POCSO\\s+ACT|MCOCA|UAPA|D\\.?V\\.?\\s+ACT|THE\\s+CONSTITUTION(?:\\s+OF\\s+INDIA)?|CONSTITUTION(?:\\s+OF\\s+INDIA)?)";
	private static final String ACT_NAMED = "((?:THE\\s+)?[A-Z][A-Za-z().,&'\\s-]{2,120}?\\b(?:ACT|CODE|SANHITA|ADHINIYAM|RULES|REGULATIONS))(?:\\s*,?\\s*((?:18|19|20)\\d{2}))?";
	private static final Pattern STATUTE_ABBR = Pattern.compile(
			"(?i)\\b(?:SECTIONS?|SECS?\\.?|SS?\\.|U/SS?\\.?|ARTICLES?|ART\\.)\\s*" + SECTION_LIST + "\\s+(?:OF\\s+(?:THE\\s+)?)?" + ACT_ABBR + "(?![A-Za-z])");
	private static final Pattern STATUTE_NAMED = Pattern.compile(
			"(?i)\\b(?:SECTIONS?|SECS?\\.?|SS?\\.|U/SS?\\.?|RULES?)\\s*" + SECTION_LIST + "\\s+OF\\s+" + ACT_NAMED);
	private static final Pattern SECTION_TOKEN = Pattern.compile("(?i)\\d{1,4}[A-Z]{0,2}(?=\\s*\\(|\\b|$)");

	private static final Pattern NEUTRAL_CITATION = Pattern.compile("\\b((?:19|20)\\d{2}\\s*:\\s*[A-Z]{2,5}(?:-[A-Z]{1,6})?\\s*:\\s*\\d{1,6}(?:-[A-Z]{2,4})?)\\b");
	private static final Pattern[] CITATIONS = {
			Pattern.compile("\\(\\s*(?:19|20)\\d{2}\\s*\\)\\s*\\d{1,3}\\s*S\\.?\\s?C\\.?\\s?C\\.?\\s*\\d{1,5}"),
			Pattern.compile("\\b(?:19|20)\\d{2}\\s*\\(\\s*\\d{1,3}\\s*\\)\\s*S\\.?\\s?C\\.?\\s?C\\.?\\s*\\d{1,5}"),
			Pattern.compile("\\b(?:19|20)\\d{2}\\s+SCC\\s+OnLine\\s+[A-Z][A-Za-z]{1,6}\\s+\\d{1,6}"),
			Pattern.compile("\\bAIR\\s+(?:19|20)\\d{2}\\s+(?:SC|Bom|Bombay|Del|Mad|Cal|All|Ker|Kar|Guj|AP|MP|Raj|P&H|Pat|Ori|Gau|Nag|NOC)\\s+\\d{1,5}"),
			Pattern.compile("\\[\\s*(?:19|20)\\d{2}\\s*\\]\\s*\\d{1,3}\\s*S\\.?C\\.?R\\.?\\s*\\d{1,5}"),
			Pattern.compile("\\b(?:19|20)\\d{2}\\s+INSC\\s+\\d{1,5}"),
			Pattern.compile("\\(?\\b(?:19|20)\\d{2}\\)?\\s*\\(?\\s*\\d{1,2}\\s*\\)?\\s*Mh\\.?\\s?L\\.?\\s?J\\.?\\s*(?:\\(Cri\\)\\s*)?\\d{1,5}"),
			Pattern.compile("\\b(?:19|20)\\d{2}\\s*(?:\\(\\s*\\d{1,2}\\s*\\)\\s*)?Bom\\.?\\s?C\\.?\\s?R\\.?\\s*(?:\\(Cri\\.?\\)\\s*)?\\d{1,5}"),
			Pattern.compile("\\b(?:19|20)\\d{2}\\s*:\\s*BHC\\s*-\\s*[A-Z]{2,6}\\s*:\\s*\\d{1,6}(?:-[A-Z]{2,4})?") };
	private static final String PARTY_WORD = "(?:[A-Z][\\w.&'()/-]*|of|and|the|for|&)";
	private static final String PARTY_NAME = "[A-Z][\\w.&'()/-]*(?:\\s+" + PARTY_WORD + "){0,8}?";
	private static final Pattern CASE_NAME_BEFORE = Pattern.compile("(" + PARTY_NAME
			+ "\\s+(?:v\\.|vs\\.?|versus|V/s\\.?)\\s+" + PARTY_NAME
			+ ")\\s*[,(]?\\s*(?:reported\\s+(?:in|at)\\s*)?[\\[(]?\\s*$");

	private static final Pattern SENTENCE_BREAK = Pattern.compile(
			"(?<!\\b(?:v|vs|V|Vs|No|Nos|Mr|Ms|Mrs|Dr|Smt|Shri|Ltd|Pvt|Co|[A-Z]))\\.\\s+(?=[A-Z(\\[])|\\n\\s*\\d{1,3}\\.\\s");
	private static final Pattern WHETHER = Pattern.compile("(?s)(?:^|[\\n.:;)]\\s*)((?:\\(?[ivx0-9]{1,4}[.)]\\s*)?Whether\\b[^?\\n]{15,400}(?:\\?|\\n))");

	private static final Map<String, Integer> MONTHS = new HashMap<>();

	static {
		String[] names = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
		for (int i = 0; i < names.length; i++) {
			MONTHS.put(names[i], i + 1);
		}
	}

	public LegalCase parse(String rawText) {
		String text = rawText.replace(' ', ' ').replace("\r", "");
		String header = text.substring(0, Math.min(text.length(), HEADER_CHARS));
		LegalCase c = new LegalCase();

		Matcher cnr = CNR.matcher(header);
		if (cnr.find()) {
			c.setCnr(cnr.group(1) + cnr.group(2) + cnr.group(3));
		}

		c.setCourtCode(inferCourt(header));
		int caseLineEnd = parseCaseNumber(header, c);
		parseParties(header, caseLineEnd, c);
		parseJudges(header, c);
		c.setDecisionDate(parseDecisionDate(header, text));
		c.setStatutes(parseStatutes(text));
		c.setIssues(parseIssues(text));
		c.setVerdict(parseVerdict(text));
		parseCitations(text, header, c);
		return c;
	}

	// ---------------------------------------------------------------- header

	/** Court code from the cause title, for forums whose code is unambiguous. */
	static String inferCourt(String header) {
		String h = header.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (h.contains("HIGH COURT OF JUDICATURE AT BOMBAY") || h.contains("HIGH COURT OF BOMBAY")
				|| h.contains("BOMBAY HIGH COURT")) {
			if (h.contains("NAGPUR BENCH") || h.contains("BENCH AT NAGPUR")) {
				return "BHC-NAG";
			}
			if (h.contains("AURANGABAD BENCH") || h.contains("BENCH AT AURANGABAD")
					|| h.contains("CHHATRAPATI SAMBHAJINAGAR")) {
				return "BHC-AUR";
			}
			if (h.contains("KOLHAPUR")) {
				return "BHC-KOP";
			}
			return "BHC-MUM";
		}
		if (h.contains("REAL ESTATE APPELLATE TRIBUNAL")) {
			return "MREAT";
		}
		if (h.contains("MAHARASHTRA REAL ESTATE REGULATORY AUTHORITY") || h.contains("MAHARERA")) {
			return "MAHARERA";
		}
		if (h.contains("MAHARASHTRA REVENUE TRIBUNAL")) {
			return "MRT-PUNE";
		}
		if (h.contains("MAHARASHTRA ADMINISTRATIVE TRIBUNAL")) {
			return "MAT-MUM";
		}
		if (h.contains("CITY CIVIL") && h.contains("SESSIONS") && (h.contains("MUMBAI") || h.contains("BOMBAY"))) {
			return "CCSC-MUM";
		}
		return null;
	}

	private static int parseCaseNumber(String header, LegalCase c) {
		Matcher m = CASE_NO.matcher(header);
		while (m.find()) {
			String type = m.group(1).replaceAll("\\s+", " ").trim();
			if (type.matches("(?i).*\\b(?:CNR|FIR|C\\.?R|SURVEY|GAT|PLOT|FLAT|ROOM|EXHIBIT|EXH)\\b.*")) {
				continue;
			}
			type = type.replaceAll("^(?i)(?:IN\\s+)?", "");
			c.setCaseType(titleCase(type));
			c.setCaseNumber(titleCase(type) + " No. " + m.group(2).replaceAll("\\s+", "") + " of " + m.group(3));
			c.setFilingYear(Integer.valueOf(m.group(3)));
			return m.end();
		}
		Matcher rera = RERA_COMPLAINT_NO.matcher(header);
		if (rera.find()) {
			c.setCaseType("Complaint");
			c.setCaseNumber(rera.group(1));
			return rera.end();
		}
		return 0;
	}

	private static void parseParties(String header, int from, LegalCase c) {
		String[] lines = header.substring(from).split("\n");
		int limit = Math.min(lines.length, 60);
		for (int i = 0; i < limit; i++) {
			Matcher inline = VERSUS_INLINE.matcher(lines[i].trim());
			if (inline.matches() && !CASE_NO.matcher(lines[i]).find()) {
				addParty(c.getPetitioners(), inline.group(1));
				addParty(c.getRespondents(), inline.group(2));
				break;
			}
			if (VERSUS_LINE.matcher(lines[i]).matches()) {
				addParty(c.getPetitioners(), firstPartyLine(lines, i - 1, -1, Math.max(0, i - 12)));
				addParty(c.getRespondents(), firstPartyLine(lines, i + 1, 1, Math.min(limit - 1, i + 12)));
				break;
			}
		}
		if (!c.getPetitioners().isEmpty() && !c.getRespondents().isEmpty()) {
			c.setTitle(c.getPetitioners().get(0) + " v. " + c.getRespondents().get(0));
		}
	}

	/**
	 * Walks from {@code start} towards {@code stop} and returns the party name
	 * block: for the petitioner side the topmost line of the block above
	 * "Versus", for the respondent side the first non-empty line below it.
	 */
	private static String firstPartyLine(String[] lines, int start, int step, int stop) {
		if (step > 0) {
			for (int i = start; i <= stop; i++) {
				String l = cleanParty(lines[i]);
				if (!l.isEmpty()) {
					return l;
				}
			}
			return null;
		}
		String candidate = null;
		for (int i = start; i >= stop; i--) {
			String raw = lines[i].trim();
			if (raw.isEmpty() && candidate != null) {
				break;
			}
			if (CASE_NO.matcher(raw).find() || raw.matches("(?i).*\\b(?:IN THE|JURISDICTION|BENCH|COURT)\\b.*")) {
				break;
			}
			String l = cleanParty(raw);
			if (!l.isEmpty()) {
				candidate = l;
			}
		}
		return candidate;
	}

	private static String cleanParty(String line) {
		String l = PARTY_ROLE.matcher(line.trim()).replaceAll("");
		l = PARTY_NOISE.matcher(l).replaceAll("").trim();
		l = l.replaceAll("[.,;:…]+$", "").trim();
		if (l.length() < 3 || l.matches("(?i)(?:and|ors?\\.?|others|anr\\.?)")) {
			return "";
		}
		return l.length() > 150 ? l.substring(0, 150).trim() : l;
	}

	private static void addParty(List<String> parties, String name) {
		if (name != null) {
			String clean = cleanParty(name);
			if (!clean.isEmpty()) {
				parties.add(clean);
			}
		}
	}

	private static void parseJudges(String header, LegalCase c) {
		Set<String> judges = new LinkedHashSet<>();
		Matcher coram = CORAM.matcher(header);
		if (coram.find()) {
			// "A. B. X, J." / "A. B. X AND C. D. Y, JJ." / "A. B. X, C.J. & C. D. Y, J."
			String block = JUDGE_SUFFIX.matcher(coram.group(1)).replaceAll("\n");
			for (String part : JUDGE_SEPARATOR.split(block)) {
				String name = part.replaceAll("(?i)^\\s*(?:THE\\s+)?(?:HON'?BLE\\s+)?", "")
						.replaceAll("[\\s,;:.]+$", "").replaceAll("\\s+", " ").trim();
				if (JUDGE_NAME.matcher(name).matches() && !name.matches("(?i).*\\b(?:ADVOCATE|COUNSEL|FOR THE|APP)\\b.*")) {
					judges.add(name);
				}
			}
		}
		if (judges.isEmpty()) {
			Matcher m = MEMBER.matcher(header);
			while (m.find()) {
				judges.add(m.group(1).replaceAll("\\s+", " ").trim());
			}
		}
		c.setJudges(new ArrayList<>(judges));
	}

	static LocalDate parseDecisionDate(String header, String text) {
		Matcher m = DATE_LABELLED.matcher(header);
		if (m.find()) {
			LocalDate d = toDate(m.group(1));
			if (d != null) {
				return d;
			}
		}
		// Orders usually end with the date of pronouncement.
		String tail = text.substring(Math.max(0, text.length() - 1500));
		Matcher t = DATE_LABELLED.matcher(tail);
		LocalDate last = null;
		while (t.find()) {
			LocalDate d = toDate(t.group(1));
			if (d != null) {
				last = d;
			}
		}
		return last;
	}

	private static LocalDate toDate(String s) {
		Matcher p = DATE_PARTS.matcher(s);
		if (!p.find()) {
			return null;
		}
		try {
			int day = Integer.parseInt(p.group(1));
			String mon = p.group(2).toUpperCase(Locale.ROOT);
			Integer month = Character.isDigit(mon.charAt(0)) ? Integer.valueOf(mon)
					: MONTHS.get(mon.length() >= 3 ? mon.substring(0, 3) : mon);
			if (month == null) {
				return null;
			}
			return LocalDate.of(Integer.parseInt(p.group(3)), month, day);
		} catch (DateTimeException | NumberFormatException e) {
			return null;
		}
	}

	// -------------------------------------------------------------- statutes

	static List<StatuteReference> parseStatutes(String text) {
		Map<String, StatuteReference> found = new LinkedHashMap<>();
		Matcher abbr = STATUTE_ABBR.matcher(text);
		while (abbr.find()) {
			addSections(found, abbr.group(1), abbr.group(2), null);
		}
		Matcher named = STATUTE_NAMED.matcher(text);
		while (named.find()) {
			String act = named.group(2).replaceAll("\\s+", " ").trim();
			if (act.split(" ").length > 14 || isVagueAct(act)) {
				continue;
			}
			addSections(found, named.group(1), act, named.group(3));
		}
		return new ArrayList<>(found.values());
	}

	/** "the Code", "the said Act", "this Act" refer back to an act named elsewhere. */
	private static boolean isVagueAct(String act) {
		String a = act.toUpperCase(Locale.ROOT).replaceAll("^THE\\s+", "").trim();
		return a.matches("(?:(?:SAID|THIS|ABOVE|AFORESAID|PRINCIPAL|SAME)\\s+)*(?:ACT|CODE|SANHITA|ADHINIYAM|RULES|REGULATIONS)")
				|| a.startsWith("CODE OF CRIMINAL PROCEDURE") || a.startsWith("CODE OF CIVIL PROCEDURE");
	}

	private static void addSections(Map<String, StatuteReference> found, String sectionList, String act, String year) {
		String actName = act.replaceAll("\\s+", " ").trim();
		if (year != null) {
			actName = actName + ", " + year;
		}
		String stripped = sectionList.replaceAll("\\(\\s*[0-9a-z]{1,3}\\s*\\)", " ");
		Matcher s = SECTION_TOKEN.matcher(stripped);
		while (s.find()) {
			StatuteReference ref = new StatuteReference(actName, s.group().toUpperCase(Locale.ROOT));
			String id = ref.identifier();
			if (!found.containsKey(id)) {
				found.put(id, ref);
			}
		}
	}

	// --------------------------------------------------------------- issues

	static List<LegalIssue> parseIssues(String text) {
		List<LegalIssue> issues = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		Matcher m = WHETHER.matcher(text);
		while (m.find() && issues.size() < MAX_ISSUES) {
			String q = m.group(1).replaceAll("^\\(?[ivx0-9]{1,4}[.)]\\s*", "").replaceAll("\\s+", " ").trim();
			if (!q.endsWith("?")) {
				q = q.replaceAll("[.,;:]$", "") + "?";
			}
			if (seen.add(NodeType.slug(q))) {
				issues.add(new LegalIssue(null, truncate(q), null));
			}
		}
		return issues;
	}

	// -------------------------------------------------------------- verdict

	static Verdict parseVerdict(String text) {
		String tail = text.substring(Math.max(0, text.length() - OPERATIVE_CHARS));
		String lower = tail.toLowerCase(Locale.ROOT);
		Object[][] cues = {
				{ VerdictResult.PARTLY_ALLOWED, "(?:partly|partially)\\s+allowed" },
				{ VerdictResult.ACQUITTED, "(?:is|are|stands?|hereby)\\s+acquitted|order\\s+of\\s+acquittal\\s+is\\s+(?:confirmed|maintained)" },
				{ VerdictResult.CONVICTED, "(?:is|are|hereby|stands?)\\s+convicted|conviction\\s+(?:is\\s+)?(?:confirmed|maintained|upheld)" },
				{ VerdictResult.BAIL_GRANTED, "(?:released|enlarged)\\s+on\\s+bail|bail\\s+(?:application\\s+)?is\\s+(?:granted|allowed)" },
				{ VerdictResult.BAIL_REJECTED, "bail\\s+application\\s+(?:is|stands)\\s+(?:rejected|dismissed)" },
				{ VerdictResult.REMANDED, "remanded|remitted\\s+back" },
				{ VerdictResult.DECREED, "suit\\s+is\\s+(?:hereby\\s+)?decreed|decreed\\s+(?:with|in\\s+terms)" },
				{ VerdictResult.WITHDRAWN, "(?:dismissed\\s+as|stands?)\\s+withdrawn" },
				{ VerdictResult.SETTLED, "(?:compromise|settlement)\\s+(?:is\\s+)?(?:recorded|accepted)|disposed\\s+of\\s+in\\s+terms\\s+of\\s+(?:the\\s+)?(?:consent|compromise)" },
				{ VerdictResult.DISMISSED, "(?:is|are|stands?|hereby|accordingly)\\s+dismissed|deserves?\\s+to\\s+be\\s+dismissed" },
				{ VerdictResult.ALLOWED, "(?:is|are|stands?|hereby|accordingly)\\s+allowed|rule\\s+is\\s+made\\s+absolute|complaint\\s+is\\s+allowed" },
				{ VerdictResult.DISPOSED_OF, "(?:is|are|stands?|accordingly)\\s+disposed\\s+of" } };
		for (Object[] cue : cues) {
			Matcher m = Pattern.compile(cue[1].toString()).matcher(lower);
			if (m.find()) {
				return new Verdict((VerdictResult) cue[0], truncate(sentenceAround(tail, m.start())));
			}
		}
		return null;
	}

	private static String sentenceAround(String text, int pos) {
		int start = pos;
		while (start > 0 && ".\n".indexOf(text.charAt(start - 1)) < 0 && pos - start < 250) {
			start--;
		}
		int end = pos;
		while (end < text.length() && text.charAt(end) != '.' && end - pos < 250) {
			end++;
		}
		return text.substring(start, Math.min(text.length(), end + 1)).replaceAll("\\s+", " ").trim();
	}

	// ------------------------------------------------------------ citations

	private static void parseCitations(String text, String header, LegalCase c) {
		Set<String> own = new LinkedHashSet<>();
		Matcher neutral = NEUTRAL_CITATION.matcher(header.substring(0, Math.min(header.length(), 1500)));
		if (neutral.find()) {
			own.add(neutral.group(1).replaceAll("\\s+", ""));
		}
		c.setCitations(new ArrayList<>(own));
		Set<String> ownKeys = new LinkedHashSet<>();
		for (String o : own) {
			ownKeys.add(NodeType.slug(o));
		}

		Map<String, CaseReference> refs = new LinkedHashMap<>();
		for (Pattern p : CITATIONS) {
			Matcher m = p.matcher(text);
			while (m.find()) {
				String citation = m.group().replaceAll("\\s+", " ").trim();
				String key = NodeType.slug(citation);
				if (ownKeys.contains(key) || refs.containsKey(key)) {
					continue;
				}
				String before = sentenceBefore(text.substring(Math.max(0, m.start() - 250), m.start()))
						.replaceAll("\\s+", " ");
				String after = sentenceAfter(text.substring(m.end(), Math.min(text.length(), m.end() + 250)))
						.toLowerCase(Locale.ROOT);
				CaseReference ref = new CaseReference(null, citation, treatment(before.toLowerCase(Locale.ROOT) + " " + after));
				Matcher name = CASE_NAME_BEFORE.matcher(before);
				if (name.find()) {
					ref.setTitle(truncate(name.group(1).trim()));
				}
				refs.put(key, ref);
			}
		}
		c.setReferences(new ArrayList<>(refs.values()));
	}

	/** The part of {@code s} after the last sentence break ("v." and initials are not breaks). */
	private static String sentenceBefore(String s) {
		Matcher m = SENTENCE_BREAK.matcher(s);
		int start = 0;
		while (m.find()) {
			start = m.start() + 1;
		}
		return s.substring(start);
	}

	/** The part of {@code s} up to the first sentence break. */
	private static String sentenceAfter(String s) {
		Matcher m = SENTENCE_BREAK.matcher(s);
		return m.find() ? s.substring(0, m.start() + 1) : s;
	}

	private static RelationType treatment(String window) {
		if (window.contains("overrul")) {
			return RelationType.OVERRULES;
		}
		if (window.contains("distinguish") || window.contains("not applicable to the facts")) {
			return RelationType.DISTINGUISHES;
		}
		if (window.contains("followed") || window.contains("following the") || window.contains("relied upon")
				|| window.contains("reliance") || window.contains("squarely")) {
			return RelationType.FOLLOWS;
		}
		return RelationType.CITES;
	}

	// -------------------------------------------------------------- helpers

	private static String truncate(String s) {
		return s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD - 1).trim() + "…" : s;
	}

	private static String titleCase(String s) {
		StringBuilder sb = new StringBuilder();
		for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
			if (w.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(w.length() <= 3 && w.matches("\\(?[a-z]{1,3}\\)?") && !w.matches("of|and|no") ? w.toUpperCase(Locale.ROOT)
					: Character.toUpperCase(w.charAt(0)) + w.substring(1));
		}
		return sb.toString();
	}
}
