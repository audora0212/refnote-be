package com.refnote.controller;

import com.refnote.dto.beta.BetaCountResponse;
import com.refnote.dto.beta.BetaSignupRequest;
import com.refnote.dto.beta.BetaSignupResponse;
import com.refnote.service.BetaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/beta")
@RequiredArgsConstructor
public class BetaController {

    private final BetaService betaService;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody BetaSignupRequest request) {
        BetaSignupResponse response = betaService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "data", response
        ));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCount() {
        BetaCountResponse response = betaService.getCount();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }
}
