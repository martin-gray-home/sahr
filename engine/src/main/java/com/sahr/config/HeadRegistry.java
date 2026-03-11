package com.sahr.config;

import com.sahr.core.SymbolicAttentionHead;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.ContainmentPropagationHead;
import com.sahr.heads.DependencyChainHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SubgoalExpansionHead;
import com.sahr.heads.SurfaceContactPropagationHead;
import com.sahr.heads.OntologyDefinedHead;
import com.sahr.ontology.OntologyHeadCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class HeadRegistry {
    private static final Logger logger = Logger.getLogger(HeadRegistry.class.getName());

    private HeadRegistry() {
    }

    public static List<SymbolicAttentionHead> buildHeads(EngineConfig config, OntologyContext context) {
        List<SymbolicAttentionHead> heads = new ArrayList<>();
        for (String id : config.headIds()) {
            heads.add(createHead(id, config, context));
        }
        logger.info(() -> "Initialized heads: " + config.headIds());
        return heads;
    }

    public static List<SymbolicAttentionHead> buildHeads(EngineConfig config) {
        return buildHeads(config, null);
    }

    private static SymbolicAttentionHead createHead(String id, EngineConfig config, OntologyContext context) {
        switch (id) {
            case "graph-retrieval":
                return new GraphRetrievalHead();
            case "ontology-reasoning":
                return new OntologyReasoningHead();
            case "assertion-insertion":
                return new AssertionInsertionHead();
            case "relation-propagation":
                return new RelationPropagationHead();
            case "subgoal-expansion":
                return new SubgoalExpansionHead();
            case "containment-propagation":
                return new ContainmentPropagationHead();
            case "surface-contact-propagation":
                return new SurfaceContactPropagationHead();
            case "relation-query":
                return new RelationQueryHead(config.predicateAliases());
            case "dependency-chain":
                return new DependencyChainHead();
            case "query-alignment":
                return new QueryAlignmentHead();
            case "ontology-defined":
                if (context == null || context.ontology() == null) {
                    throw new IllegalArgumentException("Ontology-defined heads require an ontology context.");
                }
                return new OntologyDefinedHead(OntologyHeadCompiler.compile(context.ontology()));
            default:
                throw new IllegalArgumentException("Unknown head id: " + id);
        }
    }
}
