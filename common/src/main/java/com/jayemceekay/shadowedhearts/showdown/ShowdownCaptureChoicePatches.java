package com.jayemceekay.shadowedhearts.showdown;

final class ShowdownCaptureChoicePatches {
    private ShowdownCaptureChoicePatches() {}

    // While Cobblemon resolves a capture, it holds the choices for this turn. Removing the last
    // actionable Pokémon would otherwise let the opponent's input finish both sides' choices.
    static final LiteralShowdownPatch CAPTURE_CHOICE = new LiteralShowdownPatch("pokemon.captureChoice",
            "  capture() {", """
              capture() {
                this.hp = 0;
            """.stripTrailing(), """
              capture() {
                if (!this.fainted && this.side.requestState === "move" && this.side.active.includes(this)
                    && this.side.active.every(ally => !ally || ally === this || ally.fainted || ally.volatiles["commanding"])
                    && !this.side.isChoiceDone()) {
                  this.side.choice.shadowedHeartsAwaitingCaptureChoice = true;
                }
                this.hp = 0;
            """.stripTrailing());

    // Side.choose and each fresh request replace the choice object, consuming the marker.
    static final LiteralShowdownPatch AWAIT_CAPTURE_CHOICE = new LiteralShowdownPatch("side.awaitCaptureChoice",
            "  isChoiceDone() {", """
              isChoiceDone() {
                if (!this.requestState)
            """.stripTrailing(), """
              isChoiceDone() {
                if (this.choice.shadowedHeartsAwaitingCaptureChoice) return false;
                if (!this.requestState)
            """.stripTrailing());
}
