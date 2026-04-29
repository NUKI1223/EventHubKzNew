package org.ngcvfb.userservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private String description;
    private String avatarUrl;

    @ElementCollection
    @CollectionTable(name = "user_contacts", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "contact_type")
    @Column(name = "contact_value")
    private Map<String, String> contacts = new HashMap<>();

    private boolean enabled = false;
}
