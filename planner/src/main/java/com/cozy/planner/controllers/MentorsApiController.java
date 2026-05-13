package com.cozy.planner.controllers;

import com.cozy.planner.config.TelegramConfig;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.Trainee;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.TraineeRepository;
import com.planner.api.MentorsApi;
import com.planner.model.TraineeDTO;
import com.planner.model.MentorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class MentorsApiController implements MentorsApi {

    private final MentorRepository mentorRepository;
    private final TraineeRepository traineeRepository;
    private final TelegramConfig telegramConfig;
    private final com.cozy.planner.service.TelegramService telegramService;

    public MentorsApiController(MentorRepository mentorRepository, 
                                  TraineeRepository traineeRepository,
                                  TelegramConfig telegramConfig,
                                  com.cozy.planner.service.TelegramService telegramService) {
        this.mentorRepository = mentorRepository;
        this.traineeRepository = traineeRepository;
        this.telegramConfig = telegramConfig;
        this.telegramService = telegramService;
    }

    @Override
    public Mono<ResponseEntity<Flux<MentorDTO>>> getClubMentors(Long clubId, ServerWebExchange exchange) {
        Flux<MentorDTO> mentorFlux = mentorRepository.findAllByClubId(clubId)
                .map(this::mapToMentorDto);
        return Mono.just(ResponseEntity.ok(mentorFlux));
    }

    @Override
    public Mono<ResponseEntity<Flux<TraineeDTO>>> getMentorTrainees(Long mentorId, ServerWebExchange exchange) {
        Flux<TraineeDTO> traineeFlux = traineeRepository.findAllByMentorId(mentorId)
                .map(this::mapToTraineeDto);
        return Mono.just(ResponseEntity.ok(traineeFlux));
    }

    @GetMapping("/api/v1/mentor/telegram/status")
    public Mono<ResponseEntity<Map<String, Object>>> getMentorTelegramStatus(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object mentorIdObj = session.getAttribute("mentor_id");
            if (!(mentorIdObj instanceof Number)) {
                Map<String, Object> result = new HashMap<>();
                result.put("enabled", telegramService.isEnabled());
                result.put("connected", false);
                result.put("telegramUsername", null);
                result.put("connectLink", null);
                return Mono.just(ResponseEntity.ok(result));
            }

            Long mentorId = ((Number) mentorIdObj).longValue();
            if (mentorId <= 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("enabled", telegramService.isEnabled());
                result.put("connected", false);
                result.put("telegramUsername", null);
                result.put("connectLink", null);
                return Mono.just(ResponseEntity.ok(result));
            }

            return mentorRepository.findById(mentorId)
                    .map(mentor -> {
                        Map<String, Object> result = new HashMap<>();
                        result.put("enabled", telegramService.isEnabled());
                        result.put("connected", mentor.hasTelegram());
                        result.put("telegramUsername", mentor.getTelegramUsername());

                        String connectLink = null;
                        if (telegramService.isEnabled() 
                                && mentor.getTelegramToken() != null 
                                && !mentor.getTelegramToken().isBlank()
                                && telegramConfig.getMentorBotUsername() != null 
                                && !telegramConfig.getMentorBotUsername().isBlank()) {
                            connectLink = "https://t.me/" + telegramConfig.getMentorBotUsername() + "?start=" + mentor.getTelegramToken();
                        } else if (telegramService.isEnabled() 
                                && mentor.getTelegramToken() != null 
                                && !mentor.getTelegramToken().isBlank()
                                && telegramConfig.getBotUsername() != null 
                                && !telegramConfig.getBotUsername().isBlank()) {
                            connectLink = "https://t.me/" + telegramConfig.getBotUsername() + "?start=" + mentor.getTelegramToken();
                        }
                        result.put("connectLink", connectLink);
                        return ResponseEntity.ok(result);
                    })
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    @PostMapping("/api/v1/mentor/telegram/generate-token")
    public Mono<ResponseEntity<Map<String, Object>>> generateMentorTelegramToken(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            Object mentorIdObj = session.getAttribute("mentor_id");
            if (!(mentorIdObj instanceof Number)) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("reason", "Not authenticated");
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result));
            }

            Long mentorId = ((Number) mentorIdObj).longValue();
            if (mentorId <= 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("reason", "Demo mode not supported");
                return Mono.just(ResponseEntity.badRequest().body(result));
            }

            if (!telegramService.isEnabled()) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("reason", "Telegram not configured");
                return Mono.just(ResponseEntity.badRequest().body(result));
            }

            return mentorRepository.findById(mentorId)
                    .flatMap(mentor -> {
                        if (mentor.hasTelegram()) {
                            Map<String, Object> result = new HashMap<>();
                            result.put("success", false);
                            result.put("reason", "Telegram already connected");
                            result.put("telegramUsername", mentor.getTelegramUsername());
                            return Mono.just(ResponseEntity.badRequest().body(result));
                        }

                        return telegramService.generateMentorTelegramToken(mentorId)
                                .flatMap(token -> {
                                    String botUsername = telegramConfig.isMentorBotEnabled() 
                                            ? telegramConfig.getMentorBotUsername() 
                                            : telegramConfig.getBotUsername();
                                    String connectLink = null;
                                    if (botUsername != null && !botUsername.isBlank()) {
                                        connectLink = "https://t.me/" + botUsername + "?start=" + token;
                                    }

                                    Map<String, Object> result = new HashMap<>();
                                    result.put("success", true);
                                    result.put("connectLink", connectLink);
                                    return Mono.just(ResponseEntity.ok(result));
                                });
                    })
                    .defaultIfEmpty(ResponseEntity.notFound().build());
        });
    }

    private MentorDTO mapToMentorDto(Mentor entity) {
        MentorDTO dto = new MentorDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSpecialization(entity.getSpecialization());
        dto.setClubId(entity.getClubId());
        return dto;
    }

    private TraineeDTO mapToTraineeDto(Trainee entity) {
        TraineeDTO dto = new TraineeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setMentorId(entity.getMentorId());
        return dto;
    }
}
