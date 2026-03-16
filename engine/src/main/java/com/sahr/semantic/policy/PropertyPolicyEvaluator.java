package com.sahr.semantic.policy;

import com.sahr.semantic.alignment.AlignmentOutput;
import com.sahr.semantic.model.AlignmentConfidence;
import com.sahr.semantic.model.InferencePolicy;
import com.sahr.semantic.model.InferencePolicyStrength;
import com.sahr.semantic.model.PropertySemantics;
import com.sahr.semantic.model.SemanticNode;
import com.sahr.semantic.model.SemanticSourceReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PropertyPolicyEvaluator {

    public List<PropertyPolicyDecision> evaluate(AlignmentOutput output) {
        Objects.requireNonNull(output, "output");
        List<PropertyPolicyDecision> decisions = new ArrayList<>();
        for (PropertySemantics semantics : output.propertySemantics()) {
            AlignmentConfidence confidence = resolveConfidence(output.canonicalNodes(), semantics.propertyIri());
            List<PropertyPolicyRule> rules = buildRules(semantics, confidence);
            if (!rules.isEmpty()) {
                decisions.add(new PropertyPolicyDecision(
                        semantics.propertyIri(),
                        confidence,
                        rules
                ));
            }
        }
        return List.copyOf(decisions);
    }

    private List<PropertyPolicyRule> buildRules(PropertySemantics semantics, AlignmentConfidence confidence) {
        List<PropertyPolicyRule> rules = new ArrayList<>();
        InferencePolicy policy = policyFor(confidence);

        if (!semantics.inversePropertyIris().isEmpty()) {
            rules.add(new PropertyPolicyRule(
                    PropertyPolicyType.INVERSE,
                    policy,
                    semantics.inversePropertyIris()
            ));
        }
        if (semantics.symmetric()) {
            rules.add(new PropertyPolicyRule(
                    PropertyPolicyType.SYMMETRIC,
                    policy,
                    List.of()
            ));
        }
        if (semantics.transitive()) {
            rules.add(new PropertyPolicyRule(
                    PropertyPolicyType.TRANSITIVE,
                    policy,
                    List.of()
            ));
        }

        return rules;
    }

    private AlignmentConfidence resolveConfidence(List<SemanticNode> nodes, String propertyIri) {
        return nodes.stream()
                .filter(node -> hasSourceId(node, propertyIri))
                .map(SemanticNode::confidence)
                .findFirst()
                .orElse(AlignmentConfidence.UNRESOLVED);
    }

    private boolean hasSourceId(SemanticNode node, String propertyIri) {
        for (SemanticSourceReference source : node.sources()) {
            if (source.sourceId().equals(propertyIri)) {
                return true;
            }
        }
        return false;
    }

    private InferencePolicy policyFor(AlignmentConfidence confidence) {
        InferencePolicyStrength strength = switch (confidence) {
            case EXACT, MANUAL_OVERRIDE -> InferencePolicyStrength.HARD;
            case STRONG -> InferencePolicyStrength.SOFT;
            case WEAK, UNRESOLVED -> InferencePolicyStrength.RANKING_HINT;
        };
        return new InferencePolicy(strength, true, "property semantics policy");
    }
}
