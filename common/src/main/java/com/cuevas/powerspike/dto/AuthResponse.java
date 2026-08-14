package com.cuevas.powerspike.dto;

/**
 * Respuesta de autenticación: token JWT + datos del usuario autenticado.
 * Se devuelve tanto en el login como en el registro (auto-login).
 */
public record AuthResponse(
        String token,
        UserDTO user
) {}
