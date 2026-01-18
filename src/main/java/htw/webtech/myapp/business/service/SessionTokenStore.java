package htw.webtech.myapp.business.service;

import htw.webtech.myapp.persistence.entity.SessionTokenEntry;
import htw.webtech.myapp.persistence.repository.SessionTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class SessionTokenStore {

    private final SessionTokenRepository repo;

    private static final Duration TTL = Duration.ofDays(14);

    public SessionTokenStore(SessionTokenRepository repo) {
        this.repo = repo;
    }

    public String issueToken(String email) {
        String token = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plus(TTL);

        repo.save(new SessionTokenEntry(token, email, now, exp));
        return token;
    }

    public String getEmailByToken(String token) {
        if (token == null || token.isBlank()) return null;

        Optional<SessionTokenEntry> found = repo.findById(token);
        if (found.isEmpty()) return null;

        SessionTokenEntry entry = found.get();
        if (entry.isExpired()) {
            repo.deleteById(token);
            return null;
        }

        return entry.getEmail();
    }

    public void revokeToken(String token) {
        if (token == null || token.isBlank()) return;
        repo.deleteById(token);
    }
}
