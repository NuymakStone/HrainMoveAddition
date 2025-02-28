/*
 * This file is part of HrainMoveAddition Anticheat.
 * Copyright (C) 2018 HrainMoveAddition Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.nuymakstone.HrainAC.check.movement.look;

import me.nuymakstone.HrainAC.HrainACPlayer;
import me.nuymakstone.HrainAC.check.CustomCheck;
import me.nuymakstone.HrainAC.check.Cancelless;
import me.nuymakstone.HrainAC.util.MathPlus;
import me.nuymakstone.HrainAC.event.Event;
import me.nuymakstone.HrainAC.event.InteractEntityEvent;
import me.nuymakstone.HrainAC.event.InteractWorldEvent;
import me.nuymakstone.HrainAC.event.MoveEvent;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * AimbotHeuristic exploits flaws in aim-bot cheats by
 * analyzing mouse movement patterns during interaction. Although
 * easily bypassed, it catches a significant number of cheaters.
 * Caution is advised since this check may false flag during
 * certain circumstances. This check should only be used as a
 * hint that the player might be cheating.
 */
public class AimbotHeuristic extends CustomCheck implements Cancelless {

    private final Map<UUID, List<Vector>> mouseMoves;
    private final Map<UUID, List<Long>> clickTimes;

    private static final int MOVES_PER_SAMPLE = 4; //must be greater than 0
    private final int MOVES_AFTER_HIT;
    private final boolean DEBUG;

    public AimbotHeuristic() {
        super("aimbotheuristic", false, -1, 5, 0.99, 5000, "&7%player% 没能绕过 aimbot (heuristic), VL %vl%", null);
        mouseMoves = new HashMap<>();
        clickTimes = new HashMap<>();

        MOVES_AFTER_HIT = MOVES_PER_SAMPLE - MOVES_PER_SAMPLE / 2;

        DEBUG = (boolean) customSetting("debug", "", false);
    }

    @EventHandler
    public void onPearl(final ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player && e.getEntity() instanceof EnderPearl) {

            final Player player = (Player) e.getEntity().getShooter();

            new BukkitRunnable() {
                @Override
                public void run() {
                    return;
                }

            }.runTaskTimer(HrainAC, 0, 10);
        }
    }

    public void check(Event e) {
        if (e instanceof MoveEvent) {
            processMove((MoveEvent) e);
        } else if (e instanceof InteractEntityEvent || e instanceof InteractWorldEvent) {
            processClick(e);
        }
    }

    private void processMove(MoveEvent e) {
        Player p = e.getPlayer();
        HrainACPlayer pp = e.getHrainACPlayer();
        UUID uuid = p.getUniqueId();

        List<Vector> lastMoves = mouseMoves.getOrDefault(uuid, new ArrayList<>());
        Vector mouseMove = new Vector(e.getTo().getYaw() - e.getFrom().getYaw(), e.getTo().getPitch() - e.getFrom().getPitch(), 0);

        lastMoves.add(mouseMove);
        //make size 1 bigger so that we can get the move before
        //the first move that we check
        if(lastMoves.size() > MOVES_PER_SAMPLE + 1) {
            lastMoves.remove(0);
        }
        mouseMoves.put(uuid, lastMoves);

        if(clickedXMovesBefore(MOVES_AFTER_HIT, pp)) {
            double minSpeed = Double.MAX_VALUE;
            double maxSpeed = 0D;
            double maxAngle = 0D;
            for(int i = 1; i < lastMoves.size(); i++) {
                Vector lastMouseMove = lastMoves.get(i - 1);
                Vector currMouseMove = lastMoves.get(i);
                double speed = currMouseMove.length();
                double lastSpeed = lastMouseMove.length();
                double angle = (lastSpeed != 0 && lastSpeed != 0) ? MathPlus.angle(lastMouseMove, currMouseMove) : 0D;
                if(Double.isNaN(angle))
                    angle = 0D;
                maxSpeed = Math.max(speed, maxSpeed);
                minSpeed = Math.min(speed, minSpeed);
                maxAngle = Math.max(angle, maxAngle);

                //stutter
                if(maxSpeed - minSpeed > 4 && minSpeed < 0.01 && maxAngle < 0.1 && lastSpeed > 1) { //this lastSpeed check eliminates a false positive
                    if(DEBUG) {
                        p.sendMessage("AimbotHeuristic: A");
                    }
                    punishEm(pp, e);
                }
                //twitching or zig zags
                else if(speed > 20 && lastSpeed > 20 && angle > 2.86) {
                    if(DEBUG) {
                        p.sendMessage("AimbotHeuristic: B");
                    }
                    punishEm(pp, e);
                }
                //jump discontinuity
                else if(speed - lastSpeed < -30 && angle > 0.8) {
                    if(DEBUG) {
                        p.sendMessage("AimbotHeuristic: C");
                    }
                    punishEm(pp, e);
                }
                else {
                    reward(pp);
                }
            }
        }
    }

    private boolean clickedXMovesBefore(long x, HrainACPlayer pp) {
        List<Long> clickTimess = clickTimes.getOrDefault(pp.getUuid(), new ArrayList<>());
        long time = pp.getCurrentTick() - x;
        for(int i = 0; i < clickTimess.size(); i++) {
            if(time == clickTimess.get(i)) {
                clickTimess.remove(i);
                return true;
            }
        }
        return false;
    }

    private void processClick(Event e) {
        UUID uuid = e.getPlayer().getUniqueId();
        List<Long> clickTimess = clickTimes.getOrDefault(uuid, new ArrayList<>());
        long currTick = e.getHrainACPlayer().getCurrentTick();
        if(!clickTimess.contains(currTick))
            clickTimess.add(e.getHrainACPlayer().getCurrentTick());

        //memory leak fail-safe, if necessary?
        for(int i = clickTimess.size() - 1; i >= 0; i--) {
            if(currTick - clickTimess.get(i) > MOVES_PER_SAMPLE + 1)
                clickTimess.remove(i);
        }

        clickTimes.put(uuid, clickTimess);
    }

    private void punishEm(HrainACPlayer pp, MoveEvent e) {
        punish(pp, false, e);
    }

    @Override
    public void removeData(Player p) {
        mouseMoves.remove(p.getUniqueId());
        clickTimes.remove(p.getUniqueId());
    }
}
