package com.cozy.planner.model.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("push_subscriptions")
public class PushSubscription {
    @Id
    private Long id;
    private Long traineeId;
    private Long mentorId;
    private String endpoint;
    private String authKey;
    private String p256dhKey;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTraineeId() { return traineeId; }
    public void setTraineeId(Long traineeId) { this.traineeId = traineeId; }
    public Long getMentorId() { return mentorId; }
    public void setMentorId(Long mentorId) { this.mentorId = mentorId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAuthKey() { return authKey; }
    public void setAuthKey(String authKey) { this.authKey = authKey; }
    public String getP256dhKey() { return p256dhKey; }
    public void setP256dhKey(String p256dhKey) { this.p256dhKey = p256dhKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
