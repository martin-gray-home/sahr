package com.sahr.config;

import com.sahr.core.SymbolicAttentionHead;
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
            case "ontology-defined":
                if (context == null || context.ontology() == null) {
                    throw new IllegalArgumentException("Ontology-defined heads require an ontology context.");
                }
                return new OntologyDefinedHead(OntologyHeadCompiler.compile(context.ontology()), config.predicateAliases());
            default:
                throw new IllegalArgumentException("Unknown head id: " + id);
        }
    }
}
