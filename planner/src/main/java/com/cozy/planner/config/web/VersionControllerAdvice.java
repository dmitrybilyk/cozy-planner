package com.cozy.planner.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class VersionControllerAdvice {

    private final String appVersion;

    public VersionControllerAdvice(@Value("${app.version:1}") String appVersion) {
        this.appVersion = appVersion;
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return appVersion;
    }
}
