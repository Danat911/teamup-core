package ru.team.up.core.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.team.up.core.entity.AssignedEvents;
import ru.team.up.core.entity.Event;

import java.util.List;

public interface AssignedEventsRepository extends JpaRepository<AssignedEvents, Long> {
    @Query(
            value = "SELECT * FROM event AS E WHERE E.status_id = 2",
    nativeQuery = true)
    public List<Event> getEventsForChecking();

    @Query(
            value = "SELECT eventId FROM event WHERE eventId = :eventList",
    nativeQuery = true)
    public List<Integer> getEventsIds(@Param("eventList") List<Event> eventList );
}
