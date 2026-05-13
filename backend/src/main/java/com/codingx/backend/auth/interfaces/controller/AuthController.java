package com.codingx.backend.auth.interfaces.controller;
import com.codingx.backend.auth.application.command.LoginCommand;
import com.codingx.backend.auth.application.service.AuthApplicationService;
import com.codingx.backend.auth.application.service.LoginResult;
import com.codingx.backend.auth.domain.model.User;
import com.codingx.backend.auth.interfaces.request.LoginRequest;
import com.codingx.backend.auth.interfaces.response.LoginResponse;
import com.codingx.backend.auth.interfaces.response.MeResponse;
import com.codingx.backend.common.model.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles HTTP requests for AuthController and delegates work to application services.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * AuthApplicationService dependency.
     */
    private final AuthApplicationService authApplicationService;

    /**
     * Authenticates the current user and returns the login result.
     * @param request input argument.
     * @return processing result.
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authApplicationService.login(new LoginCommand(request.username(), request.password()));
        return ApiResponse.success(new LoginResponse(
            result.userId(),
            result.username(),
            result.displayName(),
            result.userType(),
            result.token()
        ));
    }

    /**
     * Clears the current login session.
     * @return processing result.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authApplicationService.logoutCurrent();
        return ApiResponse.successMessage("logged out");
    }

    /**
     * Executes the logic defined by me.
     * @return processing result.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        User user = authApplicationService.currentUser();
        return ApiResponse.success(new MeResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getUserType()));
    }
}
