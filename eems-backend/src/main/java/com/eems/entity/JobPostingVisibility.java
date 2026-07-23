package com.eems.entity;

public enum JobPostingVisibility {
    INTERNAL, // visible only to existing employees browsing internal openings
    EXTERNAL, // intended for advertising outside the company (LinkedIn, job boards, etc.) - no public/unauthenticated career page exists in this app; this is a label for HR's own tracking, not a publishing mechanism
    BOTH
}
