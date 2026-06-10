package org.ngcvfb.eventservice.client;

import org.ngcvfb.eventhubkz.common.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/batch")
    List<UserDTO> getUsersByIds(@RequestParam("ids") List<Long> ids);
}
