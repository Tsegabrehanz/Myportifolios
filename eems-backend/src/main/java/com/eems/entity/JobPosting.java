package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A job opening being recruited for. Deliberately just the posting
 * itself - there's no candidate/application/interview data model here
 * (that's a real ATS subsystem of its own, not built yet). EXTERNAL
 * visibility is a label for HR's own tracking of where a role is being
 * advertised - this app has no public/unauthenticated career page that
 * would actually publish it anywhere.
 */
@Entity
@Table(name = "job_posting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Lob
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Optional - a posting can reference an existing managed Position, or stand alone for a brand-new role not yet in the position table. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobPostingVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobPostingStatus status = JobPostingStatus.DRAFT;

    private String location;

    @Column(nullable = false)
    private LocalDate postedDate;

    private LocalDate closingDate;
}
