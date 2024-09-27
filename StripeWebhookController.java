package com.example.nagoyameshi.controller;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.nagoyameshi.service.MemberinfoService;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

@RestController
public class StripeWebhookController {

    private static final Logger logger = Logger.getLogger(StripeWebhookController.class.getName());

    @Value("${stripe.api.secret}")
    private String stripeApiKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final MemberinfoService memberinfoService;

    public StripeWebhookController(MemberinfoService memberinfoService) {
        this.memberinfoService = memberinfoService;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Webhook error while validating signature.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Webhook verification failed");
        }

        logger.info("Received Stripe WebHook: " + event.getType());

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
            if (dataObjectDeserializer.getObject().isPresent()) {
                Session session = (Session) dataObjectDeserializer.getObject().get();
                handleCheckoutSession(session);
            } else {
                logger.warning("Unable to deserialize object.");
            }
        }
        return ResponseEntity.ok("");
    }

    private void handleCheckoutSession(Session session) {
        String userEmail = session.getCustomerEmail();
        logger.info("Customer email from session: " + userEmail);

        if (userEmail != null) {
            try {
                memberinfoService.upgradeUserRole(userEmail, "ROLE_PAID");
                logger.info("User role upgraded to ROLE_PAID for user: " + userEmail);
            } catch (RuntimeException e) {
                logger.severe("Error upgrading user role: " + e.getMessage());
            }
        } else {
            logger.warning("User email not found in session.");
        }
    }
}