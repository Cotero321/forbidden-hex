package com.example.akaishi.forbidden.api.magic;

import java.util.List;

public record SpellCompileResult(SpellPlan plan, List<CompileDiagnostic> diagnostics) {
    public SpellCompileResult { diagnostics = List.copyOf(diagnostics); }
    public boolean ok() { return plan != null && diagnostics.stream().noneMatch(
            d -> d.severity() == CompileDiagnostic.Severity.ERROR); }
}
