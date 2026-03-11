package com.sahr.nlp;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleParserTest {
    @Test
    void parsesConditionalWithModalConsequent() {
        RuleParser parser = new RuleParser(new StatementParser(true));
        Optional<RuleStatement> rule = parser.parse(
                "If the electrical bus voltage drops significantly, electrically powered actuators may stop functioning.");

        assertTrue(rule.isPresent());
        assertTrue(rule.get().consequent().predicate().startsWith("stop"));
    }

    @Test
    void parsesTrailingConditionalForBackupUsage() {
        RuleParser parser = new RuleParser(new StatementParser(true));
        Optional<RuleStatement> rule = parser.parse(
                "Thrusters can be used as backup attitude control if primary attitude control actuators fail.");

        assertTrue(rule.isPresent());
        assertEquals("use", rule.get().consequent().predicate());
        assertEquals("fail", rule.get().antecedent().predicate());
    }
}
