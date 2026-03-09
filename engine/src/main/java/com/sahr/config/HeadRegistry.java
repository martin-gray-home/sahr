package com.sahr.config;

import com.sahr.core.SymbolicAttentionHead;
import com.sahr.heads.AssertionInsertionHead;
import com.sahr.heads.ContainmentPropagationHead;
import com.sahr.heads.GraphRetrievalHead;
import com.sahr.heads.OntologyReasoningHead;
import com.sahr.heads.QueryAlignmentHead;
import com.sahr.heads.RelationPropagationHead;
import com.sahr.heads.RelationQueryHead;
import com.sahr.heads.SurfaceContactPropagationHead;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class HeadRegistry {
    private static final Logger logger = Logger.getLogger(HeadRegistry.class.getName());

    private HeadRegistry() {
    }

    public static List<SymbolicAttentionHead> buildHeads(EngineConfig config) {
        List<SymbolicAttentionHead> heads = new ArrayList<>();
        for (String id : config.headIds()) {
            heads.add(createHead(id, config));
        }
        logger.info(() -> "Initialized heads: " + config.headIds());
        return heads;
    }

    private static SymbolicAttentionHead createHead(String id, EngineConfig config) {
        switch (id) {
            case "graph-retrieval":
                return new GraphRetrievalHead();
            case "ontology-reasoning":
                return new OntologyReasoningHead();
            case "assertion-insertion":
                return new AssertionInsertionHead();
            case "relation-propagation":
                return new RelationPropagationHead();
            case "containment-propagation":
                return new ContainmentPropagationHead();
            case "surface-contact-propagation":
                return new SurfaceContactPropagationHead();
            case "relation-query":
                return new RelationQueryHead(config.predicateAliases());
            case "query-alignment":
                return new QueryAlignmentHead();
            default:
                throw new IllegalArgumentException("Unknown head id: " + id);
        }
    }
}
