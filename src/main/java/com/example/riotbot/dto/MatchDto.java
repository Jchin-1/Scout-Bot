package com.example.riotbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchDto(Metadata metadata, Info info) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(String matchId, List<String> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(long gameEndTimestamp, String gameMode, List<Participant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(String puuid, String summonerId, String summonerName, String riotIdGameName,
            String riotIdTagline,
            String championName,
            int kills, int deaths, int assists,
            int totalDamageDealtToChampions,
            int totalDamageTaken,
            int totalMinionsKilled,
            int neutralMinionsKilled,
            int goldEarned,
            int visionScore,
            int profileIcon,
            boolean win) {
    }
}
