package com.tonzo.api;

import com.google.inject.Provides;
import com.google.gson.JsonObject;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.VarPlayer;
import net.runelite.http.api.RuneLiteAPI;

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
	public ClientThread client_thread;
	public HttpServer server;
	public boolean tonzo_active;

	public enum EquipSlots
	{
		HEAD, BACK, NECK, WEAPON, CHEST, OFFHAND, PLACEHOLDERA, LEGS, PLACEHOLDERB, GLOVES, BOOTS, PLACEHOLDERC, RING, AMMO
	}

	@Override
	protected void startUp() throws Exception
	{
		String http_port = config.apiPort();
		log.info("TonzoAPI running on Port: " + http_port);
		int http_port_int = parseInt(http_port);
		server = HttpServer.create(new InetSocketAddress(http_port_int), 0);
		server.createContext("/inv", handleInventory());
		server.createContext("/equip", handleEquipment());
		server.createContext("/stats", this::handleStats);
		server.createContext("/start", this::handleStart);
		server.createContext("/stop", this::handleStop);
		server.createContext("/tonzo", this::handleTonzo);
		server.setExecutor(Executors.newSingleThreadExecutor());
		server.start();
	}

	@Override
	protected void shutDown() throws Exception
	{
		server.stop(1);
		log.info("TonzoAPI stopped!");
	}

	@Provides
	TonzoApiConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TonzoApiConfig.class);
	}

	public void handleStats(HttpExchange exchange) throws IOException {
		Player player = client.getLocalPlayer();

		JsonObject object = new JsonObject();
		JsonObject player_coordinates = new JsonObject();
		JsonObject player_object = new JsonObject();

		//GENERAL
		object.addProperty("gameCycle", client.getGameCycle());

		//PLAYER
		player_object.addProperty("animation", player.getAnimation());
		player_object.addProperty("animationPose", player.getPoseAnimation());
		player_object.addProperty("interactingCode", String.valueOf(player.getInteracting()));
		player_object.addProperty("runEnergy", client.getEnergy());
		player_object.addProperty("specialAttackEnergy", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
		player_object.addProperty("currentPrayer", client.getBoostedSkillLevel(Skill.PRAYER));
		player_object.addProperty("maxPrayer", client.getRealSkillLevel(Skill.PRAYER));
		player_object.addProperty("currentHealth", client.getBoostedSkillLevel(Skill.HITPOINTS));
		player_object.addProperty("maxHealth", client.getRealSkillLevel(Skill.HITPOINTS));

		//PLAYER COORDINATES
		player_coordinates.addProperty("x", player.getWorldLocation().getX());
		player_coordinates.addProperty("y", player.getWorldLocation().getY());
		player_coordinates.addProperty("x,y", String.format("%s,%s", player.getWorldLocation().getX(), player.getWorldLocation().getY()));
		player_coordinates.addProperty("plane", player.getWorldLocation().getPlane());
		player_coordinates.addProperty("regionID", player.getWorldLocation().getRegionID());
		player_coordinates.addProperty("regionX", player.getWorldLocation().getRegionX());
		player_coordinates.addProperty("regionY", player.getWorldLocation().getRegionY());

		player_object.add("playerCoordinates", player_coordinates);
		object.add("playerObject", player_object);

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
				List<Object> empty_array = new ArrayList<Object>();
				exchange.sendResponseHeaders(200, 0);
				try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
					RuneLiteAPI.GSON.toJson(empty_array, out);
				}
			}

			List<Object> item_array = new ArrayList<Object>();

			int count = 0;
			for (Item i : items) {
				Map<String, Integer> dict = new HashMap<String, Integer>();
				dict.put("id",i.getId());
				dict.put("invSlot",count);
				dict.put("quantity",i.getQuantity());
				item_array.add(dict);
				count++;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(item_array, out);
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
				List<Object> empty_array = new ArrayList<Object>();
				exchange.sendResponseHeaders(200, 0);
				try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
					RuneLiteAPI.GSON.toJson(empty_array, out);
				}
			}

			JsonObject equipment_object = new JsonObject();

			int count = 0;
			for (Item i : items) {
				JsonObject slot = new JsonObject();
				slot.addProperty("id", i.getId());
				slot.addProperty("quantity",i.getQuantity());
				if(EquipSlots.values()[count] == EquipSlots.PLACEHOLDERA || EquipSlots.values()[count] == EquipSlots.PLACEHOLDERB || EquipSlots.values()[count] == EquipSlots.PLACEHOLDERC) {

				}else{
					equipment_object.add(String.valueOf(EquipSlots.values()[count]), slot);
				}
				count++;
			}

			exchange.sendResponseHeaders(200, 0);
			try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
				RuneLiteAPI.GSON.toJson(equipment_object, out);
			}
		};
	}

	public void handleStart(HttpExchange exchange) throws IOException {
		tonzo_active = true;
		JsonObject object = new JsonObject();
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}

	public void handleStop(HttpExchange exchange) throws IOException {
		tonzo_active = false;
		JsonObject object = new JsonObject();
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}

	public void handleTonzo(HttpExchange exchange) throws IOException {
		JsonObject object = new JsonObject();
		object.addProperty("tonzoActive", tonzo_active);
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(object, out);
		}
	}

	private <T> T invokeAndWait(Callable<T> r) {
		try {
			AtomicReference<T> ref = new AtomicReference<>();
			Semaphore semaphore = new Semaphore(0);
			client_thread.invokeLater(() -> {
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
