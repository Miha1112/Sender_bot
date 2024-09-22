package ua.sh1chiro.SurveyBot.services;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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
import java.util.concurrent.CompletableFuture;


@Slf4j
@Component
@RequiredArgsConstructor
public class AppTelegramBot  extends TelegramLongPollingBot {
    private final BotConfig config;
    private final UserService userService;
    private String jdbcURL = "jdbc:mysql://localhost:3306/senderbot?useSSL=false";
    private String username = "root";
    private String password = "root";
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
        if (update.getMessage().getText().equals("/start_send")) {
            CompletableFuture.runAsync(() -> {
                try {
                    authorizeAndSendMessages(update);
                } catch (Exception e) {
                    System.out.println("Whats happens wrong 1");
                }
            });
        } else if (update.getMessage().getText().equals("/get_command")) {
            String command = "Бот має наступні команди:\n /start_send - це команда для початку роботи бота, " +
                    "\n/get_command - це команда на отримання справки з команд бота" +
                    "\n /add_sender_account - це команда для додавання акаунту з якого буде іти відправка" +
                    "\n /back - це команда для повернення до головного меню та відміна всіх активних статусів бота" +
                    "\n /add_data_in_db_nickname - це команда яка активує режим в якому бот очікуватиме файл з новими користувачами для розсилки";
            sendMessageToChat(command, chatId);
        } else if (update.getMessage().getText().equals("/add_sender_account")) {
            awaitingSenderFile.put(chatId, true);
            sendMessageToChat("Будь ласка, надішліть файл Excel з акаунтами.", chatId);
        }else if(update.getMessage().getText().equals("/back")){
            awaitingFile.put(chatId, false);
            sendMessageToChat("Стек команд очищено", chatId);
        } else if (update.getMessage().getText().equals("/add_data_in_db_nickname")) {
            // Встановлюємо для користувача стан очікування файлу awaitingSenderFile
            awaitingFile.put(chatId, true);
            sendMessageToChat("Будь ласка, надішліть файл Excel з новими користувачами для розсилки.", chatId);
        }  else {
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
        String command = "Бот має наступні команди:\n /start_send - це команда для початку роботи бота, " +
                "\n/get_command - це команда на отримання справки з команд бота" +
                "\n /add_sender_account - це команда для додавання акаунту з якого буде іти відправка" +
                "\n /back - це команда для повернення до головного меню та відміна всіх активних статусів бота" +
                "\n /add_data_in_db_nickname - це команда яка активує режим в якому бот очікуватиме файл з новими користувачами для розсилки";
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
            // Отримуємо об'єкт файлу через Telegram API
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(document.getFileId());

            File file = execute(getFileMethod);

            // Отримуємо шлях до файлу через Telegram API
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
        String jdbcURL = "jdbc:mysql://localhost:3306/senderbot?useSSL=false";
        String username = "root";
        String password = "root";

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
        String jdbcURL = "jdbc:mysql://localhost:3306/senderbot?useSSL=false";
        String username = "root";
        String password = "root";

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

            System.setProperty("webdriver.chrome.driver", "./chromedriver.exe");
            WebDriver driver = new ChromeDriver();
            String url = "https://web.telegram.org/";

            driver.get(url);
            Thread.sleep(5000);
            boolean canAuthorize = loadLocalStorage(driver, userId);

            Thread.sleep(5000);

            if (canAuthorize){
                try {
                    System.out.println("Автоматична авторизація успішна.");
                    //sendMessageToChat("Ви успішно авторизувались через збережені дані для акаунту: " + username, update.getMessage().getChatId());
                    sendMessagesToUsers(driver, usernames, telegramIds, startIndex, update);
                    } catch (NoSuchElementException e) {
                        // Ручна авторизація
                      System.out.println("Something went wrong");
                    }
            }else handleManualAuthorization(driver, usernames, userId, username, update);

            Thread.sleep(2000);
            driver.quit();

    }
    /*
    *  private void handleManualAuthorization(WebDriver driver,List<String> usernames, int userId, String username, Update update) throws InterruptedException {
        System.out.println("Не вдалося автоматично авторизуватись.");

        sendMessageToChat("Будь ласка, авторизуйтесь вручну в акаунт: " + username, update.getMessage().getChatId());

        System.out.println("Очікування ручної авторизації...");

        WebElement element = driver.findElement(By.xpath("//*[text()='Log in by phone Number']"));

        element.click();

        Thread.sleep(3000);
        WebElement phoneNumber =  driver.findElement(By.xpath("//*[@aria-label='Your phone number']"));
        phoneNumber.sendKeys(username);

        Thread.sleep(2000);

        List<WebElement> inputElements = driver.findElements(By.className("input-field-input"));
        System.out.println("Find elements: " + inputElements.size());

        WebElement phone = inputElements.get(1);
        phone.clear();
        phone.sendKeys("+" + username);
        phone.sendKeys(Keys.ENTER);
        Thread.sleep(2000);

        sendMessageToChat("На номер телефону або в телеграм буде надіслано код підтвердження, його потрібно надіслати боту щоб підтвердити авторизацію",update.getMessage().getChatId());
        awaitingCode.put(update.getMessage().getChatId(), true);
       /* saveLocalStorage(driver, userId);

        System.out.println("Авторизація пройшла успішно. Дані збережені.");

        sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());

        List<Long> telegramIds = getTelegramIdsFromDB();
        sendMessagesToUsers(driver,usernames, telegramIds, 0, update);*/

    private void handleManualAuthorization(WebDriver driver,List<String> usernames, int userId, String username, Update update) throws InterruptedException {
        System.out.println("Не вдалося автоматично авторизуватись.");

        sendMessageToChat("Будь ласка, авторизуйтесь вручну в акаунт: " + username, update.getMessage().getChatId());

        System.out.println("Очікування ручної авторизації...");

        try {
            WebElement numberBnt = driver.findElement(By.className("c-ripple"));
            numberBnt.click();
        }catch (NoSuchElementException e) {
            WebElement element = driver.findElement(By.xpath("//*[text()='Log in by phone Number']"));
            element.click();
        }

        Thread.sleep(3000);

        try {
            WebElement phoneNumber = driver.findElement(By.xpath("//*[@data-left-pattern=' ‒‒ ‒‒‒ ‒‒ ‒‒']"));
            phoneNumber.clear();
            phoneNumber.sendKeys(username);
            Thread.sleep(500);
            phoneNumber.sendKeys(Keys.ENTER);
        }catch (NoSuchElementException e) {
            WebElement phoneNumber =  driver.findElement(By.xpath("//*[@aria-label='Your phone number']"));
            phoneNumber.clear();
            phoneNumber.sendKeys("+ " + username);
            phoneNumber.sendKeys(Keys.ENTER);
            Thread.sleep(2000);
        }

        Thread.sleep(2000);

        sendMessageToChat("На номер телефону або в телеграм буде надіслано код підтвердження, його потрібно надіслати боту щоб підтвердити авторизацію",update.getMessage().getChatId());
        awaitingCode.put(update.getMessage().getChatId(), true);
        while (awaitingCode.get(update.getMessage().getChatId())) {
            Thread.sleep(1000); // перевіряємо щосекунди чи користувач надіслав код
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
            WebElement inputCode = driver.findElement(By.xpath("//input[@type='text']"));
            inputCode.sendKeys(code);
            Thread.sleep(6000);
        }



        System.out.println("Авторизація пройшла успішно. Дані збережені.");

        sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());
        saveLocalStorage(driver, userId);

        List<Long> telegramIds = getTelegramIdsFromDB();
        sendMessagesToUsers(driver,usernames, telegramIds, 0, update);
       /* saveLocalStorage(driver, userId);

        System.out.println("Авторизація пройшла успішно. Дані збережені.");

        sendMessageToChat("Авторизація пройшла успішно. Дані збережені для акаунту: " + username, update.getMessage().getChatId());

        List<Long> telegramIds = getTelegramIdsFromDB();
        sendMessagesToUsers(driver,usernames, telegramIds, 0, update);*/
    }
    private void continueAutorization(WebDriver driver,int userId, Update update, List<String> usernames) throws InterruptedException {


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

    private List<String> getSenderAccountsFromDB() {
        List<String> senderAccounts = new ArrayList<>();

        try {
            Connection connection = DriverManager.getConnection(jdbcURL, username, password);
            System.out.println("Успішне підключення до бази даних!");

            String sql = "SELECT login FROM credentials";
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String accountName = resultSet.getString("login");
                senderAccounts.add(accountName);  // Додаємо акаунт до списку
            }

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return senderAccounts;  // Повертаємо список акаунтів для відправки повідомлень
    }



    private void authorizeAndSendMessages(Update update) {
        List<String> senderAccounts = getSenderAccountsFromDB();
        List<Long> telegramIds = getTelegramIdsFromDB();
        List<String> usernames = getTelegramNicknamesFromDB();

        int startIndex = 0;
        for (int i = 0; i < senderAccounts.size(); i++) {
            String accountName = senderAccounts.get(i);
            int userId = getUserIdFromDB(accountName);
            try {
                OpenWebTg(userId, accountName, update,usernames, telegramIds, startIndex);
            } catch (Exception e) {
                sendMessageToChat("Щось пішло не так при авторизації або відправці повідомлень. Повторіть спробу",update.getMessage().getChatId());
                System.out.println("Щось пішло не так при авторизації або відправці повідомлень. Повторіть спробу");
            }
            startIndex += 15;
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

    private void sendMessagesToUsers(WebDriver driver, List<String> usernames, List<Long> telegramIds, int startIndex, Update update) throws InterruptedException {
        int count = 0;
        for (int i = startIndex; i < telegramIds.size() && count < 15; i++) {
            String username = usernames.get(i);
            long telegramId = telegramIds.get(i);

            if (username != null && !username.isEmpty()) {
                sendMessageToTelegramUsername(driver, username);
            } else {
                sendMessageToTelegramId(driver, telegramId);
            }

            count++;
            Thread.sleep(10000);
        }

        System.out.println("Відправлено " + count + " повідомлень.");
        sendMessageToChat("Відправлено " + count + " повідомлень.", update.getMessage().getChatId());
    }

    private void sendMessageToTelegramUsername(WebDriver driver, String username) throws InterruptedException {
        String tgUrl = "https://web.telegram.org/k/#?tgaddr=tg%3A%2F%2Fresolve%3Fdomain%3D" + username;
        driver.get(tgUrl);
        Thread.sleep(2000);

        // Відправка повідомлення
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement messageInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.input-message-input[contenteditable='true']")));
        messageInput.sendKeys("Доброго дня, підкажіть будь ласка, чи ви ще знаходитесь у пошуках роботи?");
        messageInput.sendKeys(Keys.RETURN);
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
    private void sendMessageToTelegramNickName(WebDriver driver, String nickName) throws InterruptedException {
        String tgUrl = "https://web.telegram.org/k/#?tgaddr=tg%3A%2F%2Fresolve%3Fdomain%3D" + nickName;
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