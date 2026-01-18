package htw.webtech.myapp.persistence.repository;

import htw.webtech.myapp.persistence.entity.SessionTokenEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTokenRepository extends JpaRepository<SessionTokenEntry, String> {
}
