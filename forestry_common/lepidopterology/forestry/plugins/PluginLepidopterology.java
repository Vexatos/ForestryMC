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
package forestry.plugins;

import java.util.Locale;

import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.network.IGuiHandler;

import forestry.api.arboriculture.ITreeRoot;
import forestry.api.core.EnumHumidity;
import forestry.api.core.EnumTemperature;
import forestry.api.core.PluginInfo;
import forestry.api.core.Tabs;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IClassification;
import forestry.api.genetics.IClassification.EnumClassLevel;
import forestry.api.lepidopterology.EnumFlutterType;
import forestry.api.lepidopterology.IButterflyRoot;
import forestry.api.recipes.RecipeManagers;
import forestry.core.config.Configuration;
import forestry.core.config.Defaults;
import forestry.core.config.ForestryBlock;
import forestry.core.config.ForestryItem;
import forestry.core.config.Property;
import forestry.core.gadgets.BlockBase;
import forestry.core.gadgets.MachineDefinition;
import forestry.core.genetics.Allele;
import forestry.core.genetics.Branch;
import forestry.core.items.ItemForestryBlock;
import forestry.core.proxy.Proxies;
import forestry.core.utils.LiquidHelper;
import forestry.core.utils.ShapedRecipeCustom;
import forestry.core.utils.Utils;
import forestry.lepidopterology.ButterflySpawner;
import forestry.lepidopterology.GuiHandlerLepidopterology;
import forestry.lepidopterology.MatingRecipe;
import forestry.lepidopterology.entities.EntityButterfly;
import forestry.lepidopterology.gadgets.TileLepidopteristChest;
import forestry.lepidopterology.genetics.AlleleButterflySpecies;
import forestry.lepidopterology.genetics.AlleleEffectNone;
import forestry.lepidopterology.genetics.ButterflyHelper;
import forestry.lepidopterology.genetics.ButterflyTemplates;
import forestry.lepidopterology.items.ItemButterflyGE;
import forestry.lepidopterology.items.ItemFlutterlyzer;
import forestry.lepidopterology.proxy.ProxyLepidopterology;

@PluginInfo(pluginID = "Lepidopterology", name = "Lepidopterology", author = "SirSengir", url = Defaults.URL, description = "Butterflies. Shiny.")
public class PluginLepidopterology extends NativePlugin {

	@SidedProxy(clientSide = "forestry.lepidopterology.proxy.ClientProxyLepidopterology", serverSide = "forestry.lepidopterology.proxy.ProxyLepidopterology")
	public static ProxyLepidopterology proxy;
	private static final String CONFIG_CATEGORY = "lepidopterology";
	private Configuration config;
	public static int spawnConstraint = 100;
	public static int entityConstraint = 1000;
	public static IClassification geometridae;
	public static IClassification saturniidae;
	public static IClassification pieridae;
	public static IClassification nymphalidae;
	public static IClassification lycaenidae;
	public static IClassification papilionidae;
	public static IClassification notchidae;
	/**
	 * See {@link IButterflyRoot} for details.
	 */
	public static IButterflyRoot butterflyInterface;
	public static MachineDefinition definitionChest;

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public void preInit() {
		super.preInit();

		proxy.addLocalizations();

		ForestryBlock.lepidopterology = new BlockBase(Material.iron);
		ForestryBlock.lepidopterology.setBlockName("for.lepidopterology").setCreativeTab(Tabs.tabLepidopterology);
		Proxies.common.registerBlock(ForestryBlock.lepidopterology, ItemForestryBlock.class);

		definitionChest = ForestryBlock.lepidopterology.addDefinition((new MachineDefinition(Defaults.DEFINITION_LEPICHEST_META, "forestry.LepiChest", TileLepidopteristChest.class,
				ShapedRecipeCustom.createShapedRecipe(new ItemStack(ForestryBlock.lepidopterology, 1, Defaults.DEFINITION_LEPICHEST_META), new Object[] { " # ", "XYX", "XXX", Character.valueOf('#'), Blocks.glass, Character.valueOf('X'),
					ForestryItem.butterflyGE.getItemStack(1, Defaults.WILDCARD), Character.valueOf('Y'), Blocks.chest }))
		.setFaces(0, 1, 2, 3, 4, 4, 0, 7)));


		AlleleManager.alleleRegistry.registerSpeciesRoot(PluginLepidopterology.butterflyInterface = new ButterflyHelper());
		createAlleles();
	}

	@Override
	public void doInit() {
		super.doInit();

		config = new Configuration();

		Property property = config.get("entities.spawn.limit", CONFIG_CATEGORY, spawnConstraint);
		property.Comment = "determines the global butterfly entity count above which natural spawning of butterflies ceases.";
		spawnConstraint = Integer.parseInt(property.Value);

		property = config.get("entities.maximum.allowed", CONFIG_CATEGORY, entityConstraint);
		property.Comment = "determines the global butterfly entity count above which butterflies will stay in item form and will not take flight anymore.";
		entityConstraint = Integer.parseInt(property.Value);

		config.save();

		Utils.registerEntity(EntityButterfly.class, "butterflyGE", 0, 0x000000, 0xffffff, 50, 1, true);
		proxy.initializeRendering();
		registerTemplates();

		definitionChest.register();

		((ITreeRoot) AlleleManager.alleleRegistry.getSpeciesRoot("rootTrees")).registerLeafTickHandler(new ButterflySpawner());
	}

	@Override
	protected void registerItems() {
		ForestryItem.flutterlyzer.registerItem(new ItemFlutterlyzer(), "flutterlyzer");
		ForestryItem.butterflyGE.registerItem(new ItemButterflyGE(EnumFlutterType.BUTTERFLY), "butterflyGE");
		ForestryItem.serumGE.registerItem(new ItemButterflyGE(EnumFlutterType.SERUM), "serumGE");
		ForestryItem.caterpillarGE.registerItem(new ItemButterflyGE(EnumFlutterType.CATERPILLAR), "caterpillarGE");
	}

	@Override
	protected void registerBackpackItems() {
	}

	@Override
	protected void registerCrates() {
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void registerRecipes() {
		CraftingManager.getInstance().getRecipeList().add(new MatingRecipe());

		RecipeManagers.carpenterManager.addRecipe(100, LiquidHelper.getLiquid(Defaults.LIQUID_WATER, 2000), null, ForestryItem.flutterlyzer.getItemStack(),
				new Object[] { "X#X", "X#X", "RDR", Character.valueOf('#'), Blocks.glass_pane, Character.valueOf('X'), "ingotBronze", Character.valueOf('R'),
			Items.redstone, Character.valueOf('D'), Items.diamond });
	}

	private void createAlleles() {

		IClassification lepidoptera = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.ORDER, "lepidoptera", "Lepidoptera");
		AlleleManager.alleleRegistry.getClassification("class.insecta").addMemberGroup(lepidoptera);

		geometridae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "geometridae", "Geometridae");
		lepidoptera.addMemberGroup(geometridae);
		saturniidae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "saturniidae", "Saturniidae");
		lepidoptera.addMemberGroup(saturniidae);

		pieridae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "pieridae", "Pieridae");
		lepidoptera.addMemberGroup(pieridae);
		nymphalidae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "nymphalidae", "Nymphalidae");
		lepidoptera.addMemberGroup(nymphalidae);
		lycaenidae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "lycaenidae", "Lycaenidae");
		lepidoptera.addMemberGroup(lycaenidae);
		papilionidae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "papilionidae", "Papilionidae");
		lepidoptera.addMemberGroup(papilionidae);
		notchidae = AlleleManager.alleleRegistry.createAndRegisterClassification(EnumClassLevel.FAMILY, "notchidae", "Notchidae");
		lepidoptera.addMemberGroup(notchidae);

		/* GEOMETRIDAE */
		IClassification opisthograptis = createButterflyBranch(geometridae, "Opisthograptis");
		IClassification chiasmia = createButterflyBranch(geometridae, "Chiasmia");

		Allele.mothBrimstone = new AlleleButterflySpecies("mothBrimstone", true, "Brimstone", opisthograptis, "luteolata", 0xffea40).setNocturnal(true).setRarity(1.0f);
		Allele.mothLatticedHeath = new AlleleButterflySpecies("mothLatticedHeath", true, "Latticed Heath", chiasmia, "clathrata", 0xf2f0be).setNocturnal(true).setRarity(0.5f);

		/* SATURNIIDAE */
		IClassification attacus = createButterflyBranch(saturniidae, "Attacus");

		Allele.mothAtlas = new AlleleButterflySpecies("mothAtlas", false, "Atlas", attacus, "atlas", 0xd96e3d).setNocturnal(true);

		/* PIERIDAE */
		IClassification pieris = createButterflyBranch(pieridae, "Pieris");
		IClassification gonepteryx = createButterflyBranch(pieridae, "Gonepteryx");
		IClassification anthocharis = createButterflyBranch(pieridae, "Anthocharis");
		IClassification colias = createButterflyBranch(pieridae, "Colias");
		IClassification pontia = createButterflyBranch(pieridae, "Pontia");
		IClassification celastrina = createButterflyBranch(pieridae, "Celastrina");

		Allele.lepiCabbageWhite = new AlleleButterflySpecies("lepiCabbageWhite", true, "Cabbage White", pieris, "rapae", 0xccffee).setRarity(1.0f);
		Allele.lepiBrimstone = new AlleleButterflySpecies("lepiBrimstone", true, "Brimstone", gonepteryx, "rhamni", 0xf0ee38).setRarity(1.0f);
		Allele.lepiAurora = new AlleleButterflySpecies("lepiAurora", true, "Orange Tip", anthocharis, "cardamines", 0xe34f05).setRarity(0.5f);
		Allele.lepiPostillion = new AlleleButterflySpecies("lepiPostillion", true, "Postillion", colias, "croceus", 0xd77e04).setRarity(0.5f);
		Allele.lepiPalaenoSulphur = new AlleleButterflySpecies("lepiPalaenoSulphur", true, "Palaeno Sulphur", colias, "palaeno", 0xf8fba3).setRarity(0.4f);
		Allele.lepiReseda = new AlleleButterflySpecies("lepiReseda", true, "Reseda", pontia, "edusa", 0x747d48).setRarity(0.3f);

		Allele.lepiSpringAzure = new AlleleButterflySpecies("lepiSpringAzure", true, "Spring Azure", celastrina, "argiolus", 0xb8cae2).setRarity(0.3f);
		Allele.lepiGozoraAzure = new AlleleButterflySpecies("lepiGozoraAzure", true, "Gozora Azure", celastrina, "gozora", 0x6870e7).setRarity(0.2f);

		/* PAPILIONIDAE */
		IClassification papilio = createButterflyBranch(papilionidae, "Papilio");
		IClassification protographium = createButterflyBranch(papilionidae, "Protographium");

		Allele.lepiCitrusSwallow = new AlleleButterflySpecies("lepiCitrusSwallow", false, "Citrus Swallowtail", papilio, "demodocus", 0xeae389).setRarity(1.0f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiEmeraldPeacock = new AlleleButterflySpecies("lepiEmeraldPeacock", true, "Emerald Peacock", papilio, "palinurus", 0x7cfe80)
		.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiThoasSwallow = new AlleleButterflySpecies("lepiThoasSwallow", false, "Thoas Swallowtail", papilio, "thoas", 0xeac783).setRarity(0.2f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiSpicebush = new AlleleButterflySpecies("lepiSpicebush", true, "Spicebush Swallowtail", papilio, "troilus", 0xeefeff).setRarity(0.5f);
		Allele.lepiBlackSwallow = new AlleleButterflySpecies("lepiBlackSwallow", true, "Black Swallowtail", papilio, "polyxenes", 0xeac783).setRarity(1.0f);

		Allele.lepiZebraSwallow = new AlleleButterflySpecies("lepiZebraSwallow", true, "Zebra Swallowtail", protographium, "marcellus", 0xeafeff).setRarity(0.5f);
		Allele.lepiDianaFrit = new AlleleButterflySpecies("lepiDianaFrit", true, "Diana Fritillary", protographium, "diana", 0xffac05).setRarity(0.6f);

		/* NYMPHALIDAE */
		IClassification pararge = createButterflyBranch(nymphalidae, "Pararge");
		IClassification polygonia = createButterflyBranch(nymphalidae, "Polygonia");
		IClassification morpho = createButterflyBranch(nymphalidae, "Morpho");
		IClassification greta = createButterflyBranch(nymphalidae, "Greta");
		IClassification batesia = createButterflyBranch(nymphalidae, "Batesia");
		IClassification myscelia = createButterflyBranch(nymphalidae, "Myscelia");
		IClassification danaus = createButterflyBranch(nymphalidae, "Danaus");
		IClassification bassarona = createButterflyBranch(nymphalidae, "Bassarona");
		IClassification parantica = createButterflyBranch(nymphalidae, "Parantica");
		IClassification heliconius = createButterflyBranch(nymphalidae, "Heliconius");
		IClassification siproeta = createButterflyBranch(nymphalidae, "Siproeta");
		IClassification cethosia = createButterflyBranch(nymphalidae, "Cethosia");

		Allele.lepiGlasswing = new AlleleButterflySpecies("lepiGlasswing", true, "Glasswing", greta, "oto", 0x583732)
		.setTemperature(EnumTemperature.WARM);

		Allele.lepiSpeckledWood = new AlleleButterflySpecies("lepiSpeckledWood", true, "Speckled Wood", pararge, "aegeria", 0x947245).setRarity(1.0f);
		Allele.lepiMadeiranSpeckledWood = new AlleleButterflySpecies("lepiMSpeckledWood", true, "Madeiran Speckled Wood", pararge, "xiphia", 0x402919).setRarity(0.5f);
		Allele.lepiCanarySpeckledWood = new AlleleButterflySpecies("lepiCSpeckledWood", true, "Canary Speckled Wood", pararge, "xiphioides", 0x51372a).setRarity(0.5f);

		Allele.lepiMenelausBlueMorpho = new AlleleButterflySpecies("lepiMBlueMorpho", true, "Menelaus Blue Morpho", morpho, "menelaus", 0x72e1fd).setRarity(0.5f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiPeleidesBlueMorpho = new AlleleButterflySpecies("lepiPBlueMorpho", true, "Peleides Blue Morpho", morpho, "peleides", 0x6ecce8).setRarity(0.25f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiRhetenorBlueMorpho = new AlleleButterflySpecies("lepiRBlueMorpho", true, "Rhetenor Blue Morpho", morpho, "rhetenor", 0x00bef8)
		.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);

		Allele.lepiComma = new AlleleButterflySpecies("lepiComma", true, "Comma", polygonia, "c-album", 0xf89505).setRarity(0.3f);
		Allele.lepiBatesia = new AlleleButterflySpecies("lepiBatesia", true, "Painted Beauty", batesia, "hypochlora", 0xfe7763).setRarity(0.3f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiBlueWing = new AlleleButterflySpecies("lepiBlueWing", true, "Blue Wing", myscelia, "ethusa", 0x3a93cc).setRarity(0.3f);

		Allele.lepiMonarch = new AlleleButterflySpecies("lepiMonarch", true, "Monarch", danaus, "plexippus", 0xffa722).setRarity(0.2f);
		Allele.lepiBlueDuke = new AlleleButterflySpecies("lepiBlueDuke", true, "Blue Duke", bassarona, "durga", 0x304240).setRarity(0.5f)
				.setTemperature(EnumTemperature.COLD);
		Allele.lepiGlassyTiger = new AlleleButterflySpecies("lepiGlassyTiger", true, "Glassy Tiger", parantica, "aglea", 0x5b3935).setRarity(0.3f);
		Allele.lepiPostman = new AlleleButterflySpecies("lepiPostman", true, "Postman", heliconius, "melpmene", 0xf7302d).setRarity(0.3f);
		Allele.lepiMalachite = new AlleleButterflySpecies("lepiMalachite", true, "Malachite", siproeta, "stelenes", 0xbdff53).setRarity(0.5f)
				.setTemperature(EnumTemperature.WARM).setHumidity(EnumHumidity.DAMP);
		Allele.lepiLLacewing = new AlleleButterflySpecies("lepiLLacewing", true, "Leopard Lacewing", cethosia, "cyane", 0xfb8a06).setRarity(0.7f);

		Allele.butterflyNone = new AlleleEffectNone("bfNone", false);
	}

	private void registerTemplates() {
		butterflyInterface.registerTemplate(ButterflyTemplates.getBrimstoneMothTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getLatticedHeathTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getAtlasMothTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getCabbageWhiteTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getGlasswingTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getEmeraldPeacockTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getThoasSwallowTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getCitrusSwallowTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getBlackSwallowTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getZebraSwallowTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getDianaFritTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getSpeckledWoodTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getMadeiranSpeckledWoodTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getCanarySpeckledWoodTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getMenelausBlueMorphoTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getRhetenorBlueMorphoTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getPeleidesBlueMorphoTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getBrimstoneTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getAuroraTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getPostillionTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getPalaenoSulphurTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getResedaTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getSpringAzureTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getGozoraAzureTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getCommaTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getBatesiaTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getBlueWingTemplate());

		butterflyInterface.registerTemplate(ButterflyTemplates.getBlueDukeTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getGlassyTigerTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getMonarchTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getPostmanTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getSpicebushTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getMalachiteTemplate());
		butterflyInterface.registerTemplate(ButterflyTemplates.getLeopardLacewingTemplate());

	}

	private IClassification createButterflyBranch(IClassification family, String scientific) {
		IClassification branch = new Branch("moth." + scientific.toLowerCase(Locale.ENGLISH), scientific);
		branch.setParent(family);
		return branch;
	}

	@Override
	public IGuiHandler getGuiHandler() {
		return new GuiHandlerLepidopterology();
	}
}
