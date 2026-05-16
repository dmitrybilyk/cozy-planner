package com.cozy.planner.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class PlannerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("db/migration/*");
        hints.resources().registerPattern("templates/*");
        hints.resources().registerPattern("static/*");
        hints.resources().registerPattern("api/*");

        hints.reflection().registerTypeIfPresent(classLoader,
                "org.postgresql.Driver",
                hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
    }
}
