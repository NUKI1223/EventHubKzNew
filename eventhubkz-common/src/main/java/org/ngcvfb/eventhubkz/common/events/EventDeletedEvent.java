package org.ngcvfb.eventhubkz.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventDeletedEvent extends BaseEvent {
    private Long id;

    public static EventDeletedEvent create(Long id) {
        EventDeletedEvent event = EventDeletedEvent.builder()
                .id(id)
                .build();
        event.initBase();
        return event;
    }
}
