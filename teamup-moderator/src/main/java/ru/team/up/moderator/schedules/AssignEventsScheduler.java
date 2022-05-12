package ru.team.up.moderator.schedules;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.team.up.core.entity.Account;
import ru.team.up.core.entity.AssignedEvents;
import ru.team.up.core.entity.Event;
import ru.team.up.core.entity.Moderator;
import ru.team.up.core.service.*;
import ru.team.up.dto.KafkaEventDto;
import ru.team.up.dto.KafkaEventTypeDto;
import ru.team.up.moderator.payload.AssignedEventPayload;
import ru.team.up.payload.Payload;

import java.util.List;

@Component
@PropertySource("classpath:app.properties")
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
public class AssignEventsScheduler {

    private final AssignedEventsServiceImpl assignedEventsServiceImpl;
    private final ModeratorsSessionsServiceImpl moderatorSessionsServiceImpl;
    private final ModeratorService moderatorService;
    private final EventService eventService;
    private final KafkaTemplate<String, KafkaEventDto> kafkaTemplate;

    @Value("${spring.kafka.template.moderator.topic.name}")
    private String topic;

    /**
     * Метод проверяет по расписанию наличие новых мероприятий в статусе "на проверке"
     * и назначает их на модераторов
     */
//    @Scheduled(fixedRate = 5000)
    @Scheduled(fixedDelayString = "${eventsScan.delay}")
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void assignEvents() {
        log.debug("Получение списка новых мероприятий");
        List<Long> newEventsIdList = assignedEventsServiceImpl.getIdNotAssignedEvents();

        if (!newEventsIdList.isEmpty()) {
            if (moderatorSessionsServiceImpl.getFreeModerator() != null) {

                newEventsIdList.forEach(eventId -> {
                    Long freeModeratorId = moderatorSessionsServiceImpl.getFreeModerator();
                    assignedEventsServiceImpl.saveAssignedEvent(AssignedEvents.builder()
                            .eventId(eventId)
                            .moderatorId(freeModeratorId)
                            .build());
                    log.debug("Мероприятию с ID = {} назначен модератор {}", eventId, freeModeratorId);

                    KafkaEventDto kafkaEventDto = createKafkaEvent(freeModeratorId, eventId);
                    sendToKafka(kafkaEventDto);
                });
                log.debug("Новые мероприятия получены и распределены на модераторов");
            } else {
                log.debug("Нет активных модераторов");
            }
        } else {
            log.debug("Список новых мероприятий пуст");
        }
    }

    /**
     * Метод для создания сообщения для кафки
     * @param moderatorId ID модератора для поиска в БД
     * @param eventId ID ивента для поиска в БД
     * @return KafkaEventDto - сообщение для кафки
     */
    private KafkaEventDto createKafkaEvent(Long moderatorId, Long eventId) {
        log.debug("Старт поиска в БД назначенного модератора и ивента");
        Moderator moderator = (Moderator) moderatorService.getOneModerator(moderatorId);
        Event event = eventService.getOneEvent(eventId);
        AssignedEventPayload payload = new AssignedEventPayload(moderator, event);

        KafkaEventDto kafkaEventDto = new KafkaEventDto();
        kafkaEventDto.setKafkaEventTypeDto(KafkaEventTypeDto.NEW_ASSIGN_EVENT);
        kafkaEventDto.setPayload(payload);
        log.debug("KafkaEventDto для отправки создан");
        return kafkaEventDto;
    }

    /**
     * Метод послания сообщения в кафку
     * @param kafkaEventDto сообщение для кафки
     */
    private void sendToKafka(KafkaEventDto kafkaEventDto) {
        log.debug("Посылка сообщения в кафку, топик: {}, сообщение: {}",
                topic, kafkaEventDto);
        kafkaTemplate.send(topic, kafkaEventDto);
        log.debug("Сообщение отправлено в кафку");
    }
}
