package redempt.redlib.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

import redempt.redlib.multiblock.MultiBlockStructure.Rotator;
import redempt.redlib.region.Region;

/**
 * Represents an instance of a multi-block structure in the world
 * @author Redempt
 *
 */
public class Structure {
	
	private MultiBlockStructure type;
	private Location loc;
	private Rotator rotator;
	
	protected Structure(MultiBlockStructure type, Location loc, Rotator rotator) {
		this.type = type;
		this.loc = loc;
		this.rotator = rotator;
	}
	
	/**
	 * Gets the type of this structure
	 * @return The type of this structure
	 */
	public MultiBlockStructure getType() {
		return type;
	}
	
	/**
	 * Gets the location of this structure (will be a corner)
	 * @return The location of this structure
	 */
	public Location getLocation() {
		return loc;
	}
	
	/**
	 * Gets the rotation of this structure. Will be a number between 0 and 3.
	 * Represents how many 90-degree clockwise rotations would be needed to
	 * rotate the original multi-block structure this structure is based on
	 * to get to its current rotation.
	 * @return The rotation of this structure
	 */
	public Rotator getRotator() {
		return rotator.clone();
	}
	
	/**
	 * Gets all blocks of the given type in this Structure
	 * @param type The type to check for
	 * @return The list of blocks in this Structure of that type
	 */
	public List<StructureBlock> getByType(Material type) {
		ItemStack item = new ItemStack(type);
		// Don't ask my why this is what's needed. I don't get it either.
		// It seems as if this enum isn't actually consistent. Passing
		// Material.DIAMOND_BLOCK from another plugin resulted in a Material whose toString() yielded
		// "_BLOCK", which makes no sense. This fixes it somehow. I believe this has to do
		// with the cross-compatibility for 1.8 and 1.13+.
		Material realType = item.getType();
		return getBlocks().stream().filter(s -> s.getBlock().getType() == realType).collect(Collectors.toList());
	}
	
	/**
	 * Get all blocks in this Structure
	 * @return The list of blocks in this Structure
	 */
	public List<StructureBlock> getBlocks() {
		ArrayList<StructureBlock> blocks = new ArrayList<>();
		int[] dimensions = this.type.getDimensions();
		for (int x = 0; x < dimensions[0]; x++) {
			for (int y = 0; y < dimensions[1]; y++) {
				for (int z = 0; z < dimensions[2]; z++) {
					rotator.setLocation(x, z);
					Block b = 
						loc.getWorld().getBlockAt(rotator.getRotatedX() + loc.getBlockX(),
						y + loc.getBlockY(),
						rotator.getRotatedZ() + loc.getBlockZ());
					blocks.add(new StructureBlock(b, this, x, y, z));
				}
			}
		}
		return blocks;
	}
	
	/**
	 * Checks whether this Structure is intact (in the same rotation and has all the correct blocks)
	 * @return Whether this Structure is intact
	 */
	public boolean isIntact() {
		return type.getAt(loc, 0, 0, 0, rotator.getRotation(), rotator.isMirrored()) != null;
	}
	
	/**
	 * Gets the region this Structure occupies
	 * @return The region this Structure occupies
	 */
	public Region getRegion() {
		int[] dimensions = this.getType().getDimensions();
		rotator.setLocation(dimensions[0], dimensions[2]);
		Location end = new Location(loc.getWorld(), rotator.getRotatedX(), dimensions[1], rotator.getRotatedZ()).add(loc);
		return new Region(loc.clone(), end);
	}
	
	/**
	 * Gets a relative block in this Structure
	 * @param x The relative X of the block
	 * @param y The relative Y of the block
	 * @param z The relative Z of the block
	 * @return The relative block
	 * @throws IndexOutOfBoundsException if the specified coordinates are not within the bounds of this Structure
	 */
	public StructureBlock getRelative(int x, int y, int z) {
		int[] dim = type.getDimensions();
		if (x < 0 || y < 0 || z < 0
				|| x > dim[0] || y > dim[1] || z > dim[2]) {
			throw new IndexOutOfBoundsException("Relative location outside bounds of structure: " + x + ", " + y + ", " + z);
		}
		rotator.setLocation(x, z);
		return new StructureBlock(loc.getWorld().getBlockAt(rotator.getRotatedX() + loc.getBlockX(), y + loc.getBlockY(), rotator.getRotatedZ() + loc.getBlockZ()),
				this,
				x, y, z);
	}
	
	/**
	 * Gets a relative block in this Structure from an absolute block in the world
	 * @param block The absolute block which is part of this Structure
	 * @return The relative block
	 * @throws IllegalArgumentException if the specified block is not within the bounds of this Structure
	 */
	public StructureBlock getBlock(Block block) {
		if (!block.getWorld().equals(loc.getWorld())) {
			return null;
		}
		Location offset = block.getLocation().subtract(loc);
		Rotator rotator = this.rotator.getInverse();
		int[] dim = type.getDimensions();
		rotator.setLocation(offset.getBlockX(), offset.getBlockZ());
		int x = rotator.getRotatedX();
		int y = offset.getBlockY();
		int z = rotator.getRotatedZ();
		if (x < 0 || y < 0 || z < 0
				|| x > dim[0] || y > dim[1] || z > dim[2]) {
			return null;
		}
		return new StructureBlock(block, this, x, y, z);
	}
	
	/**
	 * Represents a block in a Structure instance
	 * @author Redempt
	 *
	 */
	public static class StructureBlock {
		
		private int relX;
		private int relY;
		private int relZ;
		private Block block;
		private Structure structure;
		
		private StructureBlock(Block block, Structure structure, int relX, int relY, int relZ) {
			this.relX = relX;
			this.relY = relY;
			this.relZ = relZ;
			this.block = block;
			this.structure = structure;
		}
		
		/**
		 * Gets the relative coordinates of this block in the Structure.
		 * The same block will always be in the same relative location
		 * across Structures, regardless of rotation. [x, y, z]
		 * @return The relative coordiantes of this StructureBlock
		 */
		public int[] getRelativeCoordinates() {
			return new int[] {relX, relY, relZ};
		}
		
		/**
		 * @return The BlockState that the MultiBlockStructure would set at this location if it were built here
		 */
		public BlockState getStructureData() {
			return structure.getType().getData(block.getLocation(), relX, relY, relZ);
		}
		
		/**
		 * Gets the relative X of this block
		 * @return The relative X of this block
		 */
		public int getRelativeX() {
			return relX;
		}
		
		/**
		 * Gets the relative Y of this block
		 * @return The relative Y of this block
		 */
		public int getRelativeY() {
			return relY;
		}
		
		/**
		 * Gets the relative Z of this block
		 * @return The relative Z of this block
		 */
		public int getRelativeZ() {
			return relZ;
		}
		
		/**
		 * Gets the Structure this block is part of
		 * @return The Structure
		 */
		public Structure getStructure() {
			return structure;
		}
		
		/**
		 * Gets the Block this StructureBlock references
		 * @return The Block
		 */
		public Block getBlock() {
			return block;
		}
		
	}
	
}
