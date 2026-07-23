package com.eems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per employee (enforced via a unique FK, not a shared primary
 * key, to keep this a normal JPA entity rather than a @MapsId
 * association - simpler to reason about and query independently).
 */
@Entity
@Table(name = "employee_address")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;

    private String country;
    private String city;
    private String street;
    private String postalCode;
}
