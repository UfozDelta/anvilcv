package com.resumepipeline.jobs;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_posting")
public class JobPosting {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    private String title;
    private String company;
    private String location;
    private String posted;
    private String spotted;

    @Column(nullable = false)
    private String url;

    @Column(name = "company_url")
    private String companyUrl;

    private String role;

    @Column(columnDefinition = "text[]", nullable = false)
    private String[] stack = new String[0];

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    protected JobPosting() {}

    public JobPosting(String source, String externalId, String title, String company, String location,
                      String posted, String spotted, String url, String companyUrl, String role, String[] stack) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.company = company;
        this.location = location;
        this.posted = posted;
        this.spotted = spotted;
        this.url = url;
        this.companyUrl = companyUrl;
        this.role = role;
        this.stack = stack;
    }

    public UUID getId()           { return id; }
    public String getSource()     { return source; }
    public String getExternalId() { return externalId; }
    public String getTitle()      { return title; }
    public String getCompany()    { return company; }
    public String getLocation()   { return location; }
    public String getPosted()     { return posted; }
    public String getSpotted()    { return spotted; }
    public String getUrl()        { return url; }
    public String getCompanyUrl() { return companyUrl; }
    public String getRole()       { return role; }
    public String[] getStack()    { return stack; }
    public Instant getReceivedAt() { return receivedAt; }
}
