package org.danteplanner.backend.service;

import org.danteplanner.backend.dto.UserDto;
import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User findOrCreateUser(String provider, Map<String, String> userInfo) {
        String providerId = userInfo.get("id");

        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(userInfo.get("email"))
                            .name(userInfo.get("name"))
                            .provider(provider)
                            .providerId(providerId)
                            .profilePicture(userInfo.get("picture"))
                            .build();
                    return userRepository.save(newUser);
                });
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profilePicture(user.getProfilePicture())
                .provider(user.getProvider())
                .build();
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
