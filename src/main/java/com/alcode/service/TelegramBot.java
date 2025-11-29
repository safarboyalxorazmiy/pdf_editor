package com.alcode.service;

import com.alcode.config.BotConfig;
import com.alcode.user.UsersService;
import com.alcode.user.history.Label;
import com.alcode.user.history.UserHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;

import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private static final String GROUP_INVITE_LINK = "https://t.me/+jY83n3T9ES44NGYy";
    private final BotConfig config;

    private final UsersService usersService;

    private final UserHistoryService historyService;

    public TelegramBot(BotConfig config, UsersService usersService, UserHistoryService historyService) {
        this.config = config;
        this.usersService = usersService;
        this.historyService = historyService;

        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Boshlash"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error during setting bot's command list: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();

            if (update.getMessage().hasText()) {
                String messageText = update.getMessage().getText();

                Label last = historyService.getLastLabelByChatId(chatId);
                if (last != null && last.equals(Label.NAME_ENTERING)) {
                    historyService.create(Label.NAME_ENTERED, chatId, messageText);

                    String pdfUrl = historyService.getLastValueByChatId(update.getMessage().getChatId(), Label.PDF_ENTERED);
                    String imageUrl = historyService.getLastValueByChatId(update.getMessage().getChatId(), Label.IMAGE_ENTERED);
                    sendPdf(chatId, messageText, pdfUrl, imageUrl);

                    historyService.deleteAllByChatId(chatId);

                    // DELETE IMAGE AND PDF
                    new File(imageUrl).delete();
                    new File(pdfUrl).delete();

                    historyService.create(Label.PDF_ENTERING, chatId, "NO_VALUE");
                } else if (messageText.startsWith("/")) {
                    switch (messageText) {
                        case "/start" -> {
                            SendMessage message = new SendMessage();
                            message.setText("Men pdfga rasm va nom qo'yib beruvchi botman. Pdf fayl yuboring...");
                            message.setChatId(chatId);

                            try {
                                execute(message);
                            } catch (TelegramApiException ignored) {
                            }

                            historyService.create(Label.PDF_ENTERING, chatId, "NO_VALUE");
                            return;
                        }
                        case "/help" -> {
                            helpCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                            return;
                        }
                        default -> {
                            sendMessage(chatId, "Sorry, command was not recognized");
                            return;
                        }
                    }
                }
            } else if (update.getMessage().hasDocument()) {
                Document document = update.getMessage().getDocument();
                String mimeType = document.getMimeType();

                try {
                    if (mimeType == null) {
                        sendMessage(chatId, "Fayl turi qo'llab quvvatlanmaydi.");
                    }

                    if (mimeType.equals("application/pdf")) {
                        Label last = historyService.getLastLabelByChatId(chatId);


                        if (last.equals(Label.PDF_ENTERING)) {
                            String fileId = document.getFileId();
                            GetFile getFile = new GetFile(fileId);

                            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

                            String originalFilename = file.getFilePath();
                            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

                            String fileUrl = file.getFileUrl(getBotToken());
                            try {
                                URL pdfUrl = new URL(fileUrl);
                                InputStream inputStream = pdfUrl.openStream();

                                Path directoryPath = Path.of("pdfs");
                                if (!Files.exists(directoryPath)) {
                                    Files.createDirectories(directoryPath);
                                }

                                String fileName = UUID.randomUUID() + extension;

                                Path filePath = directoryPath.resolve(fileName);
                                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

                                System.out.println("PDF file saved successfully.");

                                historyService.create(Label.PDF_ENTERED, chatId, "pdfs/" + fileName);
                                historyService.create(Label.IMAGE_ENTERING, chatId, "NO_VALUE");
                                sendMessage(chatId, "Rasm yuboring");
                            } catch (IOException e) {
                                System.out.println("Failed to download the PDF file: " + e.getMessage());
                            }
                        } else {
                            sendMessage(chatId, "Kechirasiz, Bunday amal mavjud emas.");
                        }

                    }
                    else if (mimeType.equals("audio/mp3")) {
                        Label last = historyService.getLastLabelByChatId(chatId);

                        if (last.equals(Label.PDF_ENTERING)) {
                            String fileId = document.getFileId();
                            GetFile getFile = new GetFile(fileId);

                            org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

                            String originalFilename = file.getFilePath();
                            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

                            String fileUrl = file.getFileUrl(getBotToken());
                            try {
                                URL pdfUrl = new URL(fileUrl);
                                InputStream inputStream = pdfUrl.openStream();

                                Path directoryPath = Path.of("audios");
                                if (!Files.exists(directoryPath)) {
                                    Files.createDirectories(directoryPath);
                                }

                                String fileName = UUID.randomUUID() + extension;

                                Path filePath = directoryPath.resolve(fileName);
                                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

                                System.out.println("PDF file saved successfully.");

                                historyService.create(Label.PDF_ENTERED, chatId, "audios/" + fileName);
                                historyService.create(Label.IMAGE_ENTERING, chatId, "NO_VALUE");
                                sendMessage(chatId, "Rasm yuboring");
                            } catch (IOException e) {
                                System.out.println("Failed to download the PDF file: " + e.getMessage());
                            }
                        } else {
                            sendMessage(chatId, "Kechirasiz, Bunday amal mavjud emas.");
                        }

                    } else {
                        sendMessage(chatId, "Kechirasiz, Bu pdf emas.");
                    }
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else if (update.getMessage().hasPhoto()) {
                Label last = historyService.getLastLabelByChatId(update.getMessage().getChatId());

                if (last.equals(Label.IMAGE_ENTERING)) {
                    PhotoSize largestPhoto = update.getMessage().getPhoto().stream()
                            .max((p1, p2) -> Integer.compare(p1.getFileSize(), p2.getFileSize()))
                            .orElse(null);

                    if (largestPhoto != null) {
                        String fileId = largestPhoto.getFileId();
                        String fileUrl = getFileUrlFromId(fileId);

                        System.out.println(fileUrl);

                        String extension = fileUrl.substring(fileUrl.lastIndexOf("."));


                        try {
                            URL pdfUrl = new URL(fileUrl);
                            InputStream inputStream = pdfUrl.openStream();

                            Path directoryPath = Path.of("images");
                            if (!Files.exists(directoryPath)) {
                                Files.createDirectories(directoryPath);
                            }

                            String fileName = UUID.randomUUID() + extension;

                            Path filePath = directoryPath.resolve(fileName);
                            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

                            System.out.println("PDF file saved successfully.");

                            historyService.create(Label.IMAGE_ENTERED, chatId, "images/" + fileName);
                            historyService.create(Label.NAME_ENTERING, chatId, "NO_VALUE");
                            sendMessage(chatId, "Text yuboring");
                        } catch (IOException e) {
                            System.out.println("Failed to download the PDF file: " + e.getMessage());
                        }

                    }
                }
            } else if (update.getMessage().hasAudio()) {
                Audio audio = update.getMessage().getAudio();
                Label last = historyService.getLastLabelByChatId(chatId);
                try {
                    if (last.equals(Label.PDF_ENTERING)) {
                        String fileId = audio.getFileId();
                        GetFile getFile = new GetFile(fileId);
                        org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);

                        String originalFilename = file.getFilePath();
                        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

                        String fileUrl = file.getFileUrl(getBotToken());
                        try {
                            URL pdfUrl = new URL(fileUrl);
                            InputStream inputStream = pdfUrl.openStream();

                            Path directoryPath = Path.of("audios");
                            if (!Files.exists(directoryPath)) {
                                Files.createDirectories(directoryPath);
                            }

                            String fileName = UUID.randomUUID() + extension;

                            Path filePath = directoryPath.resolve(fileName);
                            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

                            System.out.println("PDF file saved successfully.");

                            historyService.create(Label.PDF_ENTERED, chatId, "audios/" + fileName);
                            historyService.create(Label.IMAGE_ENTERING, chatId, "NO_VALUE");
                            sendMessage(chatId, "Rasm yuboring");
                        } catch (IOException e) {
                            System.out.println("Failed to download the PDF file: " + e.getMessage());
                        }
                    } else {
                        sendMessage(chatId, "Kechirasiz, Bunday amal mavjud emas.");
                    }
                } catch (TelegramApiException ignored) {

                }
            }


        }

    }

    private String getFileUrlFromId(String fileId) {
        GetFile getFileRequest = new GetFile(fileId);
        try {
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileRequest);
            String fileUrl = "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
            return fileUrl;
        } catch (TelegramApiException e) {
            // Handle the exception
            e.printStackTrace();
        }
        return null;
    }

    private String generateOneTimeGroupJoinLink() {
        String uniqueLink = UUID.randomUUID().toString();
        return GROUP_INVITE_LINK + "?start=" + uniqueLink;
    }

    private void sendPdf(Long chatId, String name, String pdfUrl, String imageUrl) {
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(chatId);

        // Set the PDF document
        InputFile inputFile = new InputFile(new File(pdfUrl), name);
        sendDocument.setDocument(inputFile);

        // Set the thumbnail image
        InputFile thumbnailFile = new InputFile(
                new File(imageUrl)
        );
        sendDocument.setThumb(thumbnailFile);

        try {
            execute(sendDocument);
        } catch (TelegramApiException e) {
            log.error("Error sending document: {}", e.getMessage());
        }
    }

    private void helpCommandReceived(long chatId, String firstName) {
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();

        message.setChatId(chatId);
        message.setText(textToSend);
        message.enableHtml(true);
        try {
            execute(message);
        } catch (TelegramApiException e) {

        }
    }

    private String generateUniqueFilename(String extension) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        String timestamp = now.format(formatter);
        return "pdf_" + timestamp + extension;
    }

}