package com.sahr.heads;

import com.sahr.core.CandidateType;
import com.sahr.core.HeadContext;
import com.sahr.core.IntentDecision;
import com.sahr.core.IntentType;
import com.sahr.core.InMemoryKnowledgeBase;
import com.sahr.core.QueryGoal;
import com.sahr.core.ReasoningCandidate;
import com.sahr.ontology.InMemoryOntologyService;
import com.sahr.nlp.InputFeatureExtractor;
import com.sahr.nlp.InputFeatures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentClassifierExecutorTest {
    @Test
    void emitsQuestionIntentCandidate() {
        OntologyHeadDefinition definition = new OntologyHeadDefinition(
                "question-intent-test",
                List.of(),
                null,
                1.0,
                OntologyHeadDefinition.EXECUTOR_INTENT_CLASSIFIER,
                Map.of(
                        "intent", "question",
                        "any", "has_wh,has_question_mark"
                )
        );
        OntologyDefinedHead head = new OntologyDefinedHead(List.of(definition));

        InputFeatures features = InputFeatureExtractor.extract("What failed?");
        HeadContext context = new HeadContext(
                QueryGoal.unknown(),
                new InMemoryKnowledgeBase(),
                new InMemoryOntologyService(),
                null,
                null,
                null,
                features
        );

        List<ReasoningCandidate> candidates = head.evaluate(context);

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.type() == CandidateType.INTENT
                        && candidate.payload() instanceof IntentDecision
                        && ((IntentDecision) candidate.payload()).type() == IntentType.QUESTION));
    }
}
