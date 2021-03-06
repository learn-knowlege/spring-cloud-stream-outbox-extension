package com.dilaverdemirel.spring.outbox.listener;

import com.dilaverdemirel.spring.outbox.domain.OutboxMessage;
import com.dilaverdemirel.spring.outbox.domain.OutboxMessageStatus;
import com.dilaverdemirel.spring.outbox.dto.OutboxMessageEvent;
import com.dilaverdemirel.spring.outbox.dto.OutboxMessageEventMetaData;
import com.dilaverdemirel.spring.outbox.exception.OutboxMessageValidationException;
import com.dilaverdemirel.spring.outbox.repository.OutboxMessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.dilaverdemirel.spring.outbox.util.JsonUtil.convertToJson;
import static com.dilaverdemirel.spring.outbox.util.StringUtils.isBlank;

/**
 * @author dilaverdemirel
 * @since 10.05.2020
 */
@Component
public class OutboxMessageHandler {
    private final OutboxMessageRepository outboxMessageRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public OutboxMessageHandler(OutboxMessageRepository outboxMessageRepository,
                                ApplicationEventPublisher applicationEventPublisher) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @TransactionalEventListener(classes = {OutboxMessageEvent.class}, phase = TransactionPhase.BEFORE_COMMIT)
    public void onOutboxMessageCreate(OutboxMessageEvent outboxMessageEvent) {
        validateOutboxMessage(outboxMessageEvent);
        final var savedOutboxMessage = saveMessage(outboxMessageEvent);
        applicationEventPublisher.publishEvent(OutboxMessageEventMetaData.builder().messageId(savedOutboxMessage.getId()).build());
    }

    private OutboxMessage saveMessage(OutboxMessageEvent outboxMessageEvent) {
        final var outboxMessage = OutboxMessage.builder()
                .id(UUID.randomUUID().toString())
                .source(outboxMessageEvent.getSource())
                .sourceId(outboxMessageEvent.getSourceId())
                .channel(outboxMessageEvent.getChannel())
                .payload(convertToJson(outboxMessageEvent.getPayload()))
                .createdAt(LocalDateTime.now())
                .messageClass(outboxMessageEvent.getPayload().getClass().getName())
                .retryCount(-1)
                .status(OutboxMessageStatus.NEW).build();

        return outboxMessageRepository.save(outboxMessage);
    }

    private void validateOutboxMessage(OutboxMessageEvent outboxMessageEvent) {
        if (isBlank(outboxMessageEvent.getSource()) ||
                isBlank(outboxMessageEvent.getSourceId()) ||
                isBlank(outboxMessageEvent.getChannel()) ||
                Objects.isNull(outboxMessageEvent.getPayload())
        ) {
            throw new OutboxMessageValidationException("Please enter all fields data for outbox message send!");
        }
    }
}
