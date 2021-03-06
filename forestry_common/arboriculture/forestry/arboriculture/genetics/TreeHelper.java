/*******************************************************************************
 * Copyright (c) 2011-2014 SirSengir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v3
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-3.0.txt
 * 
 * Various Contributors including, but not limited to:
 * SirSengir (original work), CovertJaguar, Player, Binnie, MysteriousAges
 ******************************************************************************/
package forestry.arboriculture.genetics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map.Entry;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.FMLCommonHandler;

import com.mojang.authlib.GameProfile;

import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.arboriculture.EnumTreeChromosome;
import forestry.api.arboriculture.IAlleleFruit;
import forestry.api.arboriculture.IAlleleTreeSpecies;
import forestry.api.arboriculture.IArboristTracker;
import forestry.api.arboriculture.ILeafTickHandler;
import forestry.api.arboriculture.ITree;
import forestry.api.arboriculture.ITreeGenome;
import forestry.api.arboriculture.ITreeMutation;
import forestry.api.arboriculture.ITreeRoot;
import forestry.api.arboriculture.ITreekeepingMode;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IMutation;
import forestry.arboriculture.gadgets.BlockFruitPod;
import forestry.arboriculture.gadgets.TileFruitPod;
import forestry.arboriculture.gadgets.TileLeaves;
import forestry.arboriculture.gadgets.TileSapling;
import forestry.core.config.Defaults;
import forestry.core.config.ForestryBlock;
import forestry.core.config.ForestryItem;
import forestry.core.genetics.SpeciesRoot;
import forestry.core.utils.BlockUtil;
import forestry.plugins.PluginArboriculture;

public class TreeHelper extends SpeciesRoot implements ITreeRoot {

	public static final String UID = "rootTrees";
	public static int treeSpeciesCount = -1;

	@Override
	public String getUID() {
		return UID;
	}

	public TreeHelper() {
		setResearchSuitability(new ItemStack(Blocks.sapling, 1, Defaults.WILDCARD), 1.0f);
	}

	@Override
	public Class<? extends IIndividual> getMemberClass() {
		return ITree.class;
	}

	@Override
	public int getSpeciesCount() {
		if (treeSpeciesCount < 0) {
			treeSpeciesCount = 0;
			Iterator<Entry<String, IAllele> > it = AlleleManager.alleleRegistry.getRegisteredAlleles().entrySet().iterator();
			while (it.hasNext()) {
				Entry<String, IAllele> entry = it.next();
				if (entry.getValue() instanceof IAlleleTreeSpecies)
					if (((IAlleleTreeSpecies) entry.getValue()).isCounted())
						treeSpeciesCount++;
			}
		}

		return treeSpeciesCount;
	}

	@Override
	public boolean isMember(ItemStack itemstack) {
		return getType(itemstack) != EnumGermlingType.NONE;
	}

	@Override
	public boolean isMember(ItemStack stack, int type) {
		return getType(stack).ordinal() == type;
	}

	@Override
	public boolean isMember(IIndividual individual) {
		return individual instanceof ITree;
	}

	/* TREE SPECIFIC */
	@Override
	public EnumGermlingType getType(ItemStack stack) {
		if(stack == null)
			return EnumGermlingType.NONE;

		if(ForestryItem.sapling.isItemEqual(stack))
			return EnumGermlingType.SAPLING;
		else if(ForestryItem.pollenFertile.isItemEqual(stack))
			return EnumGermlingType.POLLEN;

		return EnumGermlingType.NONE;
	}

	@Override
	public ITree getTree(World world, int x, int y, int z) {
		TileEntity tile = world.getTileEntity(x, y, z);
		if (!(tile instanceof TileSapling))
			return null;

		return ((TileSapling) tile).getTree();
	}

	@Override
	public ITree getMember(ItemStack itemstack) {
		if (!isMember(itemstack))
			return null;
		if (itemstack.getTagCompound() == null)
			return null;

		return new Tree(itemstack.getTagCompound());
	}

	@Override
	public ITree getMember(NBTTagCompound compound) {
		return new Tree(compound);
	}

	@Override
	public ITree getTree(World world, ITreeGenome genome) {
		return new Tree(genome);
	}

	@Override
	public ItemStack getMemberStack(IIndividual tree, int type) {

		Item germlingItem;
		switch (EnumGermlingType.VALUES[type]) {
		case SAPLING:
			germlingItem = ForestryItem.sapling.item();
			break;
		case POLLEN:
			germlingItem = ForestryItem.pollenFertile.item();
			break;
		default:
			return null;
		}

		NBTTagCompound nbttagcompound = new NBTTagCompound();
		tree.writeToNBT(nbttagcompound);

		ItemStack treeStack = new ItemStack(germlingItem);
		treeStack.setTagCompound(nbttagcompound);

		return treeStack;

	}

	@Override
	public boolean plantSapling(World world, ITree tree, GameProfile owner, int x, int y, int z) {

		boolean placed = world.setBlock(x, y, z, ForestryBlock.saplingGE, 0, Defaults.FLAG_BLOCK_SYNCH);
		if (!placed)
			return false;

		if (world.getBlock(x, y, z) != ForestryBlock.saplingGE)
			return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if (!(tile instanceof TileSapling)) {
			world.setBlockToAir(x, y, z);
			return false;
		}

		TileSapling sapling = (TileSapling) tile;
		sapling.setTree(tree.copy());
		sapling.setOwner(owner);
		world.markBlockForUpdate(x, y, z);

		return true;
	}

	@Override
	public boolean setLeaves(World world, IIndividual tree, GameProfile owner, int x, int y, int z) {

		boolean placed = world.setBlock(x, y, z, ForestryBlock.leaves, 0, Defaults.FLAG_BLOCK_SYNCH);
		if (!placed)
			return false;

		if (world.getBlock(x, y, z) != ForestryBlock.leaves)
			return false;

		TileEntity tile = world.getTileEntity(x, y, z);
		if (!(tile instanceof TileLeaves)) {
			world.setBlockToAir(x, y, z);
			return false;
		}

		TileLeaves leaves = (TileLeaves) tile;
		leaves.setTree((ITree) tree.copy());
		leaves.setOwner(owner);
		world.markBlockForUpdate(x, y, z);

		return true;
	}

	@Override
	public boolean setFruitBlock(World world, IAlleleFruit allele, float sappiness, short[] indices, int x, int y, int z) {

		int direction = BlockUtil.getDirectionalMetadata(world, x, y, z);
		if (direction < 0)
			return false;
		boolean placed = world.setBlock(x, y, z, ForestryBlock.pods, direction, Defaults.FLAG_BLOCK_SYNCH);
		if (!placed)
			return false;

		if (world.getBlock(x, y, z) != ForestryBlock.pods)
			return false;

		TileFruitPod pod = BlockFruitPod.getPodTile(world, x, y, z);
		if (pod == null) {
			world.setBlockToAir(x, y, z);
			return false;
		}
		pod.setFruit(allele, sappiness, indices);
		world.markBlockForUpdate(x, y, z);
		return true;
	}

	/* GENOME CONVERSIONS */
	@Override
	public ITreeGenome templateAsGenome(IAllele[] template) {
		return new TreeGenome(templateAsChromosomes(template));
	}

	@Override
	public ITreeGenome templateAsGenome(IAllele[] templateActive, IAllele[] templateInactive) {
		return new TreeGenome(templateAsChromosomes(templateActive, templateInactive));
	}

	@Override
	public ITree templateAsIndividual(IAllele[] template) {
		return new Tree(templateAsGenome(template));
	}

	@Override
	public ITree templateAsIndividual(IAllele[] templateActive, IAllele[] templateInactive) {
		return new Tree(templateAsGenome(templateActive, templateInactive));
	}

	/* BREEDING TRACKER */
	@Override
	public IArboristTracker getBreedingTracker(World world, GameProfile player) {
		String filename = "ArboristTracker." + (player == null ? "common" : player.getId());
		ArboristTracker tracker = (ArboristTracker) world.loadItemData(ArboristTracker.class, filename);

		// Create a tracker if there is none yet.
		if (tracker == null) {
			tracker = new ArboristTracker(filename, player);
			world.setItemData(filename, tracker);
		}

		return tracker;
	}

	/* BREEDING MODES */
	ArrayList<ITreekeepingMode> treekeepingModes = new ArrayList<ITreekeepingMode>();
	public static ITreekeepingMode activeTreekeepingMode;

	@Override
	public ArrayList<ITreekeepingMode> getTreekeepingModes() {
		return this.treekeepingModes;
	}

	@Override
	public ITreekeepingMode getTreekeepingMode(World world) {
		if (activeTreekeepingMode != null)
			return activeTreekeepingMode;

		// No Treekeeping mode yet, item it.
		IArboristTracker tracker = getBreedingTracker(world, null);
		String mode = tracker.getModeName();
		if (mode == null || mode.isEmpty())
			mode = PluginArboriculture.treekeepingMode;

		setTreekeepingMode(world, mode);
		FMLCommonHandler.instance().getFMLLogger().debug("Set Treekeeping mode for a world to " + mode);

		return activeTreekeepingMode;
	}

	@Override
	public void registerTreekeepingMode(ITreekeepingMode mode) {
		treekeepingModes.add(mode);
	}

	@Override
	public void setTreekeepingMode(World world, String name) {
		activeTreekeepingMode = getTreekeepingMode(name);
		getBreedingTracker(world, null).setModeName(name);
	}

	@Override
	public ITreekeepingMode getTreekeepingMode(String name) {
		for (ITreekeepingMode mode : treekeepingModes)
			if (mode.getName().equals(name) || mode.getName().equals(name.toLowerCase(Locale.ENGLISH)))
				return mode;

		FMLCommonHandler.instance().getFMLLogger().debug("Failed to find a Treekeeping mode called '%s', reverting to fallback.");
		return treekeepingModes.get(0);
	}

	/* TEMPLATES */
	public static ArrayList<ITree> treeTemplates = new ArrayList<ITree>();

	@Override
	public ArrayList<ITree> getIndividualTemplates() {
		return treeTemplates;
	}

	@Override
	public void registerTemplate(String identifier, IAllele[] template) {
		treeTemplates.add(new Tree(PluginArboriculture.treeInterface.templateAsGenome(template)));
		speciesTemplates.put(identifier, template);
	}

	@Override
	public IAllele[] getDefaultTemplate() {
		return TreeTemplates.getDefaultTemplate();
	}

	/* MUTATIONS */
	private static ArrayList<ITreeMutation> treeMutations = new ArrayList<ITreeMutation>();

	@Override
	public ArrayList<ITreeMutation> getMutations(boolean shuffle) {
		if (shuffle)
			Collections.shuffle(treeMutations);
		return treeMutations;
	}

	@Override
	public void registerMutation(IMutation mutation) {
		if (AlleleManager.alleleRegistry.isBlacklisted(mutation.getTemplate()[0].getUID()))
			return;
		if (AlleleManager.alleleRegistry.isBlacklisted(mutation.getAllele0().getUID()))
			return;
		if (AlleleManager.alleleRegistry.isBlacklisted(mutation.getAllele1().getUID()))
			return;

		treeMutations.add((ITreeMutation)mutation);
	}

	/* ILEAFTICKHANDLER */
	private final LinkedList<ILeafTickHandler> leafTickHandlers = new LinkedList<ILeafTickHandler>();

	@Override
	public void registerLeafTickHandler(ILeafTickHandler handler) {
		leafTickHandlers.add(handler);
	}

	@Override
	public Collection<ILeafTickHandler> getLeafTickHandlers() {
		return leafTickHandlers;
	}

	@Override
	public IChromosomeType[] getKaryotype() {
		return EnumTreeChromosome.values();
	}

	@Override
	public IChromosomeType getKaryotypeKey() {
		return EnumTreeChromosome.SPECIES;
	}


}
