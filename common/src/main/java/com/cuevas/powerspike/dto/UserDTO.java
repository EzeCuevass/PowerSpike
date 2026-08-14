package com.cuevas.powerspike.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de registro de usuario. NUNCA incluye el password (ni hasheado).
 */
public record UserDTO(
        Long id,
        String mail,
        String username,
        String role,
        boolean emailVerified,
        LocalDateTime timestamp
) {}
