package com.sahr.agent;

import java.util.List;

public record Dataset(List<String> statements, List<String> questions) {
    public Dataset {
        statements = statements == null ? List.of() : List.copyOf(statements);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
