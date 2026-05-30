package com.finny.api.infrastructure.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FCMService {

    public void sendNotification(String deviceToken, String title, String body) {
        Message message = Message.builder()
                .setToken(deviceToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.debug("Push enviado | token: {}... | response: {}", deviceToken.substring(0, 10), response);
        } catch (FirebaseMessagingException e) {
            log.warn("Falha ao enviar push | token: {}... | erro: {}", deviceToken.substring(0, 10), e.getMessage());
        }
    }
}
