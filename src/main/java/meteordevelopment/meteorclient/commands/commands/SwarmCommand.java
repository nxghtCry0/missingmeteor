/*
 * This file is part of the MissingMeteor distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectIntPair;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.ModuleArgumentType;
import meteordevelopment.meteorclient.commands.arguments.PlayerArgumentType;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.Swarm;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.SwarmHiveInventory;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.SwarmHost;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.SwarmWorker;
import meteordevelopment.meteorclient.systems.modules.misc.swarm.SwarmWorkerInfo;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockStateArgument;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class SwarmCommand extends Command {

    private final static SimpleCommandExceptionType SWARM_NOT_ACTIVE = new SimpleCommandExceptionType(Text.literal("The swarm module must be active to use this command."));
    private @Nullable ObjectIntPair<String> pendingConnection;

    public SwarmCommand() {
        super("swarm", "HiveMind — control multiple instances with state sync, groups, and inventory sharing.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // === Connection Management ===

        builder.then(literal("disconnect").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (swarm.isActive()) {
                swarm.close();
                info("Disconnected.");
            } else {
                throw SWARM_NOT_ACTIVE.create();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("join")
            .then(argument("ip", StringArgumentType.string())
                .then(argument("port", IntegerArgumentType.integer(0, 65535))
                    .executes(context -> {
                        String ip = StringArgumentType.getString(context, "ip");
                        int port = IntegerArgumentType.getInteger(context, "port");
                        pendingConnection = new ObjectIntImmutablePair<>(ip, port);
                        info("Are you sure you want to connect to '%s:%s'?", ip, port);
                        info(Text.literal("Click here to confirm").setStyle(Style.EMPTY
                            .withFormatting(Formatting.UNDERLINE, Formatting.GREEN)
                            .withClickEvent(new MeteorClickEvent(".swarm join confirm"))
                        ));
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("confirm").executes(ctx -> {
                if (pendingConnection == null) {
                    error("No pending swarm connections.");
                    return SINGLE_SUCCESS;
                }
                Swarm swarm = Modules.get().get(Swarm.class);
                swarm.enable();
                swarm.close();
                swarm.mode.set(Swarm.Mode.Worker);
                swarm.worker = new SwarmWorker(pendingConnection.left(), pendingConnection.rightInt());
                swarm.applySettingsToWorker();
                pendingConnection = null;
                try {
                    info("Connected to (highlight)%s.", swarm.worker.getConnection());
                } catch (NullPointerException e) {
                    error("Error connecting to swarm host.");
                    swarm.close();
                    swarm.toggle();
                }
                return SINGLE_SUCCESS;
            }))
        );

        // === Connection Info ===

        builder.then(literal("connections").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();

            if (swarm.isHost()) {
                int count = swarm.host.getConnectionCount();
                if (count > 0) {
                    ChatUtils.info("--- (highlight)HiveMind Workers (%d)(default) ---", count);
                    for (SwarmWorkerInfo info : swarm.host.getWorkers()) {
                        ChatUtils.info("  (highlight)#%d %s(default) [%s] | (%.0f, %.0f, %.0f) | %dms",
                            info.id, info.playerName, info.group, info.isAlive() ? "(a)Online" : "(c)Offline",
                            info.x, info.y, info.z, info.ping);
                    }
                } else {
                    warning("No active workers connected.");
                }
            } else if (swarm.isWorker()) {
                info("Connected to (highlight)%s", swarm.worker.getConnection());
            }
            return SINGLE_SUCCESS;
        }));

        // === Individual / Group Command Sending (THE BIG ONE) ===

        builder.then(literal("send")
            .then(argument("target", StringArgumentType.string())
                .then(argument("command", StringArgumentType.greedyString())
                    .executes(context -> {
                        Swarm swarm = Modules.get().get(Swarm.class);
                        if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                        if (!swarm.isHost()) {
                            error("Only the host can send commands.");
                            return SINGLE_SUCCESS;
                        }

                        String target = StringArgumentType.getString(context, "target");
                        String command = StringArgumentType.getString(context, "command");

                        if (target.equalsIgnoreCase("all")) {
                            swarm.host.sendMessage(command);
                            info("Broadcast command to (highlight)all(default) workers.");
                        } else {
                            // Resolve: player name -> worker ID -> group name
                            SwarmHost.ResolveResult resolved = swarm.host.resolveTarget(target);
                            if (resolved == null) {
                                error("No worker or group found matching '%s'.", target);
                                return SINGLE_SUCCESS;
                            }

                            if (resolved.type() == SwarmHost.ResolveType.Worker) {
                                swarm.host.sendToWorker(resolved.workerId(), command);
                            } else {
                                swarm.host.sendToGroup(resolved.name(), command);
                            }
                        }
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );

        // === Group Management ===

        builder.then(literal("group")
            .then(argument("worker", StringArgumentType.string())
                .then(argument("group-name", StringArgumentType.string())
                    .executes(context -> {
                        Swarm swarm = Modules.get().get(Swarm.class);
                        if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                        if (!swarm.isHost()) {
                            error("Only the host can manage groups.");
                            return SINGLE_SUCCESS;
                        }

                        String workerStr = StringArgumentType.getString(context, "worker");
                        String groupName = StringArgumentType.getString(context, "group-name");

                        if (workerStr.equalsIgnoreCase("all")) {
                            swarm.host.assignAllGroups(groupName);
                        } else {
                            // Resolve: player name -> worker ID
                            SwarmWorkerInfo found = swarm.host.getWorkerByName(workerStr);
                            if (found == null) {
                                try {
                                    int workerId = Integer.parseInt(workerStr);
                                    swarm.host.assignGroup(workerId, groupName);
                                } catch (NumberFormatException e) {
                                    error("No worker found matching '%s'. Use a player name, worker ID, or 'all'.", workerStr);
                                }
                            } else {
                                swarm.host.assignGroup(found.id, groupName);
                            }
                        }
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );

        builder.then(literal("groups").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (!swarm.isHost()) {
                error("Only the host can list groups.");
                return SINGLE_SUCCESS;
            }

            Map<String, Integer> groups = new java.util.LinkedHashMap<>();
            for (SwarmWorkerInfo info : swarm.host.getWorkers()) {
                groups.merge(info.group, 1, Integer::sum);
            }

            if (groups.isEmpty()) {
                warning("No workers connected.");
            } else {
                ChatUtils.info("--- (highlight)HiveMind Groups(default) ---");
                for (Map.Entry<String, Integer> entry : groups.entrySet()) {
                    ChatUtils.info("  (highlight)%s(default): %d worker(s)", entry.getKey(), entry.getValue());
                }
            }
            return SINGLE_SUCCESS;
        }));

        // === Worker List (detailed) ===

        builder.then(literal("workers").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (!swarm.isHost()) {
                error("Only the host can list workers.");
                return SINGLE_SUCCESS;
            }

            int count = swarm.host.getConnectionCount();
            if (count == 0) {
                warning("No workers connected.");
            } else {
                ChatUtils.info("--- (highlight)HiveMind Workers (%d)(default) ---", count);
                for (SwarmWorkerInfo info : swarm.host.getWorkers()) {
                    ChatUtils.info("  (highlight)%s(default) | #%d | Group: (highlight)%s(default) | Pos: (%.0f, %.0f, %.0f) | Ping: %dms | %s",
                        info.playerName, info.id, info.group, info.x, info.y, info.z, info.ping,
                        info.isAlive() ? "(a)Online" : "(c)Timeout");
                }
            }
            return SINGLE_SUCCESS;
        }));

        // === Inventory Hive ===

        builder.then(literal("inv")
            .executes(context -> {
                Swarm swarm = Modules.get().get(Swarm.class);
                if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                if (!swarm.isHost()) {
                    error("Only the host can view hive inventory.");
                    return SINGLE_SUCCESS;
                }

                SwarmHiveInventory hive = swarm.host.getHiveInventory();
                List<Map.Entry<Integer, Integer>> sorted = hive.getSortedInventory();

                if (sorted.isEmpty()) {
                    warning("Hive inventory is empty.");
                } else {
                    ChatUtils.info("--- (highlight)HiveMind Inventory (%d workers)(default) ---", hive.getWorkerCount());
                    for (Map.Entry<Integer, Integer> entry : sorted) {
                        ChatUtils.info("  Item ID %d: (highlight)%d(default) total", entry.getKey(), entry.getValue());
                    }
                }
                return SINGLE_SUCCESS;
            })
            .then(literal("find")
                .then(argument("item-name", StringArgumentType.string())
                    .executes(context -> {
                        Swarm swarm = Modules.get().get(Swarm.class);
                        if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                        if (!swarm.isHost()) {
                            error("Only the host can search inventory.");
                            return SINGLE_SUCCESS;
                        }

                        String itemName = StringArgumentType.getString(context, "item-name");
                        ChatUtils.info("Searching for '%s' across all workers...", itemName);
                        // Basic search — report which workers have items
                        for (SwarmWorkerInfo info : swarm.host.getWorkers()) {
                            if (!info.inventory.isEmpty()) {
                                ChatUtils.info("  Worker #%d: %d item types", info.id, info.inventory.size());
                            }
                        }
                        return SINGLE_SUCCESS;
                    })
                )
            )
            .then(literal("request")
                .then(argument("worker-id", IntegerArgumentType.integer())
                    .then(argument("item-name", StringArgumentType.string())
                        .executes(context -> {
                            Swarm swarm = Modules.get().get(Swarm.class);
                            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                            if (!swarm.isHost()) {
                                error("Only the host can request items.");
                                return SINGLE_SUCCESS;
                            }

                            int workerId = IntegerArgumentType.getInteger(context, "worker-id");
                            String itemName = StringArgumentType.getString(context, "item-name");
                            swarm.host.requestItem(workerId, itemName);
                            info("Requested (highlight)%s(default) from worker #%d.", itemName, workerId);
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
            .then(literal("drop")
                .then(argument("worker-id", IntegerArgumentType.integer())
                    .then(argument("slot", IntegerArgumentType.integer(0, 41))
                        .executes(context -> {
                            Swarm swarm = Modules.get().get(Swarm.class);
                            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                            if (!swarm.isHost()) {
                                error("Only the host can request drops.");
                                return SINGLE_SUCCESS;
                            }

                            int workerId = IntegerArgumentType.getInteger(context, "worker-id");
                            int slot = IntegerArgumentType.getInteger(context, "slot");
                            swarm.host.requestDrop(workerId, slot);
                            info("Requested worker #%d to drop slot %d.", workerId, slot);
                            return SINGLE_SUCCESS;
                        })
                    )
                )
            )
        );

        // === Ping ===

        builder.then(literal("ping").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (!swarm.isHost()) {
                error("Only the host can ping workers.");
                return SINGLE_SUCCESS;
            }
            swarm.host.pingAll();
            info("Ping sent to all workers.");
            return SINGLE_SUCCESS;
        }));

        // === LEGACY COMMANDS (backward compatible with existing swarm workflow) ===

        builder.then(literal("follow").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput() + " " + mc.player.getName().getString());
            } else if (swarm.isWorker()) {
                error("The follow host command must be used by the host.");
            }
            return SINGLE_SUCCESS;
        }).then(argument("player", PlayerArgumentType.create()).executes(context -> {
            PlayerEntity playerEntity = PlayerArgumentType.get(context);
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker() && playerEntity != null) {
                PathManagers.get().follow(entity -> entity.getName().getString().equalsIgnoreCase(playerEntity.getName().getString()));
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("goto")
            .then(argument("x", IntegerArgumentType.integer())
                .then(argument("z", IntegerArgumentType.integer()).executes(context -> {
                    Swarm swarm = Modules.get().get(Swarm.class);
                    if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                    if (swarm.isHost()) {
                        swarm.host.sendMessage(context.getInput());
                    } else if (swarm.isWorker()) {
                        int x = IntegerArgumentType.getInteger(context, "x");
                        int z = IntegerArgumentType.getInteger(context, "z");
                        PathManagers.get().moveTo(new BlockPos(x, 0, z), true);
                    }
                    return SINGLE_SUCCESS;
                }))
            )
        );

        builder.then(literal("infinity-miner").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                runInfinityMiner();
            }
            return SINGLE_SUCCESS;
        })
        .then(argument("target", BlockStateArgumentType.blockState(REGISTRY_ACCESS)).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                Modules.get().get(InfinityMiner.class).targetBlocks.set(List.of(context.getArgument("target", BlockStateArgument.class).getBlockState().getBlock()));
                runInfinityMiner();
            }
            return SINGLE_SUCCESS;
        }))
        .then(argument("repair", BlockStateArgumentType.blockState(REGISTRY_ACCESS)).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                Modules.get().get(InfinityMiner.class).targetBlocks.set(List.of(context.getArgument("target", BlockStateArgument.class).getBlockState().getBlock()));
                Modules.get().get(InfinityMiner.class).repairBlocks.set(List.of(context.getArgument("repair", BlockStateArgument.class).getBlockState().getBlock()));
                runInfinityMiner();
            }
            return SINGLE_SUCCESS;
        }))
        .then(literal("logout").then(argument("logout", BoolArgumentType.bool()).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                Modules.get().get(InfinityMiner.class).logOut.set(BoolArgumentType.getBool(context, "logout"));
            }
            return SINGLE_SUCCESS;
        })))
        .then(literal("walkhome").then(argument("walkhome", BoolArgumentType.bool()).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                Modules.get().get(InfinityMiner.class).walkHome.set(BoolArgumentType.getBool(context, "walkhome"));
            }
            return SINGLE_SUCCESS;
        }))));


        builder.then(literal("mine")
            .then(argument("block", BlockStateArgumentType.blockState(REGISTRY_ACCESS)).executes(context -> {
                Swarm swarm = Modules.get().get(Swarm.class);
                if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                if (swarm.isHost()) {
                    swarm.host.sendMessage(context.getInput());
                } else if (swarm.isWorker()) {
                    swarm.worker.target = context.getArgument("block", BlockStateArgument.class).getBlockState().getBlock();
                }
                return SINGLE_SUCCESS;
            }))
        );

        builder.then(literal("toggle")
            .then(argument("module", ModuleArgumentType.create())
                .executes(context -> {
                    Swarm swarm = Modules.get().get(Swarm.class);
                    if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                    if (swarm.isHost()) {
                        swarm.host.sendMessage(context.getInput());
                    } else if (swarm.isWorker()) {
                        Module module = ModuleArgumentType.get(context);
                        module.toggle();
                    }
                    return SINGLE_SUCCESS;
                }).then(literal("on")
                    .executes(context -> {
                        Swarm swarm = Modules.get().get(Swarm.class);
                        if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                        if (swarm.isHost()) {
                            swarm.host.sendMessage(context.getInput());
                        } else if (swarm.isWorker()) {
                            Module m = ModuleArgumentType.get(context);
                            m.enable();
                        }
                        return SINGLE_SUCCESS;
                    })).then(literal("off")
                    .executes(context -> {
                        Swarm swarm = Modules.get().get(Swarm.class);
                        if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
                        if (swarm.isHost()) {
                            swarm.host.sendMessage(context.getInput());
                        } else if (swarm.isWorker()) {
                            Module m = ModuleArgumentType.get(context);
                            m.disable();
                        }
                        return SINGLE_SUCCESS;
                    })
                )
            )
        );

        builder.then(literal("scatter").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                scatter(100);
            }
            return SINGLE_SUCCESS;
        }).then(argument("radius", IntegerArgumentType.integer()).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                scatter(IntegerArgumentType.getInteger(context, "radius"));
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("stop").executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                PathManagers.get().stop();
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("exec").then(argument("command", StringArgumentType.greedyString()).executes(context -> {
            Swarm swarm = Modules.get().get(Swarm.class);
            if (!swarm.isActive()) throw SWARM_NOT_ACTIVE.create();
            if (swarm.isHost()) {
                swarm.host.sendMessage(context.getInput());
            } else if (swarm.isWorker()) {
                ChatUtils.sendPlayerMsg(StringArgumentType.getString(context, "command"));
            }
            return SINGLE_SUCCESS;
        })));
    }

    private void runInfinityMiner() {
        InfinityMiner infinityMiner = Modules.get().get(InfinityMiner.class);
        infinityMiner.disable();
        infinityMiner.enable();
    }

    private void scatter(int radius) {
        Random random = new Random();
        double a = random.nextDouble() * 2 * Math.PI;
        double r = radius * Math.sqrt(random.nextDouble());
        double x = mc.player.getX() + r * Math.cos(a);
        double z = mc.player.getZ() + r * Math.sin(a);
        PathManagers.get().stop();
        PathManagers.get().moveTo(new BlockPos((int) x, 0, (int) z), true);
    }
}
