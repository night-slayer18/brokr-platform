package io.brokr.core.model;

public enum Role {
    VIEWER,    // Can view brokers, consumers, offsets, lag, and data
    ADMIN,     // Can manage topics, reset offsets, etc.
    SUPER_ADMIN, // Can manage organizations, users, and has all privileges
    SERVER_ADMIN // Can manage servers and has all privileges
}