package ua.sh1chiro.SurveyBot.services;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
import java.util.concurrent.CompletableFuture;


@Slf4j
@Component
@RequiredArgsConstructor
public class AppTelegramBot  extends TelegramLongPollingBot {
    private final BotConfig config;
    private final UserService userService;
    private String jdbcURL = "jdbc:mysql://51.75.70.29:3306/senderbot?useSSL=false";
    private String username = "officeboy";
    private String password = "officeboy";
    String code = "";
    private Map<Long, Boolean> awaitingFile = new HashMap<>();
    private Map<Long, Boolean> awaitingSenderFile = new HashMap<>();
    private Map<Long, Boolean> awaitingCode = new HashMap<>();
    private Map<Long, String> userSentCodes = new HashMap<>();
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
                String messageText = update.getMessage().getText();
                System.out.println("send text: " + messageText);
                // Якщо бот чекає на код від користувача
                if (awaitingCode.getOrDefault(chatId, false)) {
                    // Зберігаємо код у мапу
                    userSentCodes.put(chatId, messageText);
                    // Вимикаємо стан очікування коду
                    awaitingCode.put(chatId, false);
                    code = messageText;
                    sendMessageToChat("Код отримано. Обробляємо його...", chatId);
                    return;
                }
                if(user != null)
                    checkText(update, chatId, user);
                if(user == null && update.getMessage().getText().equals("/start"))
                    registration(update);
            }else if (update.getMessage().hasDocument()) {
                if (awaitingFile.getOrDefault(chatId, false)) {
                    Document document = update.getMessage().getDocument();

                    String fileName = document.getFileName();
                    if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                        try (InputStream fileStream = downloadFileAsStream(document)) {
                            if (fileStream != null) {
                                sendMessageToChat("Бот отримав і обробляє Excel файл: " + fileName, chatId);
                                System.out.println("Бот отримав і обробляє Excel файл: " + fileName);
                                addUserForSpamToBd(fileStream);
                                awaitingFile.put(chatId, false);
                                sendMessageToChat("Бот завершив обробку, вся інформація додана до бази з файлу: " + fileName, chatId);
                            } else {
                                sendMessageToChat("Помилка під час отримання файлу.", chatId);
                                System.out.println("Помилка під час отримання файлу... ");
                            }
                        } catch (IOException e) {
                            sendMessageToChat("Помилка під час обробки файлу: " + e.getMessage(), chatId);
                            System.out.println("Помилка під час отримання файлу... ");
                            e.printStackTrace();
                        }
                    } else {
                        sendMessageToChat("Отриманий файл не є Excel файлом. Будь ласка, надішліть файл у форматі .xls або .xlsx.", chatId);
                    }
                }else if(awaitingSenderFile.getOrDefault(chatId, false)){
                    Document document = update.getMessage().getDocument();

                    String fileName = document.getFileName();
                    if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                        try (InputStream fileStream = downloadFileAsStream(document)) {
                            if (fileStream != null) {
                                sendMessageToChat("Бот отримав і обробляє Excel файл: " + fileName, chatId);
                                System.out.println("Бот отримав і обробляє Excel файл: " + fileName);
                                addUserForSenderToBd(fileStream);
                                awaitingSenderFile.put(chatId, false);
                                sendMessageToChat("Бот завершив обробку, вся інформація додана до бази з файлу: " + fileName, chatId);
                            } else {
                                sendMessageToChat("Помилка під час отримання файлу.", chatId);
                                System.out.println("Помилка під час отримання файлу... ");
                            }
                        } catch (IOException e) {
                            sendMessageToChat("Помилка під час обробки файлу: " + e.getMessage(), chatId);
                            System.out.println("Помилка під час отримання файлу... ");
                            e.printStackTrace();
                        }
                    } else {
                        sendMessageToChat("Отриманий файл не є Excel файлом. Будь ласка, надішліть файл у форматі .xls або .xlsx.", chatId);
                    }
                }else {
                    sendMessageToChat("Цей бот не очікував файл від вас. Для початку надішліть команду /add_data_in_db_nickname.", chatId);
                }

            }
        }
    }
    private String getUserSentCode(Long chatId) {
        // Перевіряємо чи є код у мапі
        if (userSentCodes.containsKey(chatId)) {
            return userSentCodes.get(chatId);
        } else {
            return null; // Повертаємо null, якщо код ще не отриманий
        }
    }
    private void checkText(Update update, Long chatId, User user) {
        String text = update.getMessage().getText();

        if (text.equals("/start_send")) {
            CompletableFuture.runAsync(() -> {
                try {
                    authorizeAndSendMessages(update);
                } catch (Exception e) {
                    System.out.println("Whats happens wrong 1");
                }
            });
        }
        else if (text.equals("/get_command")) {
            String command = "Бот має наступні команди:" +
                    "\n/start_send - команда для початку роботи;" +
                    "\n/get_command - команда на отримання справки з команд бота;" +
                    "\n/add_sender_account - команда для додавання акаунту, з якого буде відбуватись відправка повідомлень;" +
                    "\n/back - команда для повернення до головного меню та відміна всіх активних статусів бота;" +
                    "\n/add_data_in_db_nickname - команда, яка активує режим, в якому бот очікуватиме файл з новими користувачами для розсилки;" +
                    "\n/activate_account - команда для перевірки чи всі акаунти активовані, за потреби буде виведено повідомлення про необхідність активації" +
                    "\n/change_text %текст% - команда, що змінює текст розсилки. За замовчуванням - \"Ваше повідомлення\"" +
                    "\nПриклад:" +
                    "\n/change_text Привіт, гарного дня! - для розсилки буде встановлена фраза \"Привіт, гарного дня!\".";
            sendMessageToChat(command, update.getMessage().getChatId());
        }
        else if (text.equals("/add_sender_account")) {
            awaitingSenderFile.put(chatId, true);
            sendMessageToChat("Будь ласка, надішліть файл Excel з акаунтами.", chatId);
        }
        else if(text.equals("/back")){
            awaitingFile.put(chatId, false);
            sendMessageToChat("Стек команд очищено", chatId);
        }
        else if (text.equals("/add_data_in_db_nickname")) {
            awaitingFile.put(chatId, true);
            sendMessageToChat("Будь ласка, надішліть файл Excel з новими користувачами для розсилки.", chatId);
        }
        else if(text.startsWith("/change_text")){
            String newsletterText = text.replace("/change_text ", "");
            user.setNewsletterText(newsletterText);
            userService.save(user);
            sendMessageToChat("Текст розсилки змінено на:\n" + newsletterText, user.getTelegramId());
        }
        else if(text.startsWith("/activate_account")){
            String number = text.replace("/change_account ", "");
            System.out.println("Number for replace: " + number);
            try {
                changeSendingAccount(update);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        else {
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

        user.setNewsletterText("Ваше повідомлення.");

        userService.save(user);
        String command = "Бот має наступні команди:" +
                "\n/start_send - команда для початку роботи;" +
                "\n/get_command - команда на отримання справки з команд бота;" +
                "\n/add_sender_account - команда для додавання акаунту, з якого буде відбуватись відправка повідомлень;" +
                "\n/back - команда для повернення до головного меню та відміна всіх активних статусів бота;" +
                "\n/add_data_in_db_nickname - команда, яка активує режим, в якому бот очікуватиме файл з новими користувачами для розсилки;" +
                "\n/activate_account - команда для перевірки чи всі акаунти активовані, за потреби буде виведено повідомлення про необхідність активації" +
                "\n/change_text %текст% - команда, що змінює текст розсилки. За замовчуванням - \"Ваше повідомлення\"" +
                "\nПриклад:" +
                "\n/change_text Привіт, гарного дня! - для розсилки буде встановлена фраза \"Привіт, гарного дня!\".";
        sendMessageToChat(command, update.getMessage().getChatId());
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
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell nicknameCell = row.getCell(0);
                Cell telegramIdCell = row.getCell(1);
                if (nicknameCell != null && telegramIdCell != null) {
                    String nickname = nicknameCell.getStringCellValue();
                    if (telegramIdCell.getCellType() == CellType.NUMERIC) {
                        long telegramId = (long) telegramIdCell.getNumericCellValue();
                        insertUserIntoDB(nickname, telegramId);
                    } else {
                        System.out.println("Телеграм ID не є числовим значенням у рядку: " + row.getRowNum());
                    }
                } else {
                    System.out.println("Пропущений рядок: " + row.getRowNum() + " через відсутність даних.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void addUserForSenderToBd(InputStream fileStream) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(fileStream)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell nicknameCell = row.getCell(0);
                if (nicknameCell != null) {
                    String nickname = nicknameCell.getStringCellValue();
                    insertUserIntoSenderDB(nickname);
                } else {
                    System.out.println("Пропущений рядок: " + row.getRowNum() + " через відсутність даних.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private InputStream downloadFileAsStream(Document document) {
        System.out.println("Start adding Excel file");

        try {
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());

            File file = execute(getFileMethod);

            String filePath = file.getFilePath();
            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + filePath;

            // Завантажуємо файл як InputStream
            return new URL(fileUrl).openStream();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private static void insertUserIntoDB(String nickname, long telegramId) {
        String jdbcURL = "jdbc:mysql://51.75.70.29:3306/senderbot?useSSL=false";
        String username = "officeboy";
        String password = "officeboy";

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
    private static void insertUserIntoSenderDB(String nickname) {
        String jdbcURL = "jdbc:mysql://51.75.70.29:3306/senderbot?useSSL=false";
        String username = "officeboy";
        String password = "officeboy";

        String sql = "INSERT INTO credentials (login) VALUES (?, ?)";

        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, nickname);

            pstmt.executeUpdate();
            System.out.println("User added: " + nickname);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void OpenWebTg(int userId, String username, Update update,List<String> usernames, List<Long> telegramIds, int startIndex) throws InterruptedException {
//            System.setProperty("webdriver.chrome.driver", "./chromedriver.exe");
           System.setProperty("webdriver.chrome.driver", "/home/ubuntu/bot/chromedriver");
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
           WebDriver driver = new ChromeDriver(options);
            String url = "https://web.telegram.org/";

            driver.get(url);
            Thread.sleep(5000);
            boolean canAuthorize = loadLocalStorage(driver, userId);

            Thread.sleep(5000);

            if (canAuthorize){
                try {
                    System.out.println("Автоматична авторизація успішна.");
                    //sendMessageToChat("Ви успішно авторизувались через збережені дані для акаунту: " + username, update.getMessage().getChatId());
                    sendMessagesToUsers(driver, usernames, telegramIds, startIndex, update,userId);
                    } catch (NoSuchElementException e) {
                        // Ручна авторизація
                    e.printStackTrace();
                      System.out.println("Something went wrong");
                    }
            }else handleManualAuthorization(driver, usernames, userId, username, update);

            Thread.sleep(2000);
            driver.quit();

    }

    private void handleManualAuthorization(WebDriver driver,List<String> usernames, int userId, String username, Update update) throws InterruptedException {
        System.out.println("Не вдалося автоматично авторизуватись.");
        autorize(update, driver, userId, username);
        List<Long> telegramIds = getTelegramIdsFromDB();
        sendMessagesToUsers(driver,usernames, telegramIds, 0, update, userId);
    }

    private void autorize(Update update, WebDriver driver,int userId, String username) {
        sendMessageToChat("Будь ласка, авторизуйтесь вручну в акаунт: " + username, update.getMessage().getChatId());

        System.out.println("Очікування ручної авторизації...");

        try {
            WebElement numberBnt = driver.findElement(By.className("c-ripple"));
            numberBnt.click();
        }catch (NoSuchElementException e) {
            e.printStackTrace();
            WebElement element = driver.findElement(By.xpath("//*[text()='Log in by phone Number']"));
            element.click();
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            WebElement phoneNumber = driver.findElement(By.xpath("//*[@data-left-pattern=' ‒‒ ‒‒‒ ‒‒ ‒‒']"));
            phoneNumber.clear();
            phoneNumber.sendKeys(username);
            Thread.sleep(500);
            phoneNumber.sendKeys(Keys.ENTER);
        }catch (NoSuchElementException e) {
            e.printStackTrace();
            WebElement phoneNumber =  driver.findElement(By.xpath("//*[@aria-label='Your phone number']"));
            phoneNumber.clear();
            phoneNumber.sendKeys("+ " + username);
            phoneNumber.sendKeys(Keys.ENTER);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                e.printStackTrace();
                throw new RuntimeException(ex);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        sendMessageToChat("На номер телефону або в телеграм буде надіслано код підтвердження, його потрібно надіслати боту щоб підтвердити авторизацію",update.getMessage().getChatId());
        awaitingCode.put(update.getMessage().getChatId(), true);
        while (awaitingCode.get(update.getMessage().getChatId())) {
            try {
                Thread.sleep(1000); // перевіряємо щосекунди чи користувач надіслав код
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        // Отримуємо код, який користувач надіслав
        String confirmationCode = getUserSentCode(update.getMessage().getChatId());

        if (confirmationCode == null) {
            sendMessageToChat("Код не отримано. Спробуйте ще раз.", update.getMessage().getChatId());
            return;
        }
        try {
            WebElement inputCode = driver.findElement(By.tagName("input"));
            inputCode.sendKeys(code);
            Thread.sleep(6000);
        }catch (NoSuchElementException e) {
            e.printStackTrace();
            WebElement inputCode = driver.findElement(By.xpath("//input[@type='text']"));
            inputCode.sendKeys(code);
            try {
                Thread.sleep(6000);
            } catch (InterruptedException ex) {
                e.printStackTrace();
                throw new RuntimeException(ex);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        System.out.println("Авторизація пройшла успішно. Дані збережені.");

        sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());
        saveLocalStorage(driver, userId);

    }

    private void changeSendingAccount(Update update) throws InterruptedException {
            WebDriver driver = new ChromeDriver();
            String url = "https://web.telegram.org/";
            driver.get(url);

            Thread.sleep(4000);

            checkLocalStorageForAllAccounts(driver,update);
    }
    public boolean checkLocalStorageForAllAccounts(WebDriver driver, Update update) throws InterruptedException {
        List<Map<String, Object>> senderAccounts = getSenderAccountsFromDB(); // Отримуємо список акаунтів

        for (Map<String, Object> account : senderAccounts) {
            String login = (String) account.get("login");
            int userId = (int) account.get("user_id");

            boolean localStorageFound = checkLocalStorageForAccount(driver, userId);

            if (!localStorageFound) {
                System.out.println("Для акаунту " + login + " відсутнє локальне сховище." + userId);
                autorize(update, driver, userId, login);  // Передаємо user_id в метод авторизації
            }
        }

        return true; // Повертаємо true, якщо для всіх акаунтів знайдено локальне сховище
    }


    private boolean checkLocalStorageForAccount(WebDriver driver, int userId) {
        try (Connection conn = DriverManager.getConnection(jdbcURL, username, password)) {
            String sql = "SELECT COUNT(*) AS count FROM user_local_storage WHERE user_id = ?";
            PreparedStatement statement = conn.prepareStatement(sql);
            statement.setInt(1, userId);  // Передаємо user_id у запит

            ResultSet resultSet = statement.executeQuery();

            if (resultSet.next()) {
                int count = resultSet.getInt("count");
                return count > 0; // Перевіряємо, чи знайдено хоча б один запис у локальному сховищі
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
    public boolean loadLocalStorage(WebDriver driver, int userId) {
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
                Thread.sleep(2000);  // Додаємо невелику затримку для надійності
                driver.navigate().refresh();
                return  true;
            } else {
                System.out.println("Дані localStorage для користувача з id " + userId + " не знайдено.");
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
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

        return telegramIds;
    }
    private List<String> getTelegramNicknamesFromDB() {
        List<String> telegramNicknames = new ArrayList<>();

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT nickname FROM users";
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String nickname = resultSet.getString("nickname");
                if (nickname != null && !nickname.isEmpty()) {
                    telegramNicknames.add(nickname);  // Додаємо нікнейм до списку, якщо він не порожній
                }
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return telegramNicknames;  // Повертаємо список нікнеймів
    }

    private List<Map<String, Object>> getSenderAccountsFromDB() {
        List<Map<String, Object>> senderAccounts = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(jdbcURL, username, password)) {
            System.out.println("Успішне підключення до бази даних!");
            String sql = "SELECT login, id FROM credentials"; // Отримуємо логін і user_id
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> accountData = new HashMap<>();
                accountData.put("login", resultSet.getString("login"));  // Зберігаємо логін
                accountData.put("user_id", resultSet.getInt("id"));  // Зберігаємо user_id
                senderAccounts.add(accountData);  // Додаємо мапу з даними до списку
            }
            resultSet.close();
            statement.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return senderAccounts;  // Повертаємо список мап із даними про акаунти
    }




    private void authorizeAndSendMessages(Update update) {
        List<Map<String, Object>> senderAccounts = getSenderAccountsFromDB();  // Отримуємо логін і user_id
        List<Long> telegramIds = getTelegramIdsFromDB();
        List<String> usernames = getTelegramNicknamesFromDB();

        int startIndex = 0;

        for (Map<String, Object> accountData : senderAccounts) {
            String accountName = (String) accountData.get("login");  // Отримуємо логін акаунта
            int userId = (int) accountData.get("user_id");  // Отримуємо user_id акаунта
            try {
                OpenWebTg(userId, accountName, update, usernames, telegramIds, startIndex);
            } catch (Exception e) {
                e.printStackTrace();
                sendMessageToChat("Щось пішло не так при авторизації або відправці повідомлень. Повторіть спробу", update.getMessage().getChatId());
                System.out.println("Щось пішло не так при авторизації або відправці повідомлень. Повторіть спробу");
            }

            startIndex += 15;  // Переміщуємо індекс для наступної партії Telegram ID
            if (startIndex >= telegramIds.size()) {
                break;
            }
        }
    }

    private int getUserIdFromDB(String accountName) {
        int userId = -1;  // Ініціалізуємо userId з неіснуючим значенням

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT id FROM credentials WHERE login = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, accountName);  // Підставляємо значення accountName в запит

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

        return userId;  // Повертаємо знайдений userId або -1, якщо акаунт не знайдений
    }

    private void sendMessagesToUsers(WebDriver driver, List<String> usernames, List<Long> telegramIds, int startIndex, Update update, int userId) throws InterruptedException {
        User user = userService.getByTelegramId(update.getMessage().getChatId());
        String text = "Default text";
        if(user != null) {
            int count = 0;
            for (int i = startIndex; i < telegramIds.size() && count < 15; i++) {
                String username = usernames.get(i);
                long telegramId = telegramIds.get(i);
                if (user.getNewsletterText() != null && !user.getNewsletterText().isEmpty()) {
                    text = user.getNewsletterText();
                }

                if (username != null && !username.isEmpty()) {
                    sendMessageToTelegramUsername(driver, username, text);
                    Thread.sleep(4000);
                    clearBrowserCache(driver);
                    Thread.sleep(2000);
                    loadLocalStorage(driver,userId);
                } else {
                    sendMessageToTelegramId(driver, telegramId, user.getNewsletterText());
                }

                count++;
                Thread.sleep(10000);
            }

            System.out.println("Відправлено " + count + " повідомлень.");
            sendMessageToChat("Відправлено " + count + " повідомлень.", update.getMessage().getChatId());
        }
    }
    public void clearBrowserCache(WebDriver driver) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // Використовуємо JavaScript для очищення кешу
        js.executeScript("window.localStorage.clear();");
        js.executeScript("window.sessionStorage.clear();");

        // Очистити всі кешовані ресурси
        driver.manage().deleteAllCookies(); // Видалення кукіс
        driver.navigate().refresh();
        Thread.sleep(2000);
        System.out.println("Кеш браузера очищено");
    }
    private void sendMessageToTelegramUsername(WebDriver driver, String username, String newsletterText) throws InterruptedException {
        String tgUrl = "https://web.telegram.org/k/#?tgaddr=tg%3A%2F%2Fresolve%3Fdomain%3D" + username;
        driver.get(tgUrl);
        Thread.sleep(2000);
        // Відправка повідомлення
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            WebElement messageInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@contenteditable='true' and contains(@class, 'input-message-input')]")));

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].innerText = arguments[1];", messageInput, newsletterText);
            Thread.sleep(2000);
            messageInput = wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@contenteditable='true' and contains(@class, 'input-message-input')]")));
            messageInput.sendKeys(Keys.RETURN);
            System.out.println("Повідомлення відправлено для користувача " + username);

        } catch (TimeoutException e) {
            e.printStackTrace();
            System.out.println("Поле введення повідомлення не знайдено для користувача " + username);
        }
    }

    private void sendMessageToTelegramId(WebDriver driver, long telegramId, String newsletterText) throws InterruptedException {
        String tgUrl = "https://web.telegram.org/k/#?tgaddr=tg%3A%2F%2Fresolve%3Fdomain%3D" + telegramId;
        driver.get(tgUrl);
        Thread.sleep(2000);

        // Відправка повідомлення
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement messageInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.input-message-input[contenteditable='true']")));
        messageInput.sendKeys(newsletterText);
        messageInput.sendKeys(Keys.RETURN);
    }

}