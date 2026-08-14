package com.cuevas.powerspike.dto;

/**
 * Request de registro de usuario.
 * El rol se asigna por defecto ("tester") en el servicio, no se recibe por acá.
 */
public record RegisterRequest(
        String mail,
        String username,
        String password
) {}
