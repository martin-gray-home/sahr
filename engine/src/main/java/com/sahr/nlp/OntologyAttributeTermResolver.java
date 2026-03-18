package com.sahr.nlp;

import com.sahr.core.OntologyService;

public final class OntologyAttributeTermResolver implements SimpleQueryParser.AttributeTermResolver {
    private final OntologyService ontology;

    public OntologyAttributeTermResolver(OntologyService ontology) {
        this.ontology = ontology;
    }

    @Override
    public boolean isAttributeTerm(String term, boolean adjectiveHint) {
        if (AttributeTermLexicon.isPropertyTerm(term)) {
            return true;
        }
        if (!adjectiveHint || ontology == null) {
            return false;
        }
        return !ontology.getEntityIrisByLabel(term).isEmpty();
    }
}
