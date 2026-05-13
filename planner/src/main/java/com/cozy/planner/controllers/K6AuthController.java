package com.cozy.planner.controllers;

import com.cozy.planner.model.entity.Club;
import com.cozy.planner.model.entity.Location;
import com.cozy.planner.model.entity.Mentor;
import com.cozy.planner.model.entity.User;
import com.cozy.planner.repositories.ClubRepository;
import com.cozy.planner.repositories.LocationRepository;
import com.cozy.planner.repositories.MentorRepository;
import com.cozy.planner.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class K6AuthController {

    private final ServerSecurityContextRepository securityContextRepo =
            new WebSessionServerSecurityContextRepository();
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final MentorRepository mentorRepository;
    private final LocationRepository locationRepository;

    public K6AuthController(UserRepository userRepository,
                            ClubRepository clubRepository,
                            MentorRepository mentorRepository,
                            LocationRepository locationRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.mentorRepository = mentorRepository;
        this.locationRepository = locationRepository;
    }

    @PostMapping("/k6-login")
    public Mono<Void> k6Login(ServerWebExchange exchange) {
        var auth = new UsernamePasswordAuthenticationToken(
                "k6-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = new SecurityContextImpl(auth);

        return exchange.getSession()
                .flatMap(session -> {
                    session.getAttributes().put("google_sub", "k6-seed");
                    session.getAttributes().put("user_email", "k6@cozyplanner.app");
                    session.getAttributes().put("user_name", "k6");
                    return securityContextRepo.save(exchange, context);
                })
                .then(userRepository.findByGoogleSub("k6-seed")
                        .flatMap(user -> Mono.just(true))
                        .switchIfEmpty(createK6Setup().then(Mono.just(true)))
                )
                .then(Mono.fromRunnable(() -> {
                    var response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(URI.create("/planner"));
                }));
    }

    private Mono<Void> createK6Setup() {
        User user = User.builder()
                .email("k6@cozyplanner.app")
                .name("k6")
                .googleSub("k6-seed")
                .createdAt(LocalDateTime.now())
                .build();
        return userRepository.save(user)
                .flatMap(savedUser -> {
                    Club club = Club.builder()
                            .name("k6 Club")
                            .userId(savedUser.getId())
                            .build();
                    return clubRepository.save(club).flatMap(savedClub -> {
                        Mentor mentor = Mentor.builder()
                                .name("k6")
                                .clubId(savedClub.getId())
                                .build();
                        return mentorRepository.save(mentor).flatMap(savedMentor -> {
                            Location loc1 = Location.builder()
                                    .name("k6 Location 1")
                                    .description("k6 test location")
                                    .color("#10b981")
                                    .mentorId(savedMentor.getId())
                                    .build();
                            Location loc2 = Location.builder()
                                    .name("k6 Location 2")
                                    .description("k6 test location")
                                    .color("#6366f1")
                                    .mentorId(savedMentor.getId())
                                    .build();
                            return locationRepository.save(loc1)
                                    .then(locationRepository.save(loc2))
                                    .then();
                        });
                    });
                });
    }
}
