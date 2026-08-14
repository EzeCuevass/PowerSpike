package com.cuevas.powerspike.controller;

import com.cuevas.powerspike.dto.AuthResponse;
import com.cuevas.powerspike.dto.LoginRequest;
import com.cuevas.powerspike.dto.RegisterRequest;
import com.cuevas.powerspike.dto.UserDTO;
import com.cuevas.powerspike.model.UserEntity;
import com.cuevas.powerspike.security.JwtService;
import com.cuevas.powerspike.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController{

    private final UserService userService;
    private final JwtService jwtService;

    public UserController(UserService userService, JwtService jwtService){
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@RequestBody RegisterRequest request){
        if (request.mail() == null || request.mail().isBlank()
                || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try{
            UserEntity user = userService.register(request.mail(), request.username(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(buildAuthResponse(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> loginUser(@RequestBody LoginRequest request){
        if (request.mail() == null || request.mail().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        try{
            UserEntity user = userService.login(request.mail(), request.password());
            return ResponseEntity.ok(buildAuthResponse(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private AuthResponse buildAuthResponse(UserEntity user) {
        String token = jwtService.generateToken(user.getId(), user.getMail(), user.getUsername(), user.getRole());
        UserDTO dto = new UserDTO(user.getId(), user.getMail(), user.getUsername(),
                user.getRole(), user.isEmailVerified(), user.getTimestamp());
        return new AuthResponse(token, dto);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserEntity user = userService.findByMail(userDetails.getUsername());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDTO dto = new UserDTO(user.getId(), user.getMail(), user.getUsername(),
                user.getRole(), user.isEmailVerified(), user.getTimestamp());
        return ResponseEntity.ok(dto);
    }
}