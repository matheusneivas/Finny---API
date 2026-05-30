package com.finny.api.infrastructure.scheduler;

import com.finny.api.domain.entity.Transaction;
import com.finny.api.domain.repository.TransactionRepository;
import com.finny.api.domain.repository.UserDeviceTokenRepository;
import com.finny.api.infrastructure.firebase.FCMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final TransactionRepository transactionRepository;
    private final UserDeviceTokenRepository deviceTokenRepository;
    private final FCMService fcmService;

    @Scheduled(cron = "0 0 10 * * *")
    public void sendDueTransactionReminders() {
        int today = LocalDate.now().getDayOfMonth();
        log.info("Verificando vencimentos do dia {}", today);

        List<Transaction> dueToday = transactionRepository.findByDueDayOfMonth(today);

        if (dueToday.isEmpty()) {
            log.info("Nenhum vencimento para hoje");
            return;
        }

        for (Transaction transaction : dueToday) {
            List<String> tokens = deviceTokenRepository.findTokensByUserId(transaction.getUser().getId());
            if (tokens.isEmpty()) continue;

            String title = "Vencimento hoje 💸";
            String itemName = transaction.getDescription() != null && !transaction.getDescription().isBlank()
                    ? transaction.getDescription()
                    : transaction.getCategory().getName();
            String body = itemName + " — R$ " + formatAmount(transaction);

            for (String token : tokens) {
                fcmService.sendNotification(token, title, body);
            }

            log.info("Notificações enviadas | user: {} | item: {}", transaction.getUser().getId(), itemName);
        }
    }

    private String formatAmount(Transaction t) {
        return String.format("%,.2f", t.getAmount())
                .replace(",", "X").replace(".", ",").replace("X", ".");
    }
}
