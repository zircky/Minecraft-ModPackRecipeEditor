# Phase 16 — Steps 1–4 architecture audit

Base: Phase 15 commit 012173a. origin/master remains 63627c3; Phase 15 is not merged.
Branch: feat/phase-16-modded-recipe-support. User manual projects/logs are preserved.

## Cause of UNSUPPORTED

DefaultStaticAnalysisService -> Minecraft1201Integration -> ProjectRecipeParsers
registers Vanilla1201Parsers and GregTechRecipeParser only. StaticRecipeScanner
finds no handler for botania:brew and intentionally retains unsupported raw JSON.
There is no separate RecipeTypeRegistry: serializer resolution is the parser list.
RecipeLayoutResolver currently has nine Vanilla layouts and explicit registration
hooks; it never guesses a mod recipe from its namespace. ExportCapabilityResolver
resolves format exporters independently; VanillaExportSupport rejects Botania.
RecipeValidator delegates semantic validation to parsers, while RecipeChangeSet,
ProjectDocument and RecipeMergeService preserve original JSON and provenance.
RuntimeRecipeBrowserView displays normalized snapshot state separately; it is not
source serializer JSON and does not become editable through a static adapter.

## Extension

Reuse RecipeParser. ModdedRecipeAdapter supplies exact type descriptors, machine
layout metadata and independent capabilities. ModdedRecipeAdapterRegistry rejects
ambiguous keys at registration. RecipeMachineLayout is a JavaFX-free schema with
variable groups and distinct item/block/fluid/chemical channels. Existing domain
inputs/outputs, RecipeProperty and raw JSON retain amounts, custom properties,
conditions and stages without assigning defaults for unknown mod semantics.
BrewDefinition/RecipeOutput.Brew represent a declared brew reference independently
of ItemStack. Analysis treats this result as opaque, never as a vanilla item.

Steps 1–4 add only Botania brew, petal_apothecary and mana_infusion. Initial
capabilities are structural parsing and read-only viewing. Editing, creation,
export integration and the other mods remain for separately requested later steps.

## Verified artifact

Botania-1.20.1-456-FORGE.jar, SHA256
3cf540357c9cb5e460266b17a02617656df6cc7090a565fca2a462b1c3aba4ae.
Inspected original JSON, META-INF/MANIFEST.MF and javap bytecode of common.crafting
BotanicalBreweryRecipe.Serializer, PetalsRecipe.Serializer, ManaInfusionRecipe.Serializer,
StateIngredientHelper and api.brew.Brew. No Minecraft JAR is modified.
The three serializer groups contain 20 / 43 / 139 recipes respectively.
Brew translation key is derived from the declared brew registry ID using the
verified Brew.getTranslationKey format, not from the Recipe ID.

Reference project: https://github.com/VazkiiMods/Botania (Vazkii and contributors).
Fixtures are read from the user's installed artifact during opt-in external tests;
Botania code/assets/JSON are not copied into this repository or redistributed.
