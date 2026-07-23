package com.eems.dto;

import java.time.LocalDate;

public class OfferLetterDtos {

    /** Everything the PDF exporter needs - assembled by OfferLetterService from Employee/Position/Department/Salary data. */
    public record OfferLetterData(
            String employeeName,
            String positionTitle,
            String departmentName,
            LocalDate startDate,
            LocalDate issueDate,
            String compensationLine // null if no Salary record exists yet - the letter just omits that paragraph
    ) {}
}
