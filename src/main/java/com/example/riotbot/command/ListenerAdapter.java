package com.example.riotbot.command;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import reactor.core.publisher.Mono;

public abstract class ListenerAdapter {
    public Mono<Void> onSlashCommandInteraction(ChatInputInteractionEvent event) {
        return Mono.empty();
    }
}
