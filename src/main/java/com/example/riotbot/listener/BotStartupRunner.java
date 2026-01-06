package com.example.riotbot.listener;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.discordjson.json.*;
import discord4j.rest.RestClient;
import com.example.riotbot.command.ScoutCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class BotStartupRunner implements CommandLineRunner {

    private final GatewayDiscordClient client;
    private final RestClient restClient;
    private final ScoutCommand scoutCommand;

    public BotStartupRunner(GatewayDiscordClient client, RestClient restClient, ScoutCommand scoutCommand) {
        this.client = client;
        this.restClient = restClient;
        this.scoutCommand = scoutCommand;
    }

    @Override
    public void run(String... args) throws Exception {
        // Register the Slash Command
        final String commandName = "scout";
        ApplicationCommandRequest scoutRequest = ApplicationCommandRequest.builder()
                .name(commandName)
<<<<<<< HEAD
                .description("Scout the enemy team in a live game")
=======
                .description("View recent League of Legends match stats and live game status.")
>>>>>>> 21a077ed4ef3f73981e81d5138c2daeb961bae81
                .addOption(ApplicationCommandOptionData.builder()
                        .name("gamename")
                        .description("Riot ID Game Name")
                        .type(3) // String
                        .required(true)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name("tagline")
                        .description("Riot ID Tag Line")
                        .type(3) // String
                        .required(true)
                        .build())
                .build();

        restClient.getApplicationService()
                .createGlobalApplicationCommand(restClient.getApplicationId().block(), scoutRequest)
                .subscribe();

        // Register the Event Listener
        // Register the Event Listener
        client.on(ChatInputInteractionEvent.class)
                .flatMap(event -> {
                    if (event.getCommandName().equals(commandName)) {
                        return scoutCommand.handle(event)
                                .doOnError(e -> System.err.println("Error handling command: " + e.getMessage()))
                                .onErrorResume(e -> Mono.empty());
                    }
                    return Mono.empty();
                })
                .subscribe();
    }
}
