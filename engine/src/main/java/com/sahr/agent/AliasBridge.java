package com.sahr.agent;

import com.sahr.core.KnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.RelationAssertion;
import com.sahr.core.RuleAssertion;
import com.sahr.core.SymbolId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AliasBridge {
    interface AliasFormatter {
        String localName(String predicate);
    }

    private final KnowledgeBase graph;
    private final OntologyService ontology;
    private final AliasFormatter formatter;

    AliasBridge(KnowledgeBase graph, OntologyService ontology, AliasFormatter formatter) {
        this.graph = graph;
        this.ontology = ontology;
        this.formatter = formatter;
    }

    List<SymbolId> expandAliasSymbols(SymbolId node) {
        if (node == null) {
            return List.of();
        }
        Set<SymbolId> known = collectKnownSymbols();
        LinkedHashSet<SymbolId> expanded = new LinkedHashSet<>();
        expanded.add(node);
        expanded.addAll(aliasNodes(node, known));
        return new ArrayList<>(expanded);
    }

    Set<SymbolId> collectKnownSymbols() {
        Set<SymbolId> known = new HashSet<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            known.add(assertion.subject());
            known.add(assertion.object());
        }
        for (RuleAssertion rule : graph.getAllRules()) {
            RelationAssertion antecedent = rule.antecedent();
            RelationAssertion consequent = rule.consequent();
            known.add(antecedent.subject());
            known.add(antecedent.object());
            known.add(consequent.subject());
            known.add(consequent.object());
        }
        return known;
    }

    Set<String> temporalPredicateNames() {
        Set<String> predicates = new HashSet<>();
        predicates.add("before");
        predicates.add("after");
        predicates.add("during");
        return predicates;
    }

    List<SymbolId> temporalBridgeNodes(SymbolId node, Set<String> temporalPredicates) {
        if (node == null) {
            return List.of();
        }
        List<SymbolId> results = new ArrayList<>();
        for (RelationAssertion assertion : graph.getAllAssertions()) {
            if (!assertion.subject().equals(node)) {
                continue;
            }
            if (!temporalPredicates.contains(formatter.localName(assertion.predicate()))) {
                continue;
            }
            SymbolId time = assertion.object();
            for (RelationAssertion other : graph.getAllAssertions()) {
                if (other.subject().equals(node)) {
                    continue;
                }
                if (!temporalPredicates.contains(formatter.localName(other.predicate()))) {
                    continue;
                }
                if (other.object().equals(time)) {
                    results.add(other.subject());
                }
            }
        }
        return results;
    }

    List<SymbolId> aliasNodes(SymbolId node, Set<SymbolId> knownSymbols) {
        if (node == null) {
            return List.of();
        }
        List<SymbolId> aliases = new ArrayList<>();
        String value = node.value();
        addOntologyAliases(aliases, knownSymbols, value);
        if (value.startsWith("entity:")) {
            String local = value.substring("entity:".length());
            addKnownAlias(aliases, knownSymbols, "concept:" + local);
            String singular = singularize(local);
            if (!singular.equals(local)) {
                addKnownAlias(aliases, knownSymbols, "entity:" + singular);
                addKnownAlias(aliases, knownSymbols, "concept:" + singular);
            }
        } else if (value.startsWith("concept:")) {
            String local = value.substring("concept:".length());
            addKnownAlias(aliases, knownSymbols, "entity:" + local);
            String plural = pluralize(local);
            if (!plural.equals(local)) {
                addKnownAlias(aliases, knownSymbols, "entity:" + plural);
            }
        }
        addSuffixAliases(aliases, knownSymbols, value);
        return aliases;
    }

    List<SymbolId> typeNodes(SymbolId node, Set<SymbolId> knownSymbols) {
        List<SymbolId> next = new ArrayList<>();
        if (node == null) {
            return next;
        }
        graph.findEntity(node).ifPresent(entity -> {
            for (String type : entity.conceptTypes()) {
                SymbolId typeId = symbolFromType(type);
                if (typeId != null) {
                    if (knownSymbols.contains(typeId) || typeId.value().startsWith("concept:")
                            || typeId.value().startsWith("http")) {
                        next.add(typeId);
                    }
                    if (typeId.value().startsWith("http")) {
                        for (String superclass : ontology.getSuperclasses(typeId.value())) {
                            SymbolId superId = new SymbolId(superclass);
                            if (!superId.equals(node)) {
                                next.add(superId);
                            }
                        }
                    }
                }
            }
        });
        return next;
    }

    private SymbolId symbolFromType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (type.startsWith("concept:") || type.startsWith("entity:")
                || type.startsWith("http://") || type.startsWith("https://")) {
            return new SymbolId(type);
        }
        return new SymbolId("concept:" + type);
    }

    private void addKnownAlias(List<SymbolId> aliases,
                               Set<SymbolId> knownSymbols,
                               String candidate) {
        SymbolId id = new SymbolId(candidate);
        if (knownSymbols.contains(id)) {
            aliases.add(id);
        }
    }

    private void addSuffixAliases(List<SymbolId> aliases,
                                  Set<SymbolId> knownSymbols,
                                  String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String local = value;
        if (local.startsWith("entity:")) {
            local = local.substring("entity:".length());
        } else if (local.startsWith("concept:")) {
            local = local.substring("concept:".length());
        }
        String[] localTokens = local.split("_");
        if (localTokens.length < 2) {
            return;
        }
        for (SymbolId candidate : knownSymbols) {
            String candidateValue = candidate.value();
            String candidateLocal = candidateValue;
            if (candidateLocal.startsWith("entity:")) {
                candidateLocal = candidateLocal.substring("entity:".length());
            } else if (candidateLocal.startsWith("concept:")) {
                candidateLocal = candidateLocal.substring("concept:".length());
            }
            String[] candidateTokens = candidateLocal.split("_");
            if (candidateTokens.length <= localTokens.length) {
                continue;
            }
            if (endsWithTokens(candidateTokens, localTokens)) {
                aliases.add(candidate);
            }
        }
    }

    private void addOntologyAliases(List<SymbolId> aliases,
                                    Set<SymbolId> knownSymbols,
                                    String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String local = value;
        if (local.startsWith("entity:")) {
            local = local.substring("entity:".length());
        } else if (local.startsWith("concept:")) {
            local = local.substring("concept:".length());
        }
        String normalized = local.replace('_', ' ').trim();
        if (normalized.isBlank()) {
            return;
        }
        for (String iri : ontology.getEntityIrisByLabel(normalized)) {
            for (String label : ontology.getLabels(iri)) {
                String token = normalizeLabelToToken(label);
                if (token.isBlank()) {
                    continue;
                }
                addKnownAlias(aliases, knownSymbols, "entity:" + token);
                addKnownAlias(aliases, knownSymbols, "concept:" + token);
            }
        }
    }

    private String normalizeLabelToToken(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private boolean endsWithTokens(String[] candidateTokens, String[] localTokens) {
        if (candidateTokens.length < localTokens.length) {
            return false;
        }
        int offset = candidateTokens.length - localTokens.length;
        for (int i = 0; i < localTokens.length; i++) {
            if (!candidateTokens[offset + i].equals(localTokens[i])) {
                return false;
            }
        }
        return true;
    }

    private String singularize(String value) {
        if (value.endsWith("ss") || !value.endsWith("s") || value.length() <= 1) {
            return value;
        }
        return value.substring(0, value.length() - 1);
    }

    private String pluralize(String value) {
        if (value.endsWith("s")) {
            return value;
        }
        return value + "s";
    }
}
