package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.TraineeRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class PlannerController {

    private final TraineeRepository traineeRepository;

    public PlannerController(TraineeRepository traineeRepository) {
        this.traineeRepository = traineeRepository;
    }

    @GetMapping("/planner")
    public String getPlanner() {
        return "mentor-view";
    }

    @GetMapping("/planner23")
    public String getPlanner2() {
        return "mentor-view";
    }

    @GetMapping("/trainee/{token}")
    public Mono<String> getTraineeInvite(@PathVariable String token, ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> traineeRepository.findByInviteToken(token)
                        .map(trainee -> {
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return "trainee-availability";
                        })
                        .defaultIfEmpty("redirect:/signin"));
    }

    @GetMapping("/trainee/{token}/sessions")
    public Mono<String> getTraineeSessions(@PathVariable String token, ServerWebExchange exchange) {
        return exchange.getSession()
                .flatMap(session -> traineeRepository.findByInviteToken(token)
                        .map(trainee -> {
                            session.getAttributes().put("trainee_id", trainee.getId());
                            return "trainee-sessions";
                        })
                        .defaultIfEmpty("redirect:/signin"));
    }
}
