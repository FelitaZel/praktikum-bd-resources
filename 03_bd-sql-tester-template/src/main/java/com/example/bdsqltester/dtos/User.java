package com.example.bdsqltester.dtos;

public class User {
    private long id;
    private String username;

    public User(long id, String username) {
        this.id = id;
        this.username = username;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return username;
    }
}