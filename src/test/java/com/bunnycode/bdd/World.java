package com.bunnycode.bdd;

import com.bunnycode.bdd.ApiClient.ApiResponse;
import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State shared between the steps of one scenario; a fresh instance per scenario. Steps receive a
 * scoped proxy, so all state is reached through methods (proxy fields are never populated).
 */
@ScenarioScope
public class World {

    public record Account(long id, String name, String email, String password, String token) {
    }

    private final Map<String, Account> accounts = new HashMap<>();
    /** STOMP sessions by name: a user name, or a window name for the workspace scenarios. */
    private final Map<String, StompTestSession> sessions = new HashMap<>();
    private final Map<String, Long> battles = new HashMap<>();
    private ApiResponse lastResponse;

    public void remember(Account account) {
        accounts.put(account.name(), account);
    }

    public Account account(String name) {
        Account account = accounts.get(name);
        if (account == null) {
            throw new IllegalStateException("No account for " + name + " — add a Given step creating it");
        }
        return account;
    }

    public String tokenOf(String name) {
        return account(name).token();
    }

    public void connected(String name, StompTestSession session) {
        sessions.put(name, session);
    }

    public StompTestSession session(String name) {
        StompTestSession session = sessions.get(name);
        if (session == null) {
            throw new IllegalStateException(name + " is not connected");
        }
        return session;
    }

    public StompTestSession disconnect(String name) {
        StompTestSession session = session(name);
        sessions.remove(name);
        session.close();
        return session;
    }

    public List<StompTestSession> allSessions() {
        return new ArrayList<>(sessions.values());
    }

    public void rememberBattle(String battleName, long battleId) {
        battles.put(battleName, battleId);
    }

    public long battleId(String battleName) {
        Long id = battles.get(battleName);
        if (id == null) {
            throw new IllegalStateException("Unknown battle " + battleName);
        }
        return id;
    }

    public ApiResponse lastResponse() {
        return lastResponse;
    }

    public void lastResponse(ApiResponse response) {
        this.lastResponse = response;
    }
}
