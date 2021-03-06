package redempt.redlib.blockdata;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import redempt.redlib.RedLib;
import redempt.redlib.blockdata.events.DataBlockBreakEvent;
import redempt.redlib.blockdata.events.DataBlockDestroyEvent;
import redempt.redlib.blockdata.events.DataBlockDestroyEvent.DestroyCause;
import redempt.redlib.blockdata.events.DataBlockMoveEvent;
import redempt.redlib.misc.LocationUtils;
import redempt.redlib.region.RegionMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages {@link DataBlock} instances, which allow you to attach persistent metadata to blocks,
 * Keeps track of managed blocks, removing data if a block is destroyed or moving it if a block is pushed
 * by a piston.
 * @author Redempt
 */
public class BlockDataManager implements Listener {

	protected RegionMap<DataBlock> map = new RegionMap<DataBlock>(10);
	private YamlConfiguration config;
	private Path file;
	
	/**
	 * Create a BlockDataManager instance with a save file location, to be saved to and loaded from. This constructor
	 * immediately loads from the given file.
	 * @param saveFile The Path to load from immediately, and save to when save is called
	 */
	public BlockDataManager(Path saveFile) {
		this.file = saveFile;
		Bukkit.getPluginManager().registerEvents(this, RedLib.getInstance());
		if (file == null) {
			return;
		}
		try {
			if (!Files.exists(saveFile.getParent())) {
				Files.createDirectories(saveFile.getParent());
			}
			config = YamlConfiguration.loadConfiguration(saveFile.toFile());
			load();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Constructs a BlockDataManager with no save file. Data cannot be persisted between reloads and restarts if you
	 * construct it this way. Calling {@link BlockDataManager#save()} or {@link BlockDataManager#load()} will cause an error.
	 */
	public BlockDataManager() {
		this(null);
	}
	
	/**
	 * Loads all data from the save file. This should rarely be needed, as calling the constructor with a path
	 * will automatically load all block data.
	 */
	public void load() {
		config.getKeys(false).forEach(s -> {
			LocationUtils.fromStringLater(s, l -> {
				Block block = l.getBlock();
				DataBlock db = new DataBlock(block, this);
				Map<String, Object> data = new HashMap<>();
				ConfigurationSection section = config.getConfigurationSection(s);
				section.getKeys(false).forEach(key -> {
					data.put(key, section.get(key));
				});
				db.data = data;
				map.set(db.getBlock().getLocation(), db);
			});
		});
	}
	
	/**
	 * Saves all data to the save file. Call this in your onDisable.
	 */
	public void save() {
		config = new YamlConfiguration();
		try {
			map.getAll().forEach(b -> {
				String key = LocationUtils.toString(b.getBlock());
				b.data.forEach((k, v) -> {
					config.set(key + "." + k, v);
				});
			});
			config.save(file.toFile());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Gets an existing DataBlock, returning null if that Block has no data attached to it.
	 * @param block The block to check
	 * @return A DataBlock, or null
	 */
	public DataBlock getExisting(Block block) {
		Set<DataBlock> blocks = map.get(block.getLocation());
		for (DataBlock db : blocks) {
			if (db.getBlock().equals(block)) {
				return db;
			}
		}
		return null;
	}
	
	/**
	 * Gets a DataBlock from a given Block, creating a new one if that Block had no data attached to it.
	 * @param block The block to check or create a DataBlock from
	 * @return An existing or new DataBlock
	 */
	public DataBlock getDataBlock(Block block) {
		DataBlock db = getExisting(block);
		return db == null ? new DataBlock(block, this) : db;
	}
	
	/**
	 * Removes a DataBlock from this DataBlockManager
	 * @param db The DataBlock to remove
	 */
	public void remove(DataBlock db) {
		map.remove(db.getBlock().getLocation(), db);
	}
	
	/**
	 * Gets all the DataBlocks near an approximate location
	 * @param loc The location to check near
	 * @param radius The radius to check in
	 * @return
	 */
	public Set<DataBlock> getNearby(Location loc, int radius) {
		return map.getNearby(loc, radius);
	}
	
	/**
	 * @return A set of all DataBlocks managed by this BlockDataManager
	 */
	public Set<DataBlock> getAll() {
		return map.getAll();
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBreakBlock(BlockBreakEvent e) {
		DataBlock db = getExisting(e.getBlock());
		if (db == null) {
			return;
		}
		DataBlockBreakEvent event = new DataBlockBreakEvent(e, db);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			e.setCancelled(true);
			return;
		}
		map.remove(e.getBlock().getLocation(), db);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBurnBlock(BlockBurnEvent e) {
		DataBlock db = getExisting(e.getBlock());
		if (db == null) {
			return;
		}
		DataBlockDestroyEvent event = new DataBlockDestroyEvent(db, DestroyCause.FIRE);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			e.setCancelled(true);
			return;
		}
		map.remove(e.getBlock().getLocation(), db);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		List<Block> toRemove = new ArrayList<>();
		e.blockList().forEach(block -> {
			DataBlock db = getExisting(block);
			if (db == null) {
				return;
			}
			DataBlockDestroyEvent event = new DataBlockDestroyEvent(db, DestroyCause.EXPLOSION);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				toRemove.add(block);
				return;
			}
			map.remove(block.getLocation(), getExisting(block));
		});
		e.blockList().removeAll(toRemove);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		List<Block> toRemove = new ArrayList<>();
		e.blockList().forEach(block -> {
			DataBlock db = getExisting(block);
			if (db == null) {
				return;
			}
			DataBlockDestroyEvent event = new DataBlockDestroyEvent(db, DestroyCause.EXPLOSION);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				toRemove.add(block);
				return;
			}
			map.remove(block.getLocation(), getExisting(block));
		});
		e.blockList().removeAll(toRemove);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPush(BlockPistonExtendEvent e) {
		e.getBlocks().forEach(block -> {
			DataBlock db = getExisting(block);
			if (db == null) {
				return;
			}
			Location to = block.getRelative(e.getDirection()).getLocation();
			DataBlockMoveEvent event = new DataBlockMoveEvent(db, to);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				e.setCancelled(true);
				return;
			}
			map.remove(db.getBlock().getLocation(), db);
			db.setBlock(to.getBlock());
			map.set(db.getBlock().getLocation(), db);
		});
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPull(BlockPistonRetractEvent e) {
		e.getBlocks().forEach(block -> {
			DataBlock db = getExisting(block);
			if (db == null) {
				return;
			}
			Location to = block.getRelative(e.getDirection()).getLocation();
			DataBlockMoveEvent event = new DataBlockMoveEvent(db, to);
			Bukkit.getPluginManager().callEvent(event);
			if (event.isCancelled()) {
				e.setCancelled(true);
				return;
			}
			map.remove(db.getBlock().getLocation(), db);
			db.setBlock(to.getBlock());
			map.set(db.getBlock().getLocation(), db);
		});
	}
	
}
