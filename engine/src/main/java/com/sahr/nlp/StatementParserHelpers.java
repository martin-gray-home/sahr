package com.sahr.nlp;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;

interface StatementParserHelpers {
    Statement buildStatement(String subjectToken, String objectToken, String predicate, boolean objectIsConcept);

    CoreLabel findDependent(SemanticGraph graph, edu.stanford.nlp.ling.IndexedWord governor, String relation);

    String normalizeCompoundToken(SemanticGraph graph, CoreLabel head);

    String normalizeToken(String raw);

    String normalizeVerb(CoreLabel verbLabel);
}
