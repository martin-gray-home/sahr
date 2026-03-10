# SAHR North-Star Architecture

SAHR is a **symbolic transformer-style reasoning system** built on top of OWL ontologies.

The goal is to combine:

• formal semantic knowledge (OWL)  
• symbolic working memory  
• transformer-like multi-head reasoning  

to produce a deterministic and explainable reasoning engine.

---

# Core Principles

## 1. OWL is the Semantic Foundation

All concepts, properties, and semantic relationships are defined in OWL ontologies.

SAHR does not invent concept schemas.

Instead it **uses and extends OWL semantics**.

OWL provides:

• concept hierarchy  
• property semantics  
• constraints  
• logical relationships  
• external knowledge modules  

SAHR queries the ontology dynamically during reasoning.

---

## 2. Runtime Symbolic Graph = Working Memory

The system maintains a **runtime symbolic graph** representing the current state of knowledge.

This graph stores:

• entities  
• events  
• relations  
• discourse state  
• workflow state  
• tool results  

The graph is separate from the ontology.

Ontology = semantic knowledge  
Graph = runtime working memory

---

## 3. Reasoning Uses Generic Attention Head Families

SAHR performs reasoning using **generic attention heads**.

Heads are not domain-specific rules.

They are **reasoning operators** that work with ontology semantics and
apply **commonsense propagation** patterns where appropriate.

Examples of head families:

• Graph Retrieval Heads  
• Relation Query Heads  
• Ontology Reasoning Heads  
• Relation Propagation Heads  
• Query Alignment Heads  
• Action Selection Heads  
• Validation Heads  

Ontology structure determines which reasoning patterns are applied.
Propagation heads can also apply **relation-class patterns** (for example,
co-location transfer) that are configured generically and not hardcoded
to specific domains.

---

## 4. Heads Follow Transformer-Style Reasoning

Each head follows a symbolic analogue of transformer attention:

Query  
= reasoning goal

Key  
= candidate symbolic relation or action

Value  
= inferred relation, answer, or action

Attention score  
= semantic compatibility score

Heads generate **ranked candidate outputs**.

---

## 5. Parallel Multi-Head Reasoning

Multiple heads operate in parallel.

Each head proposes candidate interpretations.

Candidates compete through scoring and ranking.

This produces:

• multiple reasoning hypotheses  
• explainable candidate competition  
• graceful handling of ambiguity  

---

## 6. Candidate Ranking Drives Decisions

All head outputs are merged and ranked.

The highest-scoring candidate determines the next system step.

Candidates may represent:

• answers  
• inferred relations  
• graph updates  
• tool actions  
• clarification requests  

---

## 7. Iterative Reasoning Cycles

Complex reasoning may require multiple passes.

Each reasoning cycle performs:

1. candidate generation  
2. ranking  
3. graph updates or action execution  

The updated graph becomes the input for the next cycle.

This enables multi-step reasoning similar to stacked transformer layers.

---

# Processing Loop

Language Interpretation
-----------------------

Input is parsed into symbolic assertions and queries using an NLP parser
plus a lexical mapping layer. Tokens are mapped to ontology IRIs when
available; otherwise, the system uses runtime `concept:*` symbols to keep
the graph complete and extensible.

Question Handling
-----------------

Questions are classified before statement parsing to avoid false
assertions. WH tokens determine **expected answer type**, while the
predicate determines **query relation**. Example:

• "What is the man wearing?" → relation query (`wear`, subject `man`)  
• "Where is the man?" → location query (`locatedIn`, subject type `man`)
Queries without a resolvable subject (for example pronoun-only “where is it”) are
treated as unknown and do not return arbitrary graph answers.

Questions are represented as a structured `QueryGoal` (type, subject,
predicate, object, expected answer type, and optional surface text),
so heads reason over a consistent semantic goal rather than raw text.

Yes/No Questions
----------------

Yes/no questions (e.g., "Is the man wearing a hat") are parsed into
relation checks and return a declarative answer when a matching assertion
is found.
If no supporting evidence is available, SAHR responds with "Unknown."

Logging
-------

SAHR uses Java Util Logging (JUL). You can enable file logging by setting
`-Dsahr.log.file=path/to/sahr.log`. SLF4J bindings are routed to JUL via
`slf4j-jdk14` so third-party libraries log consistently.

Question patterns supported by the query interpreter include:
• "Who is X with" → relation query (`with`, subject `X`)
• "What is on X" → relation query (`on`, subject `X`)

Relation Ontology
-----------------

SAHR can load the OBO Relation Ontology (RO) as a canonical relations
backbone. RO is downloaded at build time and loaded from
`applications/src/main/resources/ontology/ro.owl`.

SAHR also includes a small relations pack (`sahr-relations.ttl`) to
define core predicates (on/under/wear/with) and connect them via
subproperty and inverse relationships. It also defines relation families
(`colocation`, `surfaceContact`, `containment`, `proximity`) under
`locationTransferRelation`, allowing propagation heads to reason over
family membership instead of hardcoded predicate lists.

OWL Usage Philosophy (Performance)
----------------------------------

SAHR treats OWL as a **semantic index**, not a full theorem prover.
Runtime reasoning is performed by heads over the working graph; OWL is
consulted for lightweight, cacheable lookups:

• subproperty hierarchy (e.g., `wear ⊑ colocation`)  
• inverse properties (e.g., `on` ↔ `under`)  
• class hierarchy checks (e.g., `astronaut ⊑ person`)

This avoids global DL reasoning and keeps the runtime loop fast and
predictable. Heavy OWL constructs (existentials, cardinalities, complex
class expressions) are intentionally avoided in SAHR v1.

User Input
↓
Language Interpretation
↓
Runtime Symbolic Graph Update
↓
Parallel Attention Heads
↓
Candidate Generation
↓
Candidate Ranking
↓
Winner Selection

If candidate is:

• ANSWER → return result  
• ACTION → execute tool  
• GRAPH UPDATE → update memory  
• CLARIFICATION → ask user  

↓

Next reasoning cycle if required.

Propagation Closure
-------------------

After assertions are inserted, SAHR can run a bounded propagation pass to
add derived facts (for example, co-location transfer) so future queries
answer immediately. Dedicated containment and surface-contact propagation
heads infer location transfer for relations like `inside` and `on`.
`GraphRetrievalHead` now follows short location chains (e.g., `inside` →
`locatedIn`) and can answer colocation-based location queries directly
when the derived assertion is not yet materialized.
For non-question inputs, the agent always prefers the
`assertion-insertion` candidate so statement ingestion cannot be
pre-empted by propagation heads.

Follow-up Resolution
--------------------

When a question produces an assertion candidate (for example, via
ontology symmetry or propagation), the agent applies the assertion and
re-runs reasoning a limited number of times to surface the best answer.

Subgoals
--------

Heads may emit `SUBGOAL` candidates that enqueue new `QueryGoal`s. The
agent processes subgoals breadth-first with a bounded depth to enable
multi-step reasoning (e.g., resolve a holder’s location before answering
the original question).
Subgoal expansion treats co-location predicates as aliases: both short
forms (e.g., `wear`, `with`) and their SAHR relation IRIs
(`https://sahr.ai/ontology/relations#wear`) are accepted.

Working Memory
---------------

`WorkingMemory` is session-scoped state owned by `SahrAgent`. It tracks
active entities, recent assertions, and the current goal stack. Active
entities are bounded (LRU-style) to keep focus tight. The reasoner
remains stateless and consumes working memory via `HeadContext`.
`SahrAgent.resetWorkingMemory()` clears the session state when starting a
new conversation.
In the REPL, use `:reset` to clear working memory explicitly.

Symbolic Attention Scoring
---------------------------

SAHR scores candidates using symbolic attention: heads produce a
`headScore` (local confidence), then a global `SymbolicAttentionScorer`
computes a query-match score from the `QueryGoal` (entity match,
relation match, type compatibility). The final score is:

`finalScore = headScore * queryMatchScore`

This mirrors transformer-style attention weighting while keeping
reasoning symbolic and explainable. Final candidate scores are
softmax-normalized so they form attention weights across candidates.
Internally, `SahrReasoner` delegates to `HeadExecutor` for head
execution and `CandidateSelector` for softmax normalization and
ranking.

Dependency Chain Reasoning
---------------------------

`DependencyChainHead` answers multi-hop dependency queries such as
power chains (e.g., `poweredBy` followed by `chargedBy`) by walking a
bounded chain and returning the furthest dependency as the answer.

Attention Trace Debugging
--------------------------

The REPL can print attention scores for the top candidates after each
input. Use:

`-Dsahr.repl.attentionDebug=true`
`-Dsahr.repl.attentionTopN=5`

To suppress ELK incompleteness warnings in the REPL, set:

`-Dsahr.log.elk.level=SEVERE`

---

# Architectural Outcome

SAHR becomes:

**Ontology-Grounded Symbolic Transformer**

Properties:

• interpretable reasoning  
• ontology-grounded semantics  
• multi-head reasoning architecture  
• deterministic execution  
• explainable decisions  
• extensible through ontology modules

# SAHR Implementation Specification
## Java + Gradle
### Ontology-Grounded Symbolic Transformer Reasoning Engine

This specification outlines how to build SAHR following the architectural north-star:

• OWL provides semantic knowledge  
• Runtime symbolic graph provides working memory  
• Generic attention head families perform reasoning  
• Transformer-style candidate generation and ranking  
• Iterative reasoning cycles produce answers/actions  

The engine should **derive reasoning behaviour from ontology semantics**, not hardcoded rules.

---

# 1. High-Level System Architecture

SAHR processes input through the following pipeline.

| Processing Flow |
|---|
| User Input → Language Interpretation → Runtime Symbolic Graph Update → Parallel Attention Heads → Candidate Ranking → Answer / Action / Graph Update → Next Reasoning Cycle |

The system may perform several reasoning cycles for complex problems.

---

# 2. Gradle Project Layout

The project should be structured as a **multi-module Gradle system**.

| Directory Layout |
|---|
| sahr/ |
| ├─ build.gradle |
| ├─ settings.gradle |
| ├─ engine/ |
| └─ applications/ |

Module responsibilities:

| Module | Responsibility |
|---|---|
| engine | runtime graph, ontology integration, head families, query parsing, agent loop |
| applications | runnable applications and demos |

---

# How To Add Ontologies (Classpath Packs)

SAHR uses a simple properties file to list ontology resources from the application classpath.

Steps:
1. Add the ontology file under `applications/src/main/resources/ontology/`.
2. Add a pack entry in `applications/src/main/resources/sahr/engine.properties`:
   - `ontology.<id>.resources=ontology/your-file.ext`
3. Add the pack id to the `ontologies` list in the same file.

Example:
```
ontologies=core,lexinfo,oewn,mydomain
ontology.mydomain.resources=ontology/mydomain.owl
```

The `applications` module will package the ontology into the classpath, and the engine will load it on startup.

---

# How To Add Heads (Ordered List)

Heads are listed in `applications/src/main/resources/sahr/engine.properties` and are loaded in order.

Steps:
1. Add a new head class under `engine/src/main/java/com/sahr/heads/`.
2. Register its id in `engine/src/main/java/com/sahr/config/HeadRegistry.java`.
3. Add the head id to the `heads` list in `applications/src/main/resources/sahr/engine.properties`.

Example:
```
heads=graph-retrieval,ontology-reasoning,relation-propagation
```

---

# Next Phases Plan

1. **Statement ingestion**: parse simple declarative inputs into runtime assertions.
2. **Assertion application**: apply winning `ASSERTION` candidates into the runtime graph each cycle.
3. **Ontology term mapping**: map surface terms to ontology classes and properties.
4. **Ontology-driven expansion**: use subclass, inverse, and transitive semantics in candidate generation.
5. **Contradiction + provenance**: record evidence and detect conflicts.

---

# Fast Ontology Access

The engine wraps OWL ontology access in `CachedOntologyService`, which memoizes ontology queries (subclass checks, inverse properties, transitivity, and class hierarchy lookups). This keeps head evaluations fast while preserving the same `OntologyService` interface.

---

# Generic Lexical Mapping

`LabelLexicalMapper` builds a generic token→IRI index from any loaded ontology by scanning `rdfs:label`, `skos:prefLabel`, and OntoLex `writtenRep` literals. This keeps ingestion ontology-agnostic while still aligning runtime assertions to OWL IRIs.

---

# Dual-Layer Term Handling

If a token maps to an IRI, the runtime graph stores the IRI and heads can apply OWL semantics. If no mapping exists, the system stores a runtime symbol (e.g., `concept:doctor`) and uses graph-level reasoning only. This preserves ontology correctness while allowing partial-coverage inputs to work.

---

# Offline Ontology Loading

`OntologyLoader` disables OWL imports during loading to avoid network fetches and to keep startup fast. If you need imported axioms later, we can add explicit local IRI mappings.

---

# Debug Run Target

Run the chat application with debug logging enabled:

```
./gradlew :applications:runDebug
```

This sets `sahr.log.level=FINE` to log config loading, ontology initialization, head evaluation counts, and candidate selection.

---

# Statement Parsing

`StatementParser` uses Stanford CoreNLP dependency parsing to extract subject–predicate–object structures generically (including `nmod` and `obl` prepositions). It falls back to simple pattern rules when parsing fails. Compound subjects (e.g., "man and boy") are tagged so the agent can apply multiple assertions, and multiple prepositional relations in a single sentence are surfaced as additional statements.

---

# 3. Core Runtime Model

SAHR maintains a **runtime symbolic graph** representing working memory.

The graph stores:

• entities  
• events  
• relation assertions  
• discourse state  
• workflow state  
• tool results  

Ontology knowledge is **never copied into this graph**.

Ontology = semantic schema  
Runtime graph = dynamic facts

---

# 4. Core Symbol Identifiers

All symbolic elements use stable IDs.

| Code |
|---|
| public final class SymbolId { |
|     private final String value; |
|     public SymbolId(String value) { |
|         this.value = value; |
|     } |
|     public String value() { |
|         return value; |
|     } |
| } |

Example IDs:

| Example Symbol IDs |
|---|
| person:john_doe |
| place:kitchen |
| event:sit_001 |
| concept:person |

---

# 5. Entity Representation

Entities represent objects in the runtime graph.

| Code |
|---|
| public class EntityNode { |
|     private final SymbolId id; |
|     private final String surfaceForm; |
|     private final Set<String> conceptTypes; |
|     public EntityNode(SymbolId id, String surfaceForm, Set<String> conceptTypes) { |
|         this.id = id; |
|         this.surfaceForm = surfaceForm; |
|         this.conceptTypes = conceptTypes; |
|     } |
|     public SymbolId id() { return id; } |
|     public String surfaceForm() { return surfaceForm; } |
|     public Set<String> conceptTypes() { return conceptTypes; } |
| } |

Runtime entity types are **hypotheses**, not ontology truths.

---

# 6. Relation Assertions

Assertions represent symbolic facts in the graph.

| Code |
|---|
| public class RelationAssertion { |
|     private final SymbolId subject; |
|     private final String predicate; |
|     private final SymbolId object; |
|     private final double confidence; |
|     public RelationAssertion(SymbolId subject, String predicate, SymbolId object, double confidence) { |
|         this.subject = subject; |
|         this.predicate = predicate; |
|         this.object = object; |
|         this.confidence = confidence; |
|     } |
|     public SymbolId subject() { return subject; } |
|     public String predicate() { return predicate; } |
|     public SymbolId object() { return object; } |
|     public double confidence() { return confidence; } |
| } |

Example assertion:

| Example Assertion |
|---|
| wife at table |

---

# 7. KnowledgeBase Interface

The runtime graph must support efficient lookup.

| Code |
|---|
| public interface KnowledgeBase { |
|     void addEntity(EntityNode entity); |
|     void addAssertion(RelationAssertion assertion); |
|     List<RelationAssertion> findBySubject(SymbolId subject); |
|     List<RelationAssertion> findByPredicate(String predicate); |
|     List<RelationAssertion> findByObject(SymbolId object); |
|     List<RelationAssertion> getAllAssertions(); |
| } |

---

# 8. Ontology Integration Layer

SAHR interacts with OWL through a narrow service interface.

The ontology layer uses:

• OWL API  
• ELK reasoner

---

# Ontology Service Interface

| Code |
|---|
| public interface OntologyService { |
|     boolean isSubclassOf(String child, String parent); |
|     boolean isSymmetricProperty(String property); |
|     boolean isTransitiveProperty(String property); |
|     Optional<String> getInverseProperty(String property); |
|     Set<String> getSuperclasses(String concept); |
|     Set<String> getSubclasses(String concept); |
| } |

Heads query ontology semantics dynamically.

---

# 9. Query Representation

Queries represent reasoning goals.

| Code |
|---|
| public class Query { |
|     private final String intent; |
|     private final Map<String,String> bindings; |
|     public Query(String intent, Map<String,String> bindings) { |
|         this.intent = intent; |
|         this.bindings = bindings; |
|     } |
|     public String intent() { return intent; } |
|     public Map<String,String> bindings() { return bindings; } |
| } |

Example:

| Query Example |
|---|
| intent = query_where |
| bindings = { entity: person } |

---

# 10. Symbolic Attention Head Interface

All reasoning heads follow the same contract.

| Code |
|---|
| public interface SymbolicAttentionHead { |
|     String getName(); |
|     List<HeadCandidate> evaluate(HeadContext context); |
| } |

Heads operate in parallel.

---

# 11. Head Context

Provides reasoning inputs.

| Code |
|---|
| public class HeadContext { |
|     private final Query query; |
|     private final KnowledgeBase graph; |
|     private final OntologyService ontology; |
|     public HeadContext(Query query, KnowledgeBase graph, OntologyService ontology) { |
|         this.query = query; |
|         this.graph = graph; |
|         this.ontology = ontology; |
|     } |
|     public Query query() { return query; } |
|     public KnowledgeBase graph() { return graph; } |
|     public OntologyService ontology() { return ontology; } |
| } |

---

# 12. Candidate Representation

Heads produce scored candidates.

| Code |
|---|
| public class HeadCandidate { |
|     private final RelationAssertion assertion; |
|     private final double score; |
|     private final String producedBy; |
|     public HeadCandidate(RelationAssertion assertion, double score, String producedBy) { |
|         this.assertion = assertion; |
|         this.score = score; |
|         this.producedBy = producedBy; |
|     } |
|     public RelationAssertion assertion() { return assertion; } |
|     public double score() { return score; } |
|     public String producedBy() { return producedBy; } |
| } |

---

# 13. Reasoning Engine

The engine coordinates all heads.

| Code |
|---|
| public class SahrReasoner { |
|     private final List<SymbolicAttentionHead> heads; |
|     public SahrReasoner(List<SymbolicAttentionHead> heads) { |
|         this.heads = heads; |
|     } |
|     public List<HeadCandidate> reason(HeadContext context) { |
|         List<HeadCandidate> results = new ArrayList<>(); |
|         for (SymbolicAttentionHead head : heads) { |
|             results.addAll(head.evaluate(context)); |
|         } |
|         results.sort(Comparator.comparingDouble(HeadCandidate::score).reversed()); |
|         return results; |
|     } |
| } |

This implements **parallel multi-head reasoning**.

---

# 14. Generic Head Families

Instead of hardcoding domain heads, SAHR defines **generic head families**.

| Head Family | Role |
|---|---|
| Graph Retrieval Heads | retrieve candidate relations from graph |
| Ontology Reasoning Heads | apply ontology semantics |
| Relation Propagation Heads | multi-hop graph inference |
| Query Alignment Heads | match candidates to query |
| Action Heads | propose tool calls |
| Validation Heads | detect contradictions |

Ontology structure determines which reasoning patterns apply.

---

# 15. Candidate Scoring

Each head performs symbolic compatibility scoring.

Standard scoring components:

| Scoring Factor | Meaning |
|---|---|
| query match | candidate answers the query |
| entity compatibility | entity matches requested type |
| predicate compatibility | predicate fits query |
| ontology support | subclass/inverse/transitive support |
| graph confidence | assertion strength |
| recency | newer assertions preferred |

Scores are normalized between **0.0 and 1.0**.

---

# 16. Agent Loop

The chat agent runs a reasoning loop.

| Agent Loop |
|---|
| while conversation_active |
| interpret user input |
| update runtime graph |
| run SAHR reasoning |
| select highest scoring candidate |
| if action → execute tool |
| update graph |
| respond to user |

This loop supports **multi-step reasoning**.

---

# 17. Example Reasoning

Runtime graph:

| Graph Example |
|---|
| wife at table |
| table in kitchen |

Query:

| Query |
|---|
| where is the person |

Ontology:

| Ontology |
|---|
| Wife subclassOf Person |

Heads generate candidates:

| Candidate | Score |
|---|---|
| wife at table | 0.86 |
| person at table | 0.82 |
| wife in kitchen | 0.65 |

The engine selects the highest ranked answer.

---

# 18. Implementation Roadmap

Recommended build sequence.

| Phase | Implementation |
|---|---|
| 1 | runtime graph + ontology service |
| 2 | reasoning engine + candidate ranking |
| 3 | generic head families |
| 4 | query parsing |
| 5 | agent loop |
| 6 | ontology modules (WordNet, SUMO, etc) |
| 7 | advanced reasoning heads |

---

# Final Result

SAHR becomes:

**Ontology-Grounded Symbolic Transformer**

Properties:

• deterministic reasoning  
• ontology-driven semantics  
• parallel multi-head reasoning  
• explainable candidate ranking  
• extensible through ontologies  
• compatible with agent workflows


# SAHR Implementation Clarifications
## Operational Semantics and Deterministic Reasoning

This section clarifies several implementation details required to ensure the SAHR architecture remains:

• deterministic  
• explainable  
• transformer-like in reasoning flow  
• compatible with ontology-driven semantics  

These clarifications do not change the architecture but define **precise runtime behaviour**.

---

# 19. Candidate Types

Earlier sections described head outputs as `RelationAssertion` objects.  
In practice, SAHR must support multiple candidate outcome types.

Candidates therefore represent **proposed reasoning steps** rather than only inferred relations.

| Candidate Type | Meaning |
|---|---|
| ASSERTION | inferred relation to add to the runtime graph |
| ANSWER | direct response to the user query |
| ACTION | external tool invocation |
| GRAPH_UPDATE | structural update to runtime graph |
| CLARIFICATION | request for additional user input |
| CONTRADICTION | detected conflict requiring resolution |

Each reasoning cycle selects **one winning candidate** or continues exploration.

---

# 20. General Candidate Model

Candidates must contain enough information for:

• ranking  
• explainability  
• deterministic tie-breaking  

| Code |
|---|
| public class ReasoningCandidate { |
|     private final CandidateType type; |
|     private final Object payload; |
|     private final double score; |
|     private final String producedBy; |
|     private final List<String> evidence; |
|     private final Map<String,Double> scoreBreakdown; |
|     public ReasoningCandidate( |
|         CandidateType type, |
|         Object payload, |
|         double score, |
|         String producedBy, |
|         List<String> evidence, |
|         Map<String,Double> scoreBreakdown |
|     ) { |
|         this.type = type; |
|         this.payload = payload; |
|         this.score = score; |
|         this.producedBy = producedBy; |
|         this.evidence = evidence; |
|         this.scoreBreakdown = scoreBreakdown; |
|     } |
|     public CandidateType type() { return type; } |
|     public Object payload() { return payload; } |
|     public double score() { return score; } |
|     public String producedBy() { return producedBy; } |
| } |

The payload may contain:

• relation assertions  
• answers  
• tool requests  
• clarification prompts  

---

# 21. Head Behaviour Semantics

Each attention head operates as a **goal-directed symbolic operator**.

A head performs the following steps:

1. read the current reasoning goal  
2. search for compatible symbolic structures  
3. evaluate compatibility  
4. generate ranked candidate proposals  

Heads never mutate the runtime graph directly.

They **only propose candidates**.

Graph updates occur only after the reasoning engine selects a winning candidate.

---

# 22. Head Evaluation Contract

Each head evaluates candidates relative to a reasoning goal.

| Code |
|---|
| public interface SymbolicAttentionHead { |
|     String getName(); |
|     List<ReasoningCandidate> evaluate(HeadContext context); |
| } |

The context contains:

• reasoning goal  
• runtime graph  
• ontology interface  

Heads must be **pure functions** with no side effects.

This guarantees deterministic reasoning.

---

# 23. Head Search Pattern

Each head should follow a common evaluation pattern.

| Head Evaluation Flow |
|---|
| determine query goal |
| identify candidate search space |
| compute compatibility scores |
| produce ranked candidates |

Search spaces may include:

• runtime graph relations  
• ontology semantics  
• tool affordances  
• discourse context  

---

# 24. Head Family Roles

Head families define reusable reasoning patterns.

| Head Family | Responsibility |
|---|---|
| Graph Retrieval Heads | retrieve candidate relations from graph memory |
| Ontology Reasoning Heads | apply subclass and property semantics |
| Relation Propagation Heads | perform multi-hop inference |
| Query Alignment Heads | match candidate relations to user intent |
| Action Heads | propose tool execution |
| Validation Heads | detect contradictions and inconsistencies |

New head families may be added without modifying the reasoning engine.

---

# 25. Candidate Scoring Model

Scores represent **semantic compatibility** between the query goal and candidate output.

Standard scoring components:

| Scoring Component | Meaning |
|---|---|
| query_match | candidate directly answers the query |
| entity_type_match | entity compatible with ontology type |
| predicate_match | relation predicate aligns with query |
| ontology_support | subclass / inverse / transitive inference |
| graph_confidence | confidence of supporting assertions |
| recency | preference for newer information |

Each component contributes to the final score.

Scores are normalized to the range **0.0 – 1.0**.

---

# 26. Deterministic Ranking Rules

To guarantee deterministic execution, SAHR must apply consistent ranking rules.

Candidates are ranked using the following priority:

1. highest score  
2. highest ontology support  
3. strongest supporting evidence  
4. lowest inference depth  
5. lexicographic head name (stable tie-breaker)  

This ensures identical reasoning results across runs.

---

# 27. Provenance and Evidence

Every candidate must include evidence supporting its proposal.

Evidence may include:

• source graph assertions  
• ontology relationships  
• tool results  
• earlier reasoning steps  

Evidence enables the system to produce **explainable reasoning traces**.

Example explanation:

| Explanation |
|---|
| Wife subclassOf Person |
| Wife at Table |
| Table in Kitchen |
| therefore Person in Kitchen |

---

# 28. Contradiction Detection

Symbolic systems must handle conflicting information.

Validation heads detect contradictions by checking:

• mutually exclusive relations  
• ontology disjointness rules  
• incompatible property assertions  

Example:

| Contradiction Example |
|---|
| wife at table |
| wife in garden |

When contradictions are detected, candidates may be produced with type:

| Candidate Type |
|---|
| CONTRADICTION |

Resolution strategies include:

• prefer newer information  
• prefer higher confidence sources  
• request clarification  

---

# 29. Ontology vs Runtime Reasoning

SAHR distinguishes between two reasoning domains.

| Layer | Purpose |
|---|---|
| OWL Ontology | semantic schema and logical relationships |
| Runtime Graph | episodic knowledge and working memory |

OWL provides:

• subclass reasoning  
• property semantics  
• logical constraints  

SAHR heads perform **higher-level reasoning** on top of these semantics.

---

# 30. Iterative Reasoning Layers

Reasoning cycles behave similarly to stacked transformer layers.

Each cycle performs:

| Reasoning Cycle |
|---|
| evaluate heads |
| generate candidates |
| rank candidates |
| select winner |
| update graph or perform action |

The updated graph becomes the input for the next cycle.

This enables:

• multi-step inference  
• iterative hypothesis refinement  
• complex reasoning chains  

---

# 31. Reasoning Trace

To maintain full explainability, SAHR records a reasoning trace.

Each reasoning cycle records:

| Trace Item |
|---|
| query goal |
| heads executed |
| candidates generated |
| scores assigned |
| winning candidate |

This produces a fully inspectable reasoning history.

---

# 32. Architectural Outcome

With these clarifications, SAHR becomes a fully specified system:

**Ontology-Grounded Symbolic Transformer**

Key properties:

• ontology-driven semantics  
• symbolic working memory  
• parallel reasoning heads  
• ranked candidate competition  
• deterministic reasoning  
• explainable decision traces  
• extensible head families  
• iterative reasoning cycles
