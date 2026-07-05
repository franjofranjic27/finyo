package ch.finyo.auth;

import ch.finyo.category.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private final CategoryService categoryService;

    public void provisionNewUserIfNeeded(String userId) {
        log.info("Checking if user {} needs provisioning", userId);
        categoryService.seedDefaultsIfEmpty(userId);
        log.info("Provisioning check complete for user {}", userId);
    }
}
