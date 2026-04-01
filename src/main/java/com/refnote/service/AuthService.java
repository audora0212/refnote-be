package com.refnote.service;

import com.refnote.config.JwtProvider;
import com.refnote.dto.auth.*;
import com.refnote.entity.User;
import com.refnote.exception.ApiException;
import com.refnote.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("이미 등록된 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        user = userRepository.save(user);

        return TokenResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId(), user.getEmail()))
                .build();
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return TokenResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .refreshToken(jwtProvider.generateRefreshToken(user.getId(), user.getEmail()))
                .build();
    }

    public TokenResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtProvider.validateToken(refreshToken)) {
            throw ApiException.unauthorized("유효하지 않은 리프레시 토큰입니다.");
        }

        String type = jwtProvider.getTokenType(refreshToken);
        if (!"refresh".equals(type)) {
            throw ApiException.unauthorized("유효하지 않은 리프레시 토큰입니다.");
        }

        Long userId = jwtProvider.getUserIdFromToken(refreshToken);
        String email = jwtProvider.getEmailFromToken(refreshToken);

        return TokenResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(userId, email))
                .refreshToken(jwtProvider.generateRefreshToken(userId, email))
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }
}
