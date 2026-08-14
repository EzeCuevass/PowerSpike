package com.cuevas.powerspike.dto;

/**
 * Request de inicio de sesión.
 */
public record LoginRequest(
        String mail,
        String password
) {}
