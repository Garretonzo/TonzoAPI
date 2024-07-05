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

	@Override
	protected void startUp() throws Exception
	{
		String http_port = config.apiPort();
		log.info("TonzoAPI running on Port: " + http_port);
		int http_port_int = parseInt(http_port);
		server = HttpServer.create(new InetSocketAddress(http_port_int), 0);
		server.createContext("/inventory", this::handleInventory);
		server.createContext("/state", this::handleState);
		server.setExecutor(Executors.newSingleThreadExecutor());
		server.start();

		state = new JsonObject();
		inventory = new Vector<JsonObject>();
		for (int i = 0; i < 28; i++){
			JsonObject item = new JsonObject();
			item.addProperty("id", 0);
			item.addProperty("quantity", 0);
			inventory.add(item);
		}
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
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();

		//GENERAL
		state.addProperty("gameCycle", client.getGameCycle());
		//PLAYER
		state.addProperty("animation", player.getAnimation());
		state.addProperty("interactingCode", String.valueOf(player.getInteracting()));
		state.addProperty("runEnergy", client.getEnergy());
		state.addProperty("specialAttackEnergy", client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
		state.addProperty("currentPrayer", client.getBoostedSkillLevel(Skill.PRAYER));
		state.addProperty("currentHealth", client.getBoostedSkillLevel(Skill.HITPOINTS));
		//PLAYER COORDINATES private WorldPoint client.getLocalPlayer().getWorldLocation()
		state.addProperty("x", player.getWorldLocation().getX());
		state.addProperty("y", player.getWorldLocation().getY());
		state.addProperty("x,y", String.format("%s,%s", player.getWorldLocation().getX(), player.getWorldLocation().getY()));
		state.addProperty("plane", player.getWorldLocation().getPlane());

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
			count++;
		}
		for (int i = count; i < 28; i++){
			inventory.get(count).addProperty("id", 0);
			inventory.get(count).addProperty("quantity", 0);
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
