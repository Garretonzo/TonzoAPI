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
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.VarClientInt;
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
	private JsonObject experience;
	private Vector<JsonObject> inventory;
	private int prev_health;
	private String prev_xy;

	private int consecutive_idle_ticks;
	private int consecutive_stationary_ticks;

	@Override
	protected void startUp() throws Exception
	{
		String http_port = config.apiPort();
		log.info("TonzoAPI running on Port: " + http_port);
		int http_port_int = parseInt(http_port);
		server = HttpServer.create(new InetSocketAddress(http_port_int), 0);
		server.createContext("/state", this::handleState);
		server.createContext("/experience", this::handleExperience);
		server.createContext("/inventory", this::handleInventory);
		server.setExecutor(Executors.newSingleThreadExecutor());
		server.start();

		state = new JsonObject();
		experience = new JsonObject();
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
		prev_xy = "";
		consecutive_stationary_ticks = 0;
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
		state.addProperty("inventoryTab", client.getVarcIntValue(VarClientInt.INVENTORY_TAB));

		//PLAYER COORDINATES
		int x = player.getWorldLocation().getX();
		int y = player.getWorldLocation().getY();
		String xy = String.format("%s,%s", x,y);
		state.addProperty("x", x);
		state.addProperty("y", y);
		// state.addProperty("x,y", xy);
		state.addProperty("plane", player.getWorldLocation().getPlane());
		state.addProperty("trueIdle", false);
		state.addProperty("moving", true);

		//OPEN INTERFACES
		boolean bank_open = client.getWidget(ComponentID.BANK_ITEM_CONTAINER) != null;
		state.addProperty("bankOpen", bank_open);
		boolean deposit_box_open = client.getWidget(ComponentID.DEPOSIT_BOX_INVENTORY_ITEM_CONTAINER) != null;
		state.addProperty("depositOpen", deposit_box_open);

		//LOGIC
		if (animation != -1)
		{
			consecutive_idle_ticks = 0;
		}
		else {
			consecutive_idle_ticks++;
			if (consecutive_idle_ticks >= 4 || health < prev_health) {
				state.addProperty("trueIdle", true);
				consecutive_idle_ticks = 4;
			}
		}
		prev_health = health;

		if (!prev_xy.equals(xy))
		{
			consecutive_stationary_ticks = 0;
		}
		else {
			consecutive_stationary_ticks++;
			if(consecutive_stationary_ticks >= 4) {
				state.addProperty("moving", false);
				consecutive_stationary_ticks = 4;
			}
		}
		prev_xy = xy;

		//EXPERIENCE
		int attack = client.getSkillExperience(Skill.ATTACK);
		int strength = client.getSkillExperience(Skill.STRENGTH);
		int defence = client.getSkillExperience(Skill.DEFENCE);
		int ranged = client.getSkillExperience(Skill.RANGED);
		int prayer = client.getSkillExperience(Skill.PRAYER);
		int magic = client.getSkillExperience(Skill.MAGIC);
		int runecraft = client.getSkillExperience(Skill.RUNECRAFT);
		int hitpoints = client.getSkillExperience(Skill.HITPOINTS);
		int crafting = client.getSkillExperience(Skill.CRAFTING);
		int mining = client.getSkillExperience(Skill.MINING);
		int smithing = client.getSkillExperience(Skill.SMITHING);
		int fishing = client.getSkillExperience(Skill.FISHING);
		int cooking = client.getSkillExperience(Skill.COOKING);
		int firemaking = client.getSkillExperience(Skill.FIREMAKING);
		int woodcutting = client.getSkillExperience(Skill.WOODCUTTING);
		int agility = client.getSkillExperience(Skill.AGILITY);
		int herblore = client.getSkillExperience(Skill.HERBLORE);
		int thieving = client.getSkillExperience(Skill.THIEVING);
		int fletching = client.getSkillExperience(Skill.FLETCHING);
		int slayer = client.getSkillExperience(Skill.SLAYER);
		int farming = client.getSkillExperience(Skill.FARMING);
		int construction = client.getSkillExperience(Skill.CONSTRUCTION);
		int hunter = client.getSkillExperience(Skill.HUNTER);
		experience.addProperty("attack", attack);
		experience.addProperty("strength", strength);
		experience.addProperty("defence", defence);
		experience.addProperty("ranged", ranged);
		experience.addProperty("prayer", prayer);
		experience.addProperty("magic", magic);
		experience.addProperty("runecraft", runecraft);
		experience.addProperty("hitpoints", hitpoints);
		experience.addProperty("crafting", crafting);
		experience.addProperty("mining", mining);
		experience.addProperty("smithing", smithing);
		experience.addProperty("fishing", fishing);
		experience.addProperty("cooking", cooking);
		experience.addProperty("firemaking", firemaking);
		experience.addProperty("woodcutting", woodcutting);
		experience.addProperty("agility", agility);
		experience.addProperty("herblore", herblore);
		experience.addProperty("thieving", thieving);
		experience.addProperty("fletching", fletching);
		experience.addProperty("slayer", slayer);
		experience.addProperty("farming", farming);
		experience.addProperty("construction", construction);
		experience.addProperty("hunter", hunter);

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

	private void handleExperience(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(experience, out);
		}
	}

	private void handleInventory(HttpExchange exchange) throws IOException {
		exchange.sendResponseHeaders(200, 0);
		try (OutputStreamWriter out = new OutputStreamWriter(exchange.getResponseBody())) {
			RuneLiteAPI.GSON.toJson(inventory, out);
		}
	}
}
