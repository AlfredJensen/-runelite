/*
 * Copyright (c) 2018, Seth <Sethtroll3@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.poh;

import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.HiscoreManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.hiscore.HiscoreEndpoint;
import net.runelite.http.api.hiscore.HiscoreResult;
import net.runelite.http.api.hiscore.Skill;

@PluginDescriptor(
	name = "Player-owned House",
	description = "Show minimap icons and mark unlit/lit burners",
	tags = {"construction", "poh", "minimap", "overlay"}
)
@Slf4j
public class PohPlugin extends Plugin
{
	static final Set<Integer> BURNER_UNLIT = Sets.newHashSet(ObjectID.INCENSE_BURNER, ObjectID.INCENSE_BURNER_13210, ObjectID.INCENSE_BURNER_13212);
	static final Set<Integer> BURNER_LIT = Sets.newHashSet(ObjectID.INCENSE_BURNER_13209, ObjectID.INCENSE_BURNER_13211, ObjectID.INCENSE_BURNER_13213);
	private static final double ESTIMATED_TICK_LENGTH = 0.6;

	@Getter(AccessLevel.PACKAGE)
	private final Map<TileObject, Tile> pohObjects = new HashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private final Map<Tile, IncenseBurner> incenseBurners =  new HashMap<>();

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PohOverlay overlay;

	@Inject
	private Client client;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private HiscoreManager hiscoreManager;

	@Inject
	private BurnerOverlay burnerOverlay;

	@Provides
	PohConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PohConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		overlayManager.add(burnerOverlay);
		overlay.updateConfig();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		overlayManager.remove(burnerOverlay);
		pohObjects.clear();
		incenseBurners.clear();
	}

	@Subscribe
	public void updateConfig(ConfigChanged event)
	{
		overlay.updateConfig();
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		final GameObject gameObject = event.getGameObject();

		if (!BURNER_LIT.contains(gameObject.getId()) && !BURNER_UNLIT.contains(gameObject.getId()))
		{
			if (PohIcons.getIcon(gameObject.getId()) != null)
			{
				pohObjects.put(gameObject, event.getTile());
			}

			return;
		}

		final double countdownTimer = 130.0; // Minimum amount of seconds a burner will light
		final double randomTimer = 30.0; // Minimum amount of seconds a burner will light
		incenseBurners.put(event.getTile(), new IncenseBurner(gameObject.getId(), countdownTimer, randomTimer, null));
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject gameObject = event.getGameObject();
		pohObjects.remove(gameObject);
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		DecorativeObject decorativeObject = event.getDecorativeObject();
		if (PohIcons.getIcon(decorativeObject.getId()) != null)
		{
			pohObjects.put(decorativeObject, event.getTile());
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		DecorativeObject decorativeObject = event.getDecorativeObject();
		pohObjects.remove(decorativeObject);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOADING)
		{
			pohObjects.clear();
			incenseBurners.clear();
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		final Actor actor = event.getActor();
		final String actorName = actor.getName();

		if (!(actor instanceof Player) || actor.getAnimation() != AnimationID.INCENSE_BURNER)
		{
			return;
		}

		final LocalPoint loc = actor.getLocalLocation();

		// Find burner closest to player
		incenseBurners.keySet()
			.stream()
			.min(Comparator.comparingInt(a -> loc.distanceTo(a.getLocalLocation())))
			.ifPresent(tile ->
			{
				final IncenseBurner incenseBurner = incenseBurners.get(tile);

				if (actor == client.getLocalPlayer())
				{
					int level = client.getRealSkillLevel(net.runelite.api.Skill.FIREMAKING);
					updateBurner(incenseBurner, level);
				}
				else if (actorName != null)
				{
					lookupPlayer(actorName, incenseBurner);
				}
			});

	}

	private void lookupPlayer(String playerName, IncenseBurner incenseBurner)
	{
		executor.execute(() ->
		{
			try
			{
				final HiscoreResult playerStats = hiscoreManager.lookup(playerName, HiscoreEndpoint.NORMAL);

				if (playerStats == null)
				{
					return;
				}

				final Skill fm = playerStats.getFiremaking();
				final int level = fm.getLevel();
				updateBurner(incenseBurner, Math.max(level, 1));
			}
			catch (IOException e)
			{
				log.warn("Error fetching Hiscore data " + e.getMessage());
			}
		});
	}

	private static void updateBurner(IncenseBurner incenseBurner, int fmLevel)
	{
		incenseBurner.setCountdownTimer((200 + fmLevel) * ESTIMATED_TICK_LENGTH);
		incenseBurner.setRandomTimer(fmLevel * ESTIMATED_TICK_LENGTH);
	}
}