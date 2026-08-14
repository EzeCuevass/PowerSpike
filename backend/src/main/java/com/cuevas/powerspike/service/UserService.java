package com.cuevas.powerspike.service;

import com.cuevas.powerspike.model.UserEntity;
import com.cuevas.powerspike.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class UserService {
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEnconder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEnconder;
    }

    public UserEntity register(String mail, String username, String password) {

        if (userRepository.existsByMail(mail)) {
            throw new IllegalArgumentException("El mail ya está registrado");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("El username ya está en uso");
        }

        String hashed = passwordEncoder.encode(password);

        UserEntity user = new UserEntity(mail, username, hashed, DEFAULT_ROLE, false, LocalDateTime.now());
        return userRepository.save(user);
    }
    public boolean checkPassword(String rawPassword, String hashedPassword) {
        return passwordEncoder.matches(rawPassword, hashedPassword);
    }

    public UserEntity findByMail(String mail) {
        return userRepository.findByMail(mail).orElse(null);
    }

    /**
     * Valida credenciales de login. Devuelve el usuario si son correctas,
     * o lanza IllegalArgumentException si el mail no existe o la password no matchea.
     */
    public UserEntity login(String mail, String password) {
        UserEntity user = userRepository.findByMail(mail)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Credenciales inválidas");
        }

        return user;
    }
}