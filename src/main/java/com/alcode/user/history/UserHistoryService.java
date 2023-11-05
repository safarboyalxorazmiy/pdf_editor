package com.alcode.user.history;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserHistoryService {
    private final UserHistoryRepository userHistoryRepository;

    public void create(Label label, long chatId, String value) {
        UserHistoryEntity entity = new UserHistoryEntity();
        entity.setLabel(label);
        entity.setUserId(chatId);
        entity.setValue(value);
        userHistoryRepository.save(entity);
    }

    // TODO Foydalanuvchining oxirgi ochgan labelini olish
    public Label getLastLabelByChatId(Long chatId) {
        Optional<UserHistoryEntity> last = userHistoryRepository.getLast(chatId);
        return last.map(UserHistoryEntity::getLabel).orElse(null);
    }

    public String getLastValueByChatId(Long chatId, Label label) {
        Optional<UserHistoryEntity> last = userHistoryRepository.getLastByLabel(label.name(), chatId);
        return last.map(UserHistoryEntity::getValue).orElse(null);
    }

    public void deleteAllByChatId(Long chatId) {
        userHistoryRepository.deleteByUserId(chatId);
    }

}
