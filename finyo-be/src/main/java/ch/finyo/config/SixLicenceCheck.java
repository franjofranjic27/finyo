package ch.finyo.config;

import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.profile.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Turns SIX's licence terms from a note in docs/ into a runtime assertion.
 *
 * SIX's terms of use permit the FQS data "exclusively for personal use" and forbid
 * commercial use or redistribution. finyo is within those terms while it serves a
 * single person — which it does today, self-hosted. The moment a second user signs
 * in, that ceases to be true, and the honest answer is to switch the chain to a
 * licensed provider (EODHD or Marketstack; see docs/DATENQUELLEN.md), which costs
 * three lines of YAML because the provider chain is configuration.
 *
 * The failure mode this guards against is not malice, it is forgetting: a second
 * user is added, everything keeps working, and nobody notices the terms are now
 * being broken. So the application says so, loudly, on every start.
 *
 * It warns rather than refuses to boot: locking the owner out of their own finance
 * data over a licensing question would be a worse outcome than the breach it
 * prevents. The decision stays with a human — but an informed one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SixLicenceCheck implements ApplicationRunner {

    private final MarketDataProperties properties;
    private final UserProfileRepository userProfiles;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.six().enabled()) {
            return;
        }

        // user_profile carries a UNIQUE(user_id) and is created on first sign-in,
        // which makes it the closest thing to a user registry this app has.
        long users = userProfiles.count();
        if (users > 1) {
            log.error("""
                    SIX market data is enabled but {} users exist. SIX's terms of use allow \
                    personal use only — running finyo for more than one person on SIX data \
                    breaches them. Switch the provider chain to a licensed source: \
                    set finyo.marketdata.six.enabled=false, enable eodhd, and put it first in \
                    finyo.marketdata.reference-providers. See docs/DATENQUELLEN.md.""", users);
        }
    }
}
