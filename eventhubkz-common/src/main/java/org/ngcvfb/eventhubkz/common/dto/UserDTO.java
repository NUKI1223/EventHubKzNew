package org.ngcvfb.eventhubkz.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;
    private String description;
    private String avatarUrl;
    private Map<String, String> contacts;
    private Set<String> tags;
    private boolean enabled;
}
