package com.example.riotbot.command;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.spec.EmbedCreateSpec;
import com.example.riotbot.dto.CurrentGameInfo;
import com.example.riotbot.dto.MatchDto;
import com.example.riotbot.dto.LeagueEntryDto;
import com.example.riotbot.dto.AccountDto;
import com.example.riotbot.service.RiotClient;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Component
public class ScoutCommand {

        private final RiotClient riotClient;

        public ScoutCommand(RiotClient riotClient) {
                this.riotClient = riotClient;
        }

        public Mono<Void> handle(ChatInputInteractionEvent event) {
                return event.deferReply()
                                .then(Mono.defer(() -> {
                                        try {
                                                String finalGameName = event.getOption("gamename")
                                                                .flatMap(o -> o.getValue().map(v -> v.asString()))
                                                                .orElseThrow(() -> new RuntimeException("Game Name is required"));
                                                String rawTagLine = event.getOption("tagline")
                                                                .flatMap(o -> o.getValue().map(v -> v.asString()))
                                                                .orElseThrow(() -> new RuntimeException("Tag Line is required"));

                                                // Clean Inputs
                                                String finalTagLine = rawTagLine.replace("#", "");

                                                System.out.println("Processing scout command for: " + finalGameName + " #" + finalTagLine);

                                                // STEP 1: Get Account (Americas)
                                                System.out.println("Step 1: Getting PUUID from Americas for " + finalGameName
                                                                + "#" + finalTagLine + "...");

                                                return riotClient.getAccount(finalGameName, finalTagLine)
                                                                .doOnNext(acc -> System.out.println(
                                                                                "Step 1 Success: PUUID: " + acc.puuid()))
                                                                .switchIfEmpty(Mono.error(
                                                                                new RuntimeException("Account not found")))
                                                                .onErrorResume(e -> Mono.error(new RuntimeException(
                                                                                "Step 1 Failed: " + e.getMessage())))

                                                                .flatMap(account -> {
                                                                        // STEP 2: Get Match History IDs (Americas) - Returns
                                                                        // JSON Array
                                                                        System.out.println(
                                                                                        "Step 2: Fetching recent match IDs for PUUID: "
                                                                                                        + account.puuid());

                                                                        // Fetch 10 matches (Increased count for Visibility) - Strict: ?start=0&count=10 (No filters)
                                                                        return riotClient.getMatchIds(account.puuid(), 10)
                                                                                        .doOnNext(ids -> System.out.println("Step 2 Debug: Raw Match IDs found: " + ids))
                                                                                        .flatMapMany(Flux::fromIterable)
                                                                                        .flatMap(matchId -> riotClient.getMatchDetail(matchId)
                                                                                            .doOnError(e -> System.out.println("Step 2 Warning: Failed to fetch/parse match " + matchId + ": " + e.getMessage()))
                                                                                            .onErrorResume(e -> Mono.empty())
                                                                                        )
                                                                                        .collectList()
                                                                                        .flatMap(matches -> {
                                                                                                if (matches.isEmpty()) {
                                                                                                        return Mono.error(
                                                                                                                        new RuntimeException(
                                                                                                                                        "No recent matches found."));
                                                                                                }

                                                                                                // Smart Sort: Newest First
                                                                                                matches.sort((m1, m2) -> Long
                                                                                                                .compare(m2.info()
                                                                                                                                .gameEndTimestamp(),
                                                                                                                                m1.info().gameEndTimestamp()));
                                                                                                MatchDto latestMatch = matches
                                                                                                                .get(0);

                                                                                                // DEBUG LOGGING FOR VISIBILITY
                                                                                                java.time.format.DateTimeFormatter debugFormatter = java.time.format.DateTimeFormatter
                                                                                                                .ofPattern("MM/dd HH:mm")
                                                                                                                .withZone(java.time.ZoneId.systemDefault());
                                                                                                for (MatchDto m : matches) {
                                                                                                    String date = debugFormatter.format(Instant.ofEpochMilli(m.info().gameEndTimestamp()));
                                                                                                    System.out.println("DEBUG - Match " + m.metadata().matchId() + " Mode: " + m.info().gameMode() + " Date: " + date);
                                                                                                }

                                                                                                System.out.println(
                                                                                                                "Step 2 Success: Found "
                                                                                                                                + matches.size()
                                                                                                                                + " matches. Latest ID: "
                                                                                                                                + latestMatch.metadata()
                                                                                                                                                .matchId());

                                                                                                // STEP 3: Extract Summoner ID
                                                                                                // from Latest Match
                                                                                                return Mono.justOrEmpty(
                                                                                                                latestMatch.info()
                                                                                                                                .participants()
                                                                                                                                .stream()
                                                                                                                                .filter(p -> p.puuid()
                                                                                                                                                .equals(account.puuid()))
                                                                                                                                .findFirst()
                                                                                                                                .map(MatchDto.Participant::summonerId))
                                                                                                                .switchIfEmpty(Mono
                                                                                                                                .error(new RuntimeException(
                                                                                                                                                "User not found in recent match participants?")))
                                                                                                                .map(id -> {
                                                                                                                        String cleanId = id
                                                                                                                                        .trim();
                                                                                                                        System.out.println(
                                                                                                                                        "Step 3 Success: Extracted & Sanitized Summoner ID: "
                                                                                                                                                        + cleanId);
                                                                                                                        return new Step3Result(
                                                                                                                                        cleanId,
                                                                                                                                        latestMatch,
                                                                                                                                        account);
                                                                                                                });
                                                                                        });
                                                                })

                                                                // STEP 4: Verification - Get League Rank (NA1)
                                                                .flatMap(step3Result -> {
                                                                        String summonerId = step3Result.summonerId();
                                                                        MatchDto latestMatch = step3Result.latestMatch();
                                                                        AccountDto account = step3Result.account();

                                                                        System.out.println(
                                                                                        "Step 4: Verifying ID via League-V4 for: "
                                                                                                        + summonerId);
                                                                        return riotClient.getLeagueEntries(summonerId)
                                                                                        .filter(l -> "RANKED_SOLO_5x5"
                                                                                                        .equals(l.queueType()))
                                                                                        .next()
                                                                                        .map(l -> l.tier() + " " + l.rank())
                                                                                        .defaultIfEmpty("Unranked")
                                                                                        .onErrorResume(e -> {
                                                                                                System.out.println(
                                                                                                                "Step 4 Failed (Rank Lookup): "
                                                                                                                                + e.getMessage());
                                                                                                return Mono.just(
                                                                                                                "Rank Unknown (API Error)");
                                                                                        })
                                                                                        .doOnNext(rank -> System.out.println(
                                                                                                        "Step 4 Success: User is "
                                                                                                                        + rank))
                                                                                        .map(rank -> new Step4Result(summonerId,
                                                                                                        latestMatch, rank,
                                                                                                        account));
                                                                })

                                                                // STEP 5: Get Active Game (Spectator V4 - NA1)
                                                                .flatMap(step4Result -> {
                                                                        String summonerId = step4Result.summonerId();
                                                                        MatchDto latestMatch = step4Result.latestMatch();
                                                                        String rank = step4Result.rank();
                                                                        AccountDto account = step4Result.account();

                                                                        System.out.println("Step 5: Calling Spectator V4...");
                                                                        return riotClient.getCurrentMatch(summonerId)
                                                                                        .flatMap(activeGame -> {
                                                                                                System.out.println(
                                                                                                                "Step 5 Success: Match Found!");
                                                                                                return processActiveGame(event,
                                                                                                                account,
                                                                                                                activeGame,
                                                                                                                rank);
                                                                                        })
                                                                                        // PLAN B: Use Latest Match Details
                                                                                        // (Already Fetched!)
                                                                                        .onErrorResume(e -> {
                                                                                                String errorMsg = e instanceof WebClientResponseException.NotFound
                                                                                                                ? "User is not currently in a game."
                                                                                                                : "Live Game not found (API Restricted).";
                                                                                                System.out.println(
                                                                                                                "Step 5 Fallback: "
                                                                                                                                + errorMsg
                                                                                                                                + " Using Stats from Latest Match ("
                                                                                                                                + latestMatch.metadata()
                                                                                                                                                .matchId()
                                                                                                                                                + ")");

                                                                                                // Directly use the latestMatch
                                                                                                // we found in Step 2!
                                                                                                return Mono.just(latestMatch)
                                                                                                                .flatMap(lastMatchDto -> {
                                                                                                                        MatchDto.Participant foundParticipant = null;
                                                                                                                        for (MatchDto.Participant p : lastMatchDto
                                                                                                                                        .info()
                                                                                                                                        .participants()) {
                                                                                                                                if (p.puuid().equals(
                                                                                                                                                account.puuid())) {
                                                                                                                                        foundParticipant = p;
                                                                                                                                        break;
                                                                                                                                }
                                                                                                                        }

                                                                                                                        if (foundParticipant == null) {
                                                                                                                                return Mono.error(
                                                                                                                                                new RuntimeException(
                                                                                                                                                                "User not found in match participants"));
                                                                                                                        }

                                                                                                                        // Prepare Data for Embed
                                                                                                                        String gameMode = lastMatchDto.info().gameMode();
                                                                                                                        boolean won = foundParticipant.win();
                                                                                                                        String winLossText = won ? "VICTORY" : "DEFEAT";
                                                                                                                        discord4j.rest.util.Color embedColor = won ? discord4j.rest.util.Color.GREEN : discord4j.rest.util.Color.RED;
                                                                                                                        
                                                                                                                        String championName = foundParticipant.championName();
                                                                                                                        // Handle spaces in champion name for URL (e.g. Lee Sin -> LeeSin)
                                                                                                                        String championUrlName = championName.replace(" ", "");
                                                                                                                        String thumbUrl = "https://ddragon.leagueoflegends.com/cdn/14.1.1/img/champion/" + championUrlName + ".png";
                                                                                                                        
                                                                                                                        // Identity (Author)
                                                                                                                        String authorName = finalGameName + " #" + finalTagLine;
                                                                                                                        int profileIconId = foundParticipant.profileIcon();
                                                                                                                        String profileIconUrl = "https://ddragon.leagueoflegends.com/cdn/14.1.1/img/profileicon/" + profileIconId + ".png";

                                                                                                                        // Stats
                                                                                                                        String kda = foundParticipant.kills() + "/" + foundParticipant.deaths() + "/" + foundParticipant.assists();
                                                                                                                        int totalCS = foundParticipant.totalMinionsKilled() + foundParticipant.neutralMinionsKilled();
                                                                                                                        int gold = foundParticipant.goldEarned();
                                                                                                                        int damage = foundParticipant.totalDamageDealtToChampions();
                                                                                                                        int damageTaken = foundParticipant.totalDamageTaken();
                                                                                                                        int vision = foundParticipant.visionScore();

                                                                                                                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                                                                                                                        .ofPattern("MM/dd/yyyy")
                                                                                                                                        .withZone(java.time.ZoneId.systemDefault());
                                                                                                                        String matchDate = formatter.format(Instant.ofEpochMilli(lastMatchDto.info().gameEndTimestamp()));

                                                                                                                        // Build Embed
                                                                                                                        EmbedCreateSpec embed = EmbedCreateSpec.builder()
                                                                                                                            .author(authorName, null, profileIconUrl)
                                                                                                                            .title(winLossText + " in " + gameMode)
                                                                                                                            .color(embedColor)
                                                                                                                            .thumbnail(thumbUrl)
                                                                                                                            .description("Played as **" + championName + "**")
                                                                                                                            .addField("⚔️ Combat", 
                                                                                                                                "KDA: " + kda + "\n" +
                                                                                                                                "Dmg Dealt: " + String.format("%,d", damage) + "\n" +
                                                                                                                                "Dmg Taken: " + String.format("%,d", damageTaken), true)
                                                                                                                            .addField("🚜 Farming & Gold", 
                                                                                                                                "CS: " + totalCS + "\n" +
                                                                                                                                "Gold: " + String.format("%,d", gold), true)
                                                                                                                            .addField("👀 Vision", 
                                                                                                                                "Vision Score: " + vision, true)
                                                                                                                            .footer("Scout Bot • " + matchDate, null)
                                                                                                                            .build();

                                                                                                                        return event.editReply(discord4j.core.spec.InteractionReplyEditSpec.create()
                                                                                                                            .withContent(errorMsg) // Keep the context message (e.g. "User not in game")
                                                                                                                            .withEmbeds(embed))
                                                                                                                            .then();
                                                                                                                })
                                                                                                                .onErrorResume(ex -> event.editReply("⚠️ **" + finalGameName + " #" + finalTagLine + "**\n" + 
                                                                                                                                                    "Error building stats: " + ex.getMessage()).then());
                                                                                        });
                                                                });
                                        } catch (Exception e) {
                                                return Mono.error(e);
                                        }
                                }))
                                .onErrorResume(e -> event.editReply("❌ Error: " + e.getMessage()).then())
                                .then();
        }

        private record Step3Result(String summonerId, MatchDto latestMatch, AccountDto account) {
        }

        private record Step4Result(String summonerId, MatchDto latestMatch, String rank, AccountDto account) {
        }

        private Mono<Void> processActiveGame(ChatInputInteractionEvent event,
                        AccountDto userAccount, CurrentGameInfo activeGame, String userRank) {
                // Identify user's team
                long userTeamId = activeGame.participants().stream()
                                .filter(p -> p.puuid().equals(userAccount.puuid()))
                                .findFirst()
                                .map(CurrentGameInfo.CurrentGameParticipant::teamId)
                                .orElseThrow(() -> new RuntimeException("User not found in match participants?"));

                // Get active enemies
                List<CurrentGameInfo.CurrentGameParticipant> enemies = activeGame.participants().stream()
                                .filter(p -> p.teamId() != userTeamId)
                                .toList();

                // Analyze enemies concurrently
                return Flux.fromIterable(enemies)
                                .flatMap(enemy -> analyzeEnemy(enemy), 5)
                                .collectList()
                                .flatMap(enemyStats -> {
                                        EmbedCreateSpec.Builder embedBuilder = EmbedCreateSpec.builder();
                                        embedBuilder.color(discord4j.rest.util.Color.RED);
                                        embedBuilder.title("Details for " + userAccount.gameName() + " (" + userRank
                                                        + ")");
                                        embedBuilder.description("**LIVE MATCH FOUND**");
                                        embedBuilder.timestamp(Instant.now());

                                        for (EnemyStats stats : enemyStats) {
                                                embedBuilder.addField(
                                                                stats.riotId(),
                                                                String.format("WR: %.0f%% (%dW - %dL)",
                                                                                stats.getWinRate() * 100,
                                                                                stats.wins(),
                                                                                stats.losses()),
                                                                false);
                                        }
                                        return event.editReply().withEmbeds(embedBuilder.build()).then();
                                });
        }

        private Mono<EnemyStats> analyzeEnemy(CurrentGameInfo.CurrentGameParticipant enemy) {
                String displayName = (enemy.riotId() != null && !enemy.riotId().isEmpty())
                                ? enemy.riotId()
                                : "Summoner (" + enemy.summonerId().substring(0, 5) + "...)";

                return riotClient.getMatchIds(enemy.puuid(), 10)
                                .flatMapMany(Flux::fromIterable)
                                .flatMap(matchId -> riotClient.getMatchDetail(matchId))
                                .filter(match -> match.info() != null)
                                .collectList()
                                .map(matches -> {
                                        int wins = 0;
                                        int losses = 0;
                                        for (MatchDto match : matches) {
                                                boolean won = match.info().participants().stream()
                                                                .filter(p -> p.puuid().equals(enemy.puuid()))
                                                                .findFirst()
                                                                .map(MatchDto.Participant::win)
                                                                .orElse(false);
                                                if (won)
                                                        wins++;
                                                else
                                                        losses++;
                                        }
                                        return new EnemyStats(displayName, wins, losses);
                                });
        }

        private record EnemyStats(String riotId, int wins, int losses) {
                public double getWinRate() {
                        return (wins + losses) == 0 ? 0.0 : (double) wins / (wins + losses);
                }
        }
}
