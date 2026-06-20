package org.ngcvfb.notificationservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    // Базовый адрес сайта для ссылки отметки прихода в QR билета.
    @Value("${app.frontend-url:https://eventhub.kz}")
    private String frontendUrl;

    public void sendSupportReply(String toEmail, String originalQuestion, String adminReply) {
        send(toEmail, "EventHub.kz — ответ службы поддержки",
                buildSupportHtml(originalQuestion, adminReply), "support reply");
    }

    public void sendRequestRejected(String toEmail, String eventTitle, String reason) {
        send(toEmail, "EventHub.kz — заявка на мероприятие отклонена",
                buildRejectionHtml(eventTitle, reason), "request rejection");
    }

    public void sendEventReminder(String toEmail, String eventTitle, LocalDateTime eventDate) {
        send(toEmail, "EventHub.kz — напоминание о мероприятии",
                buildReminderHtml(eventTitle, eventDate), "event reminder");
    }

    public void sendRegistrationConfirmed(String toEmail, String username, String eventTitle,
                                          LocalDateTime eventDate, LocalDateTime registeredAt,
                                          String code, Long eventId, String location, boolean online) {
        String html = buildTicketHtml(username, eventTitle, eventDate, registeredAt, code, location, online);
        byte[] qr = generateTicketQr(code);
        sendTicketEmail(toEmail, "EventHub.kz — ваш билет на мероприятие", html, qr);
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

    private String buildReminderHtml(String eventTitle, LocalDateTime eventDate) {
        String t = escape(eventTitle == null ? "(без названия)" : eventTitle);
        String d = eventDate == null ? "(дата уточняется)"
                : eventDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        return ""
                + "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f1c16;\">"
                + "  <h2 style=\"color:#2a5475;margin:0 0 12px;\">Скоро мероприятие на EventHub.kz</h2>"
                + "  <p style=\"color:#6b6253;margin:0 0 18px;\">Напоминаем о мероприятии, которое вы отметили. Не пропустите!</p>"
                + "  <div style=\"background:#f4eee3;border-left:3px solid #aecee3;padding:12px 14px;border-radius:6px;margin-bottom:14px;\">"
                + "    <div style=\"font-size:12px;color:#6b6253;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Мероприятие</div>"
                + "    <div style=\"font-weight:600;\">" + t + "</div>"
                + "  </div>"
                + "  <div style=\"background:#dceaf3;border-left:3px solid #2a5475;padding:12px 14px;border-radius:6px;\">"
                + "    <div style=\"font-size:12px;color:#2a5475;text-transform:uppercase;letter-spacing:0.4px;margin-bottom:6px;\">Когда</div>"
                + "    <div style=\"font-weight:600;\">" + d + "</div>"
                + "  </div>"
                + "  <p style=\"color:#8c8473;font-size:12px;margin-top:24px;\">Это автоматическое письмо — отвечать на него не нужно. Подробности мероприятия доступны в вашем разделе сохранённых событий на сайте.</p>"
                + "</div>";
    }

    private String buildTicketHtml(String username, String eventTitle, LocalDateTime eventDate,
                                   LocalDateTime registeredAt, String code, String location, boolean online) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        String name = escape(username == null || username.isBlank() ? "участник" : username);
        String t = escape(eventTitle == null ? "(без названия)" : eventTitle);
        String when = eventDate == null ? "(дата уточняется)" : eventDate.format(fmt);
        String reg = registeredAt == null ? "—" : registeredAt.format(fmt);
        String where = online ? "Онлайн" : escape(location == null || location.isBlank() ? "—" : location);
        String c = escape(code == null ? "—" : code);
        String calUrl = googleCalUrl(eventTitle, eventDate, location, online);
        String calBtn = calUrl == null ? "" : (""
                + "  <div style=\"text-align:center;margin-top:16px;\">"
                + "    <a href=\"" + calUrl.replace("&", "&amp;") + "\" style=\"display:inline-block;padding:10px 18px;background:#2a5475;color:#ffffff;text-decoration:none;border-radius:999px;font-weight:600;font-size:14px;\">Добавить в Google Календарь</a>"
                + "  </div>");
        return ""
                + "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1f1c16;\">"
                + "  <h2 style=\"color:#2a5475;margin:0 0 4px;\">Ваш билет на мероприятие</h2>"
                + "  <p style=\"color:#6b6253;margin:0 0 18px;\">Здравствуйте, " + name + "! Запись подтверждена. Покажите QR или назовите код организатору на входе.</p>"
                + "  <div style=\"text-align:center;margin:0 0 18px;\">"
                + "    <img src=\"cid:ticketqr\" alt=\"QR билет\" width=\"200\" height=\"200\" style=\"border:1px solid #e8dfce;border-radius:8px;padding:8px;background:#fff;\"/>"
                + "    <div style=\"margin-top:10px;font-family:monospace;font-size:22px;font-weight:bold;letter-spacing:3px;color:#2a5475;\">" + c + "</div>"
                + "    <div style=\"font-size:12px;color:#8c8473;\">код билета</div>"
                + "  </div>"
                + "  <table style=\"width:100%;border-collapse:collapse;\">"
                + row("Мероприятие", t)
                + row("Когда", when)
                + row("Где", where)
                + row("Дата регистрации", reg)
                + "  </table>"
                + calBtn
                + "  <p style=\"color:#8c8473;font-size:12px;margin-top:24px;\">Это автоматическое письмо — отвечать на него не нужно. Отменить запись можно на странице мероприятия на сайте.</p>"
                + "</div>";
    }

    private String googleCalUrl(String title, LocalDateTime start, String location, boolean online) {
        if (start == null) return null;
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String loc = online ? "Онлайн" : (location == null ? "" : location);
        return "https://calendar.google.com/calendar/render?action=TEMPLATE"
                + "&text=" + enc(title == null ? "Мероприятие" : title)
                + "&dates=" + start.format(f) + "/" + start.plusHours(2).format(f)
                + "&location=" + enc(loc)
                + "&details=" + enc("EventHub.kz");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String row(String label, String value) {
        return "<tr>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #efe7d8;font-size:12px;color:#8c8473;text-transform:uppercase;letter-spacing:0.4px;width:42%;\">" + label + "</td>"
                + "<td style=\"padding:8px 0;border-bottom:1px solid #efe7d8;font-weight:600;\">" + value + "</td>"
                + "</tr>";
    }

    /**
     * Генерирует PNG QR-кода со ссылкой отметки прихода /checkin/&lt;код&gt; — той же,
     * что кодирует билет на сайте. Нативная камера телефона открывает страницу
     * отметки. Код-билета достаточно для check-in, лишние PII в QR не кладём.
     * null при ошибке.
     */
    private byte[] generateTicketQr(String code) {
        try {
            String payload = frontendUrl + "/checkin/" + code;
            BitMatrix matrix = new MultiFormatWriter().encode(
                    payload, BarcodeFormat.QR_CODE, 220, 220,
                    Map.of(EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate ticket QR: {}", e.getMessage());
            return null;
        }
    }

    private void sendTicketEmail(String toEmail, String subject, String html, byte[] qrPng) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip ticket email: toEmail is empty");
            return;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Skip ticket email: spring.mail.username is not configured");
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true — нужно для встраивания QR как inline-изображения (cid).
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (qrPng != null) {
                helper.addInline("ticketqr", new ByteArrayResource(qrPng), "image/png");
            }
            mailSender.send(message);
            log.info("ticket email sent to {}", toEmail);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send ticket email to {}: {}", toEmail, e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
