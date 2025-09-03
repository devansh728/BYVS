package com.byvs.backend.service.service;

import com.byvs.backend.service.dto.FeedbackRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
@Service
@Slf4j
public class EmailService {

    @Value("${spring.mail.username}")
    private String fromEmail;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }
    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String membershipId) {
        Context context = new Context();
        context.setVariable("name", fullName);
        context.setVariable("membershipId", membershipId);

        String htmlContent = templateEngine.process("welcome-email", context);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Welcome to BYVS Family!");
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Email failed, welcome email");
        }
    }
    @Async
    public void sendOfficeBearerApprovalEmail(String toEmail,String District,String State,String Position, String fullName) {
        Context context = new Context();
        context.setVariable("name", fullName);
        context.setVariable("district", District);
        context.setVariable("state", State);
        context.setVariable("position", Position);

        String htmlContent = templateEngine.process("office-bearer-approval", context);

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Congratulations! Your Office Bearer Application Approved");
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Email failed, Send Office Approval");
        }
    }

    @Async
    public void sendFeedback(FeedbackRequest feedbackRequest) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(fromEmail);
            helper.setSubject("New Feedback Received");

            Context context = new Context();
            context.setVariable("name", feedbackRequest.getName());
            context.setVariable("email", feedbackRequest.getEmail());
            context.setVariable("phone", feedbackRequest.getPhone());
            context.setVariable("subject", feedbackRequest.getSubject());
            context.setVariable("message", feedbackRequest.getMessage());

            String htmlContent = templateEngine.process("feedback-email", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            System.err.println("Failed to send feedback email: " + e.getMessage());
        }
    }
}