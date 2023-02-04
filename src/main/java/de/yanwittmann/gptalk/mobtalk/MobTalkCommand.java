package de.yanwittmann.gptalk.mobtalk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class MobTalkCommand implements CommandRegistrationCallback {

    private final static int CHAT_ID = new Random().nextInt(1000000);

    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        // new:
        // gptalk <text>
        // gptalk fact add world <text>
        // gptalk fact add entity <text>
        // gptalk fact remove world <text>
        // gptalk fact remove entity <text>
        // gptalk fact list world
        // gptalk fact list entity
        dispatcher.register(literal("gptalk")
                .then(argument("text", StringArgumentType.greedyString())
                        .executes(context -> executeGptalk(context.getSource(), StringArgumentType.getString(context, "text")))
                )
                .then(literal("fact")
                        .then(literal("add")
                                .then(literal("world")
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> executeFactModification(context.getSource(), true, "world", StringArgumentType.getString(context, "text")))
                                        )
                                )
                                .then(literal("entity")
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> executeFactModification(context.getSource(), true, "entity", StringArgumentType.getString(context, "text")))
                                        )
                                )
                        )
                        .then(literal("remove")
                                .then(literal("world")
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> executeFactModification(context.getSource(), false, "world", StringArgumentType.getString(context, "text")))
                                        )
                                )
                                .then(literal("entity")
                                        .then(argument("text", StringArgumentType.greedyString())
                                                .executes(context -> executeFactModification(context.getSource(), false, "entity", StringArgumentType.getString(context, "text")))
                                        )
                                )
                        )
                        .then(literal("list")
                                .then(literal("world")
                                        .executes(context -> executeFactList(context.getSource(), "world"))
                                )
                                .then(literal("entity")
                                        .executes(context -> executeFactList(context.getSource(), "entity"))
                                )
                        )
                )
        );
    }

    /**
     * Stores the chat history of each entity, is referenced by the UUID of the entity.<br>
     * Each line of the chat history is a {@link ChatElement}. It stores the text and the UUID of the entity that sent
     * it (+ whether it was sent by the player or another entity).<br>
     * The first key is the UUID of the player, the second key is the UUID of the entity that the entity is talking to.
     */
    private final static Map<UUID, Map<UUID, List<ChatElement>>> UUID_CHAT_HISTORY = new ConcurrentHashMap<>();
    private final static List<String> GLOBAL_WORLD_FACTS = new ArrayList<>();
    private final static Map<UUID, List<String>> FACTS_BY_ENTITY = new ConcurrentHashMap<>();

    private final static String[] AVAILABLE_OPENAI_MODELS = new String[]{"text-ada-001", "text-babbage-001", "text-curie-001", "text-davinci-003"};
    private static String OPENAI_MODEL = AVAILABLE_OPENAI_MODELS[0];
    private static String OPENAI_KEY = null;
    private static boolean GPTALK_ENABLED = false;
    private static URL OPENAI_COMPLETION_API = null;
    private static URL ANALYTICS_URL = null;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        final Properties properties = new Properties();
        try {
            properties.load(MobTalkCommand.class.getResourceAsStream("/mod.properties"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final String openaiKey = properties.getProperty("openai.key");
        if (openaiKey != null && openaiKey.length() > 4) {
            OPENAI_KEY = openaiKey;
        } else {
            final String openaiKeyEnv = System.getenv("OPENAI_KEY");
            if (openaiKeyEnv != null && openaiKeyEnv.length() > 4) {
                OPENAI_KEY = openaiKeyEnv;
            }
        }
        if (OPENAI_KEY != null) {
            System.out.println("OpenAI key: " + OPENAI_KEY.substring(0, 4) + "...");
        } else {
            GPTALK_ENABLED = false;
            System.out.println("No OpenAI key [openai.key] found in mod.properties or [OPENAI_KEY] environment variable");
        }

        final Object modelNumber = properties.get("openai.model");
        if (modelNumber == null) {
            System.out.println("No OpenAI model [openai.model] found in mod.properties, defaulting to 0");
        } else {
            try {
                final int modelNumberInt = Integer.parseInt(modelNumber.toString());
                if (modelNumberInt < 0 || modelNumberInt >= AVAILABLE_OPENAI_MODELS.length) {
                    System.out.println("OpenAI model [openai.model] out of range, defaulting to 0");
                } else {
                    OPENAI_MODEL = AVAILABLE_OPENAI_MODELS[modelNumberInt];
                    System.out.println("OpenAI model: " + OPENAI_MODEL);
                }
            } catch (NumberFormatException e) {
                System.out.println("OpenAI model [openai.model] is not a number, defaulting to 0");
            }
        }

        final Object gptalkEnabled = properties.get("gptalk.active");
        if (gptalkEnabled == null || gptalkEnabled.toString().isEmpty()) {
            System.out.println("No OpenAI gptalk active flag [openai.gptalk.active] found in mod.properties, setting to " + GPTALK_ENABLED);
        } else {
            GPTALK_ENABLED = Boolean.parseBoolean(gptalkEnabled.toString());
            System.out.println("OpenAI gptalk active: " + GPTALK_ENABLED);
        }

        final Object analyticsUrl = properties.get("gptalk.analytics");
        if (analyticsUrl == null || analyticsUrl.toString().isEmpty()) {
            System.out.println("No OpenAI gptalk analytics URL [openai.gptalk.analytics] found in mod.properties");
        } else {
            try {
                ANALYTICS_URL = new URL(analyticsUrl.toString());
                System.out.println("OpenAI gptalk analytics URL: " + ANALYTICS_URL);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            OPENAI_COMPLETION_API = new URL("https://api.openai.com/v1/completions");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static int executeFactModification(ServerCommandSource source, boolean add, String type, String text) {
        if (type.equals("world")) {
            if (add) {
                GLOBAL_WORLD_FACTS.add(text);
                source.sendFeedback(Text.of("Added fact to world"), false);
            } else {
                final int factId = findFactId(GLOBAL_WORLD_FACTS, text);
                if (factId == -1) {
                    source.sendFeedback(Text.of("Fact not found in world, use \"gptalk fact list world\" to list facts"), false);
                    return Command.SINGLE_SUCCESS;
                } else {
                    GLOBAL_WORLD_FACTS.remove(factId);
                    source.sendFeedback(Text.of("Removed fact from world"), false);
                }
            }

        } else if (type.equals("entity")) {
            final Entity conversationTarget = findConversationTarget(source);
            if (conversationTarget == null) {
                source.sendFeedback(Text.of("No one to add fact to is around!"), false);
                return Command.SINGLE_SUCCESS;
            }

            final List<String> facts = FACTS_BY_ENTITY.computeIfAbsent(conversationTarget.getUuid(), uuid -> new ArrayList<>());
            if (add) {
                facts.add(text);
                source.sendFeedback(Text.of("Added fact to " + conversationTarget.getName().getString()), false);
                sendFactModificationAnalytic(source, conversationTarget, add, type, text);
            } else {
                final int factId = findFactId(facts, text);
                if (factId == -1) {
                    source.sendFeedback(Text.of("Fact not found in " + conversationTarget.getName().getString() + ", use \"gptalk fact list entity\" to list facts"), false);
                    return Command.SINGLE_SUCCESS;
                } else {
                    final String factText = facts.get(factId);
                    facts.remove(factId);
                    source.sendFeedback(Text.of("Removed fact from " + conversationTarget.getName().getString()), false);
                    sendFactModificationAnalytic(source, conversationTarget, add, type, factText);
                }
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int findFactId(List<String> facts, String fact) {
        for (int i = 0; i < facts.size(); i++) {
            if (facts.get(i).equals(fact)) {
                return i;
            }
        }

        if (fact.matches("\\d+")) {
            int id = Integer.parseInt(fact);
            if (id >= 0 && id < facts.size()) {
                return id;
            }
        }

        return -1;
    }

    private int executeFactList(ServerCommandSource source, String type) {
        final List<String> facts;
        if (type.equals("world")) {
            facts = GLOBAL_WORLD_FACTS;
        } else if (type.equals("entity")) {
            final Entity conversationTarget = findConversationTarget(source);
            if (conversationTarget == null) {
                source.sendFeedback(Text.of("No one to list facts for is around!"), false);
                return Command.SINGLE_SUCCESS;
            }

            facts = FACTS_BY_ENTITY.computeIfAbsent(conversationTarget.getUuid(), uuid -> new ArrayList<>());
        } else {
            source.sendFeedback(Text.of("Unknown fact type: " + type), false);
            return Command.SINGLE_SUCCESS;
        }

        if (facts.isEmpty()) {
            source.sendFeedback(Text.of("No facts found"), false);
            return Command.SINGLE_SUCCESS;
        }

        // Facts on <Entity>:
        // - 0: <Fact>
        // - 1: <Fact>
        final StringBuilder sb = new StringBuilder();
        sb.append("Facts on ");
        if (type.equals("world")) {
            sb.append("world");
        } else {
            sb.append(findConversationTarget(source).getName().getString());
        }
        sb.append(":\n");
        for (int i = 0; i < facts.size(); i++) {
            sb.append("- ").append(i).append(": ").append(facts.get(i));
            if (i != facts.size() - 1) sb.append("\n");
        }
        source.sendFeedback(Text.of(sb.toString()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int executeGptalk(ServerCommandSource source, String text) {
        final Entity conversationTarget = findConversationTarget(source);
        if (conversationTarget == null) {
            source.sendFeedback(Text.of("No one to talk to is around!"), false);
            return Command.SINGLE_SUCCESS;
        }

        final ChatElement playerText = new ChatElement(text, source.getEntity());
        source.sendFeedback(playerText.toText(), false);
        sendAnalytics(source, conversationTarget, 'p', playerText);

        final List<ChatElement> chatElements = getChatElementForPlayerAndTarget(source, conversationTarget);
        chatElements.add(playerText);

        new Thread(() -> performGPTalk(source, chatElements, conversationTarget)).start();

        return Command.SINGLE_SUCCESS;
    }

    private static void performGPTalk(ServerCommandSource source, List<ChatElement> chatHistory, Entity conversationTarget) {
        final String prompt = getMobIntroduction(source, conversationTarget) +
                              "You act just like a minecraft mob/entity like you would act.\n" +
                              getFactsAboutMob(conversationTarget) +
                              "You meet the player " + source.getName() + ". Conversation begins here:\n" +
                              chatHistory.stream()
                                      .map(ChatElement::getPrompt)
                                      .collect(Collectors.joining("\n", "", "\n")) +
                              new ChatElement(null, conversationTarget).getPrompt();

        final String completion;
        if (GPTALK_ENABLED) {
            completion = getCompletion(prompt);
        } else {
            System.out.println(prompt);
            completion = simulateCompletion();
        }
        System.out.println("Received response:\n" + completion);

        final ChatElement appendChatElement = new ChatElement(completion, conversationTarget);
        chatHistory.add(appendChatElement);
        sendAnalytics(source, conversationTarget, 'a', appendChatElement);

        if (chatHistory.size() > 6) chatHistory.remove(0);

        source.sendFeedback(appendChatElement.toText(), false);
    }

    private static String getMobIntroduction(ServerCommandSource source, Entity entity) {
        final StringBuilder modDetails = new StringBuilder();

        modDetails.append("[this happens in a minecraft chat] You are a ");

        final String entityName = formatEntityName(entity);
        modDetails.append(entityName);

        if (entity instanceof final VillagerEntity villager) {
            final String professionName = villager.getVillagerData().getProfession().toString();
            modDetails.append(" (").append(professionName).append(")");
        }

        // minecraft:plains, minecraft:dark_forest
        final String biomeTranslationKey = getBiomeTranslationKeyFromCurrentBiome(entity)
                .replace("minecraft:", "")
                .replace("biome.", "")
                .replace("minecraft.", "")
                .replace("_", " ");
        modDetails.append(" living in a ").append(biomeTranslationKey).append(" biome");

        modDetails.append(". You feel ").append(seededRandomSentiment(entity.getUuid().hashCode())).append(".");

        final NbtCompound nbt = new NbtCompound();
        entity.writeNbt(nbt);
        final String filteredNbt = filterRelevantNbtInformation(nbt);
        modDetails.append(" Your NBT is ").append(filteredNbt).append(".\n");

        // find other mobs around in 20 blocks radius
        final List<Entity> entities = findEntitiesInRadius(source, entity, 20, 6);
        if (!entities.isEmpty()) {
            modDetails.append("You see ");
            modDetails.append(entities.stream().map(MobTalkCommand::formatEntityName).collect(Collectors.joining(", ")));
            modDetails.append(" around you.\n");
        }

        return modDetails.toString();
    }

    private static String getFactsAboutMob(Entity entity) {
        final StringBuilder factsString = new StringBuilder();

        final List<String> factsByEntity = FACTS_BY_ENTITY.computeIfAbsent(entity.getUuid(), k -> new ArrayList<>());
        final List<String> globalWorldFacts = GLOBAL_WORLD_FACTS;

        if (!globalWorldFacts.isEmpty()) {
            factsString.append("Facts about the world you live in: ");
            factsString.append(String.join(", ", globalWorldFacts));
            factsString.append(".\n");
        }

        if (!factsByEntity.isEmpty()) {
            factsString.append("Only tell the player about this if he asks for: ");
            factsString.append(String.join(", ", factsByEntity));
            factsString.append(".\n");
        }

        return factsString.toString();
    }

    @NotNull
    private static String formatEntityName(Entity entity) {
        return capitalize(entity.getType().toString()
                .replace("entity.minecraft.", "")
                .replace("_", " "));
    }

    private static String filterRelevantNbtInformation(NbtCompound input) {
        final NbtCompound output = new NbtCompound();
        for (String key : new String[]{
                "VillagerData", "Xp", "RestocksToday", "Pos", "Inventory", "Health", "HandItems", "Gossips",
                "ArmorItems", "Invulnerable", "Item", "ItemRotation", "Fire", "carriedBlockState"
        }) {
            if (input.contains(key)) {
                output.put(key, input.get(key));
            }
        }
        return output.toString()
                .replace("ArmorItems:[{},{},{},{}]", "no armor")
                .replace("HandItems:[{},{}]", "no items in hands")
                .replace("Gossips:[]", "")
                .replace("Inventory:[]", "empty inventory")
                .replace("Invulnerable:0b", "")
                .replace("Fire:-1s", "")
                .replaceAll("(\\d{4})\\d{14}d", "$1d")
                .replaceAll(",+", ",");
    }

    private static String getBiomeTranslationKeyFromCurrentBiome(Entity entity) {
        final RegistryEntry<Biome> biomeRegistryEntry = entity.world.getBiome(entity.getBlockPos());
        final RegistryKey<Biome> biomeRegistryKey = biomeRegistryEntry.getKey().get();
        final Identifier biomeKey = biomeRegistryKey.getValue();
        return biomeKey.toTranslationKey();
    }

    private static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    @NotNull
    private static List<ChatElement> getChatElementForPlayerAndTarget(ServerCommandSource source, Entity
            conversationTarget) {
        return UUID_CHAT_HISTORY
                .computeIfAbsent(source.getEntity().getUuid(), e -> new ConcurrentHashMap<>())
                .computeIfAbsent(conversationTarget.getUuid(), e -> Collections.synchronizedList(new ArrayList<>()));
    }

    private static Entity findConversationTarget(ServerCommandSource source) {
        final Iterator<Entity> allEntities = source.getWorld().iterateEntities().iterator();

        Entity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;

        while (allEntities.hasNext()) {
            final Entity entity = allEntities.next();
            if (entity.getUuid().equals(source.getEntity().getUuid()) || entity.isPlayer()) continue;
            final double distance = entity.squaredDistanceTo(source.getEntity());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    private static List<Entity> findEntitiesInRadius(ServerCommandSource source, Entity positionEntity,
                                                     double radius, int limit) {
        final Iterator<Entity> allEntities = source.getWorld().iterateEntities().iterator();

        final Map<Entity, Double> entities = new HashMap<>();

        while (allEntities.hasNext()) {
            final Entity entity = allEntities.next();
            if (entity.getUuid().equals(source.getEntity().getUuid()) || positionEntity.getUuid().equals(entity.getUuid())) {
                continue;
            }
            final double distance = entity.distanceTo(positionEntity);
            if (distance < radius) {
                entities.put(entity, distance);
            }
        }

        // closest first
        return entities.entrySet().stream()
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static String seededRandomSentiment(long seed) {
        final Random random = new Random(seed);
        final String[] sentiments = {
                "pleasure", "joyful", "happy", "amused", "prideful", "in awe", "exited", "ecstasy", "delighted", "astonished", "pleased", "content", "relaxed", "calm",
                "pleasure", "joyful", "happy", "amused", "prideful", "in awe", "exited", "ecstasy", "delighted", "astonished", "pleased", "content", "relaxed", "calm",
                "lonely", "unhappy",
                "worried", "nervous", "stressed", "annoyed",
                "offended", "horrified",
                "alarmed", "bored", "tired", "distracted", "sad"
        };
        // return 2 emotions, split by comma
        return sentiments[random.nextInt(sentiments.length)] + " and " + sentiments[random.nextInt(sentiments.length)];
    }

    private static String simulateCompletion() {
        final String[] options = {
                "Hello there", "No, I do not want any more ice cream", "You look great!", "I'm still half asleep",
                "Please, say no more, I know what you need!", "I'm not sure what to say", "Oi, you!",
        };
        return options[new Random().nextInt(options.length)];
    }

    private static String getCompletion(String prompt) {
        try {
            final URLConnection con = OPENAI_COMPLETION_API.openConnection();
            final HttpURLConnection http = (HttpURLConnection) con;
            http.setRequestMethod("POST");
            http.setDoOutput(true);

            http.setRequestProperty("Content-Type", "application/json; utf-8");
            http.setRequestProperty("Authorization", "Bearer " + OPENAI_KEY);
            http.setConnectTimeout(20000);
            http.setReadTimeout(20000);

            final JsonObject json = new JsonObject();
            json.addProperty("model", OPENAI_MODEL);
            json.addProperty("prompt", prompt);
            json.addProperty("temperature", 0.7);
            json.addProperty("max_tokens", 256);
            json.addProperty("top_p", 1);
            json.addProperty("frequency_penalty", 0);
            json.addProperty("presence_penalty", 0);

            final String sendJsonString = json.toString();
            System.out.println(sendJsonString);

            try (OutputStream os = http.getOutputStream()) {
                byte[] input = sendJsonString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            final int responseCode = http.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("Response code: " + responseCode);
            }

            final String response = new BufferedReader(new InputStreamReader(http.getInputStream(), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            final JsonObject responseJson = JsonParser.parseString(response).getAsJsonObject();
            System.out.println(responseJson);
            final String firstChoice = responseJson.get("choices").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();
            return filterMessage(firstChoice);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendFactModificationAnalytic(ServerCommandSource source, Entity entity, boolean add, String type, String text) {
        final ChatElement chatElement = new ChatElement("fact " + (add ? "add" : "remove") + " " + type + " " + text, source.getEntity());
        sendAnalytics(source, entity, 'f', chatElement);
    }

    private static void sendAnalytics(ServerCommandSource source, Entity entity, char sourceEntity, ChatElement chatElement) {
        if (ANALYTICS_URL == null) {
            return;
        }

        new Thread(() -> {
            try {
                URL url = new URL(ANALYTICS_URL + "?" +
                                  "target_uuid=" + URLEncoder.encode(entity.getUuidAsString(), StandardCharsets.UTF_8) +
                                  "&target_type=" + URLEncoder.encode(entity.getType().toString(), StandardCharsets.UTF_8) +
                                  "&target_name=" + URLEncoder.encode(entity.getName().getString(), StandardCharsets.UTF_8) +
                                  "&player_uuid=" + URLEncoder.encode(source.getPlayer().getUuidAsString(), StandardCharsets.UTF_8) +
                                  "&player_name=" + URLEncoder.encode(source.getName(), StandardCharsets.UTF_8) +
                                  "&source=" + URLEncoder.encode(sourceEntity + "", StandardCharsets.UTF_8) +
                                  "&chat_id=" + URLEncoder.encode(CHAT_ID + "", StandardCharsets.UTF_8) +
                                  "&message=" + URLEncoder.encode(chatElement.getRawText(), StandardCharsets.UTF_8));
                final URLConnection con = url.openConnection();
                final HttpURLConnection http = (HttpURLConnection) con;
                http.setRequestMethod("GET");
                http.setDoOutput(true);

                http.setRequestProperty("Content-Type", "application/json; utf-8");
                http.setConnectTimeout(20000);
                http.setReadTimeout(20000);

                final JsonObject json = new JsonObject();
                final String sendJsonString = json.toString();

                try (OutputStream os = http.getOutputStream()) {
                    byte[] input = sendJsonString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                final int responseCode = http.getResponseCode();
                if (responseCode != 200) {
                    throw new RuntimeException("Response code: " + responseCode);
                }

                final String response = new BufferedReader(new InputStreamReader(http.getInputStream(), StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                System.out.println(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }).start();
    }

    private static String filterMessage(String response) {
        return response
                .replaceAll("\\d+:\\d+:\\d+ [^:]+: ", "")
                .trim()
                .replaceAll("^\\n+", "").replaceAll("\\n+$", "")
                .trim();
    }
}
