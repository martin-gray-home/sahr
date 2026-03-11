package com.sahr.support;

import com.sahr.agent.SahrAgent;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.SahrReasoner;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.heads.OntologyHeadDefinition;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SubgoalExpansionHead;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.nlp.TermMapper;

import java.util.ArrayList;
import java.util.List;

public final class SahrTestAgentFactory {
    private SahrTestAgentFactory() {
    }

    public static SahrAgent newAgent(InMemoryKnowledgeBase graph, com.sahr.core.SymbolicAttentionHead... extraHeads) {
        return newAgentWithMapper(graph, OwlOntologyTestSupport.buildTermMapper(), extraHeads);
    }

    public static SahrAgent newAgentWithMapper(InMemoryKnowledgeBase graph,
                                               TermMapper mapper,
                                               com.sahr.core.SymbolicAttentionHead... extraHeads) {
        List<OntologyHeadDefinition> definitions = OwlOntologyTestSupport.buildHeadDefinitions();
        List<com.sahr.core.SymbolicAttentionHead> heads = new ArrayList<>(List.of(
                new AssertionInsertionHead(),
                new SubgoalExpansionHead(),
                new GraphRetrievalHead(),
                new RelationQueryHead(),
                new QueryAlignmentHead(),
                new OntologyDefinedHead(definitions)
        ));
        if (extraHeads != null) {
            heads.addAll(List.of(extraHeads));
        }
        SahrReasoner reasoner = new SahrReasoner(heads);
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        TermMapper effectiveMapper = mapper == null ? OwlOntologyTestSupport.buildTermMapper() : mapper;
        return new SahrAgent(graph, OwlOntologyTestSupport.buildOntologyService(), reasoner, parser, statementParser,
                effectiveMapper);
    }

    public static SahrAgent newAgent(InMemoryKnowledgeBase graph) {
        return newAgentWithMapper(graph, OwlOntologyTestSupport.buildTermMapper(), new com.sahr.core.SymbolicAttentionHead[0]);
    }
}
