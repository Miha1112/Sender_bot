package ua.sh1chiro.SurveyBot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class BotConfig {
    @Value("sender_bot") String botName;
    @Value("7983747835:AAETS7H2rYv_cbrG3sjTAWqedkTS-XhJRNc") String token;
    @Value("id") String chatId;
}