package ua.sh1chiro.SurveyBot.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.sh1chiro.SurveyBot.models.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByTelegramId(Long id);
}
