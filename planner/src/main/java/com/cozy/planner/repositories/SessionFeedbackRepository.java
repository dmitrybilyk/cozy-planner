package com.cozy.planner.repositories;

import com.cozy.planner.model.entity.SessionFeedback;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SessionFeedbackRepository extends ReactiveCrudRepository<SessionFeedback, Long> {
    Flux<SessionFeedback> findAllByToTraineeIdOrderByCreatedAtDesc(Long toTraineeId);
    Flux<SessionFeedback> findAllByToMentorIdOrderByCreatedAtDesc(Long toMentorId);
    Flux<SessionFeedback> findAllByFromMentorIdOrderByCreatedAtDesc(Long fromMentorId);
    Flux<SessionFeedback> findAllByFromMentorIdAndToTraineeIdOrderByCreatedAtDesc(Long fromMentorId, Long toTraineeId);
    Mono<Long> countByToTraineeIdAndIsReadFalse(Long toTraineeId);
    Mono<Long> countByToMentorIdAndIsReadFalse(Long toMentorId);

    @Query("SELECT * FROM session_feedback WHERE (from_mentor_id = :mentorId AND to_trainee_id = :traineeId) OR (from_trainee_id = :traineeId AND to_mentor_id = :mentorId) ORDER BY created_at DESC")
    Flux<SessionFeedback> findConversation(Long mentorId, Long traineeId);
}
