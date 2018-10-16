package pl.allegro.tech.hermes.integration.management;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import pl.allegro.tech.hermes.api.MonitoringDetails;
import pl.allegro.tech.hermes.api.OwnerId;
import pl.allegro.tech.hermes.api.Subscription;
import pl.allegro.tech.hermes.api.SubscriptionHealth;
import pl.allegro.tech.hermes.api.Topic;
import pl.allegro.tech.hermes.api.UnhealthySubscription;
import pl.allegro.tech.hermes.integration.IntegrationTest;
import pl.allegro.tech.hermes.integration.env.SharedServices;
import pl.allegro.tech.hermes.integration.helper.GraphiteEndpoint;
import pl.allegro.tech.hermes.test.helper.message.TestMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.allegro.tech.hermes.test.helper.builder.SubscriptionBuilder.subscription;

public class ListUnhealthySubscriptionsForOwnerTest extends IntegrationTest {

    private Topic topic;
    private GraphiteEndpoint graphiteEndpoint;

    @BeforeMethod
    public void initializeAlways() {
        topic = operations.buildTopic("group", "topic");
        graphiteEndpoint = new GraphiteEndpoint(SharedServices.services().graphiteHttpMock());
    }

    @Test
    public void shouldNotListHealthySubscribtions() {
        // given
        createSubscriptionForOwner("s1", "Team A");
        createSubscriptionForOwner("s2", "Team B");

        // then
        assertThat(listUnhealthySubscriptionsForOwner("Team A")).isEmpty();
    }

    @Test
    public void shouldReturnOnlyUnhealthySubscriptionOfSingleOwner() {
        // given
        createSubscriptionForOwner("ownedSubscription1", "Team A");
        createSubscriptionForOwner("ownedSubscription2", "Team A");
        createSubscriptionForOwner("ownedSubscription3", "Team B");

        graphiteEndpoint.returnMetricForTopic("group", "topic", 100, 50);
        graphiteEndpoint.returnMetricForSubscription("group", "topic", "ownedSubscription1", 100);
        graphiteEndpoint.returnMetricForSubscription("group", "topic", "ownedSubscription2", 50);
        graphiteEndpoint.returnMetricForSubscription("group", "topic", "ownedSubscription3", 100);

        // then

        assertThat(listUnhealthySubscriptionsForOwner("Team A")).containsOnly(
                new UnhealthySubscription("ownedSubscription2", "group.topic", MonitoringDetails.Severity.IMPORTANT, ImmutableSet.of(SubscriptionHealth.Problem.SLOW))
        );
    }

    private void createSubscriptionForOwner(String subscriptionName, String ownerId) {
        Subscription subscription = subscription(topic, subscriptionName)
                .withEndpoint(HTTP_ENDPOINT_URL)
                .withOwner(ownerId(ownerId))
                .withMonitoringDetails(new MonitoringDetails(MonitoringDetails.Severity.IMPORTANT, ""))
                .build();

        operations.createSubscription(topic, subscription);
    }

    @NotNull
    private OwnerId ownerId(String ownerId) {
        return new OwnerId("Plaintext", ownerId);
    }

    private List<UnhealthySubscription> listUnhealthySubscriptionsForOwner(String ownerId) {
        return management.subscriptionOwnershipEndpoint().listUnhealthyForOwner("Plaintext", ownerId);
    }
}
