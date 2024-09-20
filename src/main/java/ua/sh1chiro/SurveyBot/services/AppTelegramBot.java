package ua.sh1chiro.SurveyBot.services;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.sh1chiro.SurveyBot.config.BotConfig;
import ua.sh1chiro.SurveyBot.models.User;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Document;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.*;
import java.time.Duration;
import java.util.*;


@Slf4j
@Component
@RequiredArgsConstructor
public class AppTelegramBot  extends TelegramLongPollingBot {
    private final BotConfig config;
    private final UserService userService;
    private String jdbcURL = "jdbc:mysql://localhost:3306/senderbot?useSSL=false";
    private String username = "root";
    private String password = "root";
    private Map<Long, Boolean> awaitingFile = new HashMap<>();
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(@NotNull Update update) {
        Long chatId;
        System.out.println("get id: " + update.getMessage().getChatId());;
        User user = userService.getByTelegramId(update.getMessage().getFrom().getId());

        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();

            if(update.getMessage().hasText()){
                if(user != null)
                    checkText(update, chatId, user);

                if(user == null && update.getMessage().getText().equals("/start"))
                    registration(update);
            }
        }
    }

    private void checkText(Update update, Long chatId, User user) {
        if (update.getMessage().getText().equals("/start_send")) {
            try {
                authorizeAndSendMessages(update);
            }catch (Exception e) {
                System.out.println("Whats happens wrong 1");
            }
        } else if (update.getMessage().getText().equals("/get_command")) {
            String command = "Бот має наступні команди:\n /start_send - це команда для початку роботи бота, " +
                    "\n/get_command - це команда на отримання справки з команд бота" +
                    "\n /add_sender_account - це команда для додавання акаунту з якого буде іти відправка" +
                    "\n /back - це команда для повернення до головного меню та відміна всіх активних статусів бота" +
                    "\n /add_data_in_db_nickname - це команда яка активує режим в якому бот очікуватиме файл з новими користувачами для розсилки";
            sendMessageToChat(command, chatId);
        } else if (update.getMessage().getText().equals("/add_sender_account")) {
            // метод для додавання акаунтів для відправки
        }else if(update.getMessage().getText().equals("/back")){
            awaitingFile.put(chatId, false);
            sendMessageToChat("Стек команд очищено", chatId);
        } else if (update.getMessage().getText().equals("/add_data_in_db_nickname")) {
            // Встановлюємо для користувача стан очікування файлу
            awaitingFile.put(chatId, true);
            sendMessageToChat("Будь ласка, надішліть файл Excel з новими користувачами для розсилки.", chatId);
        } else if (update.getMessage().hasDocument()) {
            // Якщо бот отримав файл, перевіряємо чи бот очікував файл
            if (awaitingFile.getOrDefault(chatId, false)) {
                // Отримуємо інформацію про файл
                Document document = update.getMessage().getDocument();

                // Перевіряємо чи файл має формат Excel
                String fileName = document.getFileName();
                if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                    // Отримання файлу як InputStream з Telegram API
                    InputStream fileStream = downloadFileAsStream(document); // Метод для отримання InputStream з Telegram
                    if (fileStream != null) {
                        sendMessageToChat("Бот отримав Excel файл: " + fileName, chatId);
                        // Виклик методу для обробки файлу
                        addUserForSpamToBd(fileStream);

                        awaitingFile.put(chatId, false);  // Після отримання файлу, скидаємо стан
                    } else {
                        sendMessageToChat("Помилка під час отримання файлу.", chatId);
                    }
                } else {
                    sendMessageToChat("Отриманий файл не є Excel файлом. Будь ласка, надішліть файл у форматі .xls або .xlsx.", chatId);
                }
            } else {
                sendMessageToChat("Цей бот не очікував файл від вас. Для початку надішліть команду /add_data_in_db_nickname.", chatId);
            }
        } else {
            sendMessageToChat("Неочікуваний вхід. Спробуйте ввести одну з доступних команд.", chatId);
        }
    }

    private void getInfoFromBD(){
        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");
            String sql = "SELECT nickname, telegram_id FROM users";

            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String nickname = resultSet.getString("nickname");
                long telegramId = resultSet.getLong("telegram_id");

                System.out.println("Nickname: " + nickname + ", Telegram ID: " + telegramId);
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registration(Update update){
        User user = new User();
        user.setTelegramId(update.getMessage().getChatId());

        org.telegram.telegrambots.meta.api.objects.User telegramUser = update.getMessage().getFrom();
        String nickname = telegramUser.getUserName();
        if(nickname != null)
            user.setNickname(nickname);

        String name = telegramUser.getFirstName();
        if(telegramUser.getLastName() != null)
            name = name.concat(" " + telegramUser.getLastName());

        if(name != null)
            user.setName(name);

        userService.save(user);
    }

    private void sendMessageToChat(String text, Long chatId){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendKeyboardToChat(Long chatId, String text, ReplyKeyboardMarkup keyboardMarkup){
        SendMessage response = new SendMessage();
        response.setChatId(chatId);
        response.setText(text);
        response.setReplyMarkup(keyboardMarkup);

        try {
            execute(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendInlineMessage(Long chatId, String text, InlineKeyboardMarkup inlineKeyboardMarkup){
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(inlineKeyboardMarkup);
        try {
            if(chatId > 0)
                execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void addUserForSpamToBd(InputStream fileStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(fileStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Перша сторінка Excel
            for (Row row : sheet) {
                String nickname = row.getCell(0).getStringCellValue();
                long telegramId = (long) row.getCell(1).getNumericCellValue();

                // Викликаємо метод для вставки в базу
                insertUserIntoDB(nickname, telegramId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private InputStream downloadFileAsStream(Document document) {
        try {
            // Отримуємо об'єкт File через file_id
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());

            // Викликаємо Telegram API для отримання інформації про файл
            File file = execute(getFileMethod); // 'execute' - це метод, який викликає API (частина TelegramBots)

            // Отримуємо URL файлу
            String filePath = file.getFilePath();
            String fileUrl = "https://api.telegram.org/file/bot/" + filePath;

            // Створюємо InputStream для подальшої обробки
            return new URL(fileUrl).openStream();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private static void insertUserIntoDB(String nickname, long telegramId) {
        String jdbcURL = "jdbc:postgresql://localhost:5432/your_db";
        String username = "your_user";
        String password = "your_password";

        String sql = "INSERT INTO users (nickname, telegram_id) VALUES (?, ?)";

        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nickname);
            pstmt.setLong(2, telegramId);

            pstmt.executeUpdate();
            System.out.println("User added: " + nickname + ", ID: " + telegramId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void OpenWebTg(int userId, String username, Update update, List<Long> telegramIds, int startIndex) throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "./chromedriver.exe");
        WebDriver driver = new ChromeDriver();
        String url = "https://web.telegram.org/";

        driver.get(url);
        Thread.sleep(5000);

        loadLocalStorage(driver, userId);

        try {
            WebElement profileElement = driver.findElement(By.xpath("//*[contains(@href, 'profile')]"));
            if (profileElement.isDisplayed()) {
                System.out.println("Автоматична авторизація успішна.");
                sendMessageToChat("Ви успішно авторизувались через збережені дані для акаунту: " + username, update.getMessage().getChatId());
                // Перевірка на пошукове поле
                try {
                    WebElement searchField = driver.findElement(By.cssSelector("input[type='text'][placeholder='Search']"));
                    if (searchField.isDisplayed()) {
                        // Почати відправку повідомлень
                        sendMessagesToUsers(driver, telegramIds, startIndex);
                    }
                } catch (NoSuchElementException searchException) {
                    System.out.println("Поле пошуку не знайдено.");
                }
            }
        } catch (NoSuchElementException e) {
            // Ручна авторизація
            handleManualAuthorization(driver, userId, username, update);
        }

        driver.quit();
    }
    private void handleManualAuthorization(WebDriver driver, int userId, String username, Update update) throws InterruptedException {
        System.out.println("Не вдалося автоматично авторизуватись.");

        // Повідомляємо користувача про необхідність ручної авторизації
        sendMessageToChat("Будь ласка, авторизуйтесь вручну в акаунт: " + username, update.getMessage().getChatId());

        // Чекаємо 60 секунд для авторизації користувача
        System.out.println("Очікування ручної авторизації...");
        Thread.sleep(60000); // Час очікування, щоб користувач міг авторизуватись

        // Після ручної авторизації зберігаємо дані з localStorage
        saveLocalStorage(driver, userId);

        System.out.println("Авторизація пройшла успішно. Дані збережені.");

        // Повідомляємо користувача про успішну авторизацію
        sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());

        // Продовжуємо роботу бота після авторизації
        List<Long> telegramIds = getTelegramIdsFromDB(); // Отримати список Telegram ID
        sendMessagesToUsers(driver, telegramIds, 0); // Відправка повідомлень після ручної авторизації
    }

    public void saveLocalStorage(WebDriver driver, int userId) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Отримуємо всі ключі з localStorage
        String script = "let items = {}; " +
                "for (let i = 0; i < localStorage.length; i++) { " +
                "    let key = localStorage.key(i); " +
                "    items[key] = localStorage.getItem(key); " +
                "} " +
                "return JSON.stringify(items);";

        String localStorageData = (String) js.executeScript(script);

        // Зберігаємо дані в базу
        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password)) {
            String sql = "INSERT INTO user_local_storage (user_id, local_storage_data) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE local_storage_data = ?";
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setInt(1, userId);
            statement.setString(2, localStorageData);
            statement.setString(3, localStorageData);  // Оновлюємо існуючі дані
            statement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadLocalStorage(WebDriver driver, int userId) {
        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password)) {
            String sql = "SELECT local_storage_data FROM user_local_storage WHERE user_id = ?";
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setInt(1, userId);
            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                String localStorageData = resultSet.getString("local_storage_data");

                // Декодуємо JSON-строку в об'єкт
                JavascriptExecutor js = (JavascriptExecutor) driver;
                String script = "let data = " + localStorageData + "; " +
                        "for (let key in data) { " +
                        "    localStorage.setItem(key, data[key]); " +
                        "}";
                js.executeScript(script);

                // Після завантаження даних перезавантажуємо сторінку
                Thread.sleep(3000);  // Додаємо невелику затримку для надійності
                driver.navigate().refresh();
            } else {
                System.out.println("Дані localStorage для користувача з id " + userId + " не знайдено.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private List<Long> getTelegramIdsFromDB() {
        List<Long> telegramIds = new ArrayList<>();

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT telegram_id FROM users";
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                long telegramId = resultSet.getLong("telegram_id");
                telegramIds.add(telegramId);  // Додаємо telegram_id до списку
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return telegramIds;  // Повертаємо список Telegram ID
    }
    private List<String> getUsernamesFromDB() {
        List<String> usernames = new ArrayList<>();

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT nickname FROM users";
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String username = resultSet.getString("nickname");
                usernames.add(username);  // Додаємо username до списку
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return usernames;  // Повертаємо список імен користувачів
    }

    private void authorizeAndSendMessages(Update update) {
        List<String> usernames = getUsernamesFromDB();
        List<Long> telegramIds = getTelegramIdsFromDB(); // Всі отримані з бази telegram_ids

        int startIndex = 0;
        for (int i = 0; i < usernames.size(); i++) {
            String username = usernames.get(i);
            int userId = getUserIdFromDB(username); // Отримати userId для акаунту
            try {
                OpenWebTg(userId, username, update, telegramIds, startIndex);
            }catch (Exception e) {
                System.out.println("Whats happens wrong 2");
            }

            startIndex += 15;
            if (startIndex >= telegramIds.size()) {
                break; // Якщо закінчилися контакти
            }
        }
    }
    private int getUserIdFromDB(String usernametg) {
        int userId = -1;  // Ініціалізуємо userId з неіснуючим значенням

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT id FROM credentials WHERE nickname = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, usernametg);  // Підставляємо значення username в запит

            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                userId = resultSet.getInt("id");  // Отримуємо user_id
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return userId;  // Повертаємо знайдений userId або -1, якщо користувач не знайдений
    }
    private void sendMessagesToUsers(WebDriver driver, List<Long> telegramIds, int startIndex) throws InterruptedException {
        int count = 0;
        for (int i = startIndex; i < telegramIds.size() && count < 15; i++) {
            long telegramId = telegramIds.get(i);
            // Відправка повідомлення користувачу за telegramId
            sendMessageToTelegramId(driver, telegramId);
            count++;
            Thread.sleep(10000); // Затримка між повідомленнями
        }
        System.out.println("Відправлено " + count + " повідомлень.");
    }

    private void sendMessageToTelegramId(WebDriver driver, long telegramId) throws InterruptedException {
        String tgUrl = "https://web.telegram.org/k/#?tgaddr=tg%3A%2F%2Fresolve%3Fdomain%3D" + telegramId;
        driver.get(tgUrl);
        Thread.sleep(2000);

        // Відправка повідомлення
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement messageInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.input-message-input[contenteditable='true']")));
        messageInput.sendKeys("Ваше повідомлення");
        messageInput.sendKeys(Keys.RETURN);
    }

    /*private void fetchAllCredentials(Update update) {
        String sql = "SELECT id, login, password FROM credentials";

        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet resultSet = pstmt.executeQuery()) {

            while (resultSet.next()) {
                int userId = resultSet.getInt("id");
                String login = resultSet.getString("login");
                String passwordValue = resultSet.getString("password");

                System.out.println("UserId: " + userId + ", Login: " + login + ", Password: " + passwordValue);

                // Викликаємо метод для відкриття веб-сторінки та авторизації
                OpenWebTg(userId,login,update); // Передаємо userId для авторизації

                // Обробка логіна та пароля
                processCredentials(login, passwordValue);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }*/

    // Метод для обробки кожного запису credentials
    public static void processCredentials(String login, String password) {
        // Додаємо потрібну логіку для обробки логіну та паролю
        System.out.println("Обробка логіну: " + login + ", паролю: " + password);
    }

}
/*  private void OpenWebTg(int userId, String username, Update update) throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "./chromedriver.exe");
        WebDriver driver = new ChromeDriver();
        String url = "https://web.telegram.org/";

        // Спочатку завантажуємо сторінку
        driver.get(url);
        Thread.sleep(5000);

        // Спроба завантажити дані з localStorage з бази
        loadLocalStorage(driver, userId);

        // Перевіряємо, чи користувач успішно залогінений після завантаження localStorage
        try {
            WebElement profileElement = driver.findElement(By.xpath("//*[contains(@href, 'profile')]"));
            if (profileElement.isDisplayed()) {
                System.out.println("Автоматична авторизація успішна.");
                sendMessageToChat("Ви успішно авторизувались через збережені дані для акаунту: " + username, update.getMessage().getChatId());
                // Продовжити роботу бота
                SendMessageToUser(driver);
            }
        } catch (NoSuchElementException e) {
            System.out.println("Не вдалося автоматично авторизуватись.");

            // Якщо автоматична авторизація не вдалася, запитуємо ручну авторизацію
            sendMessageToChat("Будь ласка, авторизуйтесь вручну в акаунт: " + username, update.getMessage().getChatId());

            // Чекаємо 60 секунд на ручну авторизацію
            Thread.sleep(60000);

            // Після ручної авторизації зберігаємо дані з localStorage
            saveLocalStorage(driver, userId);

            System.out.println("Авторизація пройшла успішно. Дані збережені.");
            sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());

            // Продовжуємо роботу бота після авторизації
            SendMessageToUser(driver);
        }

        // Закриваємо браузер
        driver.quit();
    }*/