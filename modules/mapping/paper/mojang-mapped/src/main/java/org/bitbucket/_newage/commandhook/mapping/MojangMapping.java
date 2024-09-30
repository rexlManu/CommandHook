package org.bitbucket._newage.commandhook.mapping;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import org.bitbucket._newage.commandhook.mapping.api.AMapping;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Entity;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MojangMapping extends AMapping {

    // Cache the constructors in static variables
    private static Constructor<EntitySelectorParser> twoArgConstructor; // 1.21.1 and above
    private static Constructor<EntitySelectorParser> oneArgConstructor; // 1.21 and below

    static {
        try {
            // Attempt to cache both constructors
            twoArgConstructor = EntitySelectorParser.class.getConstructor(StringReader.class, boolean.class);
        } catch (NoSuchMethodException e) {
            // Handle the case where this constructor is missing
            twoArgConstructor = null;
        }

        try {
            oneArgConstructor = EntitySelectorParser.class.getConstructor(StringReader.class);
        } catch (NoSuchMethodException e) {
            // Handle the case where this constructor is missing
            oneArgConstructor = null;
        }
    }

    private static EntitySelectorParser createEntitySelectorParser(StringReader stringReader, Constructor<EntitySelectorParser> constructor, Object... args) {
        try {
            return constructor.newInstance(stringReader, args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            // Optionally, log the exception here if needed for debugging
            return null;
        }
    }

    @Override
    public List<Entity> getEntitiesFromSelector(String selector, Block commandBlock) {
        List<Entity> entities = Collections.emptyList();

        CommandSourceStack wrapper = getCommandListenerWrapper(commandBlock);
        EntitySelectorParser argumentParser = getArgumentParser(selector);
        try {
            List<? extends net.minecraft.world.entity.Entity> nmsEntities = getNmsEntities(argumentParser, wrapper);
            entities = convertToBukkitEntity(nmsEntities);
        } catch (CommandSyntaxException ex) {
            handleCommandSyntaxException(commandBlock, ex);
        }

        return entities;
    }

    /**
     * Vanilla CommandSourceStack
     * @param block
     * @return
     */
    @Override
    public CommandSourceStack getCommandListenerWrapper(Block block) {
        Level world = ((CraftWorld) block.getWorld()).getHandle();
        BlockPos blockPosition = new BlockPos(block.getX(), block.getY(), block.getZ());

        CommandBlockEntity tileEntityCommand = (CommandBlockEntity) world.getBlockEntity(blockPosition, true);
        BaseCommandBlock commandBlockListenerAbstract = tileEntityCommand.getCommandBlock();
        return commandBlockListenerAbstract.createCommandSourceStack();
    }

    /**
     * Vanilla Argument Parser Selector
     * @param selector
     * @return
     */
    @Override
    public EntitySelectorParser getArgumentParser(String selector) {
        StringReader stringReader = new StringReader(selector);

        if (twoArgConstructor != null) {
            EntitySelectorParser parser = createEntitySelectorParser(stringReader, twoArgConstructor, true);
            if (parser != null) return parser;
        }

        if (oneArgConstructor != null) {
            EntitySelectorParser parser = createEntitySelectorParser(stringReader, oneArgConstructor);
            if (parser != null) return parser;
        }

        throw new RuntimeException("No valid constructor found for EntitySelectorParser.");
    }

    /**
     * Vanilla Entities
     * @param argumentParser
     * @param wrapper
     * @return
     * @throws CommandSyntaxException
     */
    private List<? extends net.minecraft.world.entity.Entity> getNmsEntities(EntitySelectorParser argumentParser, CommandSourceStack wrapper) throws CommandSyntaxException {
        EntitySelector selector = argumentParser.parse(false);
        return selector.findEntities(wrapper);
    }

    /**
     * Spigot related
     * @param entities
     * @return
     */
    private List<Entity> convertToBukkitEntity(List<? extends net.minecraft.world.entity.Entity> entities) {
        return entities.stream()
                .map(net.minecraft.world.entity.Entity::getBukkitEntity)
                .collect(Collectors.toList());
    }

}
