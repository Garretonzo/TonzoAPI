package com.tonzo.api;

import com.google.inject.Provides;
import com.google.gson.JsonObject;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.events.GameTick;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.Item;
import net.runelite.api.InventoryID;
import net.runelite.api.VarPlayer;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.http.api.RuneLiteAPI;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStreamWriter;

import static java.lang.Integer.parseInt;

import java.net.InetSocketAddress;

import java.util.*;
import java.util.concurrent.Executors;

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
	private HttpServer server;
	private JsonObject state;
	private Vector<JsonObject> inventory;
	private int prev_health;
	private int consecutive_idle_ticks;

	@Override
	protected void startUp() throws Exception
	{
		String http_port = config.apiPort();
		log.info("TonzoAPI running on Port: " + http_port);
		int http_port_int = parseInt(http_port);
		server = HttpServer.create(new InetSocketAddress(http_port_int), 0);
		server.createContext("/state", this::handleState);
		server.createContext("/inventory", this::handleInventory);
		server.setExecutor(Executors.newSingleThreadExecutor());
		server.start();

		state = new JsonObject();
		inventory = new Vector<JsonObject>();
		for (int i = 0; i < 28; i++){
			JsonObject item = new JsonObject();
			item.addProperty("id", 0);
			item.addProperty("quantity", 0);
			item.addProperty("slot", i);
			inventory.add(item);
		}

		prev_health = 0;
		consecutive_idle_ticks = 0;
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

	@Subscribe
	private void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();

		//GENERAL
		state.addProperty("gameCycle", client.getGameCycle());
		//PLAYER
		int animation = player.getAnimation();
		state.addProperty("animation", animation);
		state.addProperty("interactingCode", String.valueOf(player.getInteracting()));
		state.addProperty("runEnergy", client.getEnergy());
		state.addProperty("specialAttackEnergy", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
		state.addProperty("currentPrayer", client.getBoostedSkillLevel(Skill.PRAYER));
		int health = client.getBoostedSkillLevel(Skill.HITPOINTS);
		state.addProperty("currentHealth", health);
		//PLAYER COORDINATES
		int x = player.getWorldLocation().getX();
		int y = player.getWorldLocation().getY();
		state.addProperty("x", x);
		state.addProperty("y", y);
		state.addProperty("x,y", String.format("%s,%s", x,y));
		state.addProperty("plane", player.getWorldLocation().getPlane());
		state.addProperty("trueIdle", false);

		//LOGIC
		if (animation != -1)
		{
			consecutive_idle_ticks = 0;
		}
		else {
			consecutive_idle_ticks++;
			if (consecutive_idle_ticks >= 4 || health < prev_health) {
				state.addProperty("trueIdle", true);
			}
		}
		prev_health = health;

		// INVENTORY
		Item[] items;
		try {
			items = client.getItemContainer(InventoryID.INVENTORY).getItems();
		}
		catch(Exception e) {
			items = new Item[0];
		}
		int count = 0;
		for (Item i : items) {
			inventory.get(count).addProperty("id", i.getId());
			inventory.get(count).addProperty("quantity", i.getQuantity());
			inventory.get(count).addProperty("slot", count);
			count++;
		}
		for (int i = count; i < 28; i++){
			inventory.get(count).addProperty("id", -1);
			inventory.get(count).addProperty("quantity", 0);
			inventory.get(count).addProperty("slot", i);
		}
	}

	private void handleState(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(state, out);
		}
	}

	private void handleInventory(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(inventory, out);
		}
	}
}
