package com.carddemo.signon;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process {@link CredentialStore}. The VSAM KSDS it stands in for holds 10 records
 * ({@code app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS}, 800 bytes at 80); choosing a real database is a
 * later decision and is not what this slice is proving.
 *
 * <p>Keys are canonicalised, which is how the fixed-width key semantics of
 * {@code SEC-USR-ID PIC X(08)} are honoured: {@code "USER0001"} and {@code "USER0001 "} are one key.
 */
public final class InMemoryCredentialStore implements CredentialStore {

    private final Map<String, UserRecord> usersById = new ConcurrentHashMap<>();

    @Override
    public Optional<UserRecord> findByUserId(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String key = CobolField.canonical(userId);
        if (key.isEmpty() || key.length() > CobolField.USER_ID_WIDTH) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersById.get(key));
    }

    @Override
    public void save(UserRecord user) {
        usersById.put(user.userId(), user);
    }

    public void saveAll(Collection<UserRecord> users) {
        users.forEach(this::save);
    }

    public List<UserRecord> all() {
        return List.copyOf(usersById.values());
    }

    public int size() {
        return usersById.size();
    }
}
