package com.dealership.shared.security;

import com.dealership.identity.Role;
import java.util.UUID;

public record AuthPrincipal(UUID userId, String email, Role role) {}
