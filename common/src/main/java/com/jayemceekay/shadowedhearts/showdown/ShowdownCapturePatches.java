package com.jayemceekay.shadowedhearts.showdown;

/** Supported capture source shapes, shared by runtime patching and packaged-runtime tests. */
final class ShowdownCapturePatches {
    private ShowdownCapturePatches() {}

    static final LiteralShowdownPatch MOVE_TARGET = new LiteralShowdownPatch("battle.capturedAllyTarget",
            "        this.actions.runMove(", """
                    if (action.pokemon.fainted)
                      return false;
                    this.actions.runMove(
                      action.move,
            """.stripTrailing(), """
                    if (action.pokemon.fainted)
                      return false;
                    // An explicitly targeted ally may have been replaced while this action was queued.
                    if (action.originalTarget?.captured && action.originalTarget.isAlly(action.pokemon) &&
                        this.actions.targetTypeChoices(this.dex.moves.get(action.maxMove || action.zmove || action.move).target)) break;
                    this.actions.runMove(
                      action.move,
            """.stripTrailing());

    static final LiteralShowdownPatch BAG_ITEM = new LiteralShowdownPatch("battle.capturedBagItem",
            "    this.add(\"bagitem\", pokemon.uuid, itemName, itemId);", """
                if (this.ended) {
                  return;
                }
                this.add("bagitem", pokemon.uuid, itemName, itemId);
            """.stripTrailing(), """
                if (this.ended || pokemon.captured) {
                  return;
                }
                this.add("bagitem", pokemon.uuid, itemName, itemId);
            """.stripTrailing());

    static final LiteralShowdownPatch REVIVAL_AVAILABLE = new LiteralShowdownPatch("moves.revivalAvailable",
            "    slotCondition: \"revivalblessing\",", """
                  if (!source.side.pokemon.filter((ally) => ally.fainted).length) {
                    return false;
                  }
                },
                slotCondition: "revivalblessing",
            """.stripTrailing(), """
                  if (!source.side.pokemon.some((ally) => ally.fainted && !ally.captured)) {
                    return false;
                  }
                },
                slotCondition: "revivalblessing",
            """.stripTrailing());

    static final LiteralShowdownPatch REVIVAL_AUTO_CHOICE = new LiteralShowdownPatch("side.revivalAutoChoice",
            "      if (this.slotConditions[pokemon.position][\"revivalblessing\"]) {", """
                  if (this.slotConditions[pokemon.position]["revivalblessing"]) {
                    slot = 0;
                    while (!this.pokemon[slot].fainted)
                      slot++;
                  } else {
            """.stripTrailing(), """
                  if (this.slotConditions[pokemon.position]["revivalblessing"]) {
                    slot = this.pokemon.findIndex((ally) => ally.fainted && !ally.captured);
                    if (slot < 0) {
                      this.removeSlotCondition(pokemon, "revivalblessing");
                      pokemon.switchFlag = false;
                      this.choice.forcedSwitchesLeft = Math.max(0, this.choice.forcedSwitchesLeft - 1);
                      return this.choosePass();
                    }
                    slotText = String(slot + 1);
                  } else {
            """.stripTrailing());

    static final LiteralShowdownPatch REVIVAL_TARGET = new LiteralShowdownPatch("side.revivalTarget",
            "    const targetPokemon = this.pokemon[slot];", """
                const targetPokemon = this.pokemon[slot];
                if (this.slotConditions[pokemon.position]["revivalblessing"]) {
                  if (!targetPokemon.fainted) {
            """.stripTrailing(), """
                const targetPokemon = this.pokemon[slot];
                if (this.slotConditions[pokemon.position]["revivalblessing"]) {
                  if (!targetPokemon.fainted || targetPokemon.captured) {
            """.stripTrailing());

    static final LiteralShowdownPatch CAPTURE = new LiteralShowdownPatch("battle.capture",
            "  capture(pokemon) {", """
              capture(pokemon) {
                if (this.ended)
                  return;
                if (!pokemon.fainted) {
                  this.add("capture", pokemon);
                  if (pokemon.side.pokemonLeft)
                    pokemon.side.pokemonLeft--;
                  this.singleEvent("End", pokemon.getAbility(), pokemon.abilityState, pokemon);
                  pokemon.clearVolatile(false);
                  pokemon.fainted = true;
                  pokemon.illusion = null;
                  pokemon.isActive = false;
                  pokemon.isStarted = false;
                  pokemon.side.faintedThisTurn = pokemon;
                  if (this.checkWin())
                    return true;
                }
                return false;
              }
            """.stripTrailing(), """
              capture(pokemon) {
                if (this.ended)
                  return;
                if (!pokemon.fainted) {
                  this.add("capture", pokemon);
                  if (pokemon.side.pokemonLeft) pokemon.side.pokemonLeft--;
                  this.singleEvent("End", pokemon.getAbility(), pokemon.abilityState, pokemon);
                  pokemon.clearVolatile(false);
                  pokemon.fainted = true;
                  pokemon.captured = true;
                  pokemon.status = "fnt";
                  pokemon.illusion = null;
                  pokemon.isActive = false;
                  pokemon.isStarted = false;
                  pokemon.side.faintedThisTurn = pokemon;
                  if (this.checkWin()) return true;
                  this.checkFainted();
                }
                return false;
              }
            """.stripTrailing());

    static final LiteralShowdownPatch POSSIBLE_SWITCHES = new LiteralShowdownPatch("battle.possibleSwitches",
            "  possibleSwitches(side) {", """
              possibleSwitches(side) {
                if (!side.pokemonLeft)
                  return [];
                const canSwitchIn = [];
                for (let i = side.active.length; i < side.pokemon.length; i++) {
                  const pokemon = side.pokemon[i];
                  if (!pokemon.fainted) {
                    canSwitchIn.push(pokemon);
                  }
                }
                return canSwitchIn;
              }
            """.stripTrailing(), """
              possibleSwitches(side) {
                if (!side.pokemonLeft)
                  return [];
                const canSwitchIn = [];
                for (let i = side.active.length; i < side.pokemon.length; i++) {
                  const pokemon = side.pokemon[i];
                  if (!pokemon.fainted && !pokemon.captured) {
                    canSwitchIn.push(pokemon);
                  }
                }
                return canSwitchIn;
              }
            """.stripTrailing());

    static final LiteralShowdownPatch CAPTURED_FLAG = new LiteralShowdownPatch("pokemon.capturedFlag",
            "    this.m = {};", """
                this.m = {};
                const pokemonScripts = this.battle.format.pokemon || this.battle.dex.data.Scripts.pokemon;
            """.stripTrailing(), """
                this.m = {};
                this.captured = false;
                const pokemonScripts = this.battle.format.pokemon || this.battle.dex.data.Scripts.pokemon;
            """.stripTrailing());
}
