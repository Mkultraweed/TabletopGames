package updated_core.turn_order;

import updated_core.players.AbstractPlayer;

public abstract class TurnOrder {

    public abstract void endPlayerTurn();

    public abstract AbstractPlayer getCurrentPlayer();
}
