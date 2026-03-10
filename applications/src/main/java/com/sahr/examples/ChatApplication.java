package com.sahr.examples;

import com.sahr.agent.ChatRepl;
import com.sahr.agent.SahrAgent;
import com.sahr.config.EngineConfig;
import com.sahr.config.HeadRegistry;
import com.sahr.config.OntologyContext;
import com.sahr.config.OntologyRegistry;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.OntologyService;
import com.sahr.core.SahrReasoner;
import com.sahr.logging.SahrLogging;
import com.sahr.nlp.SimpleQueryParser;
import com.sahr.nlp.StatementParser;
import com.sahr.ontology.CompositeOntologyService;
import com.sahr.ontology.InMemoryOntologyService;

import java.io.IOException;
public final class ChatApplication {
    private static final String ENGINE_CONFIG = "sahr/engine.properties";

    public static void main(String[] args) throws IOException {
        SahrLogging.configure();

        InMemoryKnowledgeBase graph = new InMemoryKnowledgeBase();

        InMemoryOntologyService localOntology = new InMemoryOntologyService();

        EngineConfig config = EngineConfig.loadFromClasspath(ENGINE_CONFIG);
        OntologyContext context = OntologyRegistry.loadOntologyContext(config);
        OntologyService ontology = new CompositeOntologyService(java.util.List.of(localOntology, context.service()));

        SahrReasoner reasoner = new SahrReasoner(HeadRegistry.buildHeads(config));
        SimpleQueryParser parser = new SimpleQueryParser(true);
        StatementParser statementParser = new StatementParser(true);
        SahrAgent agent = new SahrAgent(graph, ontology, reasoner, parser, statementParser, context.termMapper());

        new ChatRepl(agent, System.in, System.out).run();
    }
}
