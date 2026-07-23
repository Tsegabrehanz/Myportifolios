package com.eems.controller;

import com.eems.dto.LeaveRequestDtos.CreateLeaveRequest;
import com.eems.dto.LeaveRequestDtos.LeaveDecisionRequest;
import com.eems.dto.LeaveRequestDtos.LeaveResponse;
import com.eems.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/leave-requests")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse submit(@Valid @RequestBody CreateLeaveRequest request, Authentication authentication) {
        return leaveService.submit(request, authentication);
    }

    @GetMapping
    public List<LeaveResponse> list(Authentication authentication) {
        return leaveService.listForCurrentUser(authentication);
    }

    @PatchMapping("/{id}/decision")
    public LeaveResponse decide(@PathVariable Long id, @Valid @RequestBody LeaveDecisionRequest decision, Authentication authentication) {
        return leaveService.decide(id, decision, authentication);
    }
}
