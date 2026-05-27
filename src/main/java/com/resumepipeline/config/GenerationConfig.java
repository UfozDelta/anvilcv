package com.resumepipeline.config;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_config")
public class GenerationConfig {

    public enum BoldDensity  { NONE, LIGHT, HEAVY }
    public enum Tone         { CONSERVATIVE, NEUTRAL, AGGRESSIVE }
    public enum ActionVerbStyle { TECHNICAL, LEADERSHIP, IMPACT }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "word_filter_enabled", nullable = false)
    private boolean wordFilterEnabled = true;

    @Column(name = "single_line_low", nullable = false)
    private int singleLineLow = 22;

    @Column(name = "single_line_high", nullable = false)
    private int singleLineHigh = 26;

    @Column(name = "double_line_low", nullable = false)
    private int doubleLineLow = 42;

    @Column(name = "double_line_high", nullable = false)
    private int doubleLineHigh = 50;

    @Column(name = "dead_zone_low", nullable = false)
    private int deadZoneLow = 27;

    @Column(name = "dead_zone_high", nullable = false)
    private int deadZoneHigh = 40;

    @Column(name = "min_word_floor", nullable = false)
    private int minWordFloor = 12;

    @Column(nullable = false)
    private double temperature = 1.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "bold_density", nullable = false)
    private BoldDensity boldDensity = BoldDensity.LIGHT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tone tone = Tone.NEUTRAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_verb_style", nullable = false)
    private ActionVerbStyle actionVerbStyle = ActionVerbStyle.TECHNICAL;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // getters / setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public boolean isWordFilterEnabled() { return wordFilterEnabled; }
    public void setWordFilterEnabled(boolean v) { this.wordFilterEnabled = v; }
    public int getSingleLineLow() { return singleLineLow; }
    public void setSingleLineLow(int v) { this.singleLineLow = v; }
    public int getSingleLineHigh() { return singleLineHigh; }
    public void setSingleLineHigh(int v) { this.singleLineHigh = v; }
    public int getDoubleLineLow() { return doubleLineLow; }
    public void setDoubleLineLow(int v) { this.doubleLineLow = v; }
    public int getDoubleLineHigh() { return doubleLineHigh; }
    public void setDoubleLineHigh(int v) { this.doubleLineHigh = v; }
    public int getDeadZoneLow() { return deadZoneLow; }
    public void setDeadZoneLow(int v) { this.deadZoneLow = v; }
    public int getDeadZoneHigh() { return deadZoneHigh; }
    public void setDeadZoneHigh(int v) { this.deadZoneHigh = v; }
    public int getMinWordFloor() { return minWordFloor; }
    public void setMinWordFloor(int v) { this.minWordFloor = v; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double v) { this.temperature = v; }
    public BoldDensity getBoldDensity() { return boldDensity; }
    public void setBoldDensity(BoldDensity v) { this.boldDensity = v; }
    public Tone getTone() { return tone; }
    public void setTone(Tone v) { this.tone = v; }
    public ActionVerbStyle getActionVerbStyle() { return actionVerbStyle; }
    public void setActionVerbStyle(ActionVerbStyle v) { this.actionVerbStyle = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
