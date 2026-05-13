package com.codingx.backend.auth.domain.service;

public interface PasswordHasher {

    String hash(String plainPassword);

    boolean matches(String plainPassword, String passwordHash);
}