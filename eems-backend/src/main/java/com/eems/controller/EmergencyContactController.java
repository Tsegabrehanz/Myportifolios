package com.eems.controller;

import com.eems.dto.EmergencyContactDtos.ContactResponse;
import com.eems.dto.EmergencyContactDtos.CreateContactRequest;
import com.eems.service.EmergencyContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/emergency-contacts")
@RequiredArgsConstructor
public class EmergencyContactController {

    private final EmergencyContactService contactService;

    @GetMapping
    public List<ContactResponse> list(@PathVariable Long employeeId, Authentication authentication) {
        return contactService.list(employeeId, authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse create(@PathVariable Long employeeId, @Valid @RequestBody CreateContactRequest request, Authentication authentication) {
        return contactService.create(employeeId, request, authentication);
    }

    @DeleteMapping("/{contactId}")
    public void delete(@PathVariable Long employeeId, @PathVariable Long contactId, Authentication authentication) {
        contactService.delete(employeeId, contactId, authentication);
    }
}
