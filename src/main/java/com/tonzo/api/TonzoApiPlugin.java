package com.tonzo.api;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.callback.ClientThread;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.VarPlayer;
import net.runelite.http.api.RuneLiteAPI;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStreamWriter;
import static java.lang.Integer.parseInt;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Semaphore;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "TonzoAPI"
)
public class TonzoApiPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private TonzoApiConfig config;

	@Inject
	public ClientThread clientThread;
	public HttpServer server;

	public enum equipmentSlots
	{
		head, back, neck, weapon, chest, shield, placeholderA, legs, placeholderB, gloves, boots, placeholderC, ring, ammo
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("TonzoAPI started!");

		String httpPort = config.apiPort();
		log.info("TonzoAPI running on Port: " + httpPort);

		int httpPortInt = parseInt(httpPort);

		server = HttpServer.create(new InetSocketAddress(httpPortInt), 0);
		server.createContext("/inv", handleInventory());
		server.createContext("/equip", handleEquipment());
		server.createContext("/stats", this::handleStats);
		server.setExecutor(Executors.newSingleThreadExecutor());
		server.start();
	}

	@Override
	protected void shutDown() throws Exception
	{
		server.stop(1);
		log.info("TonzoAPI stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "TonzoAPI says " + config.apiPort(), null);
		}
	}

	@Provides
	TonzoApiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TonzoApiConfig.class);
	}

	public void handleStats(HttpExchange exchange) throws IOException {
		Player player = client.getLocalPlayer();

		JsonObject object = new JsonObject();
		JsonObject camera = new JsonObject();
		JsonObject playerCoordinates = new JsonObject();
		JsonObject playerObject = new JsonObject();

		//GENERAL
		object.addProperty("gameCycle", client.getGameCycle());

		//PLAYER
		playerObject.addProperty("animation", player.getAnimation());
		playerObject.addProperty("animationPose", player.getPoseAnimation());
		playerObject.addProperty("interactingCode", String.valueOf(player.getInteracting()));
		playerObject.addProperty("runEnergy", client.getEnergy());
		playerObject.addProperty("specialAttackEnergy", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
		playerObject.addProperty("currentPrayer", client.getBoostedSkillLevel(Skill.PRAYER));
		playerObject.addProperty("maxPrayer", client.getRealSkillLevel(Skill.PRAYER));
		playerObject.addProperty("currentHealth", client.getBoostedSkillLevel(Skill.HITPOINTS));
		playerObject.addProperty("maxHealth", client.getRealSkillLevel(Skill.HITPOINTS));

		//PLAYER COORDINATES
		playerCoordinates.addProperty("x", player.getWorldLocation().getX());
		playerCoordinates.addProperty("y", player.getWorldLocation().getY());
		playerCoordinates.addProperty("plane", player.getWorldLocation().getPlane());
		playerCoordinates.addProperty("regionID", player.getWorldLocation().getRegionID());
		playerCoordinates.addProperty("regionX", player.getWorldLocation().getRegionX());
		playerCoordinates.addProperty("regionY", player.getWorldLocation().getRegionY());

		//CAMERA
		camera.addProperty("yaw", client.getCameraYaw());
		camera.addProperty("pitch", client.getCameraPitch());
		camera.addProperty("x", client.getCameraX());
		camera.addProperty("y", client.getCameraY());
		camera.addProperty("z", client.getCameraZ());

		playerObject.add("playerCoordinates", playerCoordinates);
		object.add("camera", camera);
		object.add("playerObject", playerObject);

		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}

	private HttpHandler handleInventory() {
		return exchange -> {
			Item[] items = invokeAndWait(() -> {
				ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
				if (itemContainer != null) {
					return itemContainer.getItems();
				}
				return null;
			});

			if (items == null) {
				List<Object> emptyArray = new ArrayList<Object>();
				exchange.sendResponseHeaders(200, 0);
				try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
					RuneLiteAPI.GSON.toJson(emptyArray, out);
				}
			}

			List<Object> itemArray = new ArrayList<Object>();

			int count = 0;
			for (Item i : items) {
				Map<String, Integer> dict = new HashMap<String, Integer>();
				dict.put("id",i.getId());
				dict.put("invSlot",count);
				dict.put("quantity",i.getQuantity());
				itemArray.add(dict);
				count++;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(itemArray, out);
			}
		};
	}

	private HttpHandler handleEquipment() {
		return exchange -> {
			Item[] items = invokeAndWait(() -> {
				ItemContainer itemContainer = client.getItemContainer(InventoryID.EQUIPMENT);
				if (itemContainer != null) {
					return itemContainer.getItems();
				}
				return null;
			});

			if (items == null) {
				List<Object> emptyArray = new ArrayList<Object>();
				exchange.sendResponseHeaders(200, 0);
				try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
					RuneLiteAPI.GSON.toJson(emptyArray, out);
				}
			}

			JsonObject equipmentObject = new JsonObject();

			int count = 0;
			for (Item i : items) {
				JsonObject slot = new JsonObject();
				slot.addProperty("id", i.getId());
				slot.addProperty("quantity",i.getQuantity());
				//Do this better, ask Rob
				if(equipmentSlots.values()[count] == equipmentSlots.placeholderA || equipmentSlots.values()[count] == equipmentSlots.placeholderB || equipmentSlots.values()[count] == equipmentSlots.placeholderC) {

				}else{
					equipmentObject.add(String.valueOf(equipmentSlots.values()[count]), slot);
				}
				count++;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(equipmentObject, out);
			}
		};
	}

	private <T> T invokeAndWait(Callable<T> r) {
		try {
			AtomicReference<T> ref = new AtomicReference<>();
			Semaphore semaphore = new Semaphore(0);
			clientThread.invokeLater(() -> {
				try {

					ref.set(r.call());
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					semaphore.release();
				}
			});
			semaphore.acquire();
			return ref.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
