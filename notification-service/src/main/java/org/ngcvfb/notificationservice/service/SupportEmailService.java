package org.ngcvfb.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public void sendSupportReply(String toEmail, String originalQuestion, String adminReply) {
        send(toEmail, "EventHub.kz — ответ службы поддержки",
                buildSupportHtml(originalQuestion, adminReply), "support reply");
    }

    public void sendRequestRejected(String toEmail, String eventTitle, String reason) {
        send(toEmail, "EventHub.kz — заявка на мероприятие отклонена",
                buildRejectionHtml(eventTitle, reason), "request rejection");
    }

    private void send(String toEmail, String subject, String html, String label) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip {} email: toEmail is empty", label);
            return;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Skip {} email: spring.mail.username is not configured", label);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("{} email sent to {}", label, toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send {} email to {}: {}", label, toEmail, e.getMessage());
        }
    }

    private String buildSupportHtml(String originalQuestion, String adminReply) {
        String q = escape(originalQuestion == null ? "" : originalQuestion);
        String a = escape(adminReply == null || adminReply.isBlank() ? "(без комментария)" : adminReply);
        return ""
                + "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f1c16;\">"
                + "  <h2 style=\"color:#2a5475;margin:0 0 12px;\">Ответ службы поддержки EventHub.kz</h2>"
                + "  <p style=\"color:#6b6253;margin:0 0 18px;\">Спасибо за обращение. Ниже — ваш вопрос и ответ команды.</p>"
                + "  <div style=\"background:#f4eee3;border-left:3px solid #aecee3;padding:12px 14px;border-radius:6px;margin-bottom:14px;\">"
                + "    <div style=\"font-size:12px;color:#6b6253;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Ваш вопрос</div>"
                + "    <div style=\"white-space:pre-wrap;\">" + q + "</div>"
                + "  </div>"
                + "  <div style=\"background:#dceaf3;border-left:3px solid #2a5475;padding:12px 14px;border-radius:6px;\">"
                + "    <div style=\"font-size:12px;color:#2a5475;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Ответ команды</div>"
                + "    <div style=\"white-space:pre-wrap;\">" + a + "</div>"
                + "  </div>"
                + "  <p style=\"color:#8c8473;font-size:12px;margin-top:24px;\">Это автоматическое письмо — отвечать на него не нужно. Если ответ не помог, напишите новое обращение через раздел «Поддержка» на сайте.</p>"
                + "</div>";
    }

    private String buildRejectionHtml(String eventTitle, String reason) {
        String t = escape(eventTitle == null ? "(без названия)" : eventTitle);
        String r = escape(reason == null || reason.isBlank() ? "(без комментария)" : reason);
        return ""
                + "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f1c16;\">"
                + "  <h2 style=\"color:#2a5475;margin:0 0 12px;\">Заявка на мероприятие отклонена</h2>"
                + "  <p style=\"color:#6b6253;margin:0 0 18px;\">Команда EventHub.kz рассмотрела вашу заявку и не приняла её к публикации.</p>"
                + "  <div style=\"background:#f4eee3;border-left:3px solid #aecee3;padding:12px 14px;border-radius:6px;margin-bottom:14px;\">"
                + "    <div style=\"font-size:12px;color:#6b6253;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Мероприятие</div>"
                + "    <div style=\"font-weight:600;\">" + t + "</div>"
                + "  </div>"
                + "  <div style=\"background:#dceaf3;border-left:3px solid #2a5475;padding:12px 14px;border-radius:6px;\">"
                + "    <div style=\"font-size:12px;color:#2a5475;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Причина отказа</div>"
                + "    <div style=\"white-space:pre-wrap;\">" + r + "</div>"
                + "  </div>"
                + "  <p style=\"color:#8c8473;font-size:12px;margin-top:24px;\">Вы можете доработать заявку с учётом комментария и подать её снова через раздел «Создать событие».</p>"
                + "</div>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
