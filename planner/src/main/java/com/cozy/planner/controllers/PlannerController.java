package com.cozy.planner.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PlannerController {

    @GetMapping("/planner")
    public String getPlanner() {
        return "coach-view";
    }

    @GetMapping("/planner23")
    public String getPlanner2() {
        return "coach-view";
    }
}
