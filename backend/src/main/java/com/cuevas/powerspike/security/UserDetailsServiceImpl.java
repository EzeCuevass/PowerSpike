package com.cuevas.powerspike.security;

import com.cuevas.powerspike.model.UserEntity;
import com.cuevas.powerspike.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga el usuario desde la DB para que Spring Security lo use como principal.
 * Busca por mail (es el identificador de login).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String mail) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByMail(mail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + mail));

        return User.builder()
                .username(user.getMail())
                .password(user.getPassword())
                .roles(user.getRole())
                .build();
    }
}
