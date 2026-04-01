package com.refnote.service;

import com.refnote.dto.beta.BetaCountResponse;
import com.refnote.dto.beta.BetaSignupRequest;
import com.refnote.dto.beta.BetaSignupResponse;
import com.refnote.entity.BetaSignup;
import com.refnote.exception.ApiException;
import com.refnote.repository.BetaSignupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BetaService {

    private final BetaSignupRepository betaSignupRepository;

    @Transactional
    public BetaSignupResponse signup(BetaSignupRequest request) {
        if (betaSignupRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("이미 베타 신청된 이메일입니다.");
        }

        BetaSignup signup = BetaSignup.builder()
                .email(request.getEmail())
                .build();
        betaSignupRepository.save(signup);

        long totalCount = betaSignupRepository.count();

        return BetaSignupResponse.builder()
                .message("신청 완료")
                .totalCount(totalCount)
                .build();
    }

    @Transactional(readOnly = true)
    public BetaCountResponse getCount() {
        long count = betaSignupRepository.count();
        return BetaCountResponse.builder().count(count).build();
    }
}
