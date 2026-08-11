package com.tengames.fixtures.espn;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ESPN sits behind an edge that refuses the JDK client's default agent with a
 * 403. Both callers log the failure and carry on, so the symptom was not an
 * error anyone saw: the cup finals were simply absent from the pool and no
 * live score ever moved.
 */
class EspnClientConfigTest {

    @Test
    void everyRequestIdentifiesItselfWithAnAgentEspnAccepts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = new EspnClientConfig().espnRestClient(builder,
                new EspnProperties(true, "https://espn.example", null, null));
        server.expect(requestTo("https://espn.example/eng.charity/scoreboard"))
                .andExpect(header("User-Agent", EspnClientConfig.USER_AGENT))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        client.get().uri("/eng.charity/scoreboard").retrieve().toBodilessEntity();

        server.verify();
        // the leading token is what the edge matches on; the rest says who we are
        assertThat(EspnClientConfig.USER_AGENT).startsWith("curl/").contains("TenGames");
    }
}
