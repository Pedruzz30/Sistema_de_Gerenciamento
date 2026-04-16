package repository;

import model.LogAcao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaLogRepository extends JpaRepository<LogAcao, Long> {
}
