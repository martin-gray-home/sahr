package com.sahr.nlp;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Converts dependency structures into multiple semantic statement candidates.
 * Keeps rules minimal and ontology-driven (surface predicates only).
 */
public final class DependencySemanticMapper {
    private static final Set<String> LOCATION_PREPS = Set.of(
            "in",
            "inside",
            "on",
            "under",
            "above",
            "below",
            "with",
            "near",
            "beside",
            "alongside",
            "next",
            "next_to",
            "next-to"
    );
    private static final Set<String> TEMPORAL_PREPS = Set.of(
            "before",
            "after",
            "during",
            "while",
            "within"
    );

    List<Statement> map(SemanticGraph graph, StatementParserHelpers helpers) {
        if (graph == null) {
            return List.of();
        }
        List<Statement> candidates = new ArrayList<>();
        addVerbObjectCandidates(graph, helpers, candidates);
        addPrepositionCandidates(graph, helpers, candidates);
        addTemporalCandidates(graph, helpers, candidates);
        addPossessiveCandidates(graph, helpers, candidates);
        addAdjectiveCandidates(graph, helpers, candidates);
        addAdjectiveComplementCandidates(graph, helpers, candidates);
        addAdverbCandidates(graph, helpers, candidates);
        return dedupe(candidates);
    }

    private void addVerbObjectCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"obj".equals(edge.getRelation().getShortName())) {
                continue;
            }
            var verbNode = edge.getGovernor();
            CoreLabel subject = helpers.findDependent(graph, verbNode, "nsubj");
            if (subject == null) {
                continue;
            }
            CoreLabel object = edge.getDependent().backingLabel();
            String subjectToken = helpers.normalizeCompoundToken(graph, subject);
            String objectToken = helpers.normalizeCompoundToken(graph, object);
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            String predicate = helpers.normalizeVerb(verbNode.backingLabel());
            if (predicate.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, predicate, false));
        }
    }

    private void addPrepositionCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null) {
                continue;
            }
            String prep = normalizePrep(specific);
            if (!LOCATION_PREPS.contains(prep)) {
                continue;
            }
            var verbNode = edge.getGovernor();
            CoreLabel object = edge.getDependent().backingLabel();
            CoreLabel subject = helpers.findDependent(graph, verbNode, "nsubj");
            CoreLabel directObject = helpers.findDependent(graph, verbNode, "obj");
            CoreLabel anchor = directObject != null ? directObject : subject;
            if (anchor == null) {
                continue;
            }
            String subjectToken = helpers.normalizeCompoundToken(graph, anchor);
            String objectToken = helpers.normalizeCompoundToken(graph, object);
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, prep, false));
        }
    }

    private void addTemporalCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"nmod".equals(relation) && !"obl".equals(relation)) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null) {
                continue;
            }
            String prep = normalizePrep(specific);
            if (!TEMPORAL_PREPS.contains(prep)) {
                continue;
            }
            var verbNode = edge.getGovernor();
            CoreLabel object = edge.getDependent().backingLabel();
            CoreLabel subject = helpers.findDependent(graph, verbNode, "nsubj");
            CoreLabel directObject = helpers.findDependent(graph, verbNode, "obj");
            CoreLabel anchor = directObject != null ? directObject : subject;
            if (anchor == null) {
                continue;
            }
            String subjectToken = helpers.normalizeCompoundToken(graph, anchor);
            String objectToken = helpers.normalizeCompoundToken(graph, object);
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, prep, false));
        }
    }

    private void addAdjectiveCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"amod".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel noun = edge.getGovernor().backingLabel();
            CoreLabel adjective = edge.getDependent().backingLabel();
            String subjectToken = helpers.normalizeCompoundToken(graph, noun);
            String objectToken = helpers.normalizeToken(adjective.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, "hasAttribute", false));
        }
    }

    private void addAdjectiveComplementCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            String relation = edge.getRelation().getShortName();
            if (!"acomp".equals(relation) && !"xcomp".equals(relation)) {
                continue;
            }
            CoreLabel adjective = edge.getDependent().backingLabel();
            CoreLabel subject = helpers.findDependent(graph, edge.getGovernor(), "nsubj");
            if (subject == null) {
                subject = helpers.findDependent(graph, edge.getGovernor(), "nsubjpass");
            }
            if (subject == null) {
                continue;
            }
            String subjectToken = helpers.normalizeCompoundToken(graph, subject);
            String objectToken = helpers.normalizeToken(adjective.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, "hasAttribute", false));
        }
    }

    private void addPossessiveCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"nmod".equals(edge.getRelation().getShortName())) {
                continue;
            }
            String specific = edge.getRelation().getSpecific();
            if (specific == null) {
                continue;
            }
            String normalizedSpecific = specific.toLowerCase(Locale.ROOT);
            if (!"poss".equals(normalizedSpecific) && !"of".equals(normalizedSpecific)) {
                continue;
            }
            CoreLabel possessed = edge.getGovernor().backingLabel();
            CoreLabel possessor = edge.getDependent().backingLabel();
            String subjectToken = helpers.normalizeCompoundToken(graph, possessor);
            String objectToken = helpers.normalizeCompoundToken(graph, possessed);
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, "possess", false));
            out.add(helpers.buildStatement(subjectToken, objectToken, "have", false));
        }
    }

    private void addAdverbCandidates(SemanticGraph graph, StatementParserHelpers helpers, List<Statement> out) {
        for (SemanticGraphEdge edge : graph.edgeIterable()) {
            if (!"advmod".equals(edge.getRelation().getShortName())) {
                continue;
            }
            CoreLabel subject = helpers.findDependent(graph, edge.getGovernor(), "nsubj");
            if (subject == null) {
                continue;
            }
            CoreLabel adverb = edge.getDependent().backingLabel();
            String subjectToken = helpers.normalizeCompoundToken(graph, subject);
            String objectToken = helpers.normalizeToken(adverb.word());
            if (subjectToken.isEmpty() || objectToken.isEmpty()) {
                continue;
            }
            out.add(helpers.buildStatement(subjectToken, objectToken, "hasManner", false));
        }
    }

    private String normalizePrep(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        if ("next".equals(normalized)) {
            return "next";
        }
        return normalized.replace(' ', '_');
    }

    private List<Statement> dedupe(List<Statement> statements) {
        List<Statement> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Statement statement : statements) {
            String key = statement.subject().value() + "|" + statement.predicate() + "|" + statement.object().value();
            if (seen.add(key)) {
                unique.add(statement);
            }
        }
        return unique;
    }
}
