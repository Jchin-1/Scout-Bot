package com.example.riotbot.service;

import com.example.riotbot.dto.AccountDto;
import com.example.riotbot.dto.SummonerDto;
import com.example.riotbot.dto.CurrentGameInfo;
import com.example.riotbot.dto.MatchDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RiotClient {

    private final WebClient webClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final String apiKey;

    private static final String AMERICAS_BASE_URL = "https://americas.api.riotgames.com";
    private static final String NA1_BASE_URL = "https://na1.api.riotgames.com";

    public RiotClient(WebClient.Builder webClientBuilder, @Value("${riot.api.key}") String apiKey,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    public Mono<AccountDto> getAccount(String gameName, String tagLine) {
        return webClient.get()
                .uri(AMERICAS_BASE_URL + "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(AccountDto.class);
    }

    public Mono<SummonerDto> getSummoner(String puuid) {
        return webClient.get()
                .uri(NA1_BASE_URL + "/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(SummonerDto.class);
    }

    public Mono<String> getSummonerJson(String puuid) {
        return webClient.get()
                .uri(NA1_BASE_URL + "/lol/summoner/v4/summoners/by-puuid/{puuid}", puuid)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<CurrentGameInfo> getCurrentMatch(String summonerId) {
        System.out.println("DEBUG - Calling Spectator V4 for Summoner ID: " + summonerId);
        System.out.println(
                "DEBUG - Target URL: " + NA1_BASE_URL + "/lol/spectator/v4/active-games/by-summoner/" + summonerId);
        if (apiKey != null) {
            System.out.println("DEBUG - API Key Length: " + apiKey.length());
        }

        return webClient.get()
                .uri(NA1_BASE_URL + "/lol/spectator/v4/active-games/by-summoner/{summonerId}", summonerId)
                .header("X-Riot-Token", apiKey)
                .header("User-Agent", "RiotBot/1.0")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(CurrentGameInfo.class);
    }

    public Mono<CurrentGameInfo> getCurrentMatchByPuuid(String puuid) {
        return webClient.get()
                .uri(NA1_BASE_URL + "/lol/spectator/v5/active-games/by-puuid/{puuid}", puuid)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(CurrentGameInfo.class);
    }

    public Mono<java.util.List<String>> getMatchIds(String puuid, int count) {
        return webClient.get()
                .uri(AMERICAS_BASE_URL + "/lol/match/v5/matches/by-puuid/{puuid}/ids?start=0&count={count}", puuid,
                        count)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // Manual parsing to ensure no brackets/quotes remain
                    String clean = json.replace("[", "").replace("]", "").replace("\"", "").trim();
                    if (clean.isEmpty()) {
                        return new java.util.ArrayList<String>();
                    }
                    return java.util.Arrays.asList(clean.split(","));
                });
    }

    public Mono<MatchDto> getMatchDetail(String matchId) {
        return webClient.get()
                .uri(AMERICAS_BASE_URL + "/lol/match/v5/matches/{matchId}", matchId)
                .header("X-Riot-Token", apiKey)
                .retrieve()
                .bodyToMono(MatchDto.class);
    }

    public Flux<com.example.riotbot.dto.LeagueEntryDto> getLeagueEntries(String summonerId) {
        System.out.println("DEBUG - Calling League V4 for Summoner ID: " + summonerId);
        System.out.println("DEBUG - Target URL: " + NA1_BASE_URL + "/lol/league/v4/entries/by-summoner/" + summonerId);
        if (apiKey != null) {
            System.out.println("DEBUG - API Key Length: " + apiKey.length());
        }

        return webClient.get()
                .uri(NA1_BASE_URL + "/lol/league/v4/entries/by-summoner/{summonerId}", summonerId)
                .header("X-Riot-Token", apiKey)
                .header("User-Agent", "RiotBot/1.0")
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(com.example.riotbot.dto.LeagueEntryDto.class);
    }

    public Mono<String> getLatestDDragonVersion() {
        return webClient.get()
                .uri("https://ddragon.leagueoflegends.com/api/versions.json")
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    // Quick parse: ["14.1.1", ...] -> 14.1.1
                    // Remove brackets and quotes, split by comma, take first
                    String clean = json.replace("[", "").replace("]", "").replace("\"", "").trim();
                    return clean.split(",")[0];
                });
    }
}
